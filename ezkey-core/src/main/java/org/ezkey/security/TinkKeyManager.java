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
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.ezkey.config.TinkProperties;
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

  private volatile KeysetHandle keysetHandle;
  private volatile long keysetFileLastModified = 0;
  private volatile String keysetFilePath;
  private volatile Aead masterAead;

  public TinkKeyManager(TinkProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties");
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

      // 3. Load or create keyset
      String keysetPath = properties.getKeysetFile();
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
   * <p><b>Automatic Keyset Reload:</b> This method automatically checks if the keyset file has been
   * modified (e.g., after key rotation by another instance) and reloads it if necessary. This ensures
   * that all application instances stay synchronized with the latest keyset state.
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

    // Check if keyset file has been modified (e.g., by another instance after rotation)
    if (keysetFilePath != null) {
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
            logger.info("✅ Keyset reloaded successfully");
          }
        }
      } catch (Exception e) {
        logger.warn(
            "Failed to check/reload keyset file. Using cached keyset. Error: {}",
            e.getMessage());
        logger.debug("Keyset reload error", e);
        // Continue with existing keyset - don't fail if reload check fails
      }
    }

    try {
      return keysetHandle.getPrimitive(Aead.class);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unable to obtain AEAD primitive from keyset", e);
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
    Path path = Paths.get(normalizedPath);

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
      long primaryKeyId = handle.getKeysetInfo().getPrimaryKeyId();
      logger.info(
          "✅ Keyset decrypted successfully. Primary key ID: {} (unsigned: {})",
          primaryKeyId,
          Long.toUnsignedString(primaryKeyId));
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

    // Get current primary key ID before rotation
    long oldPrimaryKeyId = keysetHandle.getKeysetInfo().getPrimaryKeyId();
    logger.info(
        "Current primary key ID: {} (unsigned: {})",
        oldPrimaryKeyId,
        Long.toUnsignedString(oldPrimaryKeyId));

    // Use KeysetManager to rotate
    // Generate new key template matching current algorithm
    com.google.crypto.tink.KeyTemplate template = getKeyTemplate();
    KeysetManager manager = KeysetManager.withKeysetHandle(keysetHandle);

    // Rotate using the template - KeysetManager handles the rest
    // Note: This creates a new key and promotes it to primary
    KeysetManager rotatedManager = manager.add(template);

    // Get the new key ID (the one that was just added)
    @SuppressWarnings("deprecation")
    var newKeysetInfo = rotatedManager.getKeysetHandle().getKeysetInfo();
    int signedNewKeyIdInt =
        newKeysetInfo.getKeyInfoList().stream()
            .filter(
                keyInfo -> {
                  // Compare unsigned values: convert both to unsigned longs for comparison
                  long keyInfoUnsigned = toUnsignedLong(keyInfo.getKeyId());
                  return keyInfoUnsigned != oldPrimaryKeyId;
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

    logger.info(
        "✅ Key rotation completed. Old primary: {} (unsigned: {}), New primary: {} (unsigned: {})",
        oldPrimaryKeyId,
        Long.toUnsignedString(oldPrimaryKeyId),
        newPrimaryKeyId,
        Long.toUnsignedString(newPrimaryKeyId));
    return newPrimaryKeyId;
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
}
