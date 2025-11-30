package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.crypto.tink.Aead;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for EncryptionService.
 *
 * <p>Tests encryption/decryption with format ENC:keyID:Base64(ciphertext).
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
class EncryptionServiceTest {

  @Mock private TinkKeyManager keyManager;

  @Mock private Aead aead;

  private EncryptionService encryptionService;

  private static final long TEST_KEY_ID = 1234567L;

  @BeforeEach
  void setUp() throws Exception {
    // Setup mocks
    when(keyManager.isInitialized()).thenReturn(true);
    when(keyManager.getCurrentPrimaryKeyId()).thenReturn(TEST_KEY_ID);
    when(keyManager.getAeadPrimitive()).thenReturn(aead);

    encryptionService = new EncryptionService(keyManager);
  }

  @Test
  void testEncrypt() throws Exception {
    // Arrange
    String plaintext = "test-data-123";
    byte[] ciphertext = "encrypted-bytes".getBytes(StandardCharsets.UTF_8);
    String ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext);

    when(aead.encrypt(any(byte[].class), any())).thenReturn(ciphertext);

    // Act
    String result = encryptionService.encrypt(plaintext);

    // Assert
    assertNotNull(result);
    assertTrue(result.startsWith("ENC:" + TEST_KEY_ID + ":"));
    assertEquals("ENC:" + TEST_KEY_ID + ":" + ciphertextBase64, result);
  }

  @Test
  void testDecrypt() throws Exception {
    // Arrange
    String plaintext = "test-data-123";
    byte[] ciphertext = "encrypted-bytes".getBytes(StandardCharsets.UTF_8);
    String ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext);
    String encryptedValue = "ENC:" + TEST_KEY_ID + ":" + ciphertextBase64;

    when(aead.decrypt(any(byte[].class), any())).thenReturn(plaintext.getBytes(StandardCharsets.UTF_8));

    // Act
    String result = encryptionService.decrypt(encryptedValue);

    // Assert
    assertEquals(plaintext, result);
  }

  @Test
  void testIsEncrypted() {
    // Arrange - Use valid Base64 with minimum length
    String validBase64 = Base64.getEncoder().encodeToString("test-data-1234567890".getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:1234567:" + validBase64;

    // Act
    boolean result = encryptionService.isEncrypted(encryptedValue);

    // Assert
    assertTrue(result);
  }

  @Test
  void testIsEncryptedInvalidFormat() {
    // Arrange
    String invalidValue = "ENC:AbCdEf123"; // Missing key ID

    // Act
    boolean result = encryptionService.isEncrypted(invalidValue);

    // Assert
    assertFalse(result);
  }

  @Test
  void testIsEncryptedPlaintext() {
    // Arrange
    String plaintext = "plaintext-data";

    // Act
    boolean result = encryptionService.isEncrypted(plaintext);

    // Assert
    assertFalse(result);
  }

  @Test
  void testIsEncryptedNull() {
    // Act
    boolean result = encryptionService.isEncrypted(null);

    // Assert
    assertFalse(result);
  }

  @Test
  void testParseKeyIdFromPrefix() {
    // Arrange - Use valid Base64 with minimum length
    String validBase64 = Base64.getEncoder().encodeToString("test-data-1234567890".getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:1234567:" + validBase64;

    // Act
    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    // Assert
    assertNotNull(keyId);
    assertEquals(1234567L, keyId);
  }

  @Test
  void testParseKeyIdFromPrefixMissingKeyId() {
    // Arrange
    String invalidValue = "ENC:AbCdEf123"; // Missing key ID

    // Act
    Long keyId = encryptionService.parseKeyIdFromPrefix(invalidValue);

    // Assert
    assertNull(keyId);
  }

  @Test
  void testParseKeyIdFromPrefixInvalidPrefix() {
    // Arrange
    String invalidValue = "INVALID:1234567:AbCdEf123";

    // Act
    Long keyId = encryptionService.parseKeyIdFromPrefix(invalidValue);

    // Assert
    assertNull(keyId);
  }

  @Test
  void testExtractCiphertext() {
    // Arrange - Use valid Base64 with minimum length
    String validBase64 = Base64.getEncoder().encodeToString("test-data-1234567890".getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:1234567:" + validBase64;

    // Act
    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    // Assert
    assertEquals(validBase64, ciphertext);
  }

  @Test
  void testExtractCiphertextInvalidFormat() {
    // Arrange
    String invalidValue = "INVALID:AbCdEf123";

    // Act
    String ciphertext = encryptionService.extractCiphertext(invalidValue);

    // Assert
    assertNull(ciphertext);
  }

  @Test
  void testEncryptNull() {
    // Act
    String result = encryptionService.encrypt(null);

    // Assert
    assertNull(result);
  }

  @Test
  void testDecryptNull() {
    // Act
    String result = encryptionService.decrypt(null);

    // Assert
    assertNull(result);
  }

  @Test
  void testEncryptAlreadyEncrypted() throws Exception {
    // Arrange - Use valid Base64 with minimum length
    String validBase64 = Base64.getEncoder().encodeToString("test-data-1234567890".getBytes(StandardCharsets.UTF_8));
    String alreadyEncrypted = "ENC:1234567:" + validBase64;

    // Act
    String result = encryptionService.encrypt(alreadyEncrypted);

    // Assert - Should return unchanged (prevents double encryption)
    assertEquals(alreadyEncrypted, result);
  }

  @Test
  void testEncryptWhenNotAvailable() {
    // Arrange
    when(keyManager.isInitialized()).thenReturn(false);
    String plaintext = "test-data";

    // Act
    String result = encryptionService.encrypt(plaintext);

    // Assert - Should return plaintext unchanged
    assertEquals(plaintext, result);
  }

  @Test
  void testDecryptWhenNotAvailable() {
    // Arrange
    when(keyManager.isInitialized()).thenReturn(false);
    String validBase64 = Base64.getEncoder().encodeToString("test-data-1234567890".getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:1234567:" + validBase64;

    // Act
    String result = encryptionService.decrypt(encryptedValue);

    // Assert - Should return value unchanged
    assertEquals(encryptedValue, result);
  }

  @Test
  void testRoundTripEncryptionDecryption() throws Exception {
    // Arrange
    String plaintext = "sensitive-data-123";
    byte[] ciphertext = Base64.getDecoder().decode("dGVzdC1kYXRh");
    when(aead.encrypt(any(byte[].class), any())).thenReturn(ciphertext);
    when(aead.decrypt(any(byte[].class), any())).thenReturn(plaintext.getBytes(StandardCharsets.UTF_8));

    // Act
    String encrypted = encryptionService.encrypt(plaintext);
    String decrypted = encryptionService.decrypt(encrypted);

    // Assert
    assertNotNull(encrypted);
    assertTrue(encrypted.startsWith("ENC:" + TEST_KEY_ID + ":"));
    assertEquals(plaintext, decrypted);
  }

  @Test
  void testParseKeyIdLargeValue() {
    // Arrange - Use valid Base64 with minimum length
    long largeKeyId = Long.MAX_VALUE;
    String validBase64 = Base64.getEncoder().encodeToString("test-data-1234567890".getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:" + largeKeyId + ":" + validBase64;

    // Act
    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    // Assert
    assertNotNull(keyId);
    assertEquals(largeKeyId, keyId);
  }

  @Test
  void testExtractCiphertextWithComplexBase64() {
    // Arrange
    // Base64 with padding and special characters (ensure minimum length)
    String complexData = "AbCdEf123+/=XYZ1234567890";
    String complexBase64 = Base64.getEncoder().encodeToString(complexData.getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:1234567:" + complexBase64;

    // Act
    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    // Assert
    assertEquals(complexBase64, ciphertext);
  }

  // Validation tests

  @Test
  void testParseKeyIdOutOfRangeNegative() {
    // Arrange - Negative key ID (invalid)
    String encryptedValue = "ENC:-1:AbCdEf12345678901234567890";

    // Act
    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    // Assert
    assertNull(keyId);
  }

  @Test
  void testParseKeyIdOutOfRangeTooLarge() {
    // Arrange - Key ID larger than Long.MAX_VALUE (invalid)
    String encryptedValue = "ENC:99999999999999999999:AbCdEf12345678901234567890";

    // Act
    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    // Assert - Should fail to parse (NumberFormatException)
    assertNull(keyId);
  }

  @Test
  void testParseKeyIdNonNumeric() {
    // Arrange - Non-numeric key ID (should not match pattern)
    String encryptedValue = "ENC:abc123:AbCdEf12345678901234567890";

    // Act
    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    // Assert - Pattern should not match
    assertNull(keyId);
  }

  @Test
  void testExtractCiphertextEmptyBase64() {
    // Arrange - Empty Base64
    String encryptedValue = "ENC:1234567:";

    // Act
    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    // Assert
    assertNull(ciphertext);
  }

  @Test
  void testExtractCiphertextTooShort() {
    // Arrange - Base64 too short (less than minimum)
    String encryptedValue = "ENC:1234567:AbC"; // Only 3 chars, minimum is 20

    // Act
    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    // Assert
    assertNull(ciphertext);
  }

  @Test
  void testExtractCiphertextInvalidBase64() {
    // Arrange - Invalid Base64 characters
    String encryptedValue = "ENC:1234567:AbCdEf!@#$%12345678901234567890";

    // Act
    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    // Assert - Should be null due to invalid Base64
    assertNull(ciphertext);
  }

  @Test
  void testExtractCiphertextPrefixManipulationDoublePrefix() {
    // Arrange - Double prefix attack (nested ENC: in Base64)
    // This should match pattern but Base64 validation should catch invalid encoding
    String nestedBase64 = Base64.getEncoder().encodeToString("ENC:1234567:data".getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:1234567:" + nestedBase64;

    // Act
    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    // Assert - Should extract Base64 (pattern matches)
    // Base64 validation will check if it's valid Base64
    assertNotNull(ciphertext);
    assertEquals(nestedBase64, ciphertext);
  }

  @Test
  void testExtractCiphertextMissingSeparator() {
    // Arrange - Missing colon separator
    String encryptedValue = "ENC:1234567AbCdEf12345678901234567890";

    // Act
    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    // Assert - Pattern should not match
    assertNull(ciphertext);
  }

  @Test
  void testIsEncryptedEmptyString() {
    // Arrange
    String empty = "";

    // Act
    boolean result = encryptionService.isEncrypted(empty);

    // Assert
    assertFalse(result);
  }

  @Test
  void testDecryptWithInvalidKeyId() {
    // Arrange - Invalid key ID (negative)
    String encryptedValue = "ENC:-1:AbCdEf12345678901234567890";

    // Act
    String result = encryptionService.decrypt(encryptedValue);

    // Assert - Should return original value (validation failed)
    assertEquals(encryptedValue, result);
  }

  @Test
  void testDecryptWithInvalidBase64() {
    // Arrange - Invalid Base64
    String encryptedValue = "ENC:1234567:Invalid!@#$%Base64";

    // Act
    String result = encryptionService.decrypt(encryptedValue);

    // Assert - Should return original value (validation failed)
    assertEquals(encryptedValue, result);
  }

  @Test
  void testDecryptWithTooShortBase64() {
    // Arrange - Base64 too short
    String encryptedValue = "ENC:1234567:AbC";

    // Act
    String result = encryptionService.decrypt(encryptedValue);

    // Assert - Should return original value (validation failed)
    assertEquals(encryptedValue, result);
  }

  @Test
  void testParseKeyIdMaxValue() {
    // Arrange - Maximum valid key ID
    long maxKeyId = Long.MAX_VALUE;
    String encryptedValue = "ENC:" + maxKeyId + ":AbCdEf12345678901234567890";

    // Act
    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    // Assert
    assertNotNull(keyId);
    assertEquals(maxKeyId, keyId);
  }

  @Test
  void testParseKeyIdZero() {
    // Arrange - Zero is valid
    String encryptedValue = "ENC:0:AbCdEf12345678901234567890";

    // Act
    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    // Assert
    assertNotNull(keyId);
    assertEquals(0L, keyId);
  }
}

