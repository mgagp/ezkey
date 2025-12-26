/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminProvisioningService
 * Description: Service for provisioning tenants and administrators with proper limits and audit.
 */

package org.ezkey.admin.service;

import java.time.OffsetDateTime;
import org.ezkey.admin.config.AdminSecurityProperties;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.signature.ECP256KeyPair;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class AdminProvisioningService {

  private static final Logger logger = LoggerFactory.getLogger(AdminProvisioningService.class);

  private final TenantRepository tenantRepository;
  private final EzkeyAdminRepository adminRepository;
  private final IntegrationRepository integrationRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final AdminRecoveryService recoveryService;
  private final SignatureService signatureService;
  private final AdminSecurityProperties securityProperties;

  public AdminProvisioningService(
      TenantRepository tenantRepository,
      EzkeyAdminRepository adminRepository,
      IntegrationRepository integrationRepository,
      EnrollmentRepository enrollmentRepository,
      AdminRecoveryService recoveryService,
      SignatureService signatureService,
      AdminSecurityProperties securityProperties) {
    this.tenantRepository = tenantRepository;
    this.adminRepository = adminRepository;
    this.integrationRepository = integrationRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.recoveryService = recoveryService;
    this.signatureService = signatureService;
    this.securityProperties = securityProperties;
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
   */
  public record ProvisioningResult(
      EzkeyAdmin admin,
      Enrollment enrollment,
      String enrollmentProofToken,
      Integer enrollmentChallenge,
      java.util.List<String> recoveryCodes) {}

  /**
   * Creates a new tenant.
   *
   * <p>Only global administrators can create tenants. The tenant is created with the specified name
   * and description, and linked to the creating administrator for audit purposes.
   *
   * @param tenantName the unique name of the tenant
   * @param tenantDescription optional description of the tenant
   * @param creatorPrincipal the principal of the creating administrator (must be global admin)
   * @return the created tenant
   * @throws IllegalArgumentException if tenant name already exists or creator is not global admin
   */
  @Transactional
  public Tenant createTenant(
      String tenantName, String tenantDescription, AdminPrincipal creatorPrincipal) {
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

    // Create tenant
    Tenant tenant = new Tenant(tenantName, tenantDescription);
    tenant.setCreatedByAdmin(creator);
    tenant.setCreatedAt(OffsetDateTime.now());
    tenant.setActive(true);

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
   * @param email the email address (required for SOC 2 compliance)
   * @param firstName the first name (required for SOC 2 compliance)
   * @param lastName the last name (required for SOC 2 compliance)
   * @param creatorPrincipal the principal of the creating administrator (must be global admin)
   * @return ProvisioningResult with admin, enrollment, and onboarding credentials
   * @throws IllegalArgumentException if limit exceeded, username exists, or creator is not global
   *     admin
   */
  @Transactional
  public ProvisioningResult createGlobalAdmin(
      String username,
      String email,
      String firstName,
      String lastName,
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
      throw new IllegalArgumentException(
          String.format(
              "Maximum global admins limit reached (%d). Cannot create more global admins.",
              securityProperties.getMaxGlobalAdmins()));
    }

    // Check if username already exists
    if (adminRepository.existsByUsername(username)) {
      throw new IllegalArgumentException("Username already exists: " + username);
    }

    // Get creator admin
    EzkeyAdmin creator =
        adminRepository
            .findById(creatorPrincipal.adminId())
            .orElseThrow(() -> new ResourceNotFoundException("Admin", creatorPrincipal.adminId()));

    // Get system tenant (global admins belong to system tenant)
    Tenant systemTenant =
        tenantRepository
            .findByTenantName("Ezkey System")
            .orElseThrow(() -> new RuntimeException("System tenant not found"));

    // Get system integration (for admin enrollment)
    Integration systemIntegration =
        integrationRepository
            .findByIsSystemIntegrationAndActiveTrue(true)
            .orElseThrow(() -> new RuntimeException("System integration not found"));

    // Create admin
    EzkeyAdmin admin = new EzkeyAdmin(username, AdminType.GLOBAL_ADMIN);
    admin.setEmail(email);
    admin.setFirstName(firstName);
    admin.setLastName(lastName);
    admin.setTenant(systemTenant);
    admin.setCreatedByAdmin(creator);
    admin.setCreatedAt(OffsetDateTime.now());
    admin.setActive(true);
    admin.setChallengeRequired(false);

    // Generate recovery codes
    AdminRecoveryService.RecoveryCodesResult recoveryCodes =
        recoveryService.generateRecoveryCodes();
    admin.setRecoveryCodes(recoveryCodes.getHashedCodes().toArray(new String[0]));

    admin = adminRepository.save(admin);

    // Create enrollment
    ProvisioningResult result = createAdminEnrollment(admin, systemIntegration, recoveryCodes);

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
      String firstName,
      String lastName,
      Integer tenantId,
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

    // Get creator admin
    EzkeyAdmin creator =
        adminRepository
            .findById(creatorPrincipal.adminId())
            .orElseThrow(() -> new ResourceNotFoundException("Admin", creatorPrincipal.adminId()));

    // Get system integration (for admin enrollment)
    Integration systemIntegration =
        integrationRepository
            .findByIsSystemIntegrationAndActiveTrue(true)
            .orElseThrow(() -> new RuntimeException("System integration not found"));

    // Create admin
    EzkeyAdmin admin = new EzkeyAdmin(username, AdminType.TENANT_ADMIN);
    admin.setEmail(email);
    admin.setFirstName(firstName);
    admin.setLastName(lastName);
    admin.setTenant(tenant);
    admin.setCreatedByAdmin(creator);
    admin.setCreatedAt(OffsetDateTime.now());
    admin.setActive(true);
    admin.setChallengeRequired(false);

    // Generate recovery codes
    AdminRecoveryService.RecoveryCodesResult recoveryCodes =
        recoveryService.generateRecoveryCodes();
    admin.setRecoveryCodes(recoveryCodes.getHashedCodes().toArray(new String[0]));

    admin = adminRepository.save(admin);

    // Create enrollment
    ProvisioningResult result = createAdminEnrollment(admin, systemIntegration, recoveryCodes);

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
      AdminRecoveryService.RecoveryCodesResult recoveryCodes) {
    logger.debug("Creating enrollment for admin: {}", admin.getUsername());

    // Generate EC P-256 key pair for enrollment
    ECP256KeyPair keyPair = signatureService.generateECP256KeyPair();

    // Generate enrollment proof token
    String enrollmentProofToken = signatureService.generateProofToken();

    // Generate enrollment challenge code (6 digits)
    Integer enrollmentChallenge = signatureService.generateSecureChallenge(6);

    // Create enrollment name
    String enrollmentName =
        String.format(
                "%s Admin MFA - %s %s",
                admin.getAdminType() == AdminType.GLOBAL_ADMIN ? "Global" : "Tenant",
                admin.getFirstName() != null ? admin.getFirstName() : "",
                admin.getLastName() != null ? admin.getLastName() : "")
            .trim();

    // Create enrollment
    Enrollment enrollment = new Enrollment();
    enrollment.setIntegrationId(systemIntegration.getId());
    enrollment.setEnrollmentName(enrollmentName);
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setActive(true);
    enrollment.setEnrollmentProofToken(enrollmentProofToken);
    enrollment.setEnrollmentChallenge(enrollmentChallenge);
    enrollment.setAuthAttemptChallengeRequired(false);
    enrollment.setIntegrationPublicKey(keyPair.base64PublicKey());
    enrollment.setIntegrationPrivateKey(keyPair.base64PrivateKey());
    enrollment.setCreatedAt(OffsetDateTime.now());

    enrollment = enrollmentRepository.save(enrollment);

    // Link admin to enrollment
    admin.setMfaEnrollment(enrollment);
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
        recoveryCodes.getPlainCodes());
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
  public Page<EzkeyAdmin> listAdmins(Integer tenantId, Pageable pageable) {
    logger.debug("Listing admins - tenantId: {}, pageable: {}", tenantId, pageable);

    if (tenantId == null) {
      // GlobalAdmin: return all admins
      logger.debug("GlobalAdmin listing all admins");
      return adminRepository.findAll(pageable);
    } else {
      // TenantAdmin: return only admins from their tenant
      logger.debug("TenantAdmin listing admins for tenant: {}", tenantId);
      return adminRepository.findByTenantTenantId(tenantId, pageable);
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
