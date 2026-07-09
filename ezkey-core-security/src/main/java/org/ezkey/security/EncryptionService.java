package org.ezkey.security;

import com.google.crypto.tink.Aead;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.EncryptionKey.KeyStatus;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * High-level encryption service using Tink AEAD primitives.
 *
 * <p>Encrypts/decrypts UTF-8 strings and returns/accepts Base64-encoded ciphertext.
 *
 * <p><b>Encryption Format:</b>
 *
 * <p>Format: {@code ENC:keyID:Base64(ciphertext)} where keyID is the Tink primary key ID.
 *
 * <p>The key ID in the format enables future SQL queries to identify records encrypted with
 * specific keys, facilitating key rotation and re-encryption operations.
 *
 * @since 2025
 */
@Service
public class EncryptionService implements EncryptionOperations {

  private static final Logger logger = LoggerFactory.getLogger(EncryptionService.class);

  private final TinkKeyManager keyManager;

  /**
   * Pattern for encrypted values: {@code ENC:keyID:Base64(ciphertext)}.
   *
   * <p>Group 1: key ID (numeric), Group 2: Base64 ciphertext
   *
   * <p>Strict pattern ensures:
   *
   * <ul>
   *   <li>Key ID contains only digits (prevents injection attacks)
   *   <li>Base64 contains only valid Base64 characters
   *   <li>Exact format match (prevents prefix manipulation)
   * </ul>
   */
  private static final Pattern ENCRYPTED_FORMAT_PATTERN =
      Pattern.compile("^ENC:(\\d+):([A-Za-z0-9+/=]+)$");

  /**
   * Minimum Base64-encoded ciphertext length.
   *
   * <p>Tink AEAD ciphertexts have a minimum size (nonce + ciphertext + tag). This value ensures
   * that truncated or corrupted data is detected early.
   *
   * <p>For AES-256-GCM: 12 bytes nonce + minimum 16 bytes tag = ~20 bytes minimum = ~27 Base64
   * chars. Using 20 as conservative minimum.
   */
  private static final int MIN_CIPHERTEXT_BASE64_LENGTH = 20;

  private final TinkProperties properties;
  private final EncryptionKeyRepository keyRepository;

