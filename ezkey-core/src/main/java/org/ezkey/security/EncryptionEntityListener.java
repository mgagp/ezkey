package org.ezkey.security;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.util.function.Supplier;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * JPA entity listener that applies encryption/decryption for sensitive fields.
 *
 * <p>When DEBUG is enabled for this class, integration private key diagnostics log full field
 * values (local development only; not for production or shared logs).
 *
 * <p>Currently encrypts/decrypts: Enrollment.integrationPrivateKey.
 *
 * <p>This listener uses a static field to store the EncryptionService because JPA entity listeners
 * are not managed by Spring and cannot use dependency injection directly. The service is injected
 * via a setter method and also retrieved from ApplicationContext as a fallback.
 *
 * @since 2025
 */
@Component
@DependsOn({"encryptionService", "tinkKeyManager"})
public class EncryptionEntityListener implements ApplicationContextAware {

  private static final Logger logger = LoggerFactory.getLogger(EncryptionEntityListener.class);

  /** Transient field name for {@link Enrollment#integrationPrivateKey} (diagnostics only). */
  private static final String INTEGRATION_PRIVATE_KEY_TRANSIENT = "integrationPrivateKey";

  /**
   * Static field for EncryptionService, accessible via reflection from Enrollment entity.
   *
   * <p>Made static with package-private accessor to allow access from Enrollment.
   */
  private static EncryptionService encryptionService;

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
   * Initialize the encryption service after Spring context is ready.
   *
   * <p>This method tries multiple approaches to get the EncryptionService: 1. Direct injection via
   * setter (preferred) 2. Retrieval from ApplicationContext (fallback)
   */
  @PostConstruct
  public void initializeEncryptionService() {
    // Try to get from ApplicationContext if not already injected
    if (encryptionService == null && applicationContext != null) {
      ObjectProvider<EncryptionService> provider =
          applicationContext.getBeanProvider(EncryptionService.class);
      EncryptionService service = provider.getIfAvailable();
      if (service != null) {
        EncryptionEntityListener.encryptionService = service;
        logger.info("EncryptionService retrieved from ApplicationContext");
      }
    }

    // Log final state
    if (encryptionService != null) {
      logger.info(
          "EncryptionEntityListener initialized with EncryptionService. "
              + "Encryption available: {}",
          encryptionService.isEncryptionAvailable());
    } else {
      logger.warn(
          "EncryptionEntityListener initialized but EncryptionService is null. "
              + "Encryption will be disabled. "
              + "This is normal if encryption is disabled or master key is not configured.");
    }
  }

  /**
   * Set the encryption service (injected by Spring).
   *
   * <p>This method is called by Spring's dependency injection framework. The service is stored in a
   * static field for access from JPA entity listener callbacks.
   *
   * @param service the encryption service to use (may be null if encryption is disabled)
   */
  @Autowired(required = false)
  public void setEncryptionService(EncryptionService service) {
    EncryptionEntityListener.encryptionService = service;
    if (service != null) {
      logger.info("EncryptionService injected via @Autowired setter");
    }
  }

  /**
   * Get the encryption service (with lazy fallback to ApplicationContext).
   *
   * @return the encryption service, or null if not available
   */
  private static EncryptionService getEncryptionService() {
    if (encryptionService != null) {
      return encryptionService;
    }
    // Lazy fallback: try to get from ApplicationContext
    if (applicationContext != null) {
      ObjectProvider<EncryptionService> provider =
          applicationContext.getBeanProvider(EncryptionService.class);
      EncryptionService service = provider.getIfAvailable();
      if (service != null) {
        encryptionService = service; // Cache for future use
        logger.debug("EncryptionService retrieved from ApplicationContext (lazy)");
        return service;
      }
    }
    return null;
  }

  @PrePersist
  @PreUpdate
  public void encrypt(Object entity) {
    EncryptionService service = getEncryptionService();

    if (entity instanceof Enrollment enrollment) {
      encryptField(
          enrollment,
          "integrationPrivateKey",
          "encryptedIntegrationPrivateKey",
          service,
          () -> "integration private key for enrollment " + enrollment.getEnrollmentId());
      encryptField(
          enrollment,
          "enrollmentProofToken",
          "encryptedEnrollmentProofToken",
          service,
          () -> "enrollment proof token for enrollment " + enrollment.getEnrollmentId());
    } else if (entity instanceof AuthAttempt authAttempt) {
      encryptField(
          authAttempt,
          "authAttemptProofToken",
          "encryptedAuthAttemptProofToken",
          service,
          () -> "auth attempt proof token for authAttempt " + authAttempt.getAuthAttemptId());
      encryptField(
          authAttempt,
          "deviceProofToken",
          "encryptedDeviceProofToken",
          service,
          () -> "device proof token for authAttempt " + authAttempt.getAuthAttemptId());
    }
  }

