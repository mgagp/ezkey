package org.ezkey.security;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.util.Map;
import java.util.function.Supplier;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.entity.ApiKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * JPA entity listener that applies encryption/decryption for sensitive fields.
 *
 * <p>When DEBUG is enabled for this class, integration private key diagnostics log full field
 * values (local development only; not for production or shared logs).
 *
 * <p>Currently encrypts/decrypts: Enrollment.integrationPrivateKey,
 * Enrollment.enrollmentProofToken, AuthAttempt.authAttemptProofToken, ApiKey.secretKeyHash.
 *
 * <p><b>Lazy resolution (Spring Boot 4.2, #1458).</b> Hibernate asks Spring for this listener while
 * it builds the entity manager factory. The encryption service depends on {@code TinkKeyManager},
 * which depends on a JPA repository, which needs that same entity manager factory. Resolving {@link
 * EncryptionOperations} at construction therefore closes a bean cycle. The listener only receives
 * an {@link ObjectProvider} and hands it to {@link EncryptionOperationsHolder}; the encryption
 * service is looked up on the first encrypted write or read, once the factory exists. Do not
 * reintroduce an eager lookup (setter injection, {@code @PostConstruct}).
 *
 * @since 2025
 */
@Component
public class EncryptionEntityListener {

  private static final Logger logger = LoggerFactory.getLogger(EncryptionEntityListener.class);

  /** Transient field name for {@link Enrollment#integrationPrivateKey} (diagnostics only). */
  private static final String INTEGRATION_PRIVATE_KEY_TRANSIENT = "integrationPrivateKey";

  /**
   * Maps each encrypted persistent field name to its companion indexed key-id field name
   * (I-2026-0029). Populated whenever the persistent field is written, so the two columns always
   * stay in sync (see {@link #updateEncryptionKeyIdField}).
   */
  private static final Map<String, String> KEY_ID_FIELD_BY_PERSISTENT_FIELD =
      Map.of(
          "encryptedIntegrationPrivateKey", "integrationPrivateKeyEncryptionKeyId",
          "encryptedEnrollmentProofToken", "enrollmentProofTokenEncryptionKeyId",
          "encryptedAuthAttemptProofToken", "authAttemptProofTokenEncryptionKeyId",
          "secretKeyHash", "secretKeyHashEncryptionKeyId");

  /**
   * Registers a lazy source of encryption operations without resolving it.
   *
   * @param encryptionOperationsProvider provider of the runtime encryption operations; may have no
   *     bean when encryption is not configured
   */
  public EncryptionEntityListener(
      ObjectProvider<EncryptionOperations> encryptionOperationsProvider) {
    EncryptionOperationsHolder.setSupplier(encryptionOperationsProvider::getIfAvailable);
    logger.debug("EncryptionEntityListener registered a lazy encryption operations provider");
  }

  @PrePersist
  @PreUpdate
  public void encrypt(Object entity) {
    EncryptionOperations operations = EncryptionOperationsHolder.get();

    if (entity instanceof Enrollment enrollment) {
      encryptField(
          enrollment,
          "integrationPrivateKey",
          "encryptedIntegrationPrivateKey",
          operations,
          () -> "integration private key for enrollment " + enrollment.getEnrollmentId());
      encryptField(
          enrollment,
          "enrollmentProofToken",
          "encryptedEnrollmentProofToken",
          operations,
          () -> "enrollment proof token for enrollment " + enrollment.getEnrollmentId());
    } else if (entity instanceof AuthAttempt authAttempt) {
      encryptField(
          authAttempt,
          "authAttemptProofToken",
          "encryptedAuthAttemptProofToken",
          operations,
          () -> "auth attempt proof token for authAttempt " + authAttempt.getAuthAttemptId());
    } else if (entity instanceof ApiKey apiKey) {
      encryptField(
          apiKey,
          "secretKeyHashPlaintext",
          "secretKeyHash",
          operations,
          () -> "API key secret hash for apiKey " + apiKey.getApiKeyId());
    }
  }

  private void encryptField(
      Object entity,
      String transientFieldName,
      String persistentFieldName,
      EncryptionOperations operations,
      Supplier<String> contextSupplier) {

    String context = contextSupplier.get();
    String plaintext = getFieldValue(entity, transientFieldName);

    logIntegrationPrivateKeyDiagnostics(
        entity,
        transientFieldName,
        persistentFieldName,
        plaintext,
        operations,
        "afterTransientRead");

    // When transient is null/blank, use persistent field as fallback source (setter wrote
    // plaintext to the mapped column; reflection may not see @Transient at PrePersist/flush).
    if ((plaintext == null || plaintext.isBlank())
        && "enrollmentProofToken".equals(transientFieldName)
        && entity instanceof Enrollment) {
      try {
        String fromPersistent = getFieldValue(entity, persistentFieldName);
        if (fromPersistent != null
            && !fromPersistent.isBlank()
            && (operations == null || !operations.isEncrypted(fromPersistent))) {
          plaintext = fromPersistent;
        }
      } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
        logger.warn(
            "Fallback read of enrollment proof token from persistent field failed for {}, "
                + "skipping encryption of proof token",
            context,
            e);
        // Leave plaintext null so we return early; persistent field stays as set by setter
      }
    }

    if ((plaintext == null || plaintext.isBlank())
        && "secretKeyHashPlaintext".equals(transientFieldName)
        && entity instanceof ApiKey) {
      try {
        String fromPersistent = getFieldValue(entity, persistentFieldName);
        if (fromPersistent != null
            && !fromPersistent.isBlank()
            && (operations == null || !operations.isEncrypted(fromPersistent))) {
          plaintext = fromPersistent;
        }
      } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
        logger.warn(
            "Fallback read of API key secret hash from persistent field failed for {}, "
                + "skipping encryption of secret hash",
            context,
            e);
      }
    }

    // Same pattern as enrollment proof token: setIntegrationPrivateKey copies plaintext into
    // encryptedIntegrationPrivateKey, but the transient integrationPrivateKey can be null when
    // this listener runs (e.g. at PrePersist before id assignment). Without this fallback the
    // column would stay plaintext despite Tink being available.
    if ((plaintext == null || plaintext.isBlank())
        && INTEGRATION_PRIVATE_KEY_TRANSIENT.equals(transientFieldName)
        && entity instanceof Enrollment) {
      try {
        String fromPersistent = getFieldValue(entity, persistentFieldName);
        if (fromPersistent != null
            && !fromPersistent.isBlank()
            && (operations == null || !operations.isEncrypted(fromPersistent))) {
          plaintext = fromPersistent;
        }
      } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
        logger.warn(
            "Fallback read of integration private key from persistent field failed for {}, "
                + "skipping encryption of integration private key",
            context,
            e);
      }
    }

    logIntegrationPrivateKeyDiagnostics(
        entity, transientFieldName, persistentFieldName, plaintext, operations, "afterFallbacks");

    if (plaintext == null || plaintext.isBlank()) {
      warnIfIntegrationPrivateKeyTransientBlankButPersistentPlaintext(
          entity, transientFieldName, persistentFieldName, operations);
      logger.trace("Field {} is null or blank for {}", transientFieldName, context);
      return;
    }

    if (operations == null || !operations.isEncryptionAvailable()) {
      AtRestEncryptionAccess.requireEncryptionAvailableForPersist(operations, context);
      logger.debug("Encryption unavailable, storing plaintext for {}", context);
      setFieldValue(entity, persistentFieldName, plaintext);
      updateEncryptionKeyIdField(entity, persistentFieldName, operations, plaintext);
      return;
    }

    if (operations.isEncrypted(plaintext)) {
      logger.trace("Field {} already encrypted for {}", transientFieldName, context);
      setFieldValue(entity, persistentFieldName, plaintext);
      updateEncryptionKeyIdField(entity, persistentFieldName, operations, plaintext);
      return;
    }

    try {
      String encrypted = operations.encrypt(plaintext);
      setFieldValue(entity, persistentFieldName, encrypted);
      updateEncryptionKeyIdField(entity, persistentFieldName, operations, encrypted);
      logger.debug("Encrypted {}", context);
      logIntegrationPrivateKeyDiagnostics(
          entity, transientFieldName, persistentFieldName, plaintext, operations, "afterEncrypt");
    } catch (IllegalArgumentException | IllegalStateException | SecurityException exception) {
      logger.error("Failed to encrypt {}", context, exception);
      AtRestEncryptionAccess.handleEncryptFailure(operations, context, exception);
      setFieldValue(entity, persistentFieldName, plaintext);
      updateEncryptionKeyIdField(entity, persistentFieldName, operations, plaintext);
    }
  }

  /**
   * DEBUG-only, <b>redacted</b> diagnostics for {@code integrationPrivateKey} encryption state.
   *
   * <p>Never logs key material. Enable {@code
   * logging.level.org.ezkey.security.EncryptionEntityListener=DEBUG} only when troubleshooting
   * encrypt-at-flush behaviour.
   */
  private void logIntegrationPrivateKeyDiagnostics(
      Object entity,
      String transientFieldName,
      String persistentFieldName,
      String plaintextFromTransient,
      EncryptionOperations operations,
      String phase) {

    if (!INTEGRATION_PRIVATE_KEY_TRANSIENT.equals(transientFieldName)
        || !(entity instanceof Enrollment enrollment)) {
      return;
    }
    if (!logger.isDebugEnabled()) {
      return;
    }

    String persistent = getFieldValue(entity, persistentFieldName);
    boolean encryptionAvailable = operations != null && operations.isEncryptionAvailable();
    boolean persistentLooksEncrypted =
        operations != null && persistent != null && operations.isEncrypted(persistent);
    boolean transientPresent = plaintextFromTransient != null && !plaintextFromTransient.isBlank();
    int persistentLen = persistent != null ? persistent.length() : 0;

    logger.debug(
        "EncryptionEntityListener [integrationPrivateKey] phase={} enrollmentId={} entityClass={}"
            + " transientPresent={} transientCharLength={} persistentPresent={}"
            + " persistentCharLength={} persistentLooksEncrypted={} encryptionAvailable={}",
        phase,
        enrollment.getEnrollmentId(),
        entity.getClass().getName(),
        transientPresent,
        plaintextFromTransient != null ? plaintextFromTransient.length() : 0,
        persistent != null,
        persistentLen,
        persistentLooksEncrypted,
        encryptionAvailable);
  }

  /**
   * If the transient integration private key is blank but the mapped column still holds a value
   * that is not {@code ENC:...}, the listener will return without encrypting (should be rare after
   * {@code encryptField} persistent fallback for {@code integrationPrivateKey}).
   */
  private void warnIfIntegrationPrivateKeyTransientBlankButPersistentPlaintext(
      Object entity,
      String transientFieldName,
      String persistentFieldName,
      EncryptionOperations operations) {

    if (!INTEGRATION_PRIVATE_KEY_TRANSIENT.equals(transientFieldName)
        || !(entity instanceof Enrollment enrollment)) {
      return;
    }

    String persistent = getFieldValue(entity, persistentFieldName);
    if (persistent == null || persistent.isBlank()) {
      return;
    }
    if (operations != null && operations.isEncrypted(persistent)) {
      return;
    }

    boolean encryptionAvailable = operations != null && operations.isEncryptionAvailable();
    logger.warn(
        "Enrollment integration_private_key: transient integrationPrivateKey is blank but "
            + "encryptedIntegrationPrivateKey holds an unencrypted value; listener will skip "
            + "encryption for this field. enrollmentId={} entityClass={} encryptionAvailable={} "
            + "persistentCharLength={}",
        enrollment.getEnrollmentId(),
        entity.getClass().getName(),
        encryptionAvailable,
        persistent.length());
  }

  private String getFieldValue(Object entity, String fieldName) {
    try {
      java.lang.reflect.Field field = entity.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return (String) field.get(entity);
    } catch (NoSuchFieldException e) {
      Class<?> superClass = entity.getClass().getSuperclass();
      if (superClass != null) {
        try {
          java.lang.reflect.Field field = superClass.getDeclaredField(fieldName);
          field.setAccessible(true);
          return (String) field.get(entity);
        } catch (ReflectiveOperationException | ClassCastException | SecurityException exception) {
          logger.debug(
              "Failed to read field {} on {}",
              fieldName,
              entity.getClass().getSimpleName(),
              exception);
        }
      }
      return null;
    } catch (ReflectiveOperationException | ClassCastException | SecurityException exception) {
      logger.debug(
          "Failed to read field {} on {}", fieldName, entity.getClass().getSimpleName(), exception);
      return null;
    }
  }

  /**
   * Keeps the indexed {@code *_encryption_key_id} companion column (I-2026-0029) in sync with
   * whatever value was just written to the ciphertext column.
   *
   * <p>{@code null} when {@code storedValue} does not match the {@code ENC:{keyId}:} format
   * (plaintext-at-rest, e.g. encryption disabled/unavailable) — same rows that a {@code LIKE
   * 'ENC:{keyId}:%'} predicate would already exclude today.
   *
   * @param entity the entity being persisted
   * @param persistentFieldName the ciphertext field name just written (e.g. {@code
   *     encryptedIntegrationPrivateKey})
   * @param operations encryption operations used to parse the key id, or null if unavailable
   * @param storedValue the value just written to the persistent field
   */
  private void updateEncryptionKeyIdField(
      Object entity,
      String persistentFieldName,
      EncryptionOperations operations,
      String storedValue) {
    String keyIdFieldName = KEY_ID_FIELD_BY_PERSISTENT_FIELD.get(persistentFieldName);
    if (keyIdFieldName == null) {
      return;
    }
    Long keyId =
        (operations != null && storedValue != null && operations.isEncrypted(storedValue))
            ? operations.parseKeyIdFromPrefix(storedValue)
            : null;
    setFieldValue(entity, keyIdFieldName, keyId);
  }

  private void setFieldValue(Object entity, String fieldName, Object value) {
    try {
      java.lang.reflect.Field field = entity.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(entity, value);
    } catch (NoSuchFieldException e) {
      Class<?> superClass = entity.getClass().getSuperclass();
      if (superClass != null) {
        try {
          java.lang.reflect.Field field = superClass.getDeclaredField(fieldName);
          field.setAccessible(true);
          field.set(entity, value);
          return;
        } catch (ReflectiveOperationException
            | IllegalArgumentException
            | IllegalStateException
            | SecurityException exception) {
          logger.error(
              "Failed to write field {} on {}",
              fieldName,
              entity.getClass().getSimpleName(),
              exception);
        }
      }
    } catch (ReflectiveOperationException
        | IllegalArgumentException
        | IllegalStateException
        | SecurityException exception) {
      logger.error(
          "Failed to write field {} on {}",
          fieldName,
          entity.getClass().getSimpleName(),
          exception);
    }
  }
}
