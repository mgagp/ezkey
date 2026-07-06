package org.ezkey.security;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KeysetManager;
import com.google.crypto.tink.KeysetReader;
import com.google.crypto.tink.KeysetWriter;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.ezkey.config.TinkProperties;
import org.ezkey.config.TinkProperties.Keyset.StorageMode;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Central manager for Tink keyset lifecycle: load/create, provide primitives, and prepare for
 * rotation.
 *
 * <p>This implementation uses master key encryption for keysets:
 *
 * <ul>
 *   <li>Master key is loaded from a secure file (Base64-encoded 256-bit key)
 *   <li>Keysets are encrypted with master key using AES-256-GCM
 *   <li>Master key file must have secure permissions (600 on Unix/Linux)
 * </ul>
 *
 * <p><b>Security Properties:</b>
 *
 * <ul>
 *   <li>Master key stored outside application directory
 *   <li>Keysets encrypted at rest on disk
 *   <li>Zero-downtime key rotation support (future)
 * </ul>
 *
 * @since 2025
 */
@Component
public class TinkKeyManager implements KeyManagementOperations {

  private static final Logger logger = LoggerFactory.getLogger(TinkKeyManager.class);

  private final TinkProperties properties;
  private final KeysetBlobRepository keysetBlobRepository;

  private volatile KeysetHandle keysetHandle;
  private volatile long keysetFileLastModified = 0;
  private volatile String keysetFilePath;
  private volatile Aead masterAead;
  private volatile long databaseKeysetVersion = 0;

  /**
   * ThreadLocal guard to prevent recursive database calls during keyset reload.
   *
   * <p>When getAeadPrimitive() checks the database for version changes, this can trigger JPA
   * operations that use EncryptionEntityListener, which calls getAeadPrimitive() again, causing a
   * StackOverflowError. This flag prevents that recursion.
   */
  private static final ThreadLocal<Boolean> CHECKING_DATABASE =
      ThreadLocal.withInitial(() -> false);

  /**
   * Timestamp of last database version check to throttle frequency.
   *
   * <p>We don't need to check the database on every getAeadPrimitive() call. Checking every 5
   * seconds is sufficient for synchronization while avoiding performance issues.
   */
  private volatile long lastDatabaseCheckTime = 0;

  /** Minimum interval between database version checks in milliseconds. */
  private static final long DATABASE_CHECK_INTERVAL_MS = 5000;

  /**
   * Constructor with optional KeysetBlobRepository for database-backed keyset storage.
   *
   * <p>Uses Spring's ObjectProvider to handle optional KeysetBlobRepository gracefully. This allows
   * the manager to work in FILE mode even when the repository is not available.
   *
   * @param properties Tink configuration properties
   * @param keysetBlobRepositoryProvider optional provider for database keyset storage
   */
  public TinkKeyManager(
      TinkProperties properties,
      org.springframework.beans.factory.ObjectProvider<KeysetBlobRepository>
          keysetBlobRepositoryProvider) {
    this.properties = Objects.requireNonNull(properties, "properties");
    this.keysetBlobRepository = keysetBlobRepositoryProvider.getIfAvailable();
    try {
      AeadConfig.register();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to register Tink AeadConfig", e);
    }
  }

