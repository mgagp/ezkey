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
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit tests for EncryptionService.
 *
 * <p>Tests encryption/decryption with format ENC:keyID:Base64(ciphertext).
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EncryptionServiceTest {

  @Mock private TinkKeyManager keyManager;

  @Mock private Aead aead;

  @Mock private ObjectProvider<TinkProperties> propertiesProvider;

  @Mock private ObjectProvider<EncryptionKeyRepository> keyRepositoryProvider;

  private EncryptionService encryptionService;

  private static final long TEST_KEY_ID = 1234567L;

  @BeforeEach
  void setUp() throws Exception {
    when(keyManager.isInitialized()).thenReturn(true);
    when(keyManager.getCurrentPrimaryKeyId()).thenReturn(TEST_KEY_ID);
    when(keyManager.getAeadPrimitive()).thenReturn(aead);
    when(propertiesProvider.getIfAvailable()).thenReturn(null);
    when(keyRepositoryProvider.getIfAvailable()).thenReturn(null);

    encryptionService =
        new EncryptionService(keyManager, propertiesProvider, keyRepositoryProvider);
  }

  @Test
  void testEncrypt() throws Exception {
    String plaintext = "test-data-123";
    byte[] ciphertext = "encrypted-bytes".getBytes(StandardCharsets.UTF_8);
    String ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext);

    when(aead.encrypt(any(byte[].class), any())).thenReturn(ciphertext);

    String result = encryptionService.encrypt(plaintext);

    assertNotNull(result);
    assertTrue(result.startsWith("ENC:" + TEST_KEY_ID + ":"));
    assertEquals("ENC:" + TEST_KEY_ID + ":" + ciphertextBase64, result);
  }

  @Test
  void testDecrypt() throws Exception {
    String plaintext = "test-data-123";
    byte[] ciphertext = "encrypted-bytes".getBytes(StandardCharsets.UTF_8);
    String ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext);
    String encryptedValue = "ENC:" + TEST_KEY_ID + ":" + ciphertextBase64;

    when(aead.decrypt(any(byte[].class), any()))
        .thenReturn(plaintext.getBytes(StandardCharsets.UTF_8));

    String result = encryptionService.decrypt(encryptedValue);

    assertEquals(plaintext, result);
  }

  @Test
  void testIsEncrypted() {
    String validBase64 =
        Base64.getEncoder().encodeToString("test-data-1234567890".getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:1234567:" + validBase64;

    boolean result = encryptionService.isEncrypted(encryptedValue);

    assertTrue(result);
  }

  @Test
  void testIsEncryptedInvalidFormat() {
    String invalidValue = "ENC:AbCdEf123";

    boolean result = encryptionService.isEncrypted(invalidValue);

    assertFalse(result);
  }

  @Test
  void testIsEncryptedPlaintext() {
    String plaintext = "plaintext-data";

    boolean result = encryptionService.isEncrypted(plaintext);

    assertFalse(result);
  }

  @Test
  void testIsEncryptedNull() {
    boolean result = encryptionService.isEncrypted(null);

    assertFalse(result);
  }

  @Test
  void testParseKeyIdFromPrefix() {
    String validBase64 =
        Base64.getEncoder().encodeToString("test-data-1234567890".getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:1234567:" + validBase64;

    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    assertNotNull(keyId);
    assertEquals(1234567L, keyId);
  }

  @Test
  void testParseKeyIdFromPrefixMissingKeyId() {
    String invalidValue = "ENC:AbCdEf123";

    Long keyId = encryptionService.parseKeyIdFromPrefix(invalidValue);

    assertNull(keyId);
  }

  @Test
  void testParseKeyIdFromPrefixInvalidPrefix() {
    String invalidValue = "INVALID:1234567:AbCdEf123";

    Long keyId = encryptionService.parseKeyIdFromPrefix(invalidValue);

    assertNull(keyId);
  }

  @Test
  void testExtractCiphertext() {
    String validBase64 =
        Base64.getEncoder().encodeToString("test-data-1234567890".getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:1234567:" + validBase64;

    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    assertEquals(validBase64, ciphertext);
  }

  @Test
  void testExtractCiphertextInvalidFormat() {
    String invalidValue = "INVALID:AbCdEf123";

    String ciphertext = encryptionService.extractCiphertext(invalidValue);

    assertNull(ciphertext);
  }

  @Test
  void testEncryptNull() {
    String result = encryptionService.encrypt(null);

    assertNull(result);
  }

  @Test
  void testDecryptNull() {
    String result = encryptionService.decrypt(null);

    assertNull(result);
  }

  @Test
  void testEncryptAlreadyEncrypted() throws Exception {
    String validBase64 =
        Base64.getEncoder().encodeToString("test-data-1234567890".getBytes(StandardCharsets.UTF_8));
    String alreadyEncrypted = "ENC:1234567:" + validBase64;

    String result = encryptionService.encrypt(alreadyEncrypted);

    assertEquals(alreadyEncrypted, result);
  }

  @Test
  void testEncryptWhenNotAvailable() {
    when(keyManager.isInitialized()).thenReturn(false);
    String plaintext = "test-data";

    String result = encryptionService.encrypt(plaintext);

    assertEquals(plaintext, result);
  }

  @Test
  void testDecryptWhenNotAvailable() {
    when(keyManager.isInitialized()).thenReturn(false);
    String validBase64 =
        Base64.getEncoder().encodeToString("test-data-1234567890".getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:1234567:" + validBase64;

    String result = encryptionService.decrypt(encryptedValue);

    assertEquals(encryptedValue, result);
  }

  @Test
  void testRoundTripEncryptionDecryption() throws Exception {
    String plaintext = "sensitive-data-123";
    byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
    String ciphertextBase64 =
        Base64.getEncoder()
            .encodeToString("dummy-ciphertext-for-test".getBytes(StandardCharsets.UTF_8));
    byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);

    when(aead.encrypt(plaintextBytes, null)).thenReturn(ciphertext);
    when(aead.decrypt(any(byte[].class), any())).thenReturn(plaintextBytes);

    String encrypted = encryptionService.encrypt(plaintext);
    String decrypted = encryptionService.decrypt(encrypted);

    assertNotNull(encrypted);
    assertTrue(encrypted.startsWith("ENC:" + TEST_KEY_ID + ":"));
    assertEquals(plaintext, decrypted);
  }

  @Test
  void testParseKeyIdLargeValue() {
    long largeKeyId = Long.MAX_VALUE;
    String validBase64 =
        Base64.getEncoder().encodeToString("test-data-1234567890".getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:" + largeKeyId + ":" + validBase64;

    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    assertNotNull(keyId);
    assertEquals(largeKeyId, keyId);
  }

  @Test
  void testExtractCiphertextWithComplexBase64() {
    String complexData = "AbCdEf123+/=XYZ1234567890";
    String complexBase64 =
        Base64.getEncoder().encodeToString(complexData.getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:1234567:" + complexBase64;

    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    assertEquals(complexBase64, ciphertext);
  }

  @Test
  void testParseKeyIdOutOfRangeNegative() {
    String encryptedValue = "ENC:-1:AbCdEf12345678901234567890";

    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    assertNull(keyId);
  }

  @Test
  void testParseKeyIdOutOfRangeTooLarge() {
    String encryptedValue = "ENC:99999999999999999999:AbCdEf12345678901234567890";

    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    assertNull(keyId);
  }

  @Test
  void testParseKeyIdNonNumeric() {
    String encryptedValue = "ENC:abc123:AbCdEf12345678901234567890";

    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    assertNull(keyId);
  }

  @Test
  void testExtractCiphertextEmptyBase64() {
    String encryptedValue = "ENC:1234567:";

    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    assertNull(ciphertext);
  }

  @Test
  void testExtractCiphertextTooShort() {
    String encryptedValue = "ENC:1234567:AbC";

    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    assertNull(ciphertext);
  }

  @Test
  void testExtractCiphertextInvalidBase64() {
    String encryptedValue = "ENC:1234567:AbCdEf!@#$%12345678901234567890";

    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    assertNull(ciphertext);
  }

  @Test
  void testExtractCiphertextPrefixManipulationDoublePrefix() {
    String nestedBase64 =
        Base64.getEncoder().encodeToString("ENC:1234567:data".getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:1234567:" + nestedBase64;

    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    assertNotNull(ciphertext);
    assertEquals(nestedBase64, ciphertext);
  }

  @Test
  void testExtractCiphertextMissingSeparator() {
    String encryptedValue = "ENC:1234567AbCdEf12345678901234567890";

    String ciphertext = encryptionService.extractCiphertext(encryptedValue);

    assertNull(ciphertext);
  }

  @Test
  void testIsEncryptedEmptyString() {
    String empty = "";

    boolean result = encryptionService.isEncrypted(empty);

    assertFalse(result);
  }

  @Test
  void testDecryptWithInvalidKeyId() {
    String encryptedValue = "ENC:-1:AbCdEf12345678901234567890";

    String result = encryptionService.decrypt(encryptedValue);

    assertEquals(encryptedValue, result);
  }

  @Test
  void testDecryptWithInvalidBase64() {
    String encryptedValue = "ENC:1234567:Invalid!@#$%Base64";

    String result = encryptionService.decrypt(encryptedValue);

    assertEquals(encryptedValue, result);
  }

  @Test
  void testDecryptWithTooShortBase64() {
    String encryptedValue = "ENC:1234567:AbC";

    String result = encryptionService.decrypt(encryptedValue);

    assertEquals(encryptedValue, result);
  }

  @Test
  void testParseKeyIdMaxValue() {
    long maxKeyId = Long.MAX_VALUE;
    String encryptedValue = "ENC:" + maxKeyId + ":AbCdEf12345678901234567890";

    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    assertNotNull(keyId);
    assertEquals(maxKeyId, keyId);
  }

  @Test
  void testParseKeyIdZero() {
    String encryptedValue = "ENC:0:AbCdEf12345678901234567890";

    Long keyId = encryptionService.parseKeyIdFromPrefix(encryptedValue);

    assertNotNull(keyId);
    assertEquals(0L, keyId);
  }
}
