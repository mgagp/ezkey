package org.ezkey.security;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA entity listener that applies encryption/decryption for sensitive fields.
 *
 * <p>Currently encrypts/decrypts: Enrollment.integrationPrivateKey.
 *
 * <p>This listener uses a static field to store the EncryptionService because JPA
 * entity listeners are not managed by Spring and cannot use dependency injection
 * directly. The service is injected via a setter method and also retrieved from
 * ApplicationContext as a fallback.
 *
 * @since 2025
 */
@Component
@DependsOn({"encryptionService", "tinkKeyManager"})
public class EncryptionEntityListener implements ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(EncryptionEntityListener.class);

    /** 
     * Static field for EncryptionService, accessible via reflection from Enrollment entity.
     * 
     * <p>Made package-private (not private) to allow access via reflection from Enrollment.
     */
    static EncryptionService encryptionService;
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
     * <p>This method tries multiple approaches to get the EncryptionService:
     * 1. Direct injection via setter (preferred)
     * 2. Retrieval from ApplicationContext (fallback)
     */
    @PostConstruct
    public void initializeEncryptionService() {
        // Try to get from ApplicationContext if not already injected
        if (encryptionService == null && applicationContext != null) {
            try {
                EncryptionService service = applicationContext.getBean(EncryptionService.class);
                EncryptionEntityListener.encryptionService = service;
                logger.info("EncryptionService retrieved from ApplicationContext");
            } catch (BeansException e) {
                logger.warn("EncryptionService bean not found in ApplicationContext", e);
            }
        }

        // Log final state
        if (encryptionService != null) {
            logger.info(
                "EncryptionEntityListener initialized with EncryptionService. "
                    + "Encryption available: {}",
                encryptionService.isEncryptionAvailable()
            );
        } else {
            logger.warn(
                "EncryptionEntityListener initialized but EncryptionService is null. "
                    + "Encryption will be disabled. "
                    + "This is normal if encryption is disabled or master key is not configured."
            );
        }
    }

    /**
     * Set the encryption service (injected by Spring).
     *
     * <p>This method is called by Spring's dependency injection framework.
     * The service is stored in a static field for access from JPA entity listener callbacks.
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
            try {
                EncryptionService service = applicationContext.getBean(EncryptionService.class);
                encryptionService = service; // Cache for future use
                logger.debug("EncryptionService retrieved from ApplicationContext (lazy)");
                return service;
            } catch (BeansException e) {
                logger.debug("EncryptionService not available in ApplicationContext", e);
            }
        }
        return null;
    }

    @PrePersist
    @PreUpdate
    public void encrypt(Object entity) {
        EncryptionService service = getEncryptionService();
        if (service == null) {
            logger.debug("EncryptionService is null, skipping encryption");
            return;
        }
        if (!service.isEncryptionAvailable()) {
            logger.debug("Encryption not available, skipping encryption");
            return;
        }
        if (entity instanceof Enrollment enrollment) {
            Integer enrollmentId = enrollment.getEnrollmentId();
            
            // Get plaintext from transient field (setIntegrationPrivateKey stores plaintext here)
            // Use reflection to access the transient field directly
            String plaintext = getTransientPrivateKey(enrollment);
            
            if (plaintext == null || plaintext.isBlank()) {
                logger.debug("Integration private key is null or blank for enrollment {}", enrollmentId);
                return;
            }
            
            // Only encrypt if NOT already encrypted (strict check: must start with "ENC:")
            // Do NOT use looksEncrypted() here - it has legacy heuristics that may match plaintext
            if (!service.isEncrypted(plaintext)) {
                try {
                    String encrypted = service.encrypt(plaintext);
                    // Store encrypted value in the persistent field
                    setEncryptedPrivateKey(enrollment, encrypted);
                    logger.debug(
                        "Encrypted integration private key for enrollment {}",
                        enrollmentId
                    );
                } catch (Exception e) {
                    logger.error("Failed to encrypt integration private key", e);
                    // Don't throw - allow entity to be saved without encryption
                }
            } else {
                logger.debug(
                    "Integration private key for enrollment {} already encrypted, skipping",
                    enrollmentId
                );
            }
        }
    }
    
    /**
     * Gets the plaintext private key from the transient field via reflection.
     * 
     * @param enrollment the enrollment entity
     * @return the plaintext private key from transient field, or null if not set
     */
    private String getTransientPrivateKey(Enrollment enrollment) {
        try {
            java.lang.reflect.Field field = Enrollment.class.getDeclaredField("integrationPrivateKey");
            field.setAccessible(true);
            return (String) field.get(enrollment);
        } catch (Exception e) {
            logger.debug("Failed to access transient private key field", e);
            return null;
        }
    }
    
    /**
     * Sets the encrypted private key in the persistent field via reflection.
     * 
     * @param enrollment the enrollment entity
     * @param encrypted the encrypted value to store
     */
    private void setEncryptedPrivateKey(Enrollment enrollment, String encrypted) {
        try {
            java.lang.reflect.Field field = Enrollment.class.getDeclaredField("encryptedIntegrationPrivateKey");
            field.setAccessible(true);
            field.set(enrollment, encrypted);
        } catch (Exception e) {
            logger.error("Failed to set encrypted private key field", e);
        }
    }

    /**
     * PostLoad callback removed - decryption now happens on-demand in getIntegrationPrivateKey().
     * 
     * <p>With the @Transient field approach, we no longer need to decrypt in @PostLoad.
     * The decryption happens lazily when getIntegrationPrivateKey() is called, avoiding
     * the dirty checking issue that triggered re-encryption cycles.
     */
}