  /**
   * Initialize Tink encryption with master key and keyset.
   *
   * <p>This method is called automatically after dependency injection. It loads the master key,
   * creates the master AEAD, and loads or creates the keyset.
   *
   * <p>If encryption is disabled or master key is not configured, initialization is skipped and the
   * service will operate without encryption (backward compatible mode).
   */
  @PostConstruct
  public void initialize() {
    if (!properties.isEnabled()) {
      if (properties.isRequired()) {
        throw new IllegalStateException(
            "Tink encryption is required (ezkey.encryption.required=true) but"
                + " ezkey.encryption.enabled=false");
      }
      logger.info("Tink encryption is disabled via configuration");
      return;
    }

    try {
      logger.info("Initializing Tink encryption...");

      byte[] masterKey = loadMasterKeyFromFile();
      logger.info("Master key loaded from file");

      this.masterAead = createMasterAead(masterKey);
      logger.info("Master AEAD created");

      String keysetPath = properties.getKeysetFile();
      StorageMode storageMode = properties.getKeyset().getStorageMode();
      logger.info("Keyset storage mode: {}", storageMode);

      boolean loadedFromDatabase = false;
      if ((storageMode == StorageMode.DATABASE || storageMode == StorageMode.HYBRID)
          && keysetBlobRepository != null) {
        try {
          loadedFromDatabase = loadKeysetFromDatabase();
          if (loadedFromDatabase) {
            logger.info("✅ Keyset loaded from database");
          }
        } catch (Exception e) {
          logger.warn(
              "Failed to load keyset from database, falling back to file: {}", e.getMessage());
          logger.debug("Database keyset load error", e);
        }
      }

      if (!loadedFromDatabase) {
        if (keysetPath == null || keysetPath.isBlank()) {
          logger.warn("ezkey.encryption.keyset-file is not configured. Encryption disabled.");
          return;
        }

        String normalizedKeysetPath = normalizePath(keysetPath);
        File keysetFile = new File(normalizedKeysetPath);

        logger.debug(
            "Keyset file check - Original path: {}, Normalized path: {}, Exists: {}",
            keysetPath,
            normalizedKeysetPath,
            keysetFile.exists());

        if (keysetFile.exists()) {
          logger.info("Loading existing keyset from: {}", normalizedKeysetPath);
          this.keysetHandle = loadEncryptedKeyset(normalizedKeysetPath);
          this.keysetFilePath = normalizedKeysetPath;
          this.keysetFileLastModified = keysetFile.lastModified();
          logger.info("✅ Keyset loaded successfully from: {}", normalizedKeysetPath);

          if ((storageMode == StorageMode.DATABASE || storageMode == StorageMode.HYBRID)
              && keysetBlobRepository != null) {
            try {
              saveKeysetToDatabase("STARTUP_FILE_SYNC");
              logger.info("✅ Keyset synchronized to database from file");
            } catch (Exception e) {
              logger.warn("Failed to sync keyset to database: {}", e.getMessage());
            }
          }
        } else {
          logger.warn(
              "Keyset file not found at: {}. Generating new keyset. "
                  + "NOTE: If this is not the first startup, check that the keyset file exists "
                  + "and is accessible. All APIs must use the same keyset file.",
              normalizedKeysetPath);
          this.keysetHandle = generateAndSaveKeyset(normalizedKeysetPath);
          this.keysetFilePath = normalizedKeysetPath;
          this.keysetFileLastModified = keysetFile.lastModified();
          logger.info("🆕 New keyset created and saved to: {}", normalizedKeysetPath);

          if ((storageMode == StorageMode.DATABASE || storageMode == StorageMode.HYBRID)
              && keysetBlobRepository != null) {
            try {
              saveKeysetToDatabase("STARTUP_NEW_KEYSET");
              logger.info("✅ New keyset saved to database");
            } catch (Exception e) {
              logger.warn("Failed to save new keyset to database: {}", e.getMessage());
            }
          }
        }
      }

      verifyKeyset();

      logger.info("Tink encryption ready for operation");
    } catch (FileNotFoundException e) {
      logger.warn(
          "Master key file not found. Encryption will be disabled. "
              + "To enable encryption, run: sudo ./scripts/generate-master-key.sh (Linux/Mac) "
              + "or .\\scripts\\generate-master-key.ps1 (Windows as Administrator)",
          e);
    } catch (Exception e) {
      logger.error(
          "Failed to initialize Tink encryption. Encryption will be disabled. "
              + "Application will continue without encryption at rest.",
          e);
    }

    enforceRequiredEncryption();
  }

