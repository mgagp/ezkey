/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EvaluatorSelfRegistrationService
 * Description: Provisions empty preview tenants for anonymous EXP1 evaluators.
 */
package org.ezkey.admin.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.ezkey.admin.config.EvaluatorSelfRegistrationProperties;
import org.ezkey.admin.domain.AdminOnboardingMode;
import org.ezkey.admin.dto.response.EvaluatorOnboardingReissueResponseDto;
import org.ezkey.admin.dto.response.EvaluatorSelfRegistrationResponseDto;
import org.ezkey.admin.exception.EvaluatorOnboardingUnavailableException;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminAuthService.TokenIssueResult;
import org.ezkey.admin.service.AdminProvisioningService.ActivationCodeReissueResult;
import org.ezkey.admin.service.AdminProvisioningService.OnboardingCredentialsResult;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anonymous evaluator self-registration: empty tenant + pending Tenant Admin + activation code.
 *
 * <p>When enabled, also mints an opaque Admin UI {@code BOOTSTRAP} session (absolute TTL) so the
 * evaluator can land in the console immediately. Reuses {@link AdminProvisioningService}
 * provisioning semantics under a Global Admin system principal. Does not create integrations, API
 * keys, or enrollments at signup. Bounded public re-issue covers incomplete enrollment after
 * session death (same flag only; no permanent password).
 */
@Service
public class EvaluatorSelfRegistrationService {

  private static final Logger logger =
      LoggerFactory.getLogger(EvaluatorSelfRegistrationService.class);

  private static final Pattern EMAIL_LIKE = Pattern.compile("@");
  private static final Pattern URL_LIKE = Pattern.compile("(?i)(https?://|www\\.)");

  private static final String TENANT_PREFIX = "eval-";
  private static final String ADMIN_USERNAME_PREFIX = "eval-admin-";
  private static final String DEFAULT_FIRST_NAME = "Evaluator";
  private static final String DEFAULT_LAST_NAME = "Preview";
  private static final String DEFAULT_TENANT_DESCRIPTION =
      "EXP1 anonymous evaluator preview tenant";

  /** Product lock: bootstrap session absolute TTL hours (ignore misconfigured property). */
  private static final int BOOTSTRAP_SESSION_TTL_HOURS_LOCK = 8;

  static final String PHASE_PENDING_ACTIVATION = "PENDING_ACTIVATION";
  static final String PHASE_DEVICE_BIND = "DEVICE_BIND";

  private final EvaluatorSelfRegistrationProperties properties;
  private final EvaluatorSelfRegistrationRateLimiter rateLimiter;
  private final AdminProvisioningService provisioningService;
  private final AdminAuthService authService;
  private final EzkeyAdminRepository adminRepository;
  private final TenantRepository tenantRepository;

  public EvaluatorSelfRegistrationService(
      EvaluatorSelfRegistrationProperties properties,
      EvaluatorSelfRegistrationRateLimiter rateLimiter,
      AdminProvisioningService provisioningService,
      AdminAuthService authService,
      EzkeyAdminRepository adminRepository,
      TenantRepository tenantRepository) {
    this.properties = properties;
    this.rateLimiter = rateLimiter;
    this.provisioningService = provisioningService;
    this.authService = authService;
    this.adminRepository = adminRepository;
    this.tenantRepository = tenantRepository;
  }

  /**
   * Returns whether anonymous evaluator signup is enabled for this installation.
   *
   * @return true when the feature flag is on
   */
  public boolean isEnabled() {
    return properties.isEnabled();
  }

