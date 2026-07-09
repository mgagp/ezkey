package org.ezkey.security;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.util.function.Supplier;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.entity.ApiKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
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
 * <p>This listener uses a static field to store {@link EncryptionOperations} because JPA entity
 * listeners are not managed by Spring and cannot use dependency injection directly. The runtime
 * implementation is injected via a setter method and also retrieved from ApplicationContext as a
 * fallback.
 *
 * @since 2025
 */
@Component
public class EncryptionEntityListener implements ApplicationContextAware {

  private static final Logger logger = LoggerFactory.getLogger(EncryptionEntityListener.class);

  /** Transient field name for {@link Enrollment#integrationPrivateKey} (diagnostics only). */
  private static final String INTEGRATION_PRIVATE_KEY_TRANSIENT = "integrationPrivateKey";

  /** Static field for encryption operations used by unmanaged JPA callbacks. */
  private static EncryptionOperations encryptionOperations;

  private static ApplicationContext applicationContext;

  /**
   * Set the application context (called by Spring).
   *
   * @param applicationContext the Spring application context
   * @throws BeansException if context cannot be set
   */
  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    EncryptionEntityListener.applicationContext = applicationContext;
  }

  /**
   * Initialize the encryption operations after Spring context is ready.
   *
   * <p>This method tries multiple approaches to get the runtime encryption operations: 1. Direct
   * injection via setter (preferred) 2. Retrieval from ApplicationContext (fallback)
   */
  @PostConstruct
  public void initializeEncryptionService() {
    // Try to get from ApplicationContext if not already injected
    if (encryptionOperations == null && applicationContext != null) {
      ObjectProvider<EncryptionOperations> provider =
          applicationContext.getBeanProvider(EncryptionOperations.class);
      EncryptionOperations operations = provider.getIfAvailable();
      if (operations != null) {
        EncryptionEntityListener.encryptionOperations = operations;
        EncryptionOperationsHolder.set(operations);
        logger.info("Encryption operations retrieved from ApplicationContext");
      }
    }

    // Log final state
    if (encryptionOperations != null) {
      logger.info(
          "EncryptionEntityListener initialized with encryption operations. "
              + "Encryption available: {}",
          encryptionOperations.isEncryptionAvailable());
    } else {
      logger.warn(
          "EncryptionEntityListener initialized but encryption operations are null. "
              + "Encryption will be disabled. "
              + "This is normal if encryption is disabled or master key is not configured.");
    }
  }

  /**
   * Set the encryption operations (injected by Spring).
   *
   * <p>This method is called by Spring's dependency injection framework. The implementation is
   * stored in a static field and in {@link EncryptionOperationsHolder} for access from unmanaged
   * JPA entity and listener callbacks.
   *
   * @param operations the encryption operations to use (may be null if encryption is disabled)
   */
  @Autowired(required = false)
  public void setEncryptionService(EncryptionOperations operations) {
    EncryptionEntityListener.encryptionOperations = operations;
    EncryptionOperationsHolder.set(operations);
    if (operations != null) {
      logger.info("Encryption operations injected via @Autowired setter");
    }
  }

  /**
   * Get the encryption operations (with lazy fallback to ApplicationContext).
   *
   * @return the encryption operations, or null if not available
   */
  private static EncryptionOperations getEncryptionOperations() {
    EncryptionOperations cachedOperations = encryptionOperations;
    if (cachedOperations != null) {
      return cachedOperations;
    }
    // Lazy fallback: try to get from ApplicationContext
    if (applicationContext != null) {
      ObjectProvider<EncryptionOperations> provider =
          applicationContext.getBeanProvider(EncryptionOperations.class);
      EncryptionOperations operations = provider.getIfAvailable();
      if (operations != null) {
        encryptionOperations = operations;
        EncryptionOperationsHolder.set(operations);
        logger.debug("Encryption operations retrieved from ApplicationContext (lazy)");
        return operations;
      }
    }
    return null;
  }

  @PrePersist
  @PreUpdate
  public void encrypt(Object entity) {
    EncryptionOperations operations = getEncryptionOperations();

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
      } catch (Exception e) {
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
      } catch (Exception e) {
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
      } catch (Exception e) {
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
      return;
    }

    if (operations.isEncrypted(plaintext)) {
      logger.trace("Field {} already encrypted for {}", transientFieldName, context);
      setFieldValue(entity, persistentFieldName, plaintext);
      return;
    }

    try {
      String encrypted = operations.encrypt(plaintext);
      setFieldValue(entity, persistentFieldName, encrypted);
      logger.debug("Encrypted {}", context);
      logIntegrationPrivateKeyDiagnostics(
          entity, transientFieldName, persistentFieldName, plaintext, operations, "afterEncrypt");
    } catch (Exception exception) {
      logger.error("Failed to encrypt {}", context, exception);
      AtRestEncryptionAccess.handleEncryptFailure(operations, context, exception);
      setFieldValue(entity, persistentFieldName, plaintext);
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
        } catch (Exception exception) {
          logger.debug(
              "Failed to read field {} on {}",
              fieldName,
              entity.getClass().getSimpleName(),
              exception);
        }
      }
      return null;
    } catch (Exception exception) {
      logger.debug(
          "Failed to read field {} on {}", fieldName, entity.getClass().getSimpleName(), exception);
      return null;
    }
  }

  private void setFieldValue(Object entity, String fieldName, String value) {
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
        } catch (Exception exception) {
          logger.error(
              "Failed to write field {} on {}",
              fieldName,
              entity.getClass().getSimpleName(),
              exception);
        }
      }
    } catch (Exception exception) {
      logger.error(
          "Failed to write field {} on {}",
          fieldName,
          entity.getClass().getSimpleName(),
          exception);
    }
  }
}