  /**
   * Fails startup when encryption is marked required but Tink did not initialize (SEC-002).
   *
   * @throws IllegalStateException when {@code ezkey.encryption.required=true} and encryption is not
   *     ready
   */
  void enforceRequiredEncryption() {
    if (!properties.isRequired()) {
      return;
    }
    if (!properties.isEnabled()) {
      throw new IllegalStateException(
          "Tink encryption is required (ezkey.encryption.required=true) but"
              + " ezkey.encryption.enabled=false");
    }
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption is required (ezkey.encryption.required=true) but initialization failed."
              + " Verify master key file, keyset configuration, and startup logs.");
    }
  }

  public boolean isInitialized() {
    return keysetHandle != null && masterAead != null;
  }

  public synchronized KeysetHandle getKeysetHandle() {
    if (keysetHandle == null) {
      throw new IllegalStateException(
          "TinkKeyManager not initialized. "
              + "Check that master key file exists and encryption is enabled in configuration.");
    }
    return keysetHandle;
  }

  public synchronized Aead getAeadPrimitive() {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption not initialized. "
              + "Check that master key file exists and encryption is enabled.");
    }

    boolean shouldCheckForUpdates = !CHECKING_DATABASE.get();
    long now = System.currentTimeMillis();
    boolean throttled = (now - lastDatabaseCheckTime) < DATABASE_CHECK_INTERVAL_MS;

    if (shouldCheckForUpdates && !throttled) {
      try {
        CHECKING_DATABASE.set(true);
        lastDatabaseCheckTime = now;
        checkAndReloadKeysetIfNeeded();
      } finally {
        CHECKING_DATABASE.set(false);
      }
    }

    try {
      return keysetHandle.getPrimitive(Aead.class);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unable to obtain AEAD primitive from keyset", e);
    }
  }

  private void checkAndReloadKeysetIfNeeded() {
    StorageMode storageMode = properties.getKeyset().getStorageMode();

    if ((storageMode == StorageMode.DATABASE || storageMode == StorageMode.HYBRID)
        && keysetBlobRepository != null) {
      try {
        var dbVersion = keysetBlobRepository.findVersion();
        if (dbVersion.isPresent() && dbVersion.get() > databaseKeysetVersion) {
          logger.info(
              "🔄 Database keyset updated (version: {} > {}), reloading keyset...",
              dbVersion.get(),
              databaseKeysetVersion);
          if (loadKeysetFromDatabase()) {
            logger.info("✅ Keyset reloaded from database successfully");
          }
        }
      } catch (Exception e) {
        logger.warn(
            "Failed to check/reload keyset from database. Using cached keyset. Error: {}",
            e.getMessage());
        logger.debug("Database keyset reload error", e);
      }
    }

    if ((storageMode == StorageMode.FILE || storageMode == StorageMode.HYBRID)
        && keysetFilePath != null) {
      try {
        File keysetFile = new File(keysetFilePath);
        if (keysetFile.exists()) {
          long currentLastModified = keysetFile.lastModified();
          if (currentLastModified > keysetFileLastModified) {
            logger.info(
                "🔄 Keyset file modified (last modified: {} > {}), reloading keyset...",
                currentLastModified,
                keysetFileLastModified);
            this.keysetHandle = loadEncryptedKeyset(keysetFilePath);
            this.keysetFileLastModified = currentLastModified;
            logger.info("✅ Keyset reloaded from file successfully");
          }
        }
      } catch (Exception e) {
        logger.warn(
            "Failed to check/reload keyset file. Using cached keyset. Error: {}", e.getMessage());
        logger.debug("Keyset reload error", e);
      }
    }
  }

  private static long toUnsignedLong(int signedKeyId) {
    return Integer.toUnsignedLong(signedKeyId);
  }

  private static long toUnsignedLong(long signedKeyId) {
    if (signedKeyId >= 0) {
      return signedKeyId;
    }
    if (signedKeyId >= Integer.MIN_VALUE && signedKeyId <= Integer.MAX_VALUE) {
      return Integer.toUnsignedLong((int) signedKeyId);
    }
    throw new IllegalArgumentException(
        "Key ID value out of range for database storage: "
            + signedKeyId
            + " (unsigned: "
            + Long.toUnsignedString(signedKeyId)
            + "). "
            + "Tink should not generate such values. If this occurs, the key ID may be too large "
            + "for PostgreSQL BIGINT with CHECK >= 0 constraint.");
  }

  public long getCurrentPrimaryKeyId() {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption not initialized. "
              + "Check that master key file exists and encryption is enabled.");
    }
    long signedKeyId = keysetHandle.getKeysetInfo().getPrimaryKeyId();
    return toUnsignedLong(signedKeyId);
  }

  private void verifyKeyset() throws GeneralSecurityException {
    Aead aead = keysetHandle.getPrimitive(Aead.class);
    String testData = "verification-test";
    byte[] ciphertext = aead.encrypt(testData.getBytes(StandardCharsets.UTF_8), null);
    byte[] plaintext = aead.decrypt(ciphertext, null);

    if (!testData.equals(new String(plaintext, StandardCharsets.UTF_8))) {
      throw new GeneralSecurityException("❌ Keyset verification failed");
    }

    logger.info("✅ Keyset verified operational");
  }

  private byte[] loadMasterKeyFromFile() throws IOException {
    String filePath = properties.getMasterKeyFile();

    if (filePath == null || filePath.isBlank()) {
      throw new IllegalStateException(
          "❌ Master key file path not configured. "
              + "Set ezkey.encryption.master-key-file in application.properties");
    }

    String normalizedPath = normalizePath(filePath);
    Path path = Path.of(normalizedPath);

    if (!Files.exists(path)) {
      throw new FileNotFoundException(
          "❌ Master key file not found: "
              + normalizedPath
              + " (original path: "
              + filePath
              + ")\n"
              + "Run: sudo ./scripts/generate-master-key.sh (Linux/Mac) or "
              + ".\\scripts\\generate-master-key.ps1 (Windows as Administrator)");
    }

    if (!isWindows()) {
      verifyFilePermissions(path);
    }

    String masterKeyBase64 = Files.readString(path, StandardCharsets.UTF_8).trim();

    if (masterKeyBase64.isEmpty()) {
      throw new IllegalStateException("❌ Master key file is empty: " + normalizedPath);
    }

    logger.info("🔑 Master key loaded from: {}", normalizedPath);
    return Base64.getDecoder().decode(masterKeyBase64);
  }

  private void verifyFilePermissions(Path path) throws IOException {
    Set<java.nio.file.attribute.PosixFilePermission> permissions =
        Files.getPosixFilePermissions(path);

    if (!permissions.contains(java.nio.file.attribute.PosixFilePermission.OWNER_READ)) {
      throw new SecurityException("❌ Master key file must be readable by owner");
    }

    Set<java.nio.file.attribute.PosixFilePermission> forbidden =
        Set.of(
            java.nio.file.attribute.PosixFilePermission.GROUP_READ,
            java.nio.file.attribute.PosixFilePermission.GROUP_WRITE,
            java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
            java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
            java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE,
            java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);

    for (java.nio.file.attribute.PosixFilePermission perm : forbidden) {
      if (permissions.contains(perm)) {
        throw new SecurityException(
            "❌ Master key file has insecure permissions: "
                + java.nio.file.attribute.PosixFilePermissions.toString(permissions)
                + "\n"
                + "Run: sudo chmod 600 "
                + path);
      }
    }

    logger.info(
        "🔒 Master key file permissions verified: {}",
        java.nio.file.attribute.PosixFilePermissions.toString(permissions));
  }

  private Aead createMasterAead(byte[] masterKey) throws GeneralSecurityException {
    if (masterKey.length != 32) {
      throw new IllegalArgumentException(
          "Master key must be exactly 32 bytes (256 bits), got: " + masterKey.length);
    }
    return new AesGcmJce(masterKey);
  }

  private KeysetHandle loadEncryptedKeyset(String keysetPath)
      throws GeneralSecurityException, IOException {
    String normalizedPath = normalizePath(keysetPath);
    logger.info("📂 Loading encrypted keyset from: {}", normalizedPath);

    File keysetFile = new File(normalizedPath);
    if (!keysetFile.exists()) {
      throw new FileNotFoundException("Keyset file not found: " + normalizedPath);
    }
    if (keysetFile.length() == 0) {
      throw new IllegalStateException("Keyset file is empty: " + normalizedPath);
    }

    try (FileInputStream fis = new FileInputStream(keysetFile)) {
      KeysetReader reader = JsonKeysetReader.withInputStream(fis);
      KeysetHandle handle = KeysetHandle.read(reader, masterAead);

      long signedPrimaryKeyId = handle.getKeysetInfo().getPrimaryKeyId();
      long unsignedPrimaryKeyId = toUnsignedLong(signedPrimaryKeyId);
      logger.info(
          "✅ Keyset decrypted successfully. Primary key ID: {} (signed: {})",
          unsignedPrimaryKeyId,
          signedPrimaryKeyId);
      return handle;
    } catch (GeneralSecurityException e) {
      logger.error(
          "❌ Failed to decrypt keyset from: {}. "
              + "This usually means the master key is incorrect or the keyset was encrypted "
              + "with a different master key. Ensure all APIs use the same master key file.",
          normalizedPath);
      throw e;
    }
  }

  private KeysetHandle generateAndSaveKeyset(String keysetPath)
      throws GeneralSecurityException, IOException {
    logger.info("🆕 Generating new keyset...");
    String normalizedPath = normalizePath(keysetPath);
    var template = getKeyTemplate();
    KeysetHandle newKeyset = KeysetHandle.generateNew(template);

    File keysetFile = new File(normalizedPath);
    File parentDir = keysetFile.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      if (!parentDir.mkdirs()) {
        logger.warn("Could not create keyset directory: {}", parentDir);
      }
    }

    try (FileOutputStream fos = new FileOutputStream(keysetFile)) {
      KeysetWriter writer = JsonKeysetWriter.withOutputStream(fos);
      newKeyset.write(writer, masterAead);
    }

    if (!isWindows()) {
      setSecureFilePermissions(keysetFile.toPath());
    }

    logger.info("✅ New keyset generated and encrypted");
    return newKeyset;
  }

  private void setSecureFilePermissions(Path path) throws IOException {
    Set<java.nio.file.attribute.PosixFilePermission> permissions =
        Set.of(
            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
    Files.setPosixFilePermissions(path, permissions);
    logger.info("🔒 Set file permissions to 600: {}", path);
  }

  private com.google.crypto.tink.KeyTemplate getKeyTemplate() throws GeneralSecurityException {
    String algorithm = properties.getAlgorithm();
    return switch (algorithm) {
      case "CHACHA20_POLY1305" -> KeyTemplates.get("CHACHA20_POLY1305");
      case "AES256_GCM" -> KeyTemplates.get("AES256_GCM");
      default -> {
        logger.warn("Unknown algorithm: {}, using AES256_GCM", algorithm);
        try {
          yield KeyTemplates.get("AES256_GCM");
        } catch (GeneralSecurityException e) {
          throw new IllegalStateException("Failed to get AES256_GCM template", e);
        }
      }
    };
  }

  private String normalizePath(String path) {
    if (path == null || path.isBlank()) {
      return path;
    }

    if (isWindows() && path.startsWith("/") && path.length() >= 3) {
      char firstChar = path.charAt(1);
      char secondChar = path.length() > 2 ? path.charAt(2) : 0;

      if (Character.isLetter(firstChar) && secondChar == '/') {
        char driveLetter = Character.toUpperCase(firstChar);
        String restOfPath = path.substring(3);
        String windowsPath = restOfPath.replace('/', '\\');
        return driveLetter + ":\\" + windowsPath;
      }
    }

    return path;
  }

  private boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  public synchronized long rotateKey() throws GeneralSecurityException, IOException {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption not initialized. Cannot rotate keys without initialized keyset.");
    }

    logger.info("🔄 Starting key rotation...");
    long signedOldPrimaryKeyId = keysetHandle.getKeysetInfo().getPrimaryKeyId();
    long oldPrimaryKeyIdUnsigned = toUnsignedLong(signedOldPrimaryKeyId);
    logger.info(
        "Current primary key ID: {} (unsigned: {})",
        signedOldPrimaryKeyId,
        Long.toUnsignedString(oldPrimaryKeyIdUnsigned));

    @SuppressWarnings("deprecation")
    var existingKeyIds =
        keysetHandle.getKeysetInfo().getKeyInfoList().stream()
            .map(keyInfo -> toUnsignedLong(keyInfo.getKeyId()))
            .collect(java.util.stream.Collectors.toSet());
    logger.debug("Existing key IDs before rotation: {}", existingKeyIds);

    com.google.crypto.tink.KeyTemplate template = getKeyTemplate();
    KeysetManager manager = KeysetManager.withKeysetHandle(keysetHandle);
    KeysetManager rotatedManager = manager.add(template);

    @SuppressWarnings("deprecation")
    var newKeysetInfo = rotatedManager.getKeysetHandle().getKeysetInfo();
    int signedNewKeyIdInt =
        newKeysetInfo.getKeyInfoList().stream()
            .filter(
                keyInfo -> {
                  long keyInfoUnsigned = toUnsignedLong(keyInfo.getKeyId());
                  return !existingKeyIds.contains(keyInfoUnsigned);
                })
            .findFirst()
            .map(keyInfo -> keyInfo.getKeyId())
            .orElseThrow(
                () -> new GeneralSecurityException("Failed to find new key after rotation"));

    KeysetHandle rotatedHandle = rotatedManager.setPrimary(signedNewKeyIdInt).getKeysetHandle();
    long signedNewPrimaryKeyId = rotatedHandle.getKeysetInfo().getPrimaryKeyId();
    long newPrimaryKeyId = toUnsignedLong(signedNewPrimaryKeyId);
    logger.info(
        "New primary key ID: {} (unsigned: {})",
        signedNewPrimaryKeyId,
        Long.toUnsignedString(newPrimaryKeyId));

    String keysetPath = properties.getKeysetFile();
    String normalizedPath = normalizePath(keysetPath);
    saveKeyset(rotatedHandle, normalizedPath);

    this.keysetHandle = rotatedHandle;
    File keysetFile = new File(normalizedPath);
    if (keysetFile.exists()) {
      this.keysetFileLastModified = keysetFile.lastModified();
      this.keysetFilePath = normalizedPath;
    }

    StorageMode storageMode = properties.getKeyset().getStorageMode();
    if ((storageMode == StorageMode.DATABASE || storageMode == StorageMode.HYBRID)
        && keysetBlobRepository != null) {
      try {
        saveKeysetToDatabase("KEY_ROTATION");
        logger.info("✅ Rotated keyset saved to database");
      } catch (Exception e) {
        logger.error("Failed to save rotated keyset to database: {}", e.getMessage());
      }
    }

    logger.info(
        "✅ Key rotation completed. Old primary: {} (unsigned: {}), New primary: {} (unsigned: {})",
        signedOldPrimaryKeyId,
        Long.toUnsignedString(oldPrimaryKeyIdUnsigned),
        newPrimaryKeyId,
        Long.toUnsignedString(newPrimaryKeyId));
    return newPrimaryKeyId;
  }

  public synchronized long addKeyWithoutPromotion() throws GeneralSecurityException, IOException {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption not initialized. Cannot add keys without initialized keyset.");
    }

    logger.info("🔄 Adding new key to keyset (without promotion)...");
    long signedCurrentPrimaryKeyId = keysetHandle.getKeysetInfo().getPrimaryKeyId();
    long currentPrimaryKeyIdUnsigned = toUnsignedLong(signedCurrentPrimaryKeyId);
    logger.info(
        "Current primary key ID (will remain primary): {} (unsigned: {})",
        signedCurrentPrimaryKeyId,
        Long.toUnsignedString(currentPrimaryKeyIdUnsigned));

    @SuppressWarnings("deprecation")
    var existingKeyIds =
        keysetHandle.getKeysetInfo().getKeyInfoList().stream()
            .map(keyInfo -> toUnsignedLong(keyInfo.getKeyId()))
            .collect(java.util.stream.Collectors.toSet());
    logger.debug("Existing key IDs before adding: {}", existingKeyIds);

    com.google.crypto.tink.KeyTemplate template = getKeyTemplate();
    KeysetManager manager = KeysetManager.withKeysetHandle(keysetHandle);
    KeysetManager updatedManager = manager.add(template);

    @SuppressWarnings("deprecation")
    var newKeysetInfo = updatedManager.getKeysetHandle().getKeysetInfo();
    long newKeyId =
        newKeysetInfo.getKeyInfoList().stream()
            .filter(
                keyInfo -> {
                  long keyInfoUnsigned = toUnsignedLong(keyInfo.getKeyId());
                  return !existingKeyIds.contains(keyInfoUnsigned);
                })
            .findFirst()
            .map(keyInfo -> toUnsignedLong(keyInfo.getKeyId()))
            .orElseThrow(() -> new GeneralSecurityException("Failed to find new key after adding"));

    KeysetHandle updatedHandle = updatedManager.getKeysetHandle();
    String keysetPath = properties.getKeysetFile();
    String normalizedPath = normalizePath(keysetPath);
    saveKeyset(updatedHandle, normalizedPath);

    this.keysetHandle = updatedHandle;
    File keysetFile = new File(normalizedPath);
    if (keysetFile.exists()) {
      this.keysetFileLastModified = keysetFile.lastModified();
      this.keysetFilePath = normalizedPath;
    }

    StorageMode storageMode = properties.getKeyset().getStorageMode();
    if ((storageMode == StorageMode.DATABASE || storageMode == StorageMode.HYBRID)
        && keysetBlobRepository != null) {
      try {
        saveKeysetToDatabase("KEY_ADDED_PENDING");
        logger.info("✅ Updated keyset (with new pending key) saved to database");
      } catch (Exception e) {
        logger.error("Failed to save updated keyset to database: {}", e.getMessage());
      }
    }

    logger.info(
        "✅ New key added (NOT promoted). New key ID: {} (unsigned: {}). "
            + "Current primary remains: {} (unsigned: {})",
        toSignedLong(newKeyId),
        Long.toUnsignedString(newKeyId),
        signedCurrentPrimaryKeyId,
        Long.toUnsignedString(currentPrimaryKeyIdUnsigned));

    return newKeyId;
  }

  public synchronized long promoteToPrimary(long keyId)
      throws GeneralSecurityException, IOException {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption not initialized. Cannot promote keys without initialized keyset.");
    }

    logger.info(
        "🔄 Promoting key {} (unsigned: {}) to PRIMARY...",
        toSignedLong(keyId),
        Long.toUnsignedString(keyId));

    long signedOldPrimaryKeyId = keysetHandle.getKeysetInfo().getPrimaryKeyId();
    long oldPrimaryKeyIdUnsigned = toUnsignedLong(signedOldPrimaryKeyId);
    logger.info(
        "Current primary key ID: {} (unsigned: {})",
        signedOldPrimaryKeyId,
        Long.toUnsignedString(oldPrimaryKeyIdUnsigned));

    int signedKeyIdInt = toSignedInt(keyId);
    @SuppressWarnings("deprecation")
    boolean keyExists =
        keysetHandle.getKeysetInfo().getKeyInfoList().stream()
            .anyMatch(keyInfo -> keyInfo.getKeyId() == signedKeyIdInt);

    if (!keyExists) {
      throw new GeneralSecurityException(
          "Key %s (unsigned: %s) not found in keyset. Cannot promote non-existent key."
              .formatted(signedKeyIdInt, Long.toUnsignedString(keyId)));
    }

    KeysetManager manager = KeysetManager.withKeysetHandle(keysetHandle);
    KeysetHandle promotedHandle = manager.setPrimary(signedKeyIdInt).getKeysetHandle();
    long newPrimaryKeyId = toUnsignedLong(promotedHandle.getKeysetInfo().getPrimaryKeyId());
    if (newPrimaryKeyId != keyId) {
      throw new GeneralSecurityException(
          "Key promotion failed. Expected primary: %s, actual: %s"
              .formatted(Long.toUnsignedString(keyId), Long.toUnsignedString(newPrimaryKeyId)));
    }

    String keysetPath = properties.getKeysetFile();
    String normalizedPath = normalizePath(keysetPath);
    saveKeyset(promotedHandle, normalizedPath);

    this.keysetHandle = promotedHandle;
    File keysetFile = new File(normalizedPath);
    if (keysetFile.exists()) {
      this.keysetFileLastModified = keysetFile.lastModified();
      this.keysetFilePath = normalizedPath;
    }

    StorageMode storageMode = properties.getKeyset().getStorageMode();
    if ((storageMode == StorageMode.DATABASE || storageMode == StorageMode.HYBRID)
        && keysetBlobRepository != null) {
      try {
        saveKeysetToDatabase("KEY_PROMOTED_PRIMARY");
        logger.info("✅ Keyset with promoted primary key saved to database");
      } catch (Exception e) {
        logger.error("Failed to save keyset to database after promotion: {}", e.getMessage());
      }
    }

    logger.info(
        "✅ Key promotion completed. Old primary: {} (unsigned: {}), New primary: {} (unsigned: {})",
        signedOldPrimaryKeyId,
        Long.toUnsignedString(oldPrimaryKeyIdUnsigned),
        toSignedLong(keyId),
        Long.toUnsignedString(keyId));

    return oldPrimaryKeyIdUnsigned;
  }

  private int toSignedInt(long unsignedKeyId) {
    return (int) unsignedKeyId;
  }

  private long toSignedLong(long unsignedKeyId) {
    return (int) unsignedKeyId;
  }

  private void saveKeyset(KeysetHandle handle, String keysetPath)
      throws IOException, GeneralSecurityException {
    if (properties.getRotation().isBackupBeforeRotation()) {
      createBackup(keysetPath);
    }

    File keysetFile = new File(keysetPath);
    File parentDir = keysetFile.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      if (!parentDir.mkdirs()) {
        logger.warn("Could not create keyset directory: {}", parentDir);
      }
    }

    try (FileOutputStream fos = new FileOutputStream(keysetFile)) {
      KeysetWriter writer = JsonKeysetWriter.withOutputStream(fos);
      handle.write(writer, masterAead);
    }

    if (!isWindows()) {
      setSecureFilePermissions(keysetFile.toPath());
    }

    logger.info("✅ Keyset saved to: {}", keysetPath);
  }

  private void createBackup(String keysetPath) throws IOException {
    File keysetFile = new File(keysetPath);
    if (!keysetFile.exists()) {
      logger.warn("Keyset file does not exist, skipping backup: {}", keysetPath);
      return;
    }

    String timestamp =
        OffsetDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
    File parentDir = keysetFile.getParentFile();
    String backupFilename = "keyset-backup-" + timestamp + ".json.encrypted";
    File backupFile = new File(parentDir, backupFilename);

    Files.copy(
        keysetFile.toPath(),
        backupFile.toPath(),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    if (!isWindows()) {
      setSecureFilePermissions(backupFile.toPath());
    }

    logger.info("✅ Keyset backup created: {}", backupFile.getAbsolutePath());
    cleanupOldBackups(parentDir);
  }

  private void cleanupOldBackups(File backupDir) {
    int retentionDays = properties.getRotation().getBackupRetentionDays();
    if (retentionDays <= 0) {
      return;
    }

    OffsetDateTime cutoffDate = OffsetDateTime.now().minusDays(retentionDays);
    File[] backupFiles =
        backupDir.listFiles(
            (dir, name) -> name.startsWith("keyset-backup-") && name.endsWith(".json.encrypted"));

    if (backupFiles == null) {
      return;
    }

    int deletedCount = 0;
    for (File backup : backupFiles) {
      try {
        OffsetDateTime backupTime =
            java.time.Instant.ofEpochMilli(backup.lastModified())
                .atOffset(java.time.ZoneOffset.UTC);
        if (backupTime.isBefore(cutoffDate)) {
          if (backup.delete()) {
            deletedCount++;
            logger.debug("Deleted old backup: {}", backup.getName());
          }
        }
      } catch (Exception e) {
        logger.warn("Failed to delete old backup: {}", backup.getName(), e);
      }
    }

    if (deletedCount > 0) {
      logger.info(
          "Cleaned up {} old backup files (retention: {} days)", deletedCount, retentionDays);
    }
  }

  @SuppressWarnings("deprecation")
  public List<Long> getAllKeyIds() {
    if (!isInitialized()) {
      throw new IllegalStateException("Tink encryption not initialized");
    }
    var keysetInfo = keysetHandle.getKeysetInfo();
    return keysetInfo.getKeyInfoList().stream()
        .map(keyInfo -> toUnsignedLong(keyInfo.getKeyId()))
        .toList();
  }

  @SuppressWarnings("deprecation")
  public Object getKeysetInfo() {
    if (!isInitialized()) {
      throw new IllegalStateException("Tink encryption not initialized");
    }
    return keysetHandle.getKeysetInfo();
  }

  public Aead getMasterAead() {
    return masterAead;
  }

  public boolean loadKeysetFromDatabase() {
    if (keysetBlobRepository == null) {
      logger.debug("KeysetBlobRepository not available, cannot load from database");
      return false;
    }

    if (masterAead == null) {
      logger.warn("Master AEAD not initialized, cannot decrypt keyset from database");
      return false;
    }

    try {
      var keysetBlobOpt = keysetBlobRepository.findKeyset();
      if (keysetBlobOpt.isEmpty()) {
        logger.debug("No keyset blob found in database");
        return false;
      }

      KeysetBlob keysetBlob = keysetBlobOpt.get();
      byte[] encryptedData = keysetBlob.getKeysetData();
      byte[] decryptedJson = masterAead.decrypt(encryptedData, null);
      String keysetJson = new String(decryptedJson, StandardCharsets.UTF_8);

      KeysetReader reader = JsonKeysetReader.withString(keysetJson);
      this.keysetHandle = com.google.crypto.tink.CleartextKeysetHandle.read(reader);
      this.databaseKeysetVersion = keysetBlob.getVersion() != null ? keysetBlob.getVersion() : 0;

      long signedPrimaryKeyId = keysetHandle.getKeysetInfo().getPrimaryKeyId();
      long unsignedPrimaryKeyId = toUnsignedLong(signedPrimaryKeyId);
      logger.info(
          "✅ Keyset loaded from database. Primary key ID: {} (signed: {}), version: {}",
          unsignedPrimaryKeyId,
          signedPrimaryKeyId,
          databaseKeysetVersion);
      return true;
    } catch (Exception e) {
      logger.error("Failed to load keyset from database: {}", e.getMessage());
      logger.debug("Database keyset load error", e);
      return false;
    }
  }

  public void saveKeysetToDatabase(String updatedBy) throws GeneralSecurityException, IOException {
    if (keysetBlobRepository == null) {
      logger.debug("KeysetBlobRepository not available, cannot save to database");
      return;
    }

    if (keysetHandle == null) {
      throw new IllegalStateException("No keyset loaded, cannot save to database");
    }

    if (masterAead == null) {
      throw new IllegalStateException("Master AEAD not initialized, cannot encrypt keyset");
    }

    try {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      com.google.crypto.tink.CleartextKeysetHandle.write(
          keysetHandle, JsonKeysetWriter.withOutputStream(baos));
      byte[] keysetJson = baos.toByteArray();
      byte[] encryptedData = masterAead.encrypt(keysetJson, null);

      KeysetBlob keysetBlob =
          keysetBlobRepository.findKeyset().orElse(new KeysetBlob(encryptedData, updatedBy));
      keysetBlob.setKeysetData(encryptedData);
      keysetBlob.setUpdatedBy(updatedBy);
      keysetBlob.setLastUpdatedAt(OffsetDateTime.now());

      KeysetBlob saved = keysetBlobRepository.save(keysetBlob);
      this.databaseKeysetVersion = saved.getVersion() != null ? saved.getVersion() : 0;

      logger.info(
          "✅ Keyset saved to database (version: {}, updated by: {})",
          databaseKeysetVersion,
          updatedBy);
    } catch (Exception e) {
      logger.error("Failed to save keyset to database: {}", e.getMessage());
      throw e;
    }
  }

  public boolean forceReloadFromDatabase() {
    logger.info("🔄 Force reloading keyset from database...");
    return loadKeysetFromDatabase();
  }

  public boolean isDatabaseStorageAvailable() {
    return keysetBlobRepository != null;
  }

  public long getDatabaseKeysetVersion() {
    return databaseKeysetVersion;
  }
}