  /**
   * Registers an anonymous evaluator tenant and pending Tenant Admin.
   *
   * <p>When the self-registration flag is on (caller must gate), mints activation material and a
   * BOOTSTRAP Admin UI session. Bootstrap mint is coupled to the same flag only — there is no
   * separate bootstrap toggle.
   *
   * @param optionalTenantLabel optional short label from the client
   * @param clientIp client IP for rate limiting
   * @return activation material, bootstrap session fields, and navigation URLs
   */
  @Transactional
  public EvaluatorSelfRegistrationResponseDto register(
      String optionalTenantLabel, String clientIp) {
    validateTenantLabel(optionalTenantLabel);
    rateLimiter.verifyAndRecordSuccess(clientIp);

    AdminPrincipal systemPrincipal = resolveSystemGlobalAdminPrincipal();
    String slug = generateUniqueTenantSlug();
    String description =
        optionalTenantLabel == null || optionalTenantLabel.isBlank()
            ? DEFAULT_TENANT_DESCRIPTION
            : DEFAULT_TENANT_DESCRIPTION + " (" + optionalTenantLabel.trim() + ")";

    Tenant tenant =
        provisioningService.createTenant(
            slug, description, null, null, null, null, null, null, null, systemPrincipal);

    String adminSuffix = slug.substring(TENANT_PREFIX.length());
    String username = ADMIN_USERNAME_PREFIX + adminSuffix;

    AdminProvisioningService.ProvisioningResult adminResult =
        provisioningService.createTenantAdmin(
            username,
            null,
            null,
            DEFAULT_FIRST_NAME,
            DEFAULT_LAST_NAME,
            tenant.getTenantId(),
            AdminOnboardingMode.ACTIVATION_CODE,
            systemPrincipal);

    EzkeyAdmin pendingAdmin = adminResult.admin();
    TokenIssueResult bootstrap = mintBootstrapSession(pendingAdmin);

    logger.info(
        "Anonymous evaluator self-registration completed for tenant slug {} (tenantId={})",
        slug,
        tenant.getTenantId());

    return new EvaluatorSelfRegistrationResponseDto(
        adminResult.activationCode(),
        adminResult.activationCodeExpiresAt(),
        properties.getAdminUiUrl(),
        properties.getGuidedTourUrl(),
        slug,
        bootstrap.plainToken(),
        bootstrap.token().getExpiresAt(),
        pendingAdmin.getUsername());
  }

  /**
   * Re-issues onboarding material and a fresh BOOTSTRAP session for an incomplete evaluator
   * enrollment after session death (logout or absolute TTL).
   *
   * <p>Same self-registration flag only. Eligible when still pending activation (new activation
   * code) or activated with enrollment still {@code CREATED} (rotated QR proof). Not available
   * after device bind. Rate-limited separately from signup. No permanent password.
   *
   * @param username evaluator Tenant Admin username from signup
   * @param clientIp client IP for rate limiting
   * @return phase-specific onboarding material plus bootstrap session fields
   * @throws EvaluatorOnboardingUnavailableException when the username is missing or ineligible
   */
  @Transactional
  public EvaluatorOnboardingReissueResponseDto reissueOnboarding(String username, String clientIp) {
    if (username == null || username.isBlank()) {
      throw new EvaluatorOnboardingUnavailableException();
    }
    String trimmed = username.trim();
    rateLimiter.verifyAndRecordReissue(clientIp, trimmed);

    if (!trimmed.toLowerCase(Locale.ROOT).startsWith(ADMIN_USERNAME_PREFIX)) {
      throw new EvaluatorOnboardingUnavailableException();
    }

    EzkeyAdmin admin =
        adminRepository
            .findByUsernameWithEnrollment(trimmed)
            .orElseThrow(EvaluatorOnboardingUnavailableException::new);

    assertEvaluatorResumeEligibleShell(admin);

    if (admin.getLifecycleStatus() == AdminLifecycleStatus.PENDING_ACTIVATION
        && admin.getEnrollment() == null) {
      ActivationCodeReissueResult codeResult =
          provisioningService.reissueActivationCodeForEvaluatorResume(admin);
      TokenIssueResult bootstrap = mintBootstrapSession(admin);
      logger.info(
          "Evaluator onboarding re-issue (PENDING_ACTIVATION) for username {}",
          admin.getUsername());
      return new EvaluatorOnboardingReissueResponseDto(
          PHASE_PENDING_ACTIVATION,
          admin.getUsername(),
          codeResult.activationCode(),
          codeResult.activationCodeExpiresAt(),
          null,
          null,
          null,
          bootstrap.plainToken(),
          bootstrap.token().getExpiresAt(),
          properties.getAdminUiUrl(),
          properties.getGuidedTourUrl());
    }

    Enrollment enrollment = admin.getEnrollment();
    if (admin.getLifecycleStatus() == AdminLifecycleStatus.ACTIVE
        && enrollment != null
        && enrollment.getStatus() == EnrollmentStatus.CREATED) {
      OnboardingCredentialsResult creds =
          provisioningService.rotateIncompleteEnrollmentProofForEvaluatorResume(admin);
      TokenIssueResult bootstrap = mintBootstrapSession(admin);
      logger.info(
          "Evaluator onboarding re-issue (DEVICE_BIND) for username {} (enrollmentId={})",
          admin.getUsername(),
          creds.enrollmentId());
      return new EvaluatorOnboardingReissueResponseDto(
          PHASE_DEVICE_BIND,
          admin.getUsername(),
          null,
          null,
          creds.enrollmentId(),
          creds.enrollmentProofToken(),
          creds.enrollmentChallenge(),
          bootstrap.plainToken(),
          bootstrap.token().getExpiresAt(),
          properties.getAdminUiUrl(),
          properties.getGuidedTourUrl());
    }

    throw new EvaluatorOnboardingUnavailableException();
  }

