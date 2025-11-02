package org.ezkey.security;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetReader;
import com.google.crypto.tink.KeysetWriter;
import com.google.crypto.tink.subtle.AesGcmJce;
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
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import org.ezkey.config.TinkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/**
 * Central manager for Tink keyset lifecycle: load/create, provide primitives, and prepare for
 * rotation.
 *
 * <p>This implementation uses master key encryption for keysets:
 * <ul>
 *   <li>Master key is loaded from a secure file (Base64-encoded 256-bit key)
 *   <li>Keysets are encrypted with master key using AES-256-GCM
 *   <li>Master key file must have secure permissions (600 on Unix/Linux)
 * </ul>
 *
 * <p><b>Security Properties:</b>
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
     * <p>This method is called automatically after dependency injection.
     * It loads the master key, creates the master AEAD, and loads or creates the keyset.
     *
     * <p>If encryption is disabled or master key is not configured, initialization is skipped
     * and the service will operate without encryption (backward compatible mode).
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
                keysetFile.exists()
            );
            
            if (keysetFile.exists()) {
                // Load existing encrypted keyset (use normalized path)
                logger.info("Loading existing keyset from: {}", normalizedKeysetPath);
                this.keysetHandle = loadEncryptedKeyset(normalizedKeysetPath);
                logger.info("✅ Keyset loaded successfully from: {}", normalizedKeysetPath);
            } else {
                // First boot - generate new keyset (use normalized path)
                logger.warn(
                    "Keyset file not found at: {}. Generating new keyset. "
                        + "NOTE: If this is not the first startup, check that the keyset file exists "
                        + "and is accessible. All APIs must use the same keyset file.",
                    normalizedKeysetPath
                );
                this.keysetHandle = generateAndSaveKeyset(normalizedKeysetPath);
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
                e
            );
        } catch (Exception e) {
            logger.error(
                "Failed to initialize Tink encryption. Encryption will be disabled. "
                    + "Application will continue without encryption at rest.",
                e
            );
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
                    + "Check that master key file exists and encryption is enabled in configuration."
            );
        }
        return keysetHandle;
    }

    /** Returns an AEAD primitive from the current keyset. */
    public Aead getAeadPrimitive() {
        if (!isInitialized()) {
            throw new IllegalStateException(
                "Tink encryption not initialized. "
                    + "Check that master key file exists and encryption is enabled."
            );
        }
        try {
            return keysetHandle.getPrimitive(Aead.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to obtain AEAD primitive from keyset", e);
        }
    }

    /**
     * Verify keyset is operational by encrypting and decrypting test data.
     */
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
                    + "Set ezkey.encryption.master-key-file in application.properties"
            );
        }

        // Normalize path (convert Unix-style /c/... to Windows C:\...)
        String normalizedPath = normalizePath(filePath);
        Path path = Paths.get(normalizedPath);

        // Verify file exists
        if (!Files.exists(path)) {
            throw new FileNotFoundException(
                "❌ Master key file not found: " + normalizedPath
                    + " (original path: " + filePath + ")\n"
                    + "Run: sudo ./scripts/generate-master-key.sh (Linux/Mac) or "
                    + ".\\scripts\\generate-master-key.ps1 (Windows as Administrator)"
            );
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

    /**
     * Verify file has secure permissions (600 or 400 on Unix/Linux).
     */
    private void verifyFilePermissions(Path path) throws IOException {
        Set<java.nio.file.attribute.PosixFilePermission> permissions =
            Files.getPosixFilePermissions(path);

        // Must have owner read
        if (!permissions.contains(java.nio.file.attribute.PosixFilePermission.OWNER_READ)) {
            throw new SecurityException("❌ Master key file must be readable by owner");
        }

        // Must NOT have group or others permissions
        Set<java.nio.file.attribute.PosixFilePermission> forbidden = Set.of(
            java.nio.file.attribute.PosixFilePermission.GROUP_READ,
            java.nio.file.attribute.PosixFilePermission.GROUP_WRITE,
            java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
            java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
            java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE,
            java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
        );

        for (java.nio.file.attribute.PosixFilePermission perm : forbidden) {
            if (permissions.contains(perm)) {
                throw new SecurityException(
                    "❌ Master key file has insecure permissions: "
                        + java.nio.file.attribute.PosixFilePermissions.toString(permissions) + "\n"
                        + "Run: sudo chmod 600 " + path
                );
            }
        }

        logger.info(
            "🔒 Master key file permissions verified: {}",
            java.nio.file.attribute.PosixFilePermissions.toString(permissions)
        );
    }

    /**
     * Create master AEAD from raw key bytes.
     *
     * <p>Uses AES-256-GCM with the provided 32-byte master key.
     * Uses Tink's subtle AesGcmJce to create an AEAD directly from the raw key.
     */
    private Aead createMasterAead(byte[] masterKey) throws GeneralSecurityException {
        if (masterKey.length != 32) {
            throw new IllegalArgumentException(
                "Master key must be exactly 32 bytes (256 bits), got: " + masterKey.length
            );
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

            // Log keyset info for debugging
            logger.info(
                "✅ Keyset decrypted successfully. Primary key ID: {}",
                handle.getKeysetInfo().getPrimaryKeyId()
            );
            return handle;
        } catch (GeneralSecurityException e) {
            logger.error(
                "❌ Failed to decrypt keyset from: {}. "
                    + "This usually means the master key is incorrect or the keyset was encrypted "
                    + "with a different master key. Ensure all APIs use the same master key file.",
                normalizedPath
            );
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

    /**
     * Set secure file permissions (600 on Unix/Linux).
     */
    private void setSecureFilePermissions(Path path) throws IOException {
        Set<java.nio.file.attribute.PosixFilePermission> permissions = Set.of(
            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
        );
        Files.setPosixFilePermissions(path, permissions);
        logger.info("🔒 Set file permissions to 600: {}", path);
    }

    /**
     * Get key template based on configuration.
     */
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
     * <p>Converts Unix-style paths (e.g., /c/ProgramData/...) to Windows-style
     * (e.g., C:\ProgramData\...) when running on Windows.
     *
     * <p>Note: This is a development convenience feature. Production deployments
     * should use standard Unix paths on Linux systems.
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

    /**
     * Check if running on Windows.
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}


