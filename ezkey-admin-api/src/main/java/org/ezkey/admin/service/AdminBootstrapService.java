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

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.config.AdminMfaProperties;
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
import org.ezkey.signature.RsaKeyPair;
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
 *   <li>Check if Integration Zero (system integration) already exists
 *   <li>If not exists, create Integration Zero with RSA-2048 key pair
 *   <li>Optionally create Enrollment Zero for admin global
 *   <li>Log enrollment credentials with highly visible formatting
 * </ol>
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li>Idempotent operation (can be run multiple times safely)
 *   <li>RSA-2048 cryptographic keys for Integration Zero
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

  private static final int RSA_KEY_SIZE = 2048;

  private final IntegrationRepository integrationRepository;

  private final EnrollmentRepository enrollmentRepository;

  private final EzkeyAdminRepository adminRepository;

  private final TenantRepository tenantRepository;

  private final SignatureService signatureService;

  private final AdminMfaProperties mfaProperties;

  private final OrganizationProperties organizationProperties;

  private final AdminRecoveryService recoveryService;

  private final QrCodeAsciiRenderer qrCodeAsciiRenderer;

  public AdminBootstrapService(
      IntegrationRepository integrationRepository,
      EnrollmentRepository enrollmentRepository,
      EzkeyAdminRepository adminRepository,
      TenantRepository tenantRepository,
      SignatureService signatureService,
      AdminMfaProperties mfaProperties,
      OrganizationProperties organizationProperties,
      AdminRecoveryService recoveryService,
      QrCodeAsciiRenderer qrCodeAsciiRenderer) {
    this.integrationRepository = integrationRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.adminRepository = adminRepository;
    this.tenantRepository = tenantRepository;
    this.signatureService = signatureService;
    this.mfaProperties = mfaProperties;
    this.organizationProperties = organizationProperties;
    this.recoveryService = recoveryService;
    this.qrCodeAsciiRenderer = qrCodeAsciiRenderer;
  }

  /**
   * Bootstrap admin MFA infrastructure on application startup.
   *
   * <p>This method is automatically triggered when the application is ready. It creates Integration
   * Zero and optionally Enrollment Zero if they don't already exist.
   */
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void bootstrapAdminMfa() {
    if (!mfaProperties.getBootstrap().isEnabled()) {
      logger.info("Admin MFA bootstrap disabled by configuration");
      return;
    }

    logger.info("🚀 Starting admin MFA bootstrap...");

    try {
      // 1. Check if Integration Zero already exists
      Optional<Integration> existingIntegration =
          integrationRepository.findByIsSystemIntegrationAndActiveTrue(true);

      if (existingIntegration.isPresent()) {
        logger.info(
            "✅ Integration Zero already exists (ID: {})", existingIntegration.get().getId());

        // Check enrollment if auto-enrollment enabled
        if (mfaProperties.getBootstrap().isAutoEnrollment()) {
          checkAndCreateEnrollmentZero(existingIntegration.get());
        }
        return;
      }

      // 2. Create Integration Zero
      Integration integrationZero = createIntegrationZero();
      logger.info("✅ Integration Zero created (ID: {})", integrationZero.getId());

      // 3. Optionally create Enrollment Zero
      if (mfaProperties.getBootstrap().isAutoEnrollment()) {
        createEnrollmentZero(integrationZero);
      }

    } catch (Exception e) {
      logger.error("❌ Failed to bootstrap admin MFA: {}", e.getMessage(), e);
      throw new RuntimeException("Admin MFA bootstrap failed", e);
    }
  }

  /**
   * Create Integration Zero for admin authentication.
   *
   * <p>Integration Zero is a special system integration marked with isSystemIntegration=true. It
   * uses the system tenant and is created by admin zero.
   *
   * @return the created Integration Zero
   */
  private Integration createIntegrationZero() {
    logger.info("🔧 Creating Integration Zero...");

    // Get System Tenant
    Tenant systemTenant =
        tenantRepository
            .findByTenantName(organizationProperties.getName())
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "System tenant not found: " + organizationProperties.getName()));

    // Get Admin Zero
    EzkeyAdmin adminZero =
        adminRepository
            .findByUsername("admin")
            .orElseThrow(() -> new RuntimeException("Admin zero not found"));

    // Create Integration Zero
    Integration integrationZero = new Integration();
    integrationZero.setLogo(null); // No logo for system integration
    integrationZero.setActive(true);
    integrationZero.setCreatedAt(OffsetDateTime.now());
    integrationZero.setTenant(systemTenant);
    integrationZero.setIsSystemIntegration(true);
    integrationZero.setCreatedByAdmin(adminZero);

    integrationRepository.save(integrationZero);

    logger.info("✅ Integration Zero created successfully (ID: {})", integrationZero.getId());

    return integrationZero;
  }

  /**
   * Check and create Enrollment Zero if it doesn't exist.
   *
   * <p>This method checks if admin zero already has an MFA enrollment. If not, it creates one.
   *
   * @param integrationZero the Integration Zero to enroll with
   */
  private void checkAndCreateEnrollmentZero(Integration integrationZero) {
    EzkeyAdmin adminZero =
        adminRepository
            .findByUsername("admin")
            .orElseThrow(() -> new RuntimeException("Admin zero not found"));

    // Check if admin already has enrollment
    if (adminZero.getMfaEnrollment() != null) {
      logger.info(
          "✅ Admin already has enrollment (ID: {})",
          adminZero.getMfaEnrollment().getEnrollmentId());

      // Set default challenge requirement if needed
      if (adminZero.getChallengeRequired() == null) {
        adminZero.setChallengeRequired(false);
        adminRepository.save(adminZero);
        logger.info("✅ Passwordless defaults configured for admin zero");
      }
      return;
    }

    createEnrollmentZero(integrationZero);
  }

  /**
   * Create Enrollment Zero for admin global.
   *
   * <p>This method creates an enrollment for the admin user with RSA-2048 key pair for
   * cryptographic operations. The enrollment credentials are logged with highly visible formatting.
   *
   * @param integrationZero the Integration Zero to enroll with
   */
  private void createEnrollmentZero(Integration integrationZero) {
    logger.info("🔧 Creating Enrollment Zero...");

    // Get Admin Zero
    EzkeyAdmin adminZero =
        adminRepository
            .findByUsername("admin")
            .orElseThrow(() -> new RuntimeException("Admin zero not found"));

    // Generate RSA-2048 key pair for enrollment
    logger.info("🔐 Generating RSA-2048 key pair for Enrollment Zero...");
    RsaKeyPair keyPair = signatureService.generateRsaKeyPair(RSA_KEY_SIZE);

    // Generate enrollment proof token
    String enrollmentProofToken = signatureService.generateProofToken();

    // Generate enrollment challenge code (6 digits)
    Integer enrollmentChallenge = signatureService.generateSecureChallenge(6);

    // Create Enrollment Zero
    Enrollment enrollmentZero = new Enrollment();
    enrollmentZero.setIntegrationId(integrationZero.getId());
    enrollmentZero.setEnrollmentName("Admin MFA");
    enrollmentZero.setStatus(EnrollmentStatus.CREATED);
    enrollmentZero.setActive(true);
    enrollmentZero.setEnrollmentProofToken(enrollmentProofToken);
    enrollmentZero.setEnrollmentChallenge(enrollmentChallenge);
    enrollmentZero.setAuthAttemptChallengeRequired(false);
    enrollmentZero.setIntegrationPublicKey(keyPair.base64PublicKey());
    enrollmentZero.setIntegrationPrivateKey(keyPair.base64PrivateKey());
    enrollmentZero.setCreatedAt(OffsetDateTime.now());

    enrollmentRepository.save(enrollmentZero);

    // Link admin to enrollment
    adminZero.setMfaEnrollment(enrollmentZero);

    // Configure passwordless authentication defaults
    // Passwordless is the ONLY mode - no flag needed (implicit)
    // Challenge is optional - default to false for convenience
    adminZero.setChallengeRequired(false);

    // Generate recovery codes for emergency access
    AdminRecoveryService.RecoveryCodesResult recoveryCodes =
        recoveryService.generateRecoveryCodes();
    adminZero.setRecoveryCodes(recoveryCodes.getHashedCodes().toArray(new String[0]));

    adminRepository.save(adminZero);

    logger.info("✅ Enrollment Zero created (ID: {})", enrollmentZero.getEnrollmentId());
    logger.info("✅ Passwordless authentication enabled for admin zero");
    logger.info(
        "✅ {} recovery codes generated for admin zero", recoveryCodes.getPlainCodes().size());

    // Log credentials with highly visible formatting
    logAdminZeroEnrollmentCredentials(enrollmentZero, recoveryCodes.getPlainCodes());
  }

  /**
   * Log admin zero enrollment credentials with highly visible formatting.
   *
   * <p>This method logs enrollment credentials and recovery codes using WARN level with
   * 80-character separator lines to ensure visibility in logs. Credentials are logged ONCE at
   * startup and should be saved securely by the administrator.
   *
   * @param enrollment the enrollment with credentials to log
   * @param recoveryCodes the plain recovery codes to log
   */
  private void logAdminZeroEnrollmentCredentials(
      Enrollment enrollment, java.util.List<String> recoveryCodes) {
    String separator = "=".repeat(80);

    logger.warn(""); // Blank line for visibility
    logger.warn(separator);
    logger.warn("📱 ADMIN ZERO PASSWORDLESS ENROLLMENT - SAVE THESE CREDENTIALS NOW!");
    logger.warn(separator);
    logger.warn("");
    logger.warn("✅ Admin Zero Created: admin (passwordless enabled)");
    logger.warn("✅ Integration Zero created: Ezkey System Admin");
    logger.warn("✅ Enrollment Zero created: {}", enrollment.getEnrollmentName());
    logger.warn("");
    logger.warn("🔐 ENROLLMENT CREDENTIALS:");
    logger.warn("   Enrollment ID: {}", enrollment.getEnrollmentId());
    logger.warn("   Enrollment Proof Token: {}", enrollment.getEnrollmentProofToken());
    logger.warn("   Enrollment Challenge Code: {}", enrollment.getEnrollmentChallenge());
    logger.warn("");

    String enrollmentPayload =
        enrollment.getEnrollmentId() + "|" + enrollment.getEnrollmentProofToken();
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
    logger.warn("       --enrollment-proof-token \"{}\"", enrollment.getEnrollmentProofToken());
    logger.warn("");
    logger.warn("   Option B - Demo-Device:");
    logger.warn("     1. Start: cd ezkey-demo-device && mvn spring-boot:run");
    logger.warn("     2. Open: http://localhost:8082");
    logger.warn("     3. Navigate to 'Bind Enrollment' page");
    logger.warn("     4. Enter credentials:");
    logger.warn("        - Enrollment ID: {}", enrollment.getEnrollmentId());
    logger.warn("        - Enrollment Proof Token: {}", enrollment.getEnrollmentProofToken());
    logger.warn(
        "     5. During verify, enter Challenge Code: {}", enrollment.getEnrollmentChallenge());
    logger.warn("");
    logger.warn("🔓 PASSWORDLESS LOGIN:");
    logger.warn("   After binding enrollment, login with:");
    logger.warn("   POST /api/v1/admin/auth/login");
    logger.warn("   {{ \"username\": \"admin\", \"authMode\": \"ezkey\" }}");
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
