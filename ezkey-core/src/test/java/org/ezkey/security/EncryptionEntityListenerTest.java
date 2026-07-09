package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.entity.ApiKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link EncryptionEntityListener}.
 *
 * <p>Validates that integration private key encryption uses the persistent field when the transient
 * field is blank (same failure mode as enrollment proof token at PrePersist).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EncryptionEntityListenerTest {

  @Mock private EncryptionOperations encryptionOperations;

  private EncryptionEntityListener listener;

  private static final String CIPHERTEXT = "ENC:1:abcdefghijklmnopqrstuvwxyz0123456789AB=";

  @BeforeEach
  void setUp() {
    listener = new EncryptionEntityListener();
    listener.setEncryptionService(encryptionOperations);
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    when(encryptionOperations.isEncrypted(anyString()))
        .thenAnswer(
            invocation -> {
              String arg = invocation.getArgument(0);
              return arg != null && arg.startsWith("ENC:");
            });
    when(encryptionOperations.encrypt(anyString())).thenReturn(CIPHERTEXT);
  }

  @AfterEach
  void tearDown() throws Exception {
    Field field = EncryptionEntityListener.class.getDeclaredField("encryptionOperations");
    field.setAccessible(true);
    field.set(null, null);
    EncryptionOperationsHolder.clear();
  }

  @Test
  void encrypt_encryptsIntegrationPrivateKeyWhenTransientBlankButPersistentHasPlaintext()
      throws Exception {
    String pkcs8Plaintext = "MIIplain-integration-private-key-simulated";

    Enrollment enrollment = new Enrollment(1, "test-enrollment", "proof-token-for-listener");
    enrollment.setIntegrationPrivateKey(pkcs8Plaintext);

    Field transientField = Enrollment.class.getDeclaredField("integrationPrivateKey");
    transientField.setAccessible(true);
    transientField.set(enrollment, null);

    listener.encrypt(enrollment);

    Field persistentField = Enrollment.class.getDeclaredField("encryptedIntegrationPrivateKey");
    persistentField.setAccessible(true);
    assertEquals(CIPHERTEXT, persistentField.get(enrollment));

    verify(encryptionOperations, times(2)).encrypt(anyString());
    verify(encryptionOperations).encrypt(eq(pkcs8Plaintext));
    assertTrue(((String) persistentField.get(enrollment)).startsWith("ENC:"));
  }

  @Test
  void encrypt_encryptsApiKeySecretHashWhenTransientBlankButPersistentHasPlaintext()
      throws Exception {
    String bcryptPlaintext = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    ApiKey apiKey = new ApiKey();
    apiKey.setApiKeyId(42);
    apiKey.setSecretKeyHash(bcryptPlaintext);

    Field transientField = ApiKey.class.getDeclaredField("secretKeyHashPlaintext");
    transientField.setAccessible(true);
    transientField.set(apiKey, null);

    listener.encrypt(apiKey);

    Field persistentField = ApiKey.class.getDeclaredField("secretKeyHash");
    persistentField.setAccessible(true);
    assertEquals(CIPHERTEXT, persistentField.get(apiKey));
    verify(encryptionOperations).encrypt(eq(bcryptPlaintext));
    assertTrue(((String) persistentField.get(apiKey)).startsWith("ENC:"));
  }
}
