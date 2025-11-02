package org.ezkey.security;

import com.google.crypto.tink.Aead;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * High-level encryption service using Tink AEAD primitives.
 *
 * <p>Encrypts/decrypts UTF-8 strings and returns/accepts Base64-encoded ciphertext.
 *
 * @since 2025
 */
@Service
public class EncryptionService {

    private static final Logger logger = LoggerFactory.getLogger(EncryptionService.class);

    private final TinkKeyManager keyManager;

    public EncryptionService(TinkKeyManager keyManager) {
        this.keyManager = keyManager;
        if (!keyManager.isInitialized()) {
            logger.warn(
                "EncryptionService created but TinkKeyManager is not initialized. "
                    + "Encryption will be disabled. "
                    + "Check configuration and ensure master key file exists."
            );
        }
    }

    /**
     * Check if encryption is available and initialized.
     *
     * @return true if encryption is ready, false otherwise
     */
    public boolean isEncryptionAvailable() {
        return keyManager.isInitialized();
    }

    /**
     * Prefix for encrypted values to distinguish from plaintext.
     *
     * <p>All encrypted values start with this prefix, making it easy to detect
     * encrypted data vs plaintext (e.g., PEM keys).
     */
    private static final String ENCRYPTED_PREFIX = "ENC:";

    /**
     * Encrypts the given plaintext string and returns Base64-encoded ciphertext with prefix.
     *
     * <p>Format: "ENC:" + Base64(encrypted_data)
     *
     * <p>This prefix allows reliable detection of encrypted values vs plaintext (e.g., PEM keys).
     *
     * @param plaintext the plaintext string to encrypt
     * @return encrypted value with "ENC:" prefix, or plaintext if encryption unavailable
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (!isEncryptionAvailable()) {
            logger.debug("Encryption not available, returning plaintext unchanged");
            return plaintext;
        }
        // Skip encryption if already encrypted (prevents double encryption)
        if (isEncrypted(plaintext)) {
            logger.debug("Value already encrypted, skipping re-encryption");
            return plaintext;
        }
        try {
            Aead aead = keyManager.getAeadPrimitive();
            byte[] ct = aead.encrypt(plaintext.getBytes(StandardCharsets.UTF_8), null);
            String encryptedBase64 = Base64.getEncoder().encodeToString(ct);
            return ENCRYPTED_PREFIX + encryptedBase64;
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypts the given encrypted value and returns the plaintext string.
     *
     * <p>If the value starts with "ENC:" prefix, it will be decrypted.
     * Otherwise, it is assumed to be plaintext (backward compatibility with existing data).
     *
     * <p>If decryption fails (e.g., corrupted data or not encrypted), the original value
     * is returned to maintain backward compatibility.
     *
     * @param encryptedValue the encrypted value (with "ENC:" prefix) or plaintext
     * @return decrypted plaintext string, or original value if not encrypted/decryption fails
     */
    public String decrypt(String encryptedValue) {
        if (encryptedValue == null) {
            return null;
        }
        if (!isEncryptionAvailable()) {
            logger.debug("Encryption not available, assuming plaintext");
            return encryptedValue;
        }
        // If not encrypted (no prefix), return as-is (backward compatibility)
        if (!isEncrypted(encryptedValue)) {
            logger.debug("Value not encrypted (no prefix), returning as plaintext");
            return encryptedValue;
        }
        try {
            // Remove prefix and decode
            String ciphertextBase64 = encryptedValue.substring(ENCRYPTED_PREFIX.length());
            Aead aead = keyManager.getAeadPrimitive();
            byte[] ct = Base64.getDecoder().decode(ciphertextBase64);
            byte[] pt = aead.decrypt(ct, null);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // If decryption fails, assume it's plaintext (backward compatibility)
            logger.warn(
                "Decryption failed, assuming plaintext for backward compatibility: {}",
                e.getMessage()
            );
            logger.debug("Decryption error details", e);
            return encryptedValue; // Return original value
        }
    }

    /**
     * Check if a value is encrypted (starts with "ENC:" prefix).
     *
     * @param value the value to check
     * @return true if the value is encrypted, false otherwise
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }
}