  private TokenIssueResult mintBootstrapSession(EzkeyAdmin admin) {
    int ttlHours = BOOTSTRAP_SESSION_TTL_HOURS_LOCK;
    if (properties.getBootstrapSessionTtlHours() != BOOTSTRAP_SESSION_TTL_HOURS_LOCK) {
      logger.warn(
          "Ignoring ezkey.evaluator.self-registration.bootstrap-session-ttl-hours={} — product"
              + " lock requires {}",
          properties.getBootstrapSessionTtlHours(),
          BOOTSTRAP_SESSION_TTL_HOURS_LOCK);
    }
    return authService.issueBootstrapSession(admin, ttlHours);
  }

  /**
   * Shell eligibility for public resume: active eval Tenant Admin under an active {@code eval-*}
   * tenant. Lifecycle / enrollment phase checks remain in {@link #reissueOnboarding}.
   */
  private static void assertEvaluatorResumeEligibleShell(EzkeyAdmin admin) {
    if (!Boolean.TRUE.equals(admin.getActive())) {
      throw new EvaluatorOnboardingUnavailableException();
    }
    if (admin.getAdminType() != AdminType.TENANT_ADMIN) {
      throw new EvaluatorOnboardingUnavailableException();
    }
    Tenant tenant = admin.getTenant();
    if (tenant == null
        || !Boolean.TRUE.equals(tenant.getActive())
        || tenant.getTenantName() == null
        || !tenant.getTenantName().toLowerCase(Locale.ROOT).startsWith(TENANT_PREFIX)) {
      throw new EvaluatorOnboardingUnavailableException();
    }
  }

  private AdminPrincipal resolveSystemGlobalAdminPrincipal() {
    List<EzkeyAdmin> globalAdmins =
        adminRepository.findByAdminTypeAndActiveTrue(AdminType.GLOBAL_ADMIN);
    if (globalAdmins.isEmpty()) {
      throw new ResourceNotFoundException("Global Admin", "bootstrap");
    }
    EzkeyAdmin creator = globalAdmins.getFirst();
    return new AdminPrincipal(creator.getAdminId(), AdminType.GLOBAL_ADMIN, null, null);
  }

  private String generateUniqueTenantSlug() {
    for (int attempt = 0; attempt < 5; attempt++) {
      String suffix =
          UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
      String slug = TENANT_PREFIX + suffix;
      if (!tenantRepository.existsByTenantName(slug)) {
        return slug;
      }
    }
    throw new IllegalStateException("Unable to generate a unique evaluator tenant slug");
  }

  static void validateTenantLabel(String tenantLabel) {
    if (tenantLabel == null || tenantLabel.isBlank()) {
      return;
    }
    String trimmed = tenantLabel.trim();
    if (EMAIL_LIKE.matcher(trimmed).find()) {
      throw new IllegalArgumentException("Tenant label must not contain an email address");
    }
    if (URL_LIKE.matcher(trimmed).find()) {
      throw new IllegalArgumentException("Tenant label must not contain a URL");
    }
  }
}
