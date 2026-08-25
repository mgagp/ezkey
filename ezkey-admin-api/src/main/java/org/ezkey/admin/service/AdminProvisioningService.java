/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminProvisioningService
 * Description: Service for provisioning tenants and administrators with proper limits and audit.
 */

package org.ezkey.admin.service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.ezkey.admin.config.AdminSecurityProperties;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.domain.AdminOnboardingMode;
import org.ezkey.admin.dto.request.AdminUpdateRequestDto;
import org.ezkey.admin.exception.AdminLimitException;
import org.ezkey.admin.exception.AdminNotAllowedException;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.admin.exception.GlobalAdminLimitException;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.security.SensitiveDataHasher;
import org.ezkey.signature.Ed25519KeyPair;
import org.ezkey.signature.SignatureService;
import org.ezkey.util.PhoneNumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for provisioning tenants and administrators with proper limits and audit.
 *
 * <p>This service handles the creation of tenants and administrators (both global and tenant
 * admins) with enforcement of configurable limits and complete audit logging. It also handles the
 * creation of enrollments and recovery codes for passwordless authentication.
 *
 * <p><b>Provisioning Operations:</b>
 *
 * <ul>
 *   <li><b>Tenant Creation:</b> Global admins can create tenants
 *   <li><b>Peer Global Admin Creation:</b> Global admins can create other global admins (with
 *       limits)
 *   <li><b>Peer Tenant Admin Creation:</b> Global admins or tenant admins can create tenant admins
 *       for a tenant (with limits)
 * </ul>
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li>Limit enforcement (max/min admins per type)
 *   <li>Tenant scoping validation
 *   <li>Automatic enrollment creation with credentials
 *   <li>Recovery code generation
 *   <li>Complete audit trail
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class AdminProvisioningService {

  private static final Logger logger = LoggerFactory.getLogger(AdminProvisioningService.class);
  private static final long ACTIVATION_CODE_TTL_DAYS = 7;

  private final TenantRepository tenantRepository;
  private final EzkeyAdminRepository adminRepository;
  private final IntegrationRepository integrationRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final AdminRecoveryService recoveryService;
  private final SignatureService signatureService;
  private final AdminSecurityProperties securityProperties;
  private final AdminTokenRepository tokenRepository;

  public AdminProvisioningService(
      TenantRepository tenantRepository,
      EzkeyAdminRepository adminRepository,
      IntegrationRepository integrationRepository,
      EnrollmentRepository enrollmentRepository,
      AdminRecoveryService recoveryService,
      SignatureService signatureService,
      AdminSecurityProperties securityProperties,
      AdminTokenRepository tokenRepository) {
    this.tenantRepository = tenantRepository;
    this.adminRepository = adminRepository;
    this.integrationRepository = integrationRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.recoveryService = recoveryService;
    this.signatureService = signatureService;
    this.securityProperties = securityProperties;
    this.tokenRepository = tokenRepository;
  }

  /**
   * Result object containing provisioning output with onboarding credentials.
   *
   * <p>This record contains all information needed for the newly created admin to complete
   * passwordless enrollment, including enrollment credentials and recovery codes.
   *
   * @param admin The created administrator entity
   * @param enrollment The enrollment created for passwordless authentication
   * @param enrollmentProofToken Enrollment proof token (shown once - save securely)
   * @param enrollmentChallenge Enrollment challenge code (6 digits, shown once - save securely)
   * @param recoveryCodes List of recovery codes (shown once, single-use - save securely)
   * @param onboardingMode Effective onboarding mode used during provisioning
   * @param activationCode One-time activation code shown once for deferred onboarding
   * @param activationCodeExpiresAt Activation code expiration timestamp for deferred onboarding
   */
  public record ProvisioningResult(
      EzkeyAdmin admin,
      Enrollment enrollment,
      String enrollmentProofToken,
      Integer enrollmentChallenge,
      java.util.List<String> recoveryCodes,
      AdminOnboardingMode onboardingMode,
      String activationCode,
      OffsetDateTime activationCodeExpiresAt) {}

  private record ActivationCodeResult(String activationCode, OffsetDateTime expiresAt) {}

  /**
   * Creates a new tenant.
   *
   * <p>Only global administrators can create tenants. The tenant is created with the specified
   * name, description, and optional organizational identity fields.
   *
   * @param tenantName the unique name of the tenant
   * @param tenantDescription optional description of the tenant
   * @param organizationName optional legal organization name
   * @param organizationDomain optional primary domain
   * @param countryCode optional ISO 3166-1 alpha-2 country code
   * @param timezone optional IANA timezone identifier
   * @param primaryContactName optional primary contact name
   * @param primaryContactEmail optional primary contact email
   * @param creatorPrincipal the creating administrator principal
   * @return the created tenant
   * @throws IllegalArgumentException if name exists or not global admin
   */
  @Transactional
  public Tenant createTenant(
      String tenantName,
      String tenantDescription,
      String organizationName,
      String organizationDomain,
      String countryCode,
      String timezone,
      String primaryContactName,
      String primaryContactEmail,
      String primaryContactPhoneNumber,
      AdminPrincipal creatorPrincipal) {
    logger.info("Creating tenant: {} (creator: {})", tenantName, creatorPrincipal.adminId());

    // Validate creator is global admin
    if (!creatorPrincipal.isGlobalAdmin()) {
      throw new IllegalArgumentException("Only global administrators can create tenants");
    }

    // Check if tenant name already exists
    if (tenantRepository.existsByTenantName(tenantName)) {
      throw new IllegalArgumentException("Tenant name already exists: " + tenantName);
    }

    // Get creator admin
    EzkeyAdmin creator =
        adminRepository
            .findById(creatorPrincipal.adminId())
            .orElseThrow(() -> new ResourceNotFoundException("Admin", creatorPrincipal.adminId()));

    // Create tenant with identity fields
    Tenant tenant = new Tenant(tenantName, tenantDescription);
    tenant.setCreatedByAdmin(creator);
    tenant.setCreatedAt(OffsetDateTime.now());
    tenant.setActive(true);
    tenant.setOrganizationName(organizationName);
    tenant.setOrganizationDomain(organizationDomain);
    tenant.setCountryCode(countryCode);
    tenant.setTimezone(timezone);
    tenant.setPrimaryContactName(primaryContactName);
    tenant.setPrimaryContactEmail(primaryContactEmail);
    tenant.setPrimaryContactPhoneNumber(
        PhoneNumberUtils.normalizeToE164OrNull(primaryContactPhoneNumber));

    tenant = tenantRepository.save(tenant);

    logger.info("✅ Tenant created: {} (ID: {})", tenantName, tenant.getTenantId());

    return tenant;
  }

  /**
   * Creates a new global administrator (peer admin).
   *
   * <p>Only global administrators can create other global administrators. The operation enforces
   * maximum limit and creates enrollment + recovery codes for passwordless authentication.
   *
   * @param username the unique username for the new admin
   * @param email the email address (required for identifiable Global Admin identity)
   * @param firstName the first name (required for identifiable Global Admin identity)
   * @param lastName the last name (required for identifiable Global Admin identity)
   * @param creatorPrincipal the principal of the creating administrator (must be global admin)
   * @return ProvisioningResult with admin, enrollment, and onboarding credentials
   * @throws GlobalAdminLimitException if the active global administrator count is at the configured
   *     maximum
   * @throws IllegalArgumentException if username exists, validation fails, or creator is not global
   *     admin
   */
  @Transactional
  public ProvisioningResult createGlobalAdmin(
      String username,
      String email,
      String phoneNumber,
      String firstName,
      String lastName,
      AdminOnboardingMode onboardingMode,
      AdminPrincipal creatorPrincipal) {
    logger.info("Creating global admin: {} (creator: {})", username, creatorPrincipal.adminId());

    // Validate creator is global admin
    if (!creatorPrincipal.isGlobalAdmin()) {
      throw new IllegalArgumentException("Only global administrators can create global admins");
    }

    // Enforce maximum limit
    long currentGlobalAdminCount =
        adminRepository.countByAdminTypeAndActiveTrue(AdminType.GLOBAL_ADMIN);
    if (currentGlobalAdminCount >= securityProperties.getMaxGlobalAdmins()) {
      throw new GlobalAdminLimitException(securityProperties.getMaxGlobalAdmins());
    }

    // Check if username already exists
    if (adminRepository.existsByUsername(username)) {
      throw new IllegalArgumentException("Username already exists: " + username);
    }

    // Check if email already exists (if provided)
    if (email != null && !email.isBlank() && adminRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("Email already exists: " + email);
    }

    // Get creator admin
    EzkeyAdmin creator =
        adminRepository
            .findById(creatorPrincipal.adminId())
            .orElseThrow(() -> new ResourceNotFoundException("Admin", creatorPrincipal.adminId()));

    // Get system tenant (global admins belong to system tenant; identified by is_system_tenant)
    Tenant systemTenant =
        tenantRepository
            .findByIsSystemTenantTrue()
            .orElseThrow(() -> new RuntimeException("System tenant not found"));

    // Create admin
    EzkeyAdmin admin = new EzkeyAdmin(username, AdminType.GLOBAL_ADMIN);
    admin.setEmail(email);
    admin.setPhoneNumber(PhoneNumberUtils.normalizeToE164OrNull(phoneNumber));
    admin.setFirstName(firstName);
    admin.setLastName(lastName);
    admin.setTenant(systemTenant);
    admin.setCreatedByAdmin(creator);
    admin.setCreatedAt(OffsetDateTime.now());
    admin.setActive(true);
    admin.setChallengeRequired(false);

    AdminOnboardingMode effectiveOnboardingMode = normalizeOnboardingMode(onboardingMode);
    if (effectiveOnboardingMode == AdminOnboardingMode.ACTIVATION_CODE) {
      admin.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);
      admin.setRecoveryCodes(null);
    } else {
      admin.setLifecycleStatus(AdminLifecycleStatus.ACTIVE);
      admin.setRecoveryCodes(null);
    }

    admin = adminRepository.save(admin);

    ProvisioningResult result;
    if (effectiveOnboardingMode == AdminOnboardingMode.ACTIVATION_CODE) {
      ActivationCodeResult activationCodeResult = issueActivationCode(admin);
      result =
          new ProvisioningResult(
              admin,
              null,
              null,
              null,
              null,
              effectiveOnboardingMode,
              activationCodeResult.activationCode(),
              activationCodeResult.expiresAt());
    } else {
      Integration systemIntegration =
          integrationRepository
              .findByIsSystemIntegrationTrueAndLifecycleStatus(IntegrationLifecycleStatus.ACTIVE)
              .orElseThrow(() -> new RuntimeException("System integration not found"));
      result = createAdminEnrollment(admin, systemIntegration, null, effectiveOnboardingMode);
    }

    logger.info("✅ Global admin created: {} (ID: {})", username, admin.getAdminId());

    return result;
  }

  /**
   * Creates a new tenant administrator (peer admin).
   *
   * <p>Global administrators can create tenant admins for any tenant. Tenant administrators can
   * create peer tenant admins for their own tenant only. The operation enforces maximum limit and
   * creates enrollment + recovery codes for passwordless authentication.
   *
   * @param username the unique username for the new admin
   * @param email optional email address
   * @param firstName optional first name
   * @param lastName optional last name
   * @param tenantId the ID of the tenant this admin will manage
   * @param creatorPrincipal the principal of the creating administrator
   * @return ProvisioningResult with admin, enrollment, and onboarding credentials
   * @throws IllegalArgumentException if limit exceeded, username exists, tenant not found, or
   *     unauthorized
   */
  @Transactional
  public ProvisioningResult createTenantAdmin(
      String username,
      String email,
      String phoneNumber,
      String firstName,
      String lastName,
      Integer tenantId,
      AdminOnboardingMode onboardingMode,
      AdminPrincipal creatorPrincipal) {
    logger.info(
        "Creating tenant admin: {} for tenant {} (creator: {})",
        username,
        tenantId,
        creatorPrincipal.adminId());

    // Get tenant
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

    // Validate that tenant is not the system tenant
    // System tenant hosts global administrators only, not tenant administrators
    if (Boolean.TRUE.equals(tenant.getIsSystemTenant())) {
      throw new IllegalArgumentException(
          "Cannot create TenantAdmin in System Tenant. TenantAdmins must be created in application"
              + " tenants.");
    }

    // Validate authorization
    if (creatorPrincipal.isGlobalAdmin()) {
      // Global admins can create tenant admins for any tenant
      logger.debug("Global admin creating tenant admin for tenant: {}", tenantId);
    } else if (creatorPrincipal.isTenantAdmin()) {
      // Tenant admins can only create peer admins for their own tenant
      if (!creatorPrincipal.tenantId().equals(tenantId)) {
        throw new IllegalArgumentException(
            "Tenant administrators can only create peer admins for their own tenant");
      }
      logger.debug("Tenant admin creating peer admin for tenant: {}", tenantId);
    } else {
      throw new IllegalArgumentException(
          "Only global or tenant administrators can create tenant admins");
    }

    // Enforce maximum limit
    long currentTenantAdminCount =
        adminRepository.countByTenantTenantIdAndAdminTypeAndActiveTrue(
            tenantId, AdminType.TENANT_ADMIN);
    if (currentTenantAdminCount >= securityProperties.getMaxTenantAdminsPerTenant()) {
      throw new IllegalArgumentException(
          String.format(
              "Maximum tenant admins limit reached for tenant %d (%d). Cannot create more tenant"
                  + " admins.",
              tenantId, securityProperties.getMaxTenantAdminsPerTenant()));
    }

    // Check if username already exists
    if (adminRepository.existsByUsername(username)) {
      throw new IllegalArgumentException("Username already exists: " + username);
    }

    // Check if email already exists (if provided)
    if (email != null && !email.isBlank() && adminRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("Email already exists: " + email);
    }

    // Get creator admin
    EzkeyAdmin creator =
        adminRepository
            .findById(creatorPrincipal.adminId())
            .orElseThrow(() -> new ResourceNotFoundException("Admin", creatorPrincipal.adminId()));

    // Create admin
    EzkeyAdmin admin = new EzkeyAdmin(username, AdminType.TENANT_ADMIN);
    admin.setEmail(email);
    admin.setPhoneNumber(PhoneNumberUtils.normalizeToE164OrNull(phoneNumber));
    admin.setFirstName(firstName);
    admin.setLastName(lastName);
    admin.setTenant(tenant);
    admin.setCreatedByAdmin(creator);
    admin.setCreatedAt(OffsetDateTime.now());
    admin.setActive(true);
    admin.setChallengeRequired(false);

    AdminOnboardingMode effectiveOnboardingMode = normalizeOnboardingMode(onboardingMode);
    if (effectiveOnboardingMode == AdminOnboardingMode.ACTIVATION_CODE) {
      admin.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);
      admin.setRecoveryCodes(null);
    } else {
      admin.setLifecycleStatus(AdminLifecycleStatus.ACTIVE);
      admin.setRecoveryCodes(null);
    }

    admin = adminRepository.save(admin);

    ProvisioningResult result;
    if (effectiveOnboardingMode == AdminOnboardingMode.ACTIVATION_CODE) {
      ActivationCodeResult activationCodeResult = issueActivationCode(admin);
      result =
          new ProvisioningResult(
              admin,
              null,
              null,
              null,
              null,
              effectiveOnboardingMode,
              activationCodeResult.activationCode(),
              activationCodeResult.expiresAt());
    } else {
      Integration systemIntegration =
          integrationRepository
              .findByIsSystemIntegrationTrueAndLifecycleStatus(IntegrationLifecycleStatus.ACTIVE)
              .orElseThrow(() -> new RuntimeException("System integration not found"));
      result = createAdminEnrollment(admin, systemIntegration, null, effectiveOnboardingMode);
    }

    logger.info(
        "✅ Tenant admin created: {} (ID: {}) for tenant: {}",
        username,
        admin.getAdminId(),
        tenantId);

    return result;
  }

  /**
   * Creates an enrollment for an administrator with passwordless authentication credentials.
   *
   * <p>This method creates an enrollment linked to the system integration, generates proof token
   * and challenge code, and links it to the admin. The enrollment credentials are returned for
   * onboarding.
   *
   * @param admin the administrator to create enrollment for
   * @param systemIntegration the system integration to enroll with
   * @param recoveryCodes the recovery codes result (for inclusion in result)
   * @return ProvisioningResult with enrollment and credentials
   */
  private ProvisioningResult createAdminEnrollment(
      EzkeyAdmin admin,
      Integration systemIntegration,
      AdminRecoveryService.RecoveryCodesResult recoveryCodes,
      AdminOnboardingMode onboardingMode) {
    logger.debug("Creating enrollment for admin: {}", admin.getUsername());

    // Generate Ed25519 key pair for integration signing (enrollment)
    Ed25519KeyPair keyPair = signatureService.generateEd25519KeyPair();

    // Generate enrollment proof token
    String enrollmentProofToken = signatureService.generateProofToken();

    // Generate enrollment challenge code (6 digits)
    Integer enrollmentChallenge = signatureService.generateSecureChallenge(6);

    // Create enrollment name (include username for uniqueness)
    String enrollmentName =
        "%s Admin MFA - %s %s (%s)"
            .formatted(
                admin.getAdminType() == AdminType.GLOBAL_ADMIN ? "Global" : "Tenant",
                admin.getFirstName() != null ? admin.getFirstName() : "",
                admin.getLastName() != null ? admin.getLastName() : "",
                admin.getUsername())
            .trim();

    // Security validation: Check for existing VERIFIED enrollment with same name
    // This ensures idempotence and prevents conflicts when tests are re-executed
    List<Enrollment> existingVerifiedEnrollments =
        enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(
            systemIntegration.getId(), enrollmentName.trim(), EnrollmentStatus.VERIFIED);

    if (!existingVerifiedEnrollments.isEmpty()) {
      Enrollment existing = existingVerifiedEnrollments.get(0);

      // If VERIFIED enrollment is active, reject creation
      // This should not happen in normal flow since username is unique, but can occur
      // if tests are re-executed and enrollment data persists
      if (Boolean.TRUE.equals(existing.getActive())) {
        logger.warn(
            "Admin enrollment creation rejected: Active VERIFIED enrollment {} (ID: {}) already"
                + " exists for integration {} and name '{}'. Admin username: {}. This can occur if"
                + " tests are re-executed with persistent data. The existing enrollment should be"
                + " reused or reset via recovery process.",
            existing.getEnrollmentName(),
            existing.getEnrollmentId(),
            systemIntegration.getId(),
            enrollmentName,
            admin.getUsername());
        throw new IllegalStateException(
            "An active verified enrollment with the same name already exists for this integration. "
                + "Enrollment name: "
                + enrollmentName
                + ", Existing enrollment ID: "
                + existing.getEnrollmentId()
                + ". This can occur when tests are re-executed with persistent enrollment data. To"
                + " resolve, use the recovery process: POST /api/v1/admin/auth/recover with a"
                + " recovery code, then POST /api/v1/admin/enrollments/reset to reset the existing"
                + " enrollment.");
      }

      // If VERIFIED enrollment is inactive, allow creation (admin has deactivated it)
      logger.info(
          "Admin enrollment creation allowed: Inactive VERIFIED enrollment {} (ID: {}) exists. "
              + "Creating new enrollment for replacement. Admin username: {}",
          existing.getEnrollmentName(),
          existing.getEnrollmentId(),
          admin.getUsername());
    }

    // Create enrollment
    Enrollment enrollment = new Enrollment();
    enrollment.setIntegrationId(systemIntegration.getId());
    enrollment.setEnrollmentName(enrollmentName);
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setActive(true);
    enrollment.setEnrollmentProofToken(enrollmentProofToken);
    enrollment.setEnrollmentChallenge(enrollmentChallenge);
    enrollment.setAuthAttemptChallengeRequired(false);
    enrollment.setIntegrationPublicKey(keyPair.base64UrlPublicKey());
    enrollment.setIntegrationPrivateKey(keyPair.base64PrivateKey());
    enrollment.setCreatedAt(OffsetDateTime.now());

    enrollment = enrollmentRepository.save(enrollment);

    // Link admin to enrollment
    admin.setEnrollment(enrollment);
    adminRepository.save(admin);

    logger.debug(
        "✅ Enrollment created for admin: {} (ID: {})",
        admin.getUsername(),
        enrollment.getEnrollmentId());

    return new ProvisioningResult(
        admin,
        enrollment,
        enrollmentProofToken,
        enrollmentChallenge,
        recoveryCodes != null ? recoveryCodes.getPlainCodes() : null,
        onboardingMode,
        null,
        null);
  }

  private AdminOnboardingMode normalizeOnboardingMode(AdminOnboardingMode onboardingMode) {
    return onboardingMode != null ? onboardingMode : AdminOnboardingMode.IMMEDIATE;
  }

  private ActivationCodeResult issueActivationCode(EzkeyAdmin admin) {
    String activationCode =
        AdminAuditConstants.ACTIVATION_TOKEN_PREFIX + UUID.randomUUID().toString().replace("-", "");
    String tokenHash = SensitiveDataHasher.sha256Hex(activationCode);
    if (tokenHash == null) {
      throw new IllegalStateException("Activation code hash could not be computed");
    }

    OffsetDateTime expiresAt = OffsetDateTime.now().plus(ACTIVATION_CODE_TTL_DAYS, ChronoUnit.DAYS);
    AdminToken activationToken =
        new AdminToken(tokenHash, admin, admin.getAdminType().name(), expiresAt);
    activationToken.setTenant(admin.getTenant());
    activationToken.setIntegration(admin.getIntegration());
    activationToken.setCreatedAt(OffsetDateTime.now());
    activationToken.setActive(true);
    tokenRepository.save(activationToken);

    logger.info(
        "✅ Activation code issued for pending admin: {} (adminId: {}, expiresAt: {})",
        admin.getUsername(),
        admin.getAdminId(),
        expiresAt);

    return new ActivationCodeResult(activationCode, expiresAt);
  }

  /**
   * Consumes a one-time activation code and creates the first enrollment for a pending admin.
   *
   * <p>This is the deferred counterpart to immediate onboarding. It validates the one-time token,
   * generates recovery codes, creates the first enrollment, marks the admin active, and deactivates
   * the activation token.
   *
   * @param activationCode the one-time activation code
   * @return provisioning result with first enrollment credentials and recovery codes
   */
  @Transactional
  public ProvisioningResult activatePendingAdmin(String activationCode) {
    logger.info("Activating pending administrator with activation code");

    if (activationCode == null
        || activationCode.isBlank()
        || !activationCode.startsWith(AdminAuditConstants.ACTIVATION_TOKEN_PREFIX)) {
      throw new AuthenticationException("Invalid activation code");
    }

    String tokenHash = SensitiveDataHasher.sha256Hex(activationCode);
    if (tokenHash == null) {
      throw new AuthenticationException("Invalid activation code");
    }

    AdminToken activationToken =
        tokenRepository
            .findByBearerTokenHashAndActiveTrueWithRelations(tokenHash)
            .orElseThrow(() -> new AuthenticationException("Invalid activation code"));

    if (activationToken.getExpiresAt() == null
        || activationToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
      throw new AuthenticationException("Activation code has expired");
    }

    EzkeyAdmin admin = activationToken.getAdmin();
    if (admin == null) {
      throw new AuthenticationException("Invalid activation code");
    }

    if (!Boolean.TRUE.equals(admin.getActive())) {
      throw new AuthenticationException("Account is inactive");
    }

    if (admin.getTenant() != null && !Boolean.TRUE.equals(admin.getTenant().getActive())) {
      throw new AuthenticationException("Tenant is inactive");
    }

    if (admin.getLifecycleStatus() != AdminLifecycleStatus.PENDING_ACTIVATION) {
      throw new AuthenticationException("Account activation is not pending");
    }

    if (admin.getEnrollment() != null) {
      throw new IllegalStateException("Pending administrator already has an enrollment");
    }

    Integration systemIntegration =
        integrationRepository
            .findByIsSystemIntegrationTrueAndLifecycleStatus(IntegrationLifecycleStatus.ACTIVE)
            .orElseThrow(() -> new RuntimeException("System integration not found"));

    admin.setLifecycleStatus(AdminLifecycleStatus.ACTIVE);
    admin.setRecoveryCodes(null);

    ProvisioningResult result =
        createAdminEnrollment(admin, systemIntegration, null, AdminOnboardingMode.ACTIVATION_CODE);

    activationToken.setActive(false);
    activationToken.setLastUsedAt(OffsetDateTime.now());
    tokenRepository.save(activationToken);

    logger.info(
        "✅ Pending administrator activated: {} (adminId: {}, enrollmentId: {})",
        result.admin().getUsername(),
        result.admin().getAdminId(),
        result.enrollment() != null ? result.enrollment().getEnrollmentId() : null);

    return result;
  }

  /**
   * Result of re-issuing a deferred onboarding activation code for a pending administrator.
   *
   * @param admin persisted administrator row the code is bound to
   * @param activationCode plaintext one-time activation code shown once to the operator
   * @param activationCodeExpiresAt expiry instant for {@code activationCode}
   * @param invalidatedPreviousTokens true when at least one issuance row was deactivated first
   * @param deactivatedActiveTokenCount number of issuance rows deactivated before minting the new
   *     code
   */
  public record ActivationCodeReissueResult(
      EzkeyAdmin admin,
      String activationCode,
      OffsetDateTime activationCodeExpiresAt,
      boolean invalidatedPreviousTokens,
      int deactivatedActiveTokenCount) {}

  /**
   * Re-issues a one-time activation code for a pending administrator whose first enrollment does
   * not exist yet. Used when the operator lost the previously issued activation code before the
   * invitee consumed it.
   *
   * @param adminId target administrator identifier
   * @param requesterPrincipal principal of the invoking Global Administrator
   * @return new activation code payload and revocation summary
   * @throws ResourceNotFoundException when the administrator does not exist
   * @throws AccessDeniedException when the caller is not a global administrator
   * @throws IllegalStateException when the administrator is not eligible for re-issue
   */
  @Transactional
  public ActivationCodeReissueResult reissueActivationCode(
      Integer adminId, AdminPrincipal requesterPrincipal) {
    if (requesterPrincipal == null || !requesterPrincipal.isGlobalAdmin()) {
      throw new AccessDeniedException(
          "Only global administrators may re-issue activation codes for pending administrators.");
    }

    EzkeyAdmin admin =
        adminRepository
            .findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Administrator", adminId));

    validateActivationCodeReissueEligibility(admin);

    int deactivated = tokenRepository.deactivateAllTokensForAdmin(adminId);

    ActivationCodeResult newCode = issueActivationCode(admin);

    logger.info(
        "✅ Activation code re-issued for pending admin {} (adminId={},"
            + " deactivatedIssuanceTokens={})",
        admin.getUsername(),
        admin.getAdminId(),
        deactivated);

    return new ActivationCodeReissueResult(
        admin, newCode.activationCode(), newCode.expiresAt(), deactivated > 0, deactivated);
  }

  private void validateActivationCodeReissueEligibility(EzkeyAdmin admin) {
    if (!Boolean.TRUE.equals(admin.getActive())) {
      throw new IllegalStateException(
          "Cannot re-issue activation codes for an inactive administrator");
    }
    if (admin.getLifecycleStatus() != AdminLifecycleStatus.PENDING_ACTIVATION) {
      throw new IllegalStateException(
          "Activation codes can only be re-issued while the administrator is pending activation");
    }
    if (admin.getEnrollment() != null) {
      throw new IllegalStateException(
          "Cannot re-issue activation codes after the first enrollment already exists");
    }
    if (admin.getTenant() != null && !Boolean.TRUE.equals(admin.getTenant().getActive())) {
      throw new IllegalStateException(
          "Cannot re-issue activation codes while the administrator's tenant is inactive");
    }
  }

  /**
   * Partially updates an administrator profile.
   *
   * <p>Only non-null fields are applied. GlobalAdmin can update any admin. TenantAdmin can update
   * admins in their own tenant. Admins can update their own profile (firstName, lastName, email,
   * challengeRequired).
   *
   * <p><b>Authorization:</b>
   *
   * <ul>
   *   <li>GlobalAdmin: can update any admin
   *   <li>TenantAdmin: can update admins in their tenant
   *   <li>Self-update: admin can update their own profile
   * </ul>
   *
   * @param adminId the administrator ID to update
   * @param request the partial update request
   * @param requesterPrincipal the principal of the requesting administrator
   * @return the updated administrator
   * @throws ResourceNotFoundException if admin not found
   * @throws IllegalArgumentException if unauthorized or email already exists
   * @throws ObjectOptimisticLockingFailureException if version mismatch (stale)
   */
  @Transactional
  public EzkeyAdmin updateAdmin(
      Integer adminId, AdminUpdateRequestDto request, AdminPrincipal requesterPrincipal) {
    EzkeyAdmin admin =
        adminRepository
            .findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Administrator", adminId));

    // Authorization: GlobalAdmin any, TenantAdmin own tenant, or self-update
    boolean canUpdate = false;
    if (requesterPrincipal.isGlobalAdmin()) {
      canUpdate = true;
    } else if (requesterPrincipal.isTenantAdmin()) {
      Integer requesterTenantId = requesterPrincipal.tenantId();
      Integer adminTenantId = admin.getTenant() != null ? admin.getTenant().getTenantId() : null;
      canUpdate = requesterTenantId != null && requesterTenantId.equals(adminTenantId);
    }
    if (!canUpdate && requesterPrincipal.adminId().equals(adminId)) {
      canUpdate = true; // Self-update
    }
    if (!canUpdate) {
      throw new IllegalArgumentException(
          "Not authorized to update this administrator. GlobalAdmin can update any admin."
              + " TenantAdmin can update admins in their tenant. Admins can update their own"
              + " profile.");
    }

    // Optimistic lock check
    if (request.version() != null && !request.version().equals(admin.getVersion())) {
      throw new ObjectOptimisticLockingFailureException(EzkeyAdmin.class, adminId);
    }

    // Apply non-null fields
    if (request.firstName() != null) {
      admin.setFirstName(request.firstName());
    }
    if (request.lastName() != null) {
      admin.setLastName(request.lastName());
    }
    if (request.email() != null) {
      if (adminRepository.existsByEmailAndAdminIdNot(request.email(), adminId)) {
        throw new IllegalArgumentException("Email already exists: " + request.email());
      }
      admin.setEmail(request.email());
    }
    if (request.phoneNumber() != null) {
      admin.setPhoneNumber(PhoneNumberUtils.normalizeToE164OrNull(request.phoneNumber()));
    }
    if (request.challengeRequired() != null) {
      admin.setChallengeRequired(request.challengeRequired());
    }

    admin = adminRepository.save(admin);
    logger.info("Admin {} profile updated by admin {}", adminId, requesterPrincipal.adminId());
    loadAdminResponseAssociations(admin);
    return admin;
  }

  /**
   * Lists administrators with tenant-based filtering.
   *
   * <p>Returns a paginated list of administrators filtered by tenant. GlobalAdmin sees all
   * administrators across all tenants. TenantAdmin sees only administrators from their tenant.
   *
   * <p><b>Tenant Filtering:</b>
   *
   * <ul>
   *   <li><b>GlobalAdmin (tenantId = null):</b> Returns all administrators
   *   <li><b>TenantAdmin (tenantId != null):</b> Returns only administrators from their tenant
   * </ul>
   *
   * @param tenantId the tenant ID to filter by (null for GlobalAdmin = all tenants)
   * @param pageable pagination and sorting parameters
   * @return page of administrators matching the tenant filter
   */
  @Transactional(readOnly = true)
  public Page<EzkeyAdmin> listAdmins(Integer tenantId, Pageable pageable) {
    logger.debug("Listing admins - tenantId: {}, pageable: {}", tenantId, pageable);

    Page<EzkeyAdmin> page;
    if (tenantId == null) {
      // GlobalAdmin: return all admins
      logger.debug("GlobalAdmin listing all admins");
      page = adminRepository.findAllForAdminProvisioningList(pageable);
    } else {
      // TenantAdmin: return only admins from their tenant
      logger.debug("TenantAdmin listing admins for tenant: {}", tenantId);
      page = adminRepository.findByTenantTenantIdForAdminProvisioningList(tenantId, pageable);
    }
    return page;
  }

  /**
   * Retrieves a single administrator by ID with tenant-based authorization.
   *
   * <p>GlobalAdmin can retrieve any admin. TenantAdmin can only retrieve admins from their own
   * tenant.
   *
   * @param adminId the administrator ID to retrieve
   * @param requesterPrincipal the principal of the requesting administrator
   * @return the administrator entity
   * @throws ResourceNotFoundException if admin not found
   * @throws IllegalArgumentException if requester does not have permission to access this admin
   */
  @Transactional(readOnly = true)
  public EzkeyAdmin getAdminById(Integer adminId, AdminPrincipal requesterPrincipal) {
    EzkeyAdmin admin =
        adminRepository
            .findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Administrator", adminId));

    if (requesterPrincipal.isGlobalAdmin()) {
      loadAdminResponseAssociations(admin);
      return admin;
    }
    if (requesterPrincipal.isTenantAdmin()) {
      Integer requesterTenantId = requesterPrincipal.tenantId();
      Integer adminTenantId = admin.getTenant() != null ? admin.getTenant().getTenantId() : null;
      if (requesterTenantId == null || !requesterTenantId.equals(adminTenantId)) {
        throw new IllegalArgumentException(
            "Tenant administrators can only access admins in their tenant");
      }
    }
    loadAdminResponseAssociations(admin);
    return admin;
  }

  /**
   * Loads lazy associations needed to build {@link org.ezkey.admin.dto.response.AdminResponseDto}
   * outside this transaction (avoids lazy init issues when open-in-view is disabled).
   *
   * @param admin administrator entity
   */
  private void loadAdminResponseAssociations(EzkeyAdmin admin) {
    admin.getTenant();
    admin.getLastLoginAt();
    if (admin.getEnrollment() != null) {
      admin.getEnrollment().getEnrollmentId();
      admin.getEnrollment().getEnrollmentName();
    }
  }

  /**
   * Retrieves onboarding credentials for an administrator.
   *
   * <p>This method retrieves sensitive onboarding credentials (enrollment proof token, challenge
   * code, recovery codes) for an administrator. Access is restricted to authorized administrators
   * who have permission to view these credentials.
   *
   * <p><b>Authorization:</b>
   *
   * <ul>
   *   <li>GlobalAdmin can retrieve onboarding credentials for any admin
   *   <li>TenantAdmin can only retrieve onboarding credentials for admins in their tenant
   * </ul>
   *
   * @param adminId the administrator ID
   * @param requesterPrincipal the principal of the requesting administrator
   * @return OnboardingCredentialsResult with enrollment credentials and recovery codes
   * @throws ResourceNotFoundException if admin not found
   * @throws IllegalArgumentException if requester doesn't have permission to access these
   *     credentials
   */
  @Transactional(readOnly = true)
  public OnboardingCredentialsResult getAdminOnboarding(
      Integer adminId, AdminPrincipal requesterPrincipal) {
    logger.info(
        "Retrieving onboarding credentials for admin: {} (requester: {})",
        adminId,
        requesterPrincipal.adminId());

    // Get admin
    EzkeyAdmin admin =
        adminRepository
            .findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin", adminId));

    // Validate authorization
    if (requesterPrincipal.isGlobalAdmin()) {
      // GlobalAdmin can access any admin's credentials
      logger.debug("GlobalAdmin retrieving onboarding credentials for admin: {}", adminId);
    } else if (requesterPrincipal.isTenantAdmin()) {
      // TenantAdmin can only access credentials for admins in their tenant
      Integer requesterTenantId = requesterPrincipal.tenantId();
      Integer adminTenantId = admin.getTenant() != null ? admin.getTenant().getTenantId() : null;

      if (!requesterTenantId.equals(adminTenantId)) {
        throw new IllegalArgumentException(
            "Tenant administrators can only access onboarding credentials for admins in their"
                + " tenant");
      }
      logger.debug(
          "TenantAdmin retrieving onboarding credentials for admin: {} in tenant: {}",
          adminId,
          requesterTenantId);
    } else {
      throw new IllegalArgumentException("Only administrators can retrieve onboarding credentials");
    }

    // Get enrollment
    if (admin.getEnrollment() == null) {
      throw new IllegalArgumentException("Admin does not have an enrollment");
    }

    Enrollment enrollment = admin.getEnrollment();

    // Reload enrollment from repository to ensure session is active and all
    // properties are loaded
    Enrollment loadedEnrollment =
        enrollmentRepository
            .findById(enrollment.getEnrollmentId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Enrollment", enrollment.getEnrollmentId()));

    // Recovery codes are stored as BCrypt hashes and cannot be retrieved in plain
    // text
    // They are only available during initial provisioning (stored in
    // ProvisioningResult)
    // Once the enrollment is bound, recovery codes cannot be retrieved for security
    // reasons
    // Return null for recovery codes - they must be saved during initial
    // provisioning
    logger.info(
        "✅ Onboarding credentials retrieved for admin: {} (enrollmentId: {})",
        adminId,
        loadedEnrollment.getEnrollmentId());

    return new OnboardingCredentialsResult(
        loadedEnrollment.getEnrollmentId(),
        loadedEnrollment.getEnrollmentProofToken(),
        loadedEnrollment.getEnrollmentChallenge(),
        null); // Recovery codes cannot be retrieved after initial provisioning (BCrypt hashed)
  }

  /**
   * Deactivates an administrator account.
   *
   * <p>This method deactivates an administrator based on the following rules:
   *
   * <ul>
   *   <li>A global admin can deactivate another global admin (but not themselves)
   *   <li>A global admin can deactivate any tenant admin
   *   <li>Cannot deactivate if it would violate minimum admin limits
   *   <li>All active tokens for the admin are revoked upon deactivation
   * </ul>
   *
   * <p><b>NOTE:</b> Unlike tenant admin minimum limits, global admin deactivation is allowed even
   * if the tenant admin is the only admin for their tenant. This is intentional to avoid preventing
   * global admins from deactivating tenant admins.
   *
   * @param adminId the ID of the administrator to deactivate
   * @param principal the admin principal performing the deactivation
   * @throws ResourceNotFoundException if the administrator to deactivate is not found (404)
   * @throws AdminNotAllowedException if the admin tries to deactivate themselves (400, RFC 9457)
   * @throws AdminLimitException if deactivation would violate minimum limits (400, RFC 9457)
   */
  @Transactional
  public boolean deactivateAdmin(Integer adminId, AdminPrincipal principal) {
    // Load the admin to deactivate
    EzkeyAdmin adminToDeactivate =
        adminRepository
            .findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Administrator", adminId));

    // Rule 1: Cannot deactivate yourself
    if (principal.adminId().equals(adminId)) {
      logger.warn("Admin {} attempted to deactivate themselves", adminToDeactivate.getUsername());
      throw new AdminNotAllowedException("Cannot deactivate your own account");
    }

    // Rule 2: Check if admin is already inactive
    if (!adminToDeactivate.getActive()) {
      logger.info("Admin {} is already inactive", adminId);
      return false; // Idempotent - nothing to do
    }

    // Rule 3: Enforce minimum limits for global admins
    if (adminToDeactivate.getAdminType() == AdminType.GLOBAL_ADMIN) {
      long activeGlobalAdmins =
          adminRepository.countByAdminTypeAndActiveTrue(AdminType.GLOBAL_ADMIN);
      int minGlobalAdmins = securityProperties.getMinGlobalAdmins();

      if (activeGlobalAdmins <= minGlobalAdmins) {
        logger.warn(
            "Cannot deactivate global admin {} - would violate minimum limit "
                + "(current: {}, min: {})",
            adminId,
            activeGlobalAdmins,
            minGlobalAdmins);
        throw new AdminLimitException(
            "Cannot deactivate global admin - would violate minimum limit of " + minGlobalAdmins);
      }
    }

    // Rule 4: For tenant admins, minimum limits are NOT enforced per issue
    // requirements
    // Global admins can deactivate tenant admins even if they're the only admin for
    // their tenant

    // Deactivate the admin
    adminToDeactivate.setActive(false);
    adminToDeactivate.setLifecycleStatus(AdminLifecycleStatus.DEACTIVATED);
    adminRepository.save(adminToDeactivate);

    // Revoke all active tokens for this admin
    int tokensRevoked = tokenRepository.deactivateAllTokensForAdmin(adminId);

    logger.info(
        "✅ Admin {} deactivated by admin {} ({} tokens revoked)",
        adminToDeactivate.getUsername(),
        principal.adminId(),
        tokensRevoked);
    return true;
  }

  /**
   * Activates (reactivates) an administrator account.
   *
   * <p>Only global administrators can activate. The operation sets {@code active = true}. It is
   * idempotent if the admin is already active. No tokens are re-created; the admin must log in
   * again to obtain a new bearer token.
   *
   * @param adminId the ID of the administrator to activate
   * @param principal the admin principal performing the activation (must be GlobalAdmin)
   * @throws ResourceNotFoundException if the administrator is not found (404)
   */
  @Transactional
  public boolean activateAdmin(Integer adminId, AdminPrincipal principal) {
    EzkeyAdmin admin =
        adminRepository
            .findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Administrator", adminId));

    if (admin.getActive()) {
      logger.info("Admin {} is already active", adminId);
      return false; // Idempotent
    }

    admin.setActive(true);
    admin.setLifecycleStatus(AdminLifecycleStatus.ACTIVE);
    adminRepository.save(admin);

    logger.info("✅ Admin {} activated by admin {}", admin.getUsername(), principal.adminId());
    return true;
  }

  /**
   * Result containing onboarding credentials for an administrator.
   *
   * @param enrollmentId Enrollment ID
   * @param enrollmentProofToken Enrollment proof token
   * @param enrollmentChallenge Enrollment challenge code
   * @param recoveryCodes Recovery codes (null if not available)
   */
  public record OnboardingCredentialsResult(
      Integer enrollmentId,
      String enrollmentProofToken,
      Integer enrollmentChallenge,
      java.util.List<String> recoveryCodes) {}

  /**
   * Result containing a newly generated set of recovery codes for an administrator.
   *
   * @param admin target administrator
   * @param recoveryCodes new plain-text recovery codes shown once
   * @param previousCodesCount count of codes that existed before regeneration
   */
  public record RecoveryCodesRegenerationResult(
      EzkeyAdmin admin, java.util.List<String> recoveryCodes, int previousCodesCount) {}

  @Transactional
  public RecoveryCodesRegenerationResult issueInitialRecoveryCodes(
      Integer adminId, AdminPrincipal requesterPrincipal) {
    EzkeyAdmin admin = loadAdminAuthorizedForRecoveryCodeManagement(adminId, requesterPrincipal);
    validateRecoveryCodeManagementEligibility(admin);

    if (admin.getLastLoginAt() == null) {
      throw new IllegalStateException(
          "Cannot issue initial recovery codes before administrator completes first authenticated"
              + " sign-in");
    }
    if (admin.getRecoveryCodes() != null && admin.getRecoveryCodes().length > 0) {
      throw new IllegalStateException(
          "Initial recovery codes already exist for this administrator; use regeneration instead");
    }

    AdminRecoveryService.RecoveryCodesResult recoveryCodes =
        recoveryService.generateRecoveryCodes();
    admin.setRecoveryCodes(recoveryCodes.getHashedCodes().toArray(new String[0]));
    adminRepository.save(admin);

    logger.info(
        "✅ Initial recovery codes issued for admin {} by admin {} (count={})",
        admin.getUsername(),
        requesterPrincipal.adminId(),
        recoveryCodes.getPlainCodes().size());

    return new RecoveryCodesRegenerationResult(admin, recoveryCodes.getPlainCodes(), 0);
  }

  /**
   * Regenerates recovery codes for an administrator within the caller's authorization scope.
   *
   * <p>GlobalAdmin can regenerate codes for any administrator. TenantAdmin can regenerate codes for
   * administrators in their tenant. The target administrator must be active. Regeneration replaces
   * the full previous set, invalidating any remaining unused codes.
   *
   * @param adminId the administrator whose recovery codes should be regenerated
   * @param requesterPrincipal the principal performing the operation
   * @return result containing the target admin, new plain-text codes, and previous code count
   * @throws ResourceNotFoundException if the target administrator does not exist
   * @throws AccessDeniedException if the caller is outside the allowed scope
   * @throws IllegalStateException if the target administrator is inactive
   */
  @Transactional
  public RecoveryCodesRegenerationResult regenerateRecoveryCodes(
      Integer adminId, AdminPrincipal requesterPrincipal) {
    EzkeyAdmin admin = loadAdminAuthorizedForRecoveryCodeManagement(adminId, requesterPrincipal);
    validateRecoveryCodeManagementEligibility(admin);

    int previousCodesCount = admin.getRecoveryCodes() != null ? admin.getRecoveryCodes().length : 0;
    if (previousCodesCount == 0) {
      throw new IllegalStateException(
          "No recovery codes exist yet for this administrator; use initial issuance instead");
    }
    AdminRecoveryService.RecoveryCodesResult recoveryCodes =
        recoveryService.rotateRecoveryCodes(admin);

    logger.info(
        "✅ Recovery codes regenerated for admin {} by admin {} (previous={}, new={})",
        admin.getUsername(),
        requesterPrincipal.adminId(),
        previousCodesCount,
        recoveryCodes.getPlainCodes().size());

    return new RecoveryCodesRegenerationResult(
        admin, recoveryCodes.getPlainCodes(), previousCodesCount);
  }

  private EzkeyAdmin loadAdminAuthorizedForRecoveryCodeManagement(
      Integer adminId, AdminPrincipal requesterPrincipal) {
    EzkeyAdmin admin =
        adminRepository
            .findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Administrator", adminId));

    if (requesterPrincipal.isGlobalAdmin()) {
      logger.debug("GlobalAdmin managing recovery codes for admin {}", adminId);
      return admin;
    }
    if (requesterPrincipal.isTenantAdmin()) {
      Integer requesterTenantId = requesterPrincipal.tenantId();
      Integer adminTenantId = admin.getTenant() != null ? admin.getTenant().getTenantId() : null;
      if (requesterTenantId == null || !requesterTenantId.equals(adminTenantId)) {
        throw new AccessDeniedException(
            "Tenant administrators can only manage recovery codes for admins in their tenant");
      }
      return admin;
    }
    throw new AccessDeniedException("Only administrators can manage recovery codes");
  }

  private void validateRecoveryCodeManagementEligibility(EzkeyAdmin admin) {
    if (!Boolean.TRUE.equals(admin.getActive())) {
      throw new IllegalStateException("Cannot manage recovery codes for an inactive administrator");
    }
    if (admin.getLifecycleStatus() != AdminLifecycleStatus.ACTIVE) {
      throw new IllegalStateException(
          "Cannot manage recovery codes before administrator activation is complete");
    }
  }

  /**
   * Extracts AdminPrincipal from authentication context.
   *
   * @param auth the authentication context
   * @return AdminPrincipal if present, null otherwise
   */
  public static AdminPrincipal extractAdminPrincipal(Authentication auth) {
    if (auth == null || auth.getPrincipal() == null) {
      return null;
    }

    Object principal = auth.getPrincipal();
    if (principal instanceof AdminPrincipal adminPrincipal) {
      return adminPrincipal;
    }

    return null;
  }
}
