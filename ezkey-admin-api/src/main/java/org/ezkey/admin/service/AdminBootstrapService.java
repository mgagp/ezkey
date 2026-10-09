/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import org.ezkey.admin.config.BootstrapCredentialsOutputMode;
import org.ezkey.admin.config.InitialGlobalAdminProperties;
import org.ezkey.admin.util.AdminEnrollmentDisplayNames;
import org.ezkey.config.OrganizationProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.security.ApplicationReadyStartupOrder;
import org.ezkey.signature.Ed25519KeyPair;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class AdminBootstrapService {

  private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapService.class);

  /** Stable code for the single system integration (lookup uses {@code isSystemIntegration}). */
  private static final String SYSTEM_INTEGRATION_CODE = "ezkey-system";

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
   *
   * <p><b>Transaction boundary:</b> {@code @Transactional} is on this public entry point so the
   * work executed inside {@link LockingTaskExecutor} (including {@code this::doBootstrapAdminMfa})
   * runs in one Spring-managed transaction. {@code @Transactional} on the private task method would
   * not apply (proxy bypass via self-invocation).
   *
   * <p><b>Startup order:</b> {@code @Order} is {@link
   * ApplicationReadyStartupOrder#ADMIN_MFA_BOOTSTRAP} so keyset empty-table sync and initial global
   * admin identity run first. Enrollment rows write encryption-key foreign keys.
   */
  @EventListener(ApplicationReadyEvent.class)
  @Order(ApplicationReadyStartupOrder.ADMIN_MFA_BOOTSTRAP)
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
   *
   * <p>Only {@link #bootstrapAdminMfa()} is the Spring transaction boundary.
   */
  private void doBootstrapAdminMfa() {
    try {
      syncSystemTenantFromOrganization();

      // 1. Check if System Integration already exists
      Optional<Integration> existingIntegration =
          integrationRepository.findByIsSystemIntegrationTrue();

      if (existingIntegration.isPresent()) {
        Integration existing = existingIntegration.get();
        syncSystemIntegrationFromOrganization(existing);
        logger.info("✅ System Integration already exists (ID: {})", existing.getId());

        // Check enrollment if auto-enrollment enabled
        if (mfaProperties.getBootstrap().isAutoEnrollment()) {
          checkAndCreateGlobalAdminEnrollment(existing);
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

    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("❌ Failed to bootstrap admin MFA: {}", e.getMessage(), e);
      throw new RuntimeException("Admin MFA bootstrap failed", e);
    }
  }

  /**
   * Updates system tenant display name and description from {@link OrganizationProperties} when
   * they differ (idempotent on each startup).
   */
  private void syncSystemTenantFromOrganization() {
    Tenant tenant =
        tenantRepository
            .findByIsSystemTenantTrue()
            .orElseThrow(() -> new RuntimeException("System tenant not found"));
    String name = organizationProperties.getName();
    String desc = organizationProperties.getDescription();
    boolean changed = false;
    if (name != null && !name.isBlank()) {
      String stripped = name.strip();
      if (!stripped.equals(tenant.getTenantName())) {
        tenant.setTenantName(stripped);
        changed = true;
      }
    }
    if (desc != null && !desc.isBlank()) {
      String stripped = desc.strip();
      if (!stripped.equals(tenant.getTenantDescription())) {
        tenant.setTenantDescription(stripped);
        changed = true;
      }
    }
    if (changed) {
      tenantRepository.save(tenant);
      logger.info(
          "✅ System tenant display name/description synced from organization configuration");
    }
  }

  /**
   * Updates system integration display name from {@link OrganizationProperties} when it differs.
   */
  private void syncSystemIntegrationFromOrganization(Integration integration) {
    String name = organizationProperties.getName();
    boolean changed = false;
    if (name == null || name.isBlank()) {
      if (!IntegrationLifecycleStatus.ACTIVE.equals(integration.getLifecycleStatus())) {
        integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
        integrationRepository.save(integration);
      }
      return;
    }
    String stripped = name.strip();
    if (!stripped.equals(integration.getName())) {
      integration.setName(stripped);
      changed = true;
    }
    if (!IntegrationLifecycleStatus.ACTIVE.equals(integration.getLifecycleStatus())) {
      integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
      changed = true;
    }
    if (changed) {
      integrationRepository.save(integration);
      logger.info(
          "✅ System integration display name/lifecycle synced from organization configuration");
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
    systemIntegration.setCode(SYSTEM_INTEGRATION_CODE);
    systemIntegration.setName(organizationProperties.getName());
    systemIntegration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
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
    if (globalAdmin.getEnrollment() != null) {
      Integer enrollmentId = globalAdmin.getEnrollment().getEnrollmentId();
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
          initialGlobalAdminProperties.getUsername(),
          java.util.List.of());
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

    // Generate Ed25519 key pair for integration signing (enrollment)
    logger.info("🔐 Generating Ed25519 key pair for Global Admin Enrollment...");
    Ed25519KeyPair keyPair = signatureService.generateEd25519KeyPair();

    // Generate enrollment proof token
    String enrollmentProofToken = signatureService.generateProofToken();

    // Generate enrollment challenge code (6 digits)
    Integer enrollmentChallenge = signatureService.generateSecureChallenge(6);

    // Person-first authenticator label. Role is Admin UI chrome, not enrollmentName
    // (I-2026-09-15-mobile-admin-enrollment-account-label).
    String enrollmentName =
        AdminEnrollmentDisplayNames.personDisplayName(
            globalAdmin.getFirstName(), globalAdmin.getLastName(), globalAdmin.getUsername());
    Enrollment globalAdminEnrollment = new Enrollment();
    globalAdminEnrollment.setIntegrationId(systemIntegration.getId());
    globalAdminEnrollment.setEnrollmentName(enrollmentName);
    globalAdminEnrollment.setStatus(EnrollmentStatus.CREATED);
    globalAdminEnrollment.setActive(true);
    globalAdminEnrollment.setEnrollmentProofToken(enrollmentProofToken);
    globalAdminEnrollment.setEnrollmentChallenge(enrollmentChallenge);
    globalAdminEnrollment.setAuthAttemptChallengeRequired(false);
    globalAdminEnrollment.setIntegrationPublicKey(keyPair.base64UrlPublicKey());
    globalAdminEnrollment.setIntegrationPrivateKey(keyPair.base64PrivateKey());
    globalAdminEnrollment.setCreatedAt(OffsetDateTime.now());
    int enrollmentExpirationHours = mfaProperties.getBootstrap().getEnrollmentExpirationHours();
    if (enrollmentExpirationHours > 0) {
      globalAdminEnrollment.setExpiresAt(OffsetDateTime.now().plusHours(enrollmentExpirationHours));
    }

    // Store token in local variable before save (to ensure we log the exact token
    // that was set)
    String tokenToLog = enrollmentProofToken;

    // Persist enrollment first. EzkeyAdmin.enrollment has CascadeType.MERGE only (no
    // PERSIST), so a new enrollment must be saved explicitly before linking to the admin;
    // otherwise Hibernate throws "references an unsaved transient instance".
    Enrollment persistedEnrollment = enrollmentRepository.save(globalAdminEnrollment);
    globalAdmin.setEnrollment(persistedEnrollment);

    // Configure passwordless authentication defaults
    // Passwordless is the ONLY mode - no flag needed (implicit)
    // Challenge is optional - default to false for convenience
    globalAdmin.setChallengeRequired(false);

    // Generate recovery codes for emergency access
    AdminRecoveryService.RecoveryCodesResult recoveryCodes =
        recoveryService.generateRecoveryCodes();
    globalAdmin.setRecoveryCodes(recoveryCodes.getHashedCodes().toArray(new String[0]));

    adminRepository.save(globalAdmin);

    logger.info(
        "✅ Global Admin Enrollment created (ID: {})", persistedEnrollment.getEnrollmentId());
    logger.info("✅ Passwordless authentication enabled for global admin");
    logger.info(
        "✅ {} recovery codes generated for global admin", recoveryCodes.getPlainCodes().size());

    // Log credentials with highly visible formatting (token/challenge/QR by design;
    // recovery codes only as a pointer to the 0600 credentials file)
    logGlobalAdminEnrollmentCredentials(persistedEnrollment, tokenToLog);

    // Export bootstrap credentials + recovery codes to file (0600)
    bootstrapCredentialsFileExporter.exportIfEnabled(
        persistedEnrollment,
        tokenToLog,
        initialGlobalAdminProperties.getUsername(),
        recoveryCodes.getPlainCodes());
  }

  /**
   * Log global admin enrollment credentials with highly visible formatting.
   *
   * <p>Proof token, challenge, and ASCII QR are logged by design for the initial enrollment wizard.
   * Recovery codes are never logged; operators read them from {@code bootstrap-credentials.json}
   * ({@code 0600}).
   *
   * @param enrollment the enrollment with credentials to log
   * @param enrollmentProofToken the enrollment proof token to log (from local variable, before
   *     save)
   */
  private void logGlobalAdminEnrollmentCredentials(
      Enrollment enrollment, String enrollmentProofToken) {
    if (mfaProperties.getBootstrap().getCredentialsOutputMode()
        == BootstrapCredentialsOutputMode.RECOVERY_PRIMARY) {
      logRecoveryPrimaryGlobalAdminCredentials(enrollment);
      return;
    }

    String separator = "=".repeat(80);
    String username = initialGlobalAdminProperties.getUsername();
    String recoveryPointer = bootstrapCredentialsFileExporter.recoveryCodesLogPointer();

    logger.warn(""); // Blank line for visibility
    logger.warn(separator);
    logger.warn("📱 GLOBAL ADMIN PASSWORDLESS ENROLLMENT - SAVE THESE CREDENTIALS NOW!");
    logger.warn(separator);
    logger.warn("");
    logger.warn("✅ Global Admin Created: {}", username);
    logger.warn("✅ System Integration created: {} Admin", organizationProperties.getName().strip());
    logger.warn("✅ Global Admin Enrollment created: {}", enrollment.getEnrollmentName());
    if (enrollment.getExpiresAt() != null) {
      logger.warn(
          "   Enrollment expires at: {} (bind before this time)", enrollment.getExpiresAt());
    }
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
    logger.warn("🔑 RECOVERY CODES: {}", recoveryPointer);
    logger.warn("   (single-use — copy from the credentials file into a password manager)");
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
    logger.warn("   - Save recovery codes from the credentials file in a password manager");
    logger.warn("   - Bind enrollment before the invitation expires (see expires at above)");
    logger.warn("   - If the invitation expired: use a recovery code from the credentials file →");
    logger.warn("     Admin UI account recovery → reset enrollment → bind with the new material");
    logger.warn("");
    logger.warn(separator);
    logger.warn("");
  }

  /**
   * Recovery-first bootstrap: log username context and a pointer to recovery codes on disk;
   * enrollment proof token, challenge, and ASCII QR are omitted. Operators use Admin UI recovery
   * (recover → reset enrollment) to obtain bind material.
   *
   * @param enrollment enrollment record (enrollment id may be logged as a non-secret correlation
   *     id)
   */
  private void logRecoveryPrimaryGlobalAdminCredentials(Enrollment enrollment) {
    String separator = "=".repeat(80);
    String username = initialGlobalAdminProperties.getUsername();
    String recoveryPointer = bootstrapCredentialsFileExporter.recoveryCodesLogPointer();

    logger.warn("");
    logger.warn(separator);
    logger.warn("GLOBAL ADMIN BOOTSTRAP (recovery-primary) — SAVE RECOVERY CODES FROM FILE");
    logger.warn(separator);
    logger.warn("");
    logger.warn("Global Admin: {}", username);
    logger.warn("Enrollment ID (reference only): {}", enrollment.getEnrollmentId());
    logger.warn("");
    logger.warn("RECOVERY CODES: {}", recoveryPointer);
    logger.warn("  (single-use — copy from the credentials file into a password manager)");
    logger.warn("");
    logger.warn("Initial enrollment (no proof token in logs):");
    logger.warn("  1) Read a recovery code from the credentials file.");
    logger.warn("  2) Open Admin UI → Login → Use account recovery.");
    logger.warn("  3) POST /api/v1/admin/auth/recover with username + one recovery code.");
    logger.warn("  4) POST /api/v1/admin/enrollments/reset with the recovery token — new bind");
    logger.warn("     credentials are returned in the HTTP response body only (not in logs).");
    logger.warn("  5) Bind the mobile app using those credentials, then use passwordless login.");
    logger.warn("");
    logger.warn(
        "For Docker automation that needs unattended bind+verify, use"
            + " credentials-output-mode=full");
    logger.warn("so enrollment secrets are also written to bootstrap-credentials.json.");
    logger.warn("");
    logger.warn(separator);
    logger.warn("");
  }
}
