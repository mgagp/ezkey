/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminBootstrapService
 * Description: Service for bootstrapping admin MFA infrastructure at application startup.
 */

package org.ezkey.admin.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.config.AdminMfaProperties;
import org.ezkey.admin.config.InitialGlobalAdminProperties;
import org.ezkey.admin.config.OrganizationProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.signature.ECP256KeyPair;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for bootstrapping admin MFA infrastructure at application startup.
 *
 * <p>This service implements the "Eat Your Own Dog Food" principle by automatically creating the
 * necessary infrastructure for Ezkey admin MFA authentication using Ezkey's own MFA solution.
 *
 * <p><b>Bootstrap Process:</b>
 *
 * <ol>
 *   <li>Check if System Integration (for global admin authentication) already exists
 *   <li>If not exists, create System Integration with Ed25519 key pair
 *   <li>Optionally create Global Admin Enrollment
 *   <li>Log enrollment credentials with highly visible formatting
 * </ol>
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li>Idempotent operation (can be run multiple times safely)
 *   <li>Ed25519 cryptographic keys for System Integration
 *   <li>Unique enrollment proof tokens for security
 *   <li>Highly visible credential logging for easy admin access
 * </ul>
 *
 * <p><b>Configuration:</b> Controlled by {@link AdminMfaProperties} configuration:
 *
 * <pre>
 * ezkey.admin.mfa.bootstrap.enabled=true
 * ezkey.admin.mfa.bootstrap.auto-enrollment=true
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class AdminBootstrapService {

  private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapService.class);

  private final IntegrationRepository integrationRepository;

  private final EnrollmentRepository enrollmentRepository;

  private final EzkeyAdminRepository adminRepository;

  private final TenantRepository tenantRepository;

  private final SignatureService signatureService;

  private final AdminMfaProperties mfaProperties;

  private final OrganizationProperties organizationProperties;

  private final InitialGlobalAdminProperties initialGlobalAdminProperties;

  private final AdminRecoveryService recoveryService;

  private final QrCodeAsciiRenderer qrCodeAsciiRenderer;

  private final QrCodePayloadService qrCodePayloadService;

  private final LockingTaskExecutor lockingTaskExecutor;

  private final BootstrapCredentialsFileExporter bootstrapCredentialsFileExporter;

  public AdminBootstrapService(
      IntegrationRepository integrationRepository,
      EnrollmentRepository enrollmentRepository,
      EzkeyAdminRepository adminRepository,
      TenantRepository tenantRepository,
      SignatureService signatureService,
      AdminMfaProperties mfaProperties,
      OrganizationProperties organizationProperties,
      InitialGlobalAdminProperties initialGlobalAdminProperties,
      AdminRecoveryService recoveryService,
      QrCodeAsciiRenderer qrCodeAsciiRenderer,
      QrCodePayloadService qrCodePayloadService,
      LockingTaskExecutor lockingTaskExecutor,
      BootstrapCredentialsFileExporter bootstrapCredentialsFileExporter) {
    this.integrationRepository = integrationRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.adminRepository = adminRepository;
    this.tenantRepository = tenantRepository;
    this.signatureService = signatureService;
    this.mfaProperties = mfaProperties;
    this.organizationProperties = organizationProperties;
    this.initialGlobalAdminProperties = initialGlobalAdminProperties;
    this.recoveryService = recoveryService;
    this.qrCodeAsciiRenderer = qrCodeAsciiRenderer;
    this.qrCodePayloadService = qrCodePayloadService;
    this.lockingTaskExecutor = lockingTaskExecutor;
    this.bootstrapCredentialsFileExporter = bootstrapCredentialsFileExporter;
  }

  /**
   * Bootstrap admin MFA infrastructure on application startup.
   *
   * <p>This method is automatically triggered when the application is ready. It creates System
   * Integration and optionally Global Admin Enrollment if they don't already exist.
   *
   * <p><b>HA Safety:</b> Uses distributed locking to ensure only one instance performs bootstrap in
   * HA deployments.
   */
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void bootstrapAdminMfa() {
    if (!mfaProperties.getBootstrap().isEnabled()) {
      logger.info("Admin MFA bootstrap disabled by configuration");
      return;
    }

    logger.info("🚀 Starting admin MFA bootstrap...");

    // Use distributed lock to ensure only one instance performs bootstrap in HA
    boolean executed =
        lockingTaskExecutor.executeWithLock(
            "ADMIN_STARTUP_BOOTSTRAP", Duration.ofMinutes(5), this::doBootstrapAdminMfa);

    if (!executed) {
      logger.info("Skipping admin MFA bootstrap - another instance is handling bootstrap");
    }
  }

  /**
   * Perform the actual admin MFA bootstrap (called within distributed lock).
   *
   * <p>This method creates System Integration and optionally Global Admin Enrollment if they don't
   * already exist.
   */
  @Transactional
  private void doBootstrapAdminMfa() {
    try {
      // 1. Check if System Integration already exists
      Optional<Integration> existingIntegration =
          integrationRepository.findByIsSystemIntegrationAndActiveTrue(true);

      if (existingIntegration.isPresent()) {
        logger.info(
            "✅ System Integration already exists (ID: {})", existingIntegration.get().getId());

        // Check enrollment if auto-enrollment enabled
        if (mfaProperties.getBootstrap().isAutoEnrollment()) {
          checkAndCreateGlobalAdminEnrollment(existingIntegration.get());
        }
        return;
      }

      // 2. Create System Integration
      Integration systemIntegration = createSystemIntegration();
      logger.info("✅ System Integration created (ID: {})", systemIntegration.getId());

      // 3. Optionally create Global Admin Enrollment
      if (mfaProperties.getBootstrap().isAutoEnrollment()) {
        createGlobalAdminEnrollment(systemIntegration);
      }

    } catch (Exception e) {
      logger.error("❌ Failed to bootstrap admin MFA: {}", e.getMessage(), e);
      throw new RuntimeException("Admin MFA bootstrap failed", e);
    }
  }

  /**
   * Create System Integration for global admin authentication.
   *
   * <p>System Integration is a special integration marked with isSystemIntegration=true. It uses
   * the system tenant and is created by the initial global admin.
   *
   * @return the created System Integration
   */
  private Integration createSystemIntegration() {
    logger.info("🔧 Creating System Integration...");

    // Get System Tenant (identified by is_system_tenant flag, not by name)
    Tenant systemTenant =
        tenantRepository
            .findByIsSystemTenantTrue()
            .orElseThrow(() -> new RuntimeException("System tenant not found"));

    // Get Initial Global Admin (using configured username)
    EzkeyAdmin globalAdmin =
        adminRepository
            .findByUsername(initialGlobalAdminProperties.getUsername())
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Initial global admin not found: "
                            + initialGlobalAdminProperties.getUsername()));

    // Create System Integration
    Integration systemIntegration = new Integration();
    systemIntegration.setCode("ezkey-system"); // Unique system integration code
    systemIntegration.setName("Ezkey System");
    systemIntegration.setActive(true);
    systemIntegration.setCreatedAt(OffsetDateTime.now());
    systemIntegration.setTenant(systemTenant);
    systemIntegration.setIsSystemIntegration(true);
    systemIntegration.setCreatedByAdmin(globalAdmin);

    integrationRepository.save(systemIntegration);

    logger.info("✅ System Integration created successfully (ID: {})", systemIntegration.getId());

    return systemIntegration;
  }

  /**
   * Check and create Global Admin Enrollment if it doesn't exist.
   *
   * <p>This method checks if the initial global admin already has an MFA enrollment. If not, it
   * creates one.
   *
   * @param systemIntegration the System Integration to enroll with
   */
  private void checkAndCreateGlobalAdminEnrollment(Integration systemIntegration) {
    EzkeyAdmin globalAdmin =
        adminRepository
            .findByUsername(initialGlobalAdminProperties.getUsername())
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Initial global admin not found: "
                            + initialGlobalAdminProperties.getUsername()));

    // Check if admin already has enrollment
    if (globalAdmin.getMfaEnrollment() != null) {
      Integer enrollmentId = globalAdmin.getMfaEnrollment().getEnrollmentId();
      logger.info("✅ Global admin already has enrollment (ID: {})", enrollmentId);

      // Set default challenge requirement if needed
      if (globalAdmin.getChallengeRequired() == null) {
        globalAdmin.setChallengeRequired(false);
        adminRepository.save(globalAdmin);
        logger.info("✅ Passwordless defaults configured for global admin");
      }

      // Export existing enrollment credentials if file doesn't exist yet
      // (Docker-only)
      // This ensures bootstrap-init can work even if enrollment was created in a
      // previous run
      // Reload enrollment from repository to ensure session is active and all
      // properties are loaded
      Enrollment existingEnrollment =
          enrollmentRepository
              .findById(enrollmentId)
              .orElseThrow(() -> new RuntimeException("Enrollment not found: " + enrollmentId));
      bootstrapCredentialsFileExporter.exportIfEnabled(
          existingEnrollment,
          existingEnrollment.getEnrollmentProofToken(),
          initialGlobalAdminProperties.getUsername());
      return;
    }

    createGlobalAdminEnrollment(systemIntegration);
  }

  /**
   * Create Global Admin Enrollment.
   *
   * <p>This method creates an enrollment for the initial global admin with Ed25519 key pair for
   * cryptographic operations. The enrollment credentials are logged with highly visible formatting.
   *
   * @param systemIntegration the System Integration to enroll with
   */
  private void createGlobalAdminEnrollment(Integration systemIntegration) {
    logger.info("🔧 Creating Global Admin Enrollment...");

    // Get Initial Global Admin (using configured username)
    EzkeyAdmin globalAdmin =
        adminRepository
            .findByUsername(initialGlobalAdminProperties.getUsername())
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Initial global admin not found: "
                            + initialGlobalAdminProperties.getUsername()));

    // Generate EC P-256 key pair for enrollment
    logger.info("🔐 Generating EC P-256 key pair for Global Admin Enrollment...");
    ECP256KeyPair keyPair = signatureService.generateECP256KeyPair();

    // Generate enrollment proof token
    String enrollmentProofToken = signatureService.generateProofToken();

    // Generate enrollment challenge code (6 digits)
    Integer enrollmentChallenge = signatureService.generateSecureChallenge(6);

    // Create Global Admin Enrollment with personalized name (include username for
    // uniqueness)
    String enrollmentName =
        "Global Admin MFA - "
            + globalAdmin.getFirstName()
            + " "
            + globalAdmin.getLastName()
            + " ("
            + globalAdmin.getUsername()
            + ")";
    Enrollment globalAdminEnrollment = new Enrollment();
    globalAdminEnrollment.setIntegrationId(systemIntegration.getId());
    globalAdminEnrollment.setEnrollmentName(enrollmentName);
    globalAdminEnrollment.setStatus(EnrollmentStatus.CREATED);
    globalAdminEnrollment.setActive(true);
    globalAdminEnrollment.setEnrollmentProofToken(enrollmentProofToken);
    globalAdminEnrollment.setEnrollmentChallenge(enrollmentChallenge);
    globalAdminEnrollment.setAuthAttemptChallengeRequired(false);
    globalAdminEnrollment.setIntegrationPublicKey(keyPair.base64PublicKey());
    globalAdminEnrollment.setIntegrationPrivateKey(keyPair.base64PrivateKey());
    globalAdminEnrollment.setCreatedAt(OffsetDateTime.now());

    // Store token in local variable before save (to ensure we log the exact token
    // that was set)
    String tokenToLog = enrollmentProofToken;

    // Link admin to enrollment. EzkeyAdmin.mfaEnrollment has CascadeType.MERGE, so
    // adminRepository.save() will cascade merge to the enrollment and persist it.
    globalAdmin.setMfaEnrollment(globalAdminEnrollment);

    // Configure passwordless authentication defaults
    // Passwordless is the ONLY mode - no flag needed (implicit)
    // Challenge is optional - default to false for convenience
    globalAdmin.setChallengeRequired(false);

    // Generate recovery codes for emergency access
    AdminRecoveryService.RecoveryCodesResult recoveryCodes =
        recoveryService.generateRecoveryCodes();
    globalAdmin.setRecoveryCodes(recoveryCodes.getHashedCodes().toArray(new String[0]));

    adminRepository.save(globalAdmin);

    // After cascade merge, the managed enrollment (with ID) is on the admin.
    // The original globalAdminEnrollment may not have the ID; use the one from admin.
    Enrollment persistedEnrollment = globalAdmin.getMfaEnrollment();

    logger.info(
        "✅ Global Admin Enrollment created (ID: {})", persistedEnrollment.getEnrollmentId());
    logger.info("✅ Passwordless authentication enabled for global admin");
    logger.info(
        "✅ {} recovery codes generated for global admin", recoveryCodes.getPlainCodes().size());

    // Log credentials with highly visible formatting
    // Use the token from local variable to ensure we log the exact token that was
    // set
    logGlobalAdminEnrollmentCredentials(
        persistedEnrollment, recoveryCodes.getPlainCodes(), tokenToLog);

    // Export bootstrap credentials to file (Docker-only, if enabled)
    bootstrapCredentialsFileExporter.exportIfEnabled(
        persistedEnrollment, tokenToLog, initialGlobalAdminProperties.getUsername());
  }

  /**
   * Log global admin enrollment credentials with highly visible formatting.
   *
   * <p>This method logs enrollment credentials and recovery codes using WARN level with
   * 80-character separator lines to ensure visibility in logs. Credentials are logged ONCE at
   * startup and should be saved securely by the administrator.
   *
   * @param enrollment the enrollment with credentials to log
   * @param recoveryCodes the plain recovery codes to log
   * @param enrollmentProofToken the enrollment proof token to log (from local variable, before
   *     save)
   */
  private void logGlobalAdminEnrollmentCredentials(
      Enrollment enrollment, java.util.List<String> recoveryCodes, String enrollmentProofToken) {
    String separator = "=".repeat(80);
    String username = initialGlobalAdminProperties.getUsername();
    String email = initialGlobalAdminProperties.getEmail();
    String firstName = initialGlobalAdminProperties.getFirstName();
    String lastName = initialGlobalAdminProperties.getLastName();
    String fullName = firstName + " " + lastName;

    logger.warn(""); // Blank line for visibility
    logger.warn(separator);
    logger.warn("📱 GLOBAL ADMIN PASSWORDLESS ENROLLMENT - SAVE THESE CREDENTIALS NOW!");
    logger.warn(separator);
    logger.warn("");
    logger.warn("✅ Global Admin Created: {} ({}) - {}", username, email, fullName);
    logger.warn("✅ System Integration created: Ezkey System Admin");
    logger.warn("✅ Global Admin Enrollment created: {}", enrollment.getEnrollmentName());
    logger.warn("");
    logger.warn("🔐 ENROLLMENT CREDENTIALS:");
    logger.warn("   Enrollment ID: {}", enrollment.getEnrollmentId());
    // Use token from parameter (from local variable before save) to ensure exact
    // match with hash
    logger.warn("   Enrollment Proof Token: {}", enrollmentProofToken);
    logger.warn("   Enrollment Challenge Code: {}", enrollment.getEnrollmentChallenge());
    logger.warn("");

    String enrollmentPayload =
        qrCodePayloadService.composePayload(enrollment.getEnrollmentId(), enrollmentProofToken);
    logger.warn("📷 QR CODE (Scan with Ezkey Mobile):");
    logger.warn("");
    for (String line : qrCodeAsciiRenderer.renderAscii(enrollmentPayload).split("\\R")) {
      logger.warn("   {}", line);
    }
    logger.warn("");
    logger.warn("🔑 RECOVERY CODES (SAVE SECURELY - SINGLE USE ONLY):");
    for (int i = 0; i < recoveryCodes.size(); i++) {
      logger.warn("   {}. {}", (i + 1), recoveryCodes.get(i));
    }
    logger.warn("");
    logger.warn("🔗 BIND ENROLLMENT (Required before first login):");
    logger.warn("");
    logger.warn("   Option A - CLI (Recommended):");
    logger.warn("     ezkey admin enroll bind \\");
    logger.warn("       --enrollment-id {} \\", enrollment.getEnrollmentId());
    logger.warn("       --enrollment-proof-token \"{}\"", enrollmentProofToken);
    logger.warn("");
    logger.warn("   Option B - Demo-Device:");
    logger.warn("     1. Start: cd ezkey-demo-device && mvn spring-boot:run");
    logger.warn("     2. Open: http://localhost:8082");
    logger.warn("     3. Navigate to 'Bind Enrollment' page");
    logger.warn("     4. Enter credentials:");
    logger.warn("        - Enrollment ID: {}", enrollment.getEnrollmentId());
    logger.warn("        - Enrollment Proof Token: {}", enrollmentProofToken);
    logger.warn(
        "     5. During verify, enter Challenge Code: {}", enrollment.getEnrollmentChallenge());
    logger.warn("");
    logger.warn("🔓 PASSWORDLESS LOGIN:");
    logger.warn("   After binding enrollment, login with:");
    logger.warn("   POST /api/v1/admin/auth/login");
    logger.warn("   {{ \"username\": \"{}\", \"authMode\": \"ezkey\" }}", username);
    logger.warn("");
    logger.warn("⚠️  SECURITY NOTICE:");
    logger.warn("   - NO PASSWORD - Ezkey is passwordless!");
    logger.warn("   - Recovery codes are single-use emergency access only");
    logger.warn("   - Save all credentials in a secure password manager");
    logger.warn("   - These credentials cannot be retrieved again without database access");
    logger.warn("   - Bind enrollment before attempting first login");
    logger.warn("");
    logger.warn(separator);
    logger.warn("");
  }
}