  private void encryptField(
      Object entity,
      String transientFieldName,
      String persistentFieldName,
      EncryptionService service,
      Supplier<String> contextSupplier) {

    String context = contextSupplier.get();
    String plaintext = getFieldValue(entity, transientFieldName);

    logIntegrationPrivateKeyDiagnostics(
        entity, transientFieldName, persistentFieldName, plaintext, service, "afterTransientRead");

    // When transient is null/blank, use persistent field as fallback source (setter wrote
    // plaintext to the mapped column; reflection may not see @Transient at PrePersist/flush).
    if ((plaintext == null || plaintext.isBlank())
        && "enrollmentProofToken".equals(transientFieldName)
        && entity instanceof Enrollment) {
      try {
        String fromPersistent = getFieldValue(entity, persistentFieldName);
        if (fromPersistent != null
            && !fromPersistent.isBlank()
            && (service == null || !service.isEncrypted(fromPersistent))) {
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
            && (service == null || !service.isEncrypted(fromPersistent))) {
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
        entity, transientFieldName, persistentFieldName, plaintext, service, "afterFallbacks");

    if (plaintext == null || plaintext.isBlank()) {
      warnIfIntegrationPrivateKeyTransientBlankButPersistentPlaintext(
          entity, transientFieldName, persistentFieldName, service);
      logger.trace("Field {} is null or blank for {}", transientFieldName, context);
      return;
    }

    if (service == null || !service.isEncryptionAvailable()) {
      logger.debug("Encryption unavailable, storing plaintext for {}", context);
      setFieldValue(entity, persistentFieldName, plaintext);
      return;
    }

    if (service.isEncrypted(plaintext)) {
      logger.trace("Field {} already encrypted for {}", transientFieldName, context);
      setFieldValue(entity, persistentFieldName, plaintext);
      return;
    }

    try {
      String encrypted = service.encrypt(plaintext);
      setFieldValue(entity, persistentFieldName, encrypted);
      logger.debug("Encrypted {}", context);
      logIntegrationPrivateKeyDiagnostics(
          entity, transientFieldName, persistentFieldName, plaintext, service, "afterEncrypt");
    } catch (Exception exception) {
      logger.error("Failed to encrypt {}", context, exception);
      setFieldValue(entity, persistentFieldName, plaintext);
    }
  }

  /**
   * DEBUG-only diagnostics for {@code integrationPrivateKey} encryption.
   *
   * <p><b>Local development only:</b> logs full transient and persistent field values (private key
   * material). Do not use this verbosity in shared or production environments.
   *
   * <p>Enable {@code logging.level.org.ezkey.security.EncryptionEntityListener=DEBUG} during Clean
   * Start to trace state at flush time.
   */
  private void logIntegrationPrivateKeyDiagnostics(
      Object entity,
      String transientFieldName,
      String persistentFieldName,
      String plaintextFromTransient,
      EncryptionService service,
      String phase) {

    if (!INTEGRATION_PRIVATE_KEY_TRANSIENT.equals(transientFieldName)
        || !(entity instanceof Enrollment enrollment)) {
      return;
    }
    if (!logger.isDebugEnabled()) {
      return;
    }

    String persistent = getFieldValue(entity, persistentFieldName);
    boolean encryptionAvailable = service != null && service.isEncryptionAvailable();
    boolean persistentLooksEncrypted =
        service != null && persistent != null && service.isEncrypted(persistent);
    boolean transientPresent = plaintextFromTransient != null && !plaintextFromTransient.isBlank();

    logger.debug(
        "EncryptionEntityListener diagnostic [integrationPrivateKey] phase={} enrollmentId={} "
            + "entityClass={} transientPresent={} transientCharLength={} persistentPresent={} "
            + "persistentLooksEncrypted={} encryptionAvailable={} "
            + "transientIntegrationPrivateKey={} persistentEncryptedIntegrationPrivateKey={}",
        phase,
        enrollment.getEnrollmentId(),
        entity.getClass().getName(),
        transientPresent,
        plaintextFromTransient != null ? plaintextFromTransient.length() : 0,
        persistent != null,
        persistentLooksEncrypted,
        encryptionAvailable,
        plaintextFromTransient,
        persistent);
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
      EncryptionService service) {

    if (!INTEGRATION_PRIVATE_KEY_TRANSIENT.equals(transientFieldName)
        || !(entity instanceof Enrollment enrollment)) {
      return;
    }

    String persistent = getFieldValue(entity, persistentFieldName);
    if (persistent == null || persistent.isBlank()) {
      return;
    }
    if (service != null && service.isEncrypted(persistent)) {
      return;
    }

    boolean encryptionAvailable = service != null && service.isEncryptionAvailable();
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
