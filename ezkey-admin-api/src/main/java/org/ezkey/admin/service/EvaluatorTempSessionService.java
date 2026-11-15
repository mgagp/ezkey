/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EvaluatorTempSessionService
 * Description: Mint, supersede, and expire EVALUATOR_TEMP temporary console sessions (Mode C).
 */

package org.ezkey.admin.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.ezkey.admin.config.EvaluatorSelfRegistrationProperties;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.AdminTokenPurpose;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.security.SensitiveDataHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Temporary evaluator console sessions ({@link AdminTokenPurpose#EVALUATOR_TEMP}).
 *
 * <p>Same gate as evaluator self-registration. One-shot mint, absolute TTL, and only for a tenant
 * created by anonymous evaluator signup ({@code selfRegistered}). Supersede after MFA VERIFIED is
 * admin-api owned (SESSION mint in {@link AdminAuthService} and TEMP validation gate in {@link
 * AdminTokenValidationService}). Auth-api does not write tokens, so expiry of a TEMP whose identity
 * is already VERIFIED is delayed supersede (revoke TEMP only). Unbound expiry cleans up — TEMP
 * identity deactivate, and soft tenant deactivate when no other bound admin remains — only for that
 * same self-registered tenant. Any other tenant keeps its admin and stays active; the expired TEMP
 * token is still revoked.
 */
@Service
public class EvaluatorTempSessionService {

  private static final Logger logger = LoggerFactory.getLogger(EvaluatorTempSessionService.class);

  /** Shared message for sequential exists-check and concurrent unique-index races. */
  static final String ALREADY_ISSUED_MESSAGE =
      "Temporary console access was already issued and cannot be recovered";

  private final EvaluatorSelfRegistrationProperties properties;
  private final EnrollmentRepository enrollmentRepository;
  private final EzkeyAdminRepository adminRepository;
  private final AdminTokenRepository tokenRepository;
  private final TenantService tenantService;
  private final AuditLogService auditLogService;

  /**
   * Creates the evaluator temporary session service.
   *
   * @param properties self-registration / Mode C gate and TTL
   * @param enrollmentRepository enrollment lookup by proof hash
   * @param adminRepository admin identity lookup
   * @param tokenRepository admin token persistence
   * @param tenantService soft tenant deactivate
   * @param auditLogService mandatory TENANT_DEACTIVATED / ADMIN_DEACTIVATED audits
   */
  public EvaluatorTempSessionService(
      EvaluatorSelfRegistrationProperties properties,
      EnrollmentRepository enrollmentRepository,
      EzkeyAdminRepository adminRepository,
      AdminTokenRepository tokenRepository,
      TenantService tenantService,
      AuditLogService auditLogService) {
    this.properties = properties;
    this.enrollmentRepository = enrollmentRepository;
    this.adminRepository = adminRepository;
    this.tokenRepository = tokenRepository;
    this.tenantService = tenantService;
    this.auditLogService = auditLogService;
  }

  /**
   * Result of minting an {@link AdminTokenPurpose#EVALUATOR_TEMP} session.
   *
   * @param plainToken opaque bearer (never log)
   * @param expiresAt absolute expiry
   * @param admin authenticated administrator
   */
  public record MintResult(String plainToken, OffsetDateTime expiresAt, EzkeyAdmin admin) {}

  /**
   * Mints a one-shot temporary console session when self-registration is enabled.
   *
   * @param enrollmentId enrollment id from activation
   * @param enrollmentProofToken plaintext proof from activation
   * @return minted token material
   * @throws AuthenticationException when gated off, invalid capability, already minted, or the
   *     tenant was not created by evaluator self-registration
   */
  @Transactional
  public MintResult mint(Integer enrollmentId, String enrollmentProofToken) {
    if (!properties.isEnabled()) {
      throw new AuthenticationException("Temporary console access is not available");
    }
    if (enrollmentId == null || enrollmentProofToken == null || enrollmentProofToken.isBlank()) {
      throw new AuthenticationException("Invalid enrollment credentials");
    }

    String proofHash = SensitiveDataHasher.sha256Hex(enrollmentProofToken.trim());
    if (proofHash == null) {
      throw new AuthenticationException("Invalid enrollment credentials");
    }

    Enrollment enrollment =
        enrollmentRepository
            .findByEnrollmentIdAndEnrollmentProofTokenHash(enrollmentId, proofHash)
            .orElseThrow(() -> new AuthenticationException("Invalid enrollment credentials"));

    if (enrollment.getStatus() == EnrollmentStatus.VERIFIED) {
      throw new AuthenticationException("Device already bound — sign in with passwordless MFA");
    }
    if (enrollment.getStatus() != EnrollmentStatus.CREATED
        && enrollment.getStatus() != EnrollmentStatus.BOUND) {
      throw new AuthenticationException("Enrollment is not eligible for temporary console access");
    }

    EzkeyAdmin admin =
        adminRepository
            .findByEnrollmentId(enrollmentId)
            .orElseThrow(() -> new AuthenticationException("Invalid enrollment credentials"));

    if (admin.getAdminType() != AdminType.TENANT_ADMIN) {
      throw new AuthenticationException("Temporary console access is tenant-scoped only");
    }
    if (!Boolean.TRUE.equals(admin.getActive())
        || admin.getLifecycleStatus() != AdminLifecycleStatus.ACTIVE) {
      throw new AuthenticationException("Account is not active");
    }
    if (admin.getTenant() == null || !Boolean.TRUE.equals(admin.getTenant().getActive())) {
      throw new AuthenticationException("Tenant is inactive");
    }
    // Cleanup on expiry is limited to evaluator-signup tenants. Refuse the foothold here so an
    // operator-provisioned tenant never enters that path (#923).
    if (!isEvaluatorSignupTenant(admin.getTenant())) {
      throw new AuthenticationException(
          "Temporary console access is only available for evaluator signup tenants");
    }

    if (tokenRepository.existsByAdminAdminIdAndTokenPurpose(
        admin.getAdminId(), AdminTokenPurpose.EVALUATOR_TEMP)) {
      throw new AuthenticationException(ALREADY_ISSUED_MESSAGE);
    }

    int ttlHours = Math.max(1, properties.getTemporarySessionTtlHours());
    OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(ttlHours);
    String plainToken = "ezkey_" + UUID.randomUUID().toString().replace("-", "");
    String hash = SensitiveDataHasher.sha256Hex(plainToken);
    if (hash == null) {
      throw new IllegalStateException("Token hash could not be computed");
    }

    AdminToken token =
        new AdminToken(
            hash, admin, admin.getAdminType().name(), expiresAt, AdminTokenPurpose.EVALUATOR_TEMP);
    token.setTenant(admin.getTenant());
    token.setIntegration(admin.getIntegration());
    token.setCreatedAt(OffsetDateTime.now());
    token.setActive(true);
    try {
      // saveAndFlush: surface uq_admin_tokens_one_evaluator_temp_per_admin races before return.
      // Catch here so ValidationExceptionHandler does not map DIV to HTTP 400 — controller maps
      // AuthenticationException to 403 with no MintResult / no Set-Cookie for the loser.
      tokenRepository.saveAndFlush(token);
    } catch (DataIntegrityViolationException e) {
      logger.info(
          "Concurrent EVALUATOR_TEMP mint rejected for adminId={} (unique index)",
          admin.getAdminId());
      throw new AuthenticationException(ALREADY_ISSUED_MESSAGE);
    }

    logger.info(
        "EVALUATOR_TEMP session minted for adminId={} tenantId={} expiresAt={}",
        admin.getAdminId(),
        admin.getTenant().getTenantId(),
        expiresAt);

    return new MintResult(plainToken, expiresAt, admin);
  }

  /**
   * Revokes all active {@link AdminTokenPurpose#EVALUATOR_TEMP} tokens for the admin linked to an
   * enrollment (admin-owned helper; primary paths are SESSION mint and TEMP validation gate).
   *
   * @param enrollmentId enrollment whose admin TEMP tokens should be revoked
   * @return number of tokens revoked
   */
  @Transactional
  public int supersedeOnVerifiedBind(Integer enrollmentId) {
    if (enrollmentId == null) {
      return 0;
    }
    return adminRepository
        .findByEnrollmentId(enrollmentId)
        .map(admin -> {
          int revoked =
              tokenRepository.deactivateTokensForAdminByPurpose(
                  admin.getAdminId(), AdminTokenPurpose.EVALUATOR_TEMP);
          if (revoked > 0) {
            logger.info(
                "Superseded {} EVALUATOR_TEMP token(s) for adminId={} (enrollmentId={})",
                revoked,
                admin.getAdminId(),
                enrollmentId);
          }
          return revoked;
        })
        .orElse(0);
  }

  /**
   * Processes expired active {@link AdminTokenPurpose#EVALUATOR_TEMP} tokens.
   *
   * <p>If the TEMP identity itself already has MFA enrollment {@link EnrollmentStatus#VERIFIED},
   * this is delayed supersede (Auth API bind does not write tokens): revoke TEMP only, keep the
   * identity and tenant so the operator can mint a fresh {@code SESSION}.
   *
   * <p>Otherwise, when the TEMP identity is still unbound, identity and tenant cleanup run only if
   * the tenant is self-registered (anonymous evaluator signup). Any other tenant keeps its admin
   * and stays active; the expired TEMP token is still revoked. For a self-registered tenant:
   * another ACTIVE tenant admin (≠ TEMP identity) with MFA enrollment VERIFIED → revoke TEMP +
   * deactivate TEMP identity only. Else soft deactivateTenant + revoke + deactivate TEMP identity.
   *
   * @return number of expired TEMP tokens processed
   */
  @Transactional
  public int processExpiredTempSessions() {
    List<AdminToken> expired =
        tokenRepository.findActiveExpiredByPurposeWithRelations(
            AdminTokenPurpose.EVALUATOR_TEMP, OffsetDateTime.now());
    if (expired.isEmpty()) {
      return 0;
    }

    int processed = 0;
    for (AdminToken token : expired) {
      processOneExpired(token);
      processed++;
    }
    return processed;
  }

  private void processOneExpired(AdminToken token) {
    EzkeyAdmin tempAdmin = token.getAdmin();
    Integer adminId = tempAdmin != null ? tempAdmin.getAdminId() : null;
    Integer tenantId =
        token.getTenant() != null
            ? token.getTenant().getTenantId()
            : (tempAdmin != null && tempAdmin.getTenant() != null
                ? tempAdmin.getTenant().getTenantId()
                : null);

    token.setActive(false);
    tokenRepository.save(token);

    if (adminId == null) {
      return;
    }

    // Ensure no other TEMP tokens remain for this identity
    tokenRepository.deactivateTokensForAdminByPurpose(adminId, AdminTokenPurpose.EVALUATOR_TEMP);

    // Bind of this TEMP identity already happened (Auth API verify). Do not treat that as
    // an abandoned foothold — lock 11 is supersede, not lock 10 tenant teardown.
    if (isVerifiedBoundAdmin(tempAdmin)) {
      logger.info(
          "EVALUATOR_TEMP expired after TEMP identity bind (delayed supersede): adminId={}"
              + " tenantId={}",
          adminId,
          tenantId);
      return;
    }

    // Only evaluator-signup tenants are cleaned up. An operator-provisioned tenant (PAM, lab,
    // enterprise) keeps its admin and stays active when a temporary session expires unbound (#923).
    if (!isEvaluatorSignupTenant(resolveTenant(token, tempAdmin))) {
      logger.info(
          "EVALUATOR_TEMP expired without bind on a non-self-registered tenant; skip identity"
              + " and tenant cleanup: adminId={} tenantId={}",
          adminId,
          tenantId);
      return;
    }

    boolean otherBoundAdmin = tenantId != null && hasOtherVerifiedActiveAdmin(tenantId, adminId);

    deactivateTempIdentity(tempAdmin);

    if (!otherBoundAdmin && tenantId != null) {
      boolean deactivated = tenantService.deactivateTenantAsSystem(tenantId);
      if (deactivated) {
        auditLogService.log(
            AuditLog.builder()
                .apiName(org.ezkey.audit.domain.ApiName.ADMIN_API)
                .eventType(EventType.TENANT_DEACTIVATED)
                .eventAction("evaluator_temp_expired_tenant_soft_deactivate")
                .eventStatus(EventStatus.SUCCESS)
                .tenantId(tenantId)
                .adminId(adminId)
                .eventDetails(
                    "{\"reason\":\"EVALUATOR_TEMP expired without other VERIFIED tenant admin\","
                        + "\"systemJob\":true}")
                .build());
      }
    }

    logger.info(
        "EVALUATOR_TEMP expired: adminId={} tenantId={} otherBoundAdmin={}",
        adminId,
        tenantId,
        otherBoundAdmin);
  }

  /**
   * Whether another ACTIVE tenant-scoped admin (not {@code excludeAdminId}) has MFA enrollment
   * VERIFIED.
   *
   * @param tenantId tenant id
   * @param excludeAdminId TEMP identity being expired
   * @return true when a bound peer admin exists
   */
  boolean hasOtherVerifiedActiveAdmin(Integer tenantId, Integer excludeAdminId) {
    List<EzkeyAdmin> peers =
        adminRepository.findByTenantAndAdminTypeAndActive(tenantId, AdminType.TENANT_ADMIN, true);
    for (EzkeyAdmin peer : peers) {
      if (peer.getAdminId().equals(excludeAdminId)) {
        continue;
      }
      if (isVerifiedBoundAdmin(peer)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether this admin has an active device-bound MFA enrollment ({@link
   * EnrollmentStatus#VERIFIED}).
   *
   * @param admin administrator to inspect
   * @return true when MFA is bound and the enrollment row is active
   */
  boolean isVerifiedBoundAdmin(EzkeyAdmin admin) {
    if (admin == null) {
      return false;
    }
    Enrollment enrollment = admin.getEnrollment();
    return enrollment != null
        && EnrollmentStatus.VERIFIED.equals(enrollment.getStatus())
        && Boolean.TRUE.equals(enrollment.getActive());
  }

  /**
   * Whether this tenant was created by anonymous evaluator self-registration.
   *
   * <p>Fail-closed: null, missing, or {@code false} is not an evaluator-signup tenant, so expiry
   * must not deactivate the admin or the tenant.
   *
   * @param tenant tenant that owns the temporary session
   * @return true only when {@code selfRegistered} is exactly true
   */
  private static boolean isEvaluatorSignupTenant(Tenant tenant) {
    return tenant != null && Boolean.TRUE.equals(tenant.getSelfRegistered());
  }

  /**
   * Tenant attached to the expired token, or the TEMP admin's tenant when the token row has none.
   *
   * @param token expired temporary session
   * @param tempAdmin admin that held the session; may be null
   * @return owning tenant, or null when neither side carries one
   */
  private static Tenant resolveTenant(AdminToken token, EzkeyAdmin tempAdmin) {
    if (token.getTenant() != null) {
      return token.getTenant();
    }
    return tempAdmin != null ? tempAdmin.getTenant() : null;
  }

  private void deactivateTempIdentity(EzkeyAdmin admin) {
    if (admin == null || !Boolean.TRUE.equals(admin.getActive())) {
      return;
    }
    admin.setActive(false);
    admin.setLifecycleStatus(AdminLifecycleStatus.DEACTIVATED);
    adminRepository.save(admin);
    tokenRepository.deactivateAllTokensForAdmin(admin.getAdminId());

    Integer tenantId = admin.getTenant() != null ? admin.getTenant().getTenantId() : null;
    auditLogService.log(
        AuditLog.builder()
            .apiName(org.ezkey.audit.domain.ApiName.ADMIN_API)
            .eventType(EventType.ADMIN_DEACTIVATED)
            .eventAction("evaluator_temp_identity_deactivated")
            .eventStatus(EventStatus.SUCCESS)
            .tenantId(tenantId)
            .adminId(admin.getAdminId())
            .eventDetails(
                "{\"reason\":\"EVALUATOR_TEMP expired or superseded\",\"systemJob\":true}")
            .build());
  }
}