  /**
   * Constructor with optional dependencies for defensive decryption handling.
   *
   * <p>Uses Spring's ObjectProvider to handle optional dependencies gracefully. This allows the
   * service to work even when TinkProperties or EncryptionKeyRepository are not available (e.g., in
   * simpler deployment scenarios).
   *
   * @param keyManager the Tink key manager (required)
   * @param propertiesProvider optional provider for Tink configuration properties
   * @param keyRepositoryProvider optional provider for key repository (for defensive decryption)
   */
  public EncryptionService(
      TinkKeyManager keyManager,
      ObjectProvider<TinkProperties> propertiesProvider,
      ObjectProvider<EncryptionKeyRepository> keyRepositoryProvider) {
    this.keyManager = keyManager;
    this.properties = propertiesProvider.getIfAvailable();
    this.keyRepository = keyRepositoryProvider.getIfAvailable();
    if (!keyManager.isInitialized()) {
      logger.warn(
          "EncryptionService created but TinkKeyManager is not initialized. "
              + "Encryption will be disabled. "
              + "Check configuration and ensure master key file exists.");
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

  @Override
  public boolean isEncryptionRequired() {
    return properties != null && properties.isRequired();
  }

  /**
   * Encrypts the given plaintext string and returns Base64-encoded ciphertext with prefix.
   *
   * <p>Format: {@code ENC:keyID:Base64(ciphertext)} where keyID is the Tink primary key ID.
   *
   * <p>The key ID is formatted as an unsigned 64-bit integer using {@code Long.toUnsignedString()}
   * to correctly handle values greater than {@code Long.MAX_VALUE} (which appear as negative when
   * stored in Java's signed {@code long} type).
   *
   * <p>This prefix allows reliable detection of encrypted values vs plaintext (e.g., PEM keys). The
   * key ID enables future SQL queries to identify records encrypted with specific keys for key
   * rotation operations.
   *
   * @param plaintext the plaintext string to encrypt
   * @return encrypted value with "ENC:keyID:" prefix, or plaintext if encryption unavailable
   * @throws IllegalStateException if encryption fails or key ID cannot be retrieved
   */
  public String encrypt(String plaintext) {
    if (plaintext == null) {
      return null;
    }
    if (!isEncryptionAvailable()) {
      logger.debug("Encryption not available, returning plaintext unchanged");
      return plaintext;
    }
    if (isEncrypted(plaintext)) {
      logger.debug("Value already encrypted, skipping re-encryption");
      return plaintext;
    }
    try {
      Aead aead = keyManager.getAeadPrimitive();
      byte[] ct = aead.encrypt(plaintext.getBytes(StandardCharsets.UTF_8), null);
      String encryptedBase64 = Base64.getEncoder().encodeToString(ct);

      long keyId = keyManager.getCurrentPrimaryKeyId();
      String keyIdStr = Long.toUnsignedString(keyId);
      return "ENC:" + keyIdStr + ":" + encryptedBase64;
    } catch (Exception e) {
      logger.error("Encryption failed", e);
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  /**
   * Decrypts the given encrypted value and returns the plaintext string.
   *
   * <p>Format: {@code ENC:keyID:Base64(ciphertext)}.
   *
   * <p>Performs strict validation before decryption:
   *
   * <ul>
   *   <li>Format integrity validation
   *   <li>Key ID validation (numeric, valid range)
   *   <li>Base64 validation (valid encoding, minimum length)
   * </ul>
   *
   * <p>If the value does not match the encrypted format, it is assumed to be plaintext.
   *
   * <p>If decryption fails (e.g., corrupted data or not encrypted), the original value is returned.
   *
   * @param encryptedValue the encrypted value (with "ENC:keyID:" prefix) or plaintext
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
    if (!isEncrypted(encryptedValue)) {
      logger.debug("Value not encrypted (no prefix), returning as plaintext");
      return encryptedValue;
    }

    String ciphertextBase64 = extractCiphertext(encryptedValue);
    if (ciphertextBase64 == null) {
      logger.warn(
          "Failed to extract/validate ciphertext from encrypted value, returning as plaintext");
      return encryptedValue;
    }

    Long keyId = parseKeyIdFromPrefix(encryptedValue);
    if (keyId == null) {
      logger.warn(
          "Invalid key ID in encrypted value (format validation failed), returning as plaintext");
      return encryptedValue;
    }

    logger.trace("Decrypting value encrypted with key ID: {}", keyId);

    try {
      Aead aead = keyManager.getAeadPrimitive();
      byte[] ct = Base64.getDecoder().decode(ciphertextBase64);
      byte[] pt = aead.decrypt(ct, null);
      return new String(pt, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      logger.warn(
          "Base64 decoding failed during decryption (validation should have caught this): {}",
          e.getMessage());
      logger.debug("Base64 decoding error", e);
      return encryptedValue;
    } catch (GeneralSecurityException e) {
      return handleDecryptionFailureWithSync(encryptedValue, ciphertextBase64, keyId, e);
    } catch (Exception e) {
      logger.warn("Decryption failed, returning original value: {}", e.getMessage());
      logger.debug("Decryption error details", e);
      return encryptedValue;
    }
  }

  /**
   * Handle decryption failure with defensive synchronization.
   *
   * <p>When decryption fails with a GeneralSecurityException, this method:
   *
   * <ol>
   *   <li>Checks if the key ID exists in the database
   *   <li>If key exists but decryption failed, tries to reload keyset from database
   *   <li>Retries decryption once after reload
   *   <li>If still fails, logs critical error and returns original value
   * </ol>
   *
   * @param encryptedValue the original encrypted value
   * @param ciphertextBase64 the extracted ciphertext
   * @param keyId the key ID from the encrypted value
   * @param originalException the original decryption exception
   * @return decrypted value if recovery succeeds, original value otherwise
   */
  private String handleDecryptionFailureWithSync(
      String encryptedValue,
      String ciphertextBase64,
      Long keyId,
      GeneralSecurityException originalException) {
    logger.warn(
        "Decryption failed for key ID {}. Attempting defensive recovery...",
        keyId != null ? Long.toUnsignedString(keyId) : "unknown");

    // Check if key exists in database (indicates key is valid but not in local keyset)
    boolean keyExistsInDb = false;
    if (keyRepository != null && keyId != null) {
      try {
        var keyOpt = keyRepository.findById(keyId);
        if (keyOpt.isPresent()) {
          KeyStatus status = keyOpt.get().getKeyStatus();
          keyExistsInDb = (status == KeyStatus.PRIMARY || status == KeyStatus.ENABLED);
          logger.info(
              "Key {} exists in database with status {}", Long.toUnsignedString(keyId), status);
        }
      } catch (Exception e) {
        logger.debug("Failed to check key in database: {}", e.getMessage());
      }
    }

    // If key exists in DB but not in local keyset, try reloading from database
    // Note: keyId cannot be null here since keyExistsInDb would be false if keyId was null
    if (keyExistsInDb && keyManager.isDatabaseStorageAvailable() && keyId != null) {
      int maxRetries = properties != null ? properties.getKeyset().getMaxReloadRetries() : 1;
      long keyIdValue = keyId; // Safe unboxing after null check

      for (int retry = 0; retry < maxRetries; retry++) {
        logger.info(
            "🔄 Key {} exists in database but decryption failed. "
                + "Attempting keyset reload from database (attempt {}/{})...",
            Long.toUnsignedString(keyIdValue),
            retry + 1,
            maxRetries);

        if (keyManager.forceReloadFromDatabase()) {
          // Retry decryption after reload
          try {
            Aead aead = keyManager.getAeadPrimitive();
            byte[] ct = Base64.getDecoder().decode(ciphertextBase64);
            byte[] pt = aead.decrypt(ct, null);
            logger.info(
                "✅ Decryption succeeded after keyset reload for key {}",
                Long.toUnsignedString(keyIdValue));
            return new String(pt, StandardCharsets.UTF_8);
          } catch (Exception retryException) {
            logger.warn(
                "Decryption still failed after keyset reload (attempt {}): {}",
                retry + 1,
                retryException.getMessage());
          }
        } else {
          logger.warn("Failed to reload keyset from database");
        }
      }
    }

    // If we get here, recovery failed
    logger.error(
        "❌ CRITICAL: Decryption failed for key {} and recovery was unsuccessful. "
            + "This may indicate: "
            + "(1) Key was disabled/deleted, "
            + "(2) Data corruption, "
            + "(3) Keyset synchronization failure. "
            + "Original error: {}",
        keyId != null ? Long.toUnsignedString(keyId) : "unknown",
        originalException.getMessage());

    // Check if degraded mode should be enabled
    if (properties != null && properties.getKeyset().isDegradedModeEnabled()) {
      logger.warn("⚠️ Degraded mode is enabled. Consider investigating this decryption failure.");
      // In degraded mode, we return the original encrypted value
      // The application can continue but encrypted data is not accessible
    }

    return encryptedValue;
  }

  /**
   * Check if a value is encrypted (matches format {@code ENC:keyID:Base64(ciphertext)}).
   *
   * <p>Performs strict format validation to prevent malicious prefix manipulation.
   *
   * @param value the value to check
   * @return true if the value matches the encrypted format exactly, false otherwise
   */
  public boolean isEncrypted(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    // Strict pattern matching prevents prefix manipulation attacks
    return ENCRYPTED_FORMAT_PATTERN.matcher(value).matches();
  }

  /**
   * Parse the key ID from an encrypted value prefix with strict validation.
   *
   * <p>Extracts and validates the key ID from format {@code ENC:keyID:Base64(ciphertext)}.
   *
   * <p>Tink key IDs are unsigned 64-bit integers. This method uses {@code Long.parseUnsignedLong()}
   * to correctly handle values greater than {@code Long.MAX_VALUE} (which appear as negative when
   * stored in Java's signed {@code long} type).
   *
   * <p>Validates:
   *
   * <ul>
   *   <li>Key ID is numeric (digits only, no sign)
   *   <li>Key ID is a valid unsigned 64-bit integer (0 to 2^64-1)
   *   <li>Format matches exact pattern
   * </ul>
   *
   * @param encryptedValue the encrypted value to parse
   * @return the key ID if format is valid, null otherwise
   */
  public Long parseKeyIdFromPrefix(String encryptedValue) {
    if (encryptedValue == null) {
      return null;
    }
    Matcher matcher = ENCRYPTED_FORMAT_PATTERN.matcher(encryptedValue);
    if (matcher.matches()) {
      String keyIdStr = matcher.group(1);
      try {
        // Parse as unsigned long (Tink key IDs are unsigned 64-bit)
        // parseUnsignedLong handles values > Long.MAX_VALUE correctly
        long keyId = Long.parseUnsignedLong(keyIdStr);
        return keyId;
      } catch (NumberFormatException e) {
        logger.warn("Failed to parse key ID from prefix (invalid format): {}", keyIdStr);
        logger.debug("Key ID parsing error", e);
        return null;
      }
    }
    return null;
  }

  /**
   * Extract and validate the Base64-encoded ciphertext from an encrypted value.
   *
   * <p>Format: {@code ENC:keyID:Base64(ciphertext)}.
   *
   * <p>Validates:
   *
   * <ul>
   *   <li>Format matches exact pattern (prevents prefix manipulation)
   *   <li>Base64 is valid Base64 encoding
   *   <li>Base64 meets minimum length requirement
   * </ul>
   *
   * @param encryptedValue the encrypted value to parse
   * @return the Base64-encoded ciphertext if valid, null otherwise
   */
  public String extractCiphertext(String encryptedValue) {
    if (encryptedValue == null) {
      return null;
    }

    Matcher matcher = ENCRYPTED_FORMAT_PATTERN.matcher(encryptedValue);
    if (!matcher.matches()) {
      logger.warn(
          "Invalid encrypted value format (does not match pattern): {}",
          maskSensitiveValue(encryptedValue));
      return null;
    }

    String ciphertextBase64 = matcher.group(2);

    // Validate Base64 is not empty
    if (ciphertextBase64 == null || ciphertextBase64.isEmpty()) {
      logger.warn("Empty Base64 ciphertext in encrypted value");
      return null;
    }

    // Validate minimum length
    if (ciphertextBase64.length() < MIN_CIPHERTEXT_BASE64_LENGTH) {
      logger.warn(
          "Base64 ciphertext too short: {} characters (minimum: {})",
          ciphertextBase64.length(),
          MIN_CIPHERTEXT_BASE64_LENGTH);
      return null;
    }

    // Validate Base64 encoding
    if (!isValidBase64(ciphertextBase64)) {
      logger.warn("Invalid Base64 encoding in encrypted value");
      return null;
    }

    return ciphertextBase64;
  }

  /**
   * Validate that a string is valid Base64 encoding.
   *
   * <p>Attempts to decode the string and catches any IllegalArgumentException if invalid.
   *
   * @param base64 the string to validate
   * @return true if valid Base64, false otherwise
   */
  private boolean isValidBase64(String base64) {
    if (base64 == null || base64.isEmpty()) {
      return false;
    }
    try {
      Base64.getDecoder().decode(base64);
      return true;
    } catch (IllegalArgumentException e) {
      logger.debug("Base64 validation failed", e);
      return false;
    }
  }

  /**
   * Mask sensitive value for logging (shows only prefix and length).
   *
   * <p>Prevents logging full encrypted values which could be sensitive.
   *
   * @param value the value to mask
   * @return masked representation (e.g., "ENC:1234567:*** (length: 50)")
   */
  private String maskSensitiveValue(String value) {
    if (value == null) {
      return "null";
    }
    if (value.length() <= 20) {
      return "*** (length: " + value.length() + ")";
    }
    // Show prefix and length only
    String prefix = value.substring(0, Math.min(20, value.length()));
    return prefix + "*** (length: " + value.length() + ")";
  }
}
