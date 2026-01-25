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
public class TinkKeyManager {

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
  private static final long DATABASE_CHECK_INTERVAL_MS = 5000; // 5 seconds

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
      logger.info("Tink encryption is disabled via configuration");
      return;
    }

    try {
      logger.info("Initializing Tink encryption...");

      // 1. Load master key from file
      byte[] masterKey = loadMasterKeyFromFile();
      logger.info("Master key loaded from file");

      // 2. Create master AEAD for keyset encryption
      this.masterAead = createMasterAead(masterKey);
      logger.info("Master AEAD created");

      // 3. Load or create keyset (with database support)
      String keysetPath = properties.getKeysetFile();
      StorageMode storageMode = properties.getKeyset().getStorageMode();
      logger.info("Keyset storage mode: {}", storageMode);

      // Try database first if DATABASE or HYBRID mode
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

      // Fall back to file if not loaded from database
      if (!loadedFromDatabase) {
        if (keysetPath == null || keysetPath.isBlank()) {
          logger.warn("ezkey.encryption.keyset-file is not configured. Encryption disabled.");
          return;
        }

        // Normalize path before checking file existence (Windows compatibility)
        String normalizedKeysetPath = normalizePath(keysetPath);
        File keysetFile = new File(normalizedKeysetPath);

        logger.debug(
            "Keyset file check - Original path: {}, Normalized path: {}, Exists: {}",
            keysetPath,
            normalizedKeysetPath,
            keysetFile.exists());

        if (keysetFile.exists()) {
          // Load existing encrypted keyset (use normalized path)
          logger.info("Loading existing keyset from: {}", normalizedKeysetPath);
          this.keysetHandle = loadEncryptedKeyset(normalizedKeysetPath);
          this.keysetFilePath = normalizedKeysetPath;
          this.keysetFileLastModified = keysetFile.lastModified();
          logger.info("✅ Keyset loaded successfully from: {}", normalizedKeysetPath);

          // Save to database if DATABASE/HYBRID mode and repository available
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
          // First boot - generate new keyset (use normalized path)
          logger.warn(
              "Keyset file not found at: {}. Generating new keyset. "
                  + "NOTE: If this is not the first startup, check that the keyset file exists "
                  + "and is accessible. All APIs must use the same keyset file.",
              normalizedKeysetPath);
          this.keysetHandle = generateAndSaveKeyset(normalizedKeysetPath);
          this.keysetFilePath = normalizedKeysetPath;
          this.keysetFileLastModified = keysetFile.lastModified();
          logger.info("🆕 New keyset created and saved to: {}", normalizedKeysetPath);

          // Save to database if DATABASE/HYBRID mode
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

      // 4. Verify keyset is usable
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
  }

  /**
   * Check if Tink encryption is initialized and ready.
   *
   * @return true if encryption is enabled and initialized, false otherwise
   */
  public boolean isInitialized() {
    return keysetHandle != null && masterAead != null;
  }

  /** Returns the loaded (or newly created) keyset handle. */
  public synchronized KeysetHandle getKeysetHandle() {
    if (keysetHandle == null) {
      throw new IllegalStateException(
          "TinkKeyManager not initialized. "
              + "Check that master key file exists and encryption is enabled in configuration.");
    }
    return keysetHandle;
  }

  /**
   * Returns an AEAD primitive from the current keyset.
   *
   * <p><b>Automatic Keyset Reload:</b> This method automatically checks if the keyset has been
   * modified (e.g., after key rotation by another instance) and reloads it if necessary. This
   * ensures that all application instances stay synchronized with the latest keyset state.
   *
   * <p><b>Synchronization Sources:</b>
   *
   * <ul>
   *   <li>DATABASE mode: Checks database version for changes (throttled to every 5 seconds)
   *   <li>FILE mode: Checks file lastModified timestamp
   *   <li>HYBRID mode: Checks database first, then file
   * </ul>
   *
   * <p><b>Recursion Protection:</b> This method uses a ThreadLocal guard to prevent infinite
   * recursion when database queries trigger JPA entity listeners that use encryption.
   *
   * @return AEAD primitive for encryption/decryption
   * @throws IllegalStateException if encryption is not initialized
   */
  public synchronized Aead getAeadPrimitive() {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption not initialized. "
              + "Check that master key file exists and encryption is enabled.");
    }

    // Skip sync checks if we're already checking (prevents recursion)
    // Also skip if called too recently (throttle database load)
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

  /**
   * Check for keyset updates and reload if needed.
   *
   * <p>This method is called from getAeadPrimitive() with recursion protection. It checks the
   * appropriate storage (database or file) for changes and reloads the keyset if needed.
   */
  private void checkAndReloadKeysetIfNeeded() {
    StorageMode storageMode = properties.getKeyset().getStorageMode();

    // Check database for changes (DATABASE or HYBRID mode)
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

    // Check file for changes (FILE or HYBRID mode, or as fallback)
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
        // Continue with existing keyset - don't fail if reload check fails
      }
    }
  }

  /**
   * Convert a Tink key ID (signed int representing unsigned 32-bit) to unsigned long for database
   * storage.
   *
   * <p>Tink returns key IDs from KeyInfo as signed ints, but they represent unsigned 32-bit
   * integers. This method converts them to unsigned longs suitable for database storage.
   *
   * @param signedKeyId the key ID as returned by Tink KeyInfo (may be negative)
   * @return the key ID as unsigned long (always >= 0)
   */
  private static long toUnsignedLong(int signedKeyId) {
    return Integer.toUnsignedLong(signedKeyId);
  }

  /**
   * Convert a Tink key ID (signed long representing unsigned 64-bit) to unsigned long for database
   * storage.
   *
   * <p>Tink returns key IDs as signed longs, but they represent unsigned 64-bit integers. This
   * method converts negative values (which represent unsigned values >= 2^63) to their unsigned
   * equivalents to comply with database CHECK constraints.
   *
   * <p><b>Note:</b> Tink typically generates 32-bit unsigned key IDs. When these are returned as
   * signed longs, negative values indicate unsigned 32-bit values that were sign-extended. We
   * convert these using Integer.toUnsignedLong(). For true 64-bit unsigned values >= 2^63, we
   * cannot represent them as positive longs, but Tink should not generate such values in practice.
   *
   * @param signedKeyId the key ID as returned by Tink (may be negative)
   * @return the key ID as unsigned long (always >= 0, suitable for database storage)
   */
  private static long toUnsignedLong(long signedKeyId) {
    // If already non-negative, return as-is
    if (signedKeyId >= 0) {
      return signedKeyId;
    }
    // For negative values, they likely represent unsigned 32-bit integers that were sign-extended
    // Convert by treating as unsigned 32-bit value
    if (signedKeyId >= Integer.MIN_VALUE && signedKeyId <= Integer.MAX_VALUE) {
      return Integer.toUnsignedLong((int) signedKeyId);
    }
    // For values outside int range but still negative, this shouldn't happen with Tink
    // but if it does, we need to handle it
    // These would represent unsigned 64-bit values >= 2^63, which we can't store as positive longs
    // For now, throw an exception to catch unexpected cases
    throw new IllegalArgumentException(
        "Key ID value out of range for database storage: "
            + signedKeyId
            + " (unsigned: "
            + Long.toUnsignedString(signedKeyId)
            + "). "
            + "Tink should not generate such values. If this occurs, the key ID may be too large "
            + "for PostgreSQL BIGINT with CHECK >= 0 constraint.");
  }

  /**
   * Returns the current primary key ID from the keyset.
   *
   * <p>The primary key ID identifies which key in the keyset is currently used for encryption. This
   * ID is included in encrypted values to enable future key rotation and re-encryption operations.
   *
   * <p><b>Note:</b> This method returns the key ID as an unsigned long (always >= 0) suitable for
   * database storage, converting Tink's signed representation if necessary.
   *
   * @return the primary key ID (unsigned 64-bit integer, always >= 0)
   * @throws IllegalStateException if encryption is not initialized
   */
  public long getCurrentPrimaryKeyId() {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption not initialized. "
              + "Check that master key file exists and encryption is enabled.");
    }
    long signedKeyId = keysetHandle.getKeysetInfo().getPrimaryKeyId();
    return toUnsignedLong(signedKeyId);
  }

  /** Verify keyset is operational by encrypting and decrypting test data. */
  private void verifyKeyset() throws GeneralSecurityException {
    Aead aead = keysetHandle.getPrimitive(Aead.class);

    // Test encrypt/decrypt
    String testData = "verification-test";
    byte[] ciphertext = aead.encrypt(testData.getBytes(StandardCharsets.UTF_8), null);
    byte[] plaintext = aead.decrypt(ciphertext, null);

    if (!testData.equals(new String(plaintext, StandardCharsets.UTF_8))) {
      throw new GeneralSecurityException("❌ Keyset verification failed");
    }

    logger.info("✅ Keyset verified operational");
  }

  /**
   * Load master key from file system.
   *
   * <p>Security considerations:
   *
   * <ul>
   *   <li>File must have 600 permissions (Unix/Linux)
   *   <li>File must be owned by application user
   *   <li>File path is outside application directory
   * </ul>
   */
  private byte[] loadMasterKeyFromFile() throws IOException {
    String filePath = properties.getMasterKeyFile();

    if (filePath == null || filePath.isBlank()) {
      throw new IllegalStateException(
          "❌ Master key file path not configured. "
              + "Set ezkey.encryption.master-key-file in application.properties");
    }

    // Normalize path (convert Unix-style /c/... to Windows C:\...)
    String normalizedPath = normalizePath(filePath);
    Path path = Path.of(normalizedPath);

    // Verify file exists
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

    // Verify file permissions (Unix/Linux only)
    if (!isWindows()) {
      verifyFilePermissions(path);
    }

    // Read master key
    String masterKeyBase64 = Files.readString(path, StandardCharsets.UTF_8).trim();

    if (masterKeyBase64.isEmpty()) {
      throw new IllegalStateException("❌ Master key file is empty: " + normalizedPath);
    }

    logger.info("🔑 Master key loaded from: {}", normalizedPath);
    return Base64.getDecoder().decode(masterKeyBase64);
  }

  /** Verify file has secure permissions (600 or 400 on Unix/Linux). */
  private void verifyFilePermissions(Path path) throws IOException {
    Set<java.nio.file.attribute.PosixFilePermission> permissions =
        Files.getPosixFilePermissions(path);

    // Must have owner read
    if (!permissions.contains(java.nio.file.attribute.PosixFilePermission.OWNER_READ)) {
      throw new SecurityException("❌ Master key file must be readable by owner");
    }

    // Must NOT have group or others permissions
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

  /**
   * Create master AEAD from raw key bytes.
   *
   * <p>Uses AES-256-GCM with the provided 32-byte master key. Uses Tink's subtle AesGcmJce to
   * create an AEAD directly from the raw key.
   */
  private Aead createMasterAead(byte[] masterKey) throws GeneralSecurityException {
    if (masterKey.length != 32) {
      throw new IllegalArgumentException(
          "Master key must be exactly 32 bytes (256 bits), got: " + masterKey.length);
    }

    // Use Tink's subtle AesGcmJce to create AEAD directly from raw key
    // This is the recommended approach for master key encryption
    return new AesGcmJce(masterKey);
  }

  /**
   * Load encrypted keyset from file.
   *
   * <p>Note: keysetPath should already be normalized before calling this method.
   */
  private KeysetHandle loadEncryptedKeyset(String keysetPath)
      throws GeneralSecurityException, IOException {

    // Path should already be normalized, but ensure it is (defensive programming)
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
      KeysetHandle handle = KeysetHandle.read(reader, masterAead); // Decrypt with master AEAD

      // Log keyset info for debugging (unsigned representation for consistency with ENC format)
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

  /**
   * Generate new keyset and save encrypted.
   *
   * <p>Note: keysetPath should already be normalized before calling this method.
   */
  private KeysetHandle generateAndSaveKeyset(String keysetPath)
      throws GeneralSecurityException, IOException {

    logger.info("🆕 Generating new keyset...");

    // Path should already be normalized, but ensure it is (defensive programming)
    String normalizedPath = normalizePath(keysetPath);

    // 1. Generate new keyset
    var template = getKeyTemplate();
    KeysetHandle newKeyset = KeysetHandle.generateNew(template);

    // 2. Create directory if needed
    File keysetFile = new File(normalizedPath);
    File parentDir = keysetFile.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      if (!parentDir.mkdirs()) {
        logger.warn("Could not create keyset directory: {}", parentDir);
      }
    }

    // 3. Save encrypted keyset
    try (FileOutputStream fos = new FileOutputStream(keysetFile)) {
      KeysetWriter writer = JsonKeysetWriter.withOutputStream(fos);
      newKeyset.write(writer, masterAead); // Encrypt with master AEAD
    }

    // 4. Secure file permissions (Unix/Linux)
    if (!isWindows()) {
      setSecureFilePermissions(keysetFile.toPath());
    }

    logger.info("✅ New keyset generated and encrypted");
    return newKeyset;
  }

  /** Set secure file permissions (600 on Unix/Linux). */
  private void setSecureFilePermissions(Path path) throws IOException {
    Set<java.nio.file.attribute.PosixFilePermission> permissions =
        Set.of(
            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
    Files.setPosixFilePermissions(path, permissions);
    logger.info("🔒 Set file permissions to 600: {}", path);
  }

  /** Get key template based on configuration. */
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

  /**
   * Normalize file path to work on both Unix/Linux and Windows.
   *
   * <p>Converts Unix-style paths (e.g., /c/ProgramData/...) to Windows-style (e.g.,
   * C:\ProgramData\...) when running on Windows.
   *
   * <p>Note: This is a development convenience feature. Production deployments should use standard
   * Unix paths on Linux systems.
   *
   * @param path the file path to normalize
   * @return normalized path for current operating system
   */
  private String normalizePath(String path) {
    if (path == null || path.isBlank()) {
      return path;
    }

    // On Windows (dev only), convert Unix-style /c/... to C:\...
    // Pattern: /c/... or /C/... -> C:\...
    if (isWindows() && path.startsWith("/") && path.length() >= 3) {
      char firstChar = path.charAt(1); // Character after first /
      char secondChar = path.length() > 2 ? path.charAt(2) : 0;

      // Match pattern: /[drive]/... where drive is a letter
      if (Character.isLetter(firstChar) && secondChar == '/') {
        char driveLetter = Character.toUpperCase(firstChar);
        String restOfPath = path.substring(3); // Skip /c/
        String windowsPath = restOfPath.replace('/', '\\');
        return driveLetter + ":\\" + windowsPath;
      }
    }

    return path;
  }

  /** Check if running on Windows. */
  private boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  /**
   * Rotate the keyset by adding a new key and promoting it to primary.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Creates a new key using the configured algorithm template
   *   <li>Promotes the new key to PRIMARY status
   *   <li>Demotes the old primary key to ENABLED (for decryption)
   *   <li>Saves the updated keyset to disk (encrypted)
   * </ul>
   *
   * <p>Zero-downtime: Old keys remain ENABLED for decryption, new key is PRIMARY for encryption.
   *
   * @return the new primary key ID
   * @throws GeneralSecurityException if key rotation fails
   * @throws IOException if keyset save fails
   */
  public synchronized long rotateKey() throws GeneralSecurityException, IOException {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption not initialized. Cannot rotate keys without initialized keyset.");
    }

    logger.info("🔄 Starting key rotation...");

    // Get current primary key ID before rotation (convert to unsigned for comparison)
    long signedOldPrimaryKeyId = keysetHandle.getKeysetInfo().getPrimaryKeyId();
    long oldPrimaryKeyIdUnsigned = toUnsignedLong(signedOldPrimaryKeyId);
    logger.info(
        "Current primary key ID: {} (unsigned: {})",
        signedOldPrimaryKeyId,
        Long.toUnsignedString(oldPrimaryKeyIdUnsigned));

    // Get all existing key IDs before rotation (converted to unsigned)
    @SuppressWarnings("deprecation")
    var existingKeyIds =
        keysetHandle.getKeysetInfo().getKeyInfoList().stream()
            .map(keyInfo -> toUnsignedLong(keyInfo.getKeyId()))
            .collect(java.util.stream.Collectors.toSet());
    logger.debug("Existing key IDs before rotation: {}", existingKeyIds);

    // Use KeysetManager to rotate
    // Generate new key template matching current algorithm
    com.google.crypto.tink.KeyTemplate template = getKeyTemplate();
    KeysetManager manager = KeysetManager.withKeysetHandle(keysetHandle);

    // Rotate using the template - KeysetManager handles the rest
    // Note: This creates a new key and adds it to the keyset
    KeysetManager rotatedManager = manager.add(template);

    // Get the new key ID (the one that was just added - not in existingKeyIds)
    @SuppressWarnings("deprecation")
    var newKeysetInfo = rotatedManager.getKeysetHandle().getKeysetInfo();
    int signedNewKeyIdInt =
        newKeysetInfo.getKeyInfoList().stream()
            .filter(
                keyInfo -> {
                  // Find the key that wasn't in the original keyset
                  long keyInfoUnsigned = toUnsignedLong(keyInfo.getKeyId());
                  return !existingKeyIds.contains(keyInfoUnsigned);
                })
            .findFirst()
            .map(keyInfo -> keyInfo.getKeyId())
            .orElseThrow(
                () -> new GeneralSecurityException("Failed to find new key after rotation"));

    // Set new key as primary (setPrimary takes int - use signed value for Tink API)
    KeysetHandle rotatedHandle = rotatedManager.setPrimary(signedNewKeyIdInt).getKeysetHandle();

    // Get new primary key ID and convert to unsigned for database storage
    long signedNewPrimaryKeyId = rotatedHandle.getKeysetInfo().getPrimaryKeyId();
    long newPrimaryKeyId = toUnsignedLong(signedNewPrimaryKeyId);
    logger.info(
        "New primary key ID: {} (unsigned: {})",
        signedNewPrimaryKeyId,
        Long.toUnsignedString(newPrimaryKeyId));

    // Save rotated keyset to disk
    String keysetPath = properties.getKeysetFile();
    String normalizedPath = normalizePath(keysetPath);
    saveKeyset(rotatedHandle, normalizedPath);

    // Update in-memory handle and file modification timestamp
    this.keysetHandle = rotatedHandle;
    File keysetFile = new File(normalizedPath);
    if (keysetFile.exists()) {
      this.keysetFileLastModified = keysetFile.lastModified();
      this.keysetFilePath = normalizedPath;
    }

    // Save to database if DATABASE/HYBRID mode
    StorageMode storageMode = properties.getKeyset().getStorageMode();
    if ((storageMode == StorageMode.DATABASE || storageMode == StorageMode.HYBRID)
        && keysetBlobRepository != null) {
      try {
        saveKeysetToDatabase("KEY_ROTATION");
        logger.info("✅ Rotated keyset saved to database");
      } catch (Exception e) {
        logger.error("Failed to save rotated keyset to database: {}", e.getMessage());
        // Don't throw - file save succeeded, database sync can be retried
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

  /**
   * Add a new key to the keyset WITHOUT promoting it to PRIMARY.
   *
   * <p>This method is used for distributed key rotation with synchronization window:
   *
   * <ol>
   *   <li>New key is added to keyset as ENABLED (not PRIMARY)
   *   <li>Keyset is saved to file and database
   *   <li>All instances sync the keyset (new key available for decryption)
   *   <li>After sync window, {@link #promoteToPrimary(long)} is called to activate the new key
   * </ol>
   *
   * <p>This approach ensures all instances have the new key before it becomes active for
   * encryption, preventing decryption failures during the transition period.
   *
   * @return the new key ID (unsigned representation)
   * @throws GeneralSecurityException if key creation fails
   * @throws IOException if keyset save fails
   */
  public synchronized long addKeyWithoutPromotion() throws GeneralSecurityException, IOException {
    if (!isInitialized()) {
      throw new IllegalStateException(
          "Tink encryption not initialized. Cannot add keys without initialized keyset.");
    }

    logger.info("🔄 Adding new key to keyset (without promotion)...");

    // Get current primary key ID for logging
    long signedCurrentPrimaryKeyId = keysetHandle.getKeysetInfo().getPrimaryKeyId();
    long currentPrimaryKeyIdUnsigned = toUnsignedLong(signedCurrentPrimaryKeyId);
    logger.info(
        "Current primary key ID (will remain primary): {} (unsigned: {})",
        signedCurrentPrimaryKeyId,
        Long.toUnsignedString(currentPrimaryKeyIdUnsigned));

    // Get all existing key IDs before adding (converted to unsigned)
    @SuppressWarnings("deprecation")
    var existingKeyIds =
        keysetHandle.getKeysetInfo().getKeyInfoList().stream()
            .map(keyInfo -> toUnsignedLong(keyInfo.getKeyId()))
            .collect(java.util.stream.Collectors.toSet());
    logger.debug("Existing key IDs before adding: {}", existingKeyIds);

    // Add new key using the configured template
    com.google.crypto.tink.KeyTemplate template = getKeyTemplate();
    KeysetManager manager = KeysetManager.withKeysetHandle(keysetHandle);
    KeysetManager updatedManager = manager.add(template);

    // Get the new key ID (the one that was just added - not in existingKeyIds)
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

    // NOTE: We do NOT call setPrimary() - the old primary key remains active
    KeysetHandle updatedHandle = updatedManager.getKeysetHandle();

    // Save keyset to disk
    String keysetPath = properties.getKeysetFile();
    String normalizedPath = normalizePath(keysetPath);
    saveKeyset(updatedHandle, normalizedPath);

    // Update in-memory handle and file modification timestamp
    this.keysetHandle = updatedHandle;
    File keysetFile = new File(normalizedPath);
    if (keysetFile.exists()) {
      this.keysetFileLastModified = keysetFile.lastModified();
      this.keysetFilePath = normalizedPath;
    }

    // Save to database if DATABASE/HYBRID mode
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

  /**
   * Promote an existing key to PRIMARY status.
   *
   * <p>This method is used after the synchronization window has passed to activate a PENDING key.
   * It assumes the key already exists in the keyset (added by {@link #addKeyWithoutPromotion()}).
   *
   * <p>The promotion:
   *
   * <ol>
   *   <li>Sets the specified key as PRIMARY in the Tink keyset
   *   <li>Demotes the old primary key to ENABLED (remains available for decryption)
   *   <li>Saves the updated keyset to file and database
   * </ol>
   *
   * @param keyId the key ID to promote (unsigned representation)
   * @return the old primary key ID (unsigned representation)
   * @throws GeneralSecurityException if key not found or promotion fails
   * @throws IOException if keyset save fails
   */
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

    // Get current primary key ID before promotion
    long signedOldPrimaryKeyId = keysetHandle.getKeysetInfo().getPrimaryKeyId();
    long oldPrimaryKeyIdUnsigned = toUnsignedLong(signedOldPrimaryKeyId);
    logger.info(
        "Current primary key ID: {} (unsigned: {})",
        signedOldPrimaryKeyId,
        Long.toUnsignedString(oldPrimaryKeyIdUnsigned));

    // Verify the key exists in the keyset
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

    // Promote the key to PRIMARY
    KeysetManager manager = KeysetManager.withKeysetHandle(keysetHandle);
    KeysetHandle promotedHandle = manager.setPrimary(signedKeyIdInt).getKeysetHandle();

    // Verify the promotion worked
    long newPrimaryKeyId = toUnsignedLong(promotedHandle.getKeysetInfo().getPrimaryKeyId());
    if (newPrimaryKeyId != keyId) {
      throw new GeneralSecurityException(
          "Key promotion failed. Expected primary: %s, actual: %s"
              .formatted(Long.toUnsignedString(keyId), Long.toUnsignedString(newPrimaryKeyId)));
    }

    // Save keyset to disk
    String keysetPath = properties.getKeysetFile();
    String normalizedPath = normalizePath(keysetPath);
    saveKeyset(promotedHandle, normalizedPath);

    // Update in-memory handle and file modification timestamp
    this.keysetHandle = promotedHandle;
    File keysetFile = new File(normalizedPath);
    if (keysetFile.exists()) {
      this.keysetFileLastModified = keysetFile.lastModified();
      this.keysetFilePath = normalizedPath;
    }

    // Save to database if DATABASE/HYBRID mode
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

  /**
   * Convert unsigned long key ID to signed int for Tink API.
   *
   * <p>Tink's KeysetManager.setPrimary() expects a signed int. This method converts our unsigned
   * long representation back to the signed int that Tink uses internally.
   *
   * @param unsignedKeyId the unsigned key ID
   * @return the signed int representation for Tink API
   */
  private int toSignedInt(long unsignedKeyId) {
    return (int) unsignedKeyId;
  }

  /**
   * Convert unsigned long key ID to signed long for logging.
   *
   * @param unsignedKeyId the unsigned key ID
   * @return the signed long representation
   */
  private long toSignedLong(long unsignedKeyId) {
    return (int) unsignedKeyId;
  }

  /**
   * Save keyset to disk with encryption.
   *
   * <p>Creates backup if backupBeforeRotation is enabled. Uses master AEAD to encrypt the keyset.
   *
   * @param handle the keyset handle to save
   * @param keysetPath the path to save the keyset
   * @throws IOException if file operations fail
   * @throws GeneralSecurityException if encryption fails
   */
  private void saveKeyset(KeysetHandle handle, String keysetPath)
      throws IOException, GeneralSecurityException {
    // Create backup if configured
    if (properties.getRotation().isBackupBeforeRotation()) {
      createBackup(keysetPath);
    }

    // Save encrypted keyset
    File keysetFile = new File(keysetPath);
    File parentDir = keysetFile.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      if (!parentDir.mkdirs()) {
        logger.warn("Could not create keyset directory: {}", parentDir);
      }
    }

    try (FileOutputStream fos = new FileOutputStream(keysetFile)) {
      KeysetWriter writer = JsonKeysetWriter.withOutputStream(fos);
      handle.write(writer, masterAead); // Encrypt with master AEAD
    }

    // Secure file permissions (Unix/Linux)
    if (!isWindows()) {
      setSecureFilePermissions(keysetFile.toPath());
    }

    logger.info("✅ Keyset saved to: {}", keysetPath);
  }

  /**
   * Create a timestamped backup of the keyset file.
   *
   * <p>Backup format: keyset-backup-YYYY-MM-DD-HHmmss.json.encrypted
   *
   * @param keysetPath the path to the keyset file
   * @throws IOException if backup creation fails
   */
  private void createBackup(String keysetPath) throws IOException {
    File keysetFile = new File(keysetPath);
    if (!keysetFile.exists()) {
      logger.warn("Keyset file does not exist, skipping backup: {}", keysetPath);
      return;
    }

    // Generate backup filename with timestamp
    String timestamp =
        OffsetDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
    File parentDir = keysetFile.getParentFile();
    String backupFilename = "keyset-backup-" + timestamp + ".json.encrypted";
    File backupFile = new File(parentDir, backupFilename);

    // Copy keyset to backup
    Files.copy(
        keysetFile.toPath(),
        backupFile.toPath(),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    // Secure backup file permissions (Unix/Linux)
    if (!isWindows()) {
      setSecureFilePermissions(backupFile.toPath());
    }

    logger.info("✅ Keyset backup created: {}", backupFile.getAbsolutePath());

    // Cleanup old backups if retention configured
    cleanupOldBackups(parentDir);
  }

  /**
   * Cleanup old backup files based on retention policy.
   *
   * @param backupDir the directory containing backup files
   */
  private void cleanupOldBackups(File backupDir) {
    int retentionDays = properties.getRotation().getBackupRetentionDays();
    if (retentionDays <= 0) {
      return; // No cleanup if retention disabled
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

  /**
   * Get all key IDs in the keyset.
   *
   * <p>Used for syncing keyset state to database. Note: This uses deprecated API but is the only
   * way to access key information in current Tink version.
   *
   * @return list of key IDs in the keyset
   */
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

  /**
   * Get keyset information for metadata sync.
   *
   * <p>Returns information about all keys in the keyset for database synchronization. Note: Uses
   * deprecated API but is the only way to access keyset information.
   *
   * @return keyset information (deprecated API)
   */
  @SuppressWarnings("deprecation")
  public Object getKeysetInfo() {
    if (!isInitialized()) {
      throw new IllegalStateException("Tink encryption not initialized");
    }
    return keysetHandle.getKeysetInfo();
  }

  /**
   * Get master AEAD (for testing or advanced use cases).
   *
   * @return the master AEAD used for keyset encryption
   */
  public Aead getMasterAead() {
    return masterAead;
  }

  /**
   * Load keyset from database.
   *
   * <p>Loads the encrypted keyset blob from the database and decrypts it using the master AEAD.
   *
   * @return true if keyset was loaded successfully, false if not found or error
   */
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

      // Decrypt the keyset blob
      byte[] decryptedJson = masterAead.decrypt(encryptedData, null);
      String keysetJson = new String(decryptedJson, StandardCharsets.UTF_8);

      // Parse the keyset JSON
      KeysetReader reader = JsonKeysetReader.withString(keysetJson);
      // Note: We're loading an already-decrypted keyset JSON, so we use cleartext read
      // The JSON was decrypted above using master AEAD
      this.keysetHandle = com.google.crypto.tink.CleartextKeysetHandle.read(reader);

      // Update tracking variables
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

  /**
   * Save keyset to database.
   *
   * <p>Serializes the current keyset to JSON, encrypts it with the master AEAD, and saves it to the
   * database. This enables distributed synchronization across multiple application instances.
   *
   * @param updatedBy identifier of who/what is saving the keyset
   * @throws GeneralSecurityException if encryption fails
   * @throws IOException if serialization fails
   */
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
      // Serialize keyset to JSON (cleartext)
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      com.google.crypto.tink.CleartextKeysetHandle.write(
          keysetHandle, JsonKeysetWriter.withOutputStream(baos));
      byte[] keysetJson = baos.toByteArray();

      // Encrypt the keyset JSON with master AEAD
      byte[] encryptedData = masterAead.encrypt(keysetJson, null);

      // Save to database
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

  /**
   * Force reload keyset from database.
   *
   * <p>Used for defensive decryption error handling - when decryption fails with unknown key ID,
   * this method can be called to reload the keyset from database in case another instance rotated
   * keys.
   *
   * @return true if reload was successful, false otherwise
   */
  public boolean forceReloadFromDatabase() {
    logger.info("🔄 Force reloading keyset from database...");
    return loadKeysetFromDatabase();
  }

  /**
   * Check if database keyset storage is available.
   *
   * @return true if database storage is configured and available
   */
  public boolean isDatabaseStorageAvailable() {
    return keysetBlobRepository != null;
  }

  /**
   * Get current database keyset version.
   *
   * @return the version number of the keyset in database, or 0 if not loaded from database
   */
  public long getDatabaseKeysetVersion() {
    return databaseKeysetVersion;
  }
}
