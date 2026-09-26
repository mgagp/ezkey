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
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.security.SensitiveDataHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mode C temporary evaluator console sessions ({@link AdminTokenPurpose#EVALUATOR_TEMP}).
 *
 * <p>Same gate as evaluator self-registration. One-shot mint, absolute TTL, supersede on MFA
 * VERIFIED bind, expiry predicate with soft {@code deactivateTenant} when no other bound admin.
 */
@Service
public class EvaluatorTempSessionService {

  private static final Logger logger = LoggerFactory.getLogger(EvaluatorTempSessionService.class);

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
   * @throws AuthenticationException when gated off, invalid capability, or already minted
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

    if (tokenRepository.existsByAdminAdminIdAndTokenPurpose(
        admin.getAdminId(), AdminTokenPurpose.EVALUATOR_TEMP)) {
      throw new AuthenticationException(
          "Temporary console access was already issued and cannot be recovered");
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
    tokenRepository.save(token);

    logger.info(
        "EVALUATOR_TEMP session minted for adminId={} tenantId={} expiresAt={}",
        admin.getAdminId(),
        admin.getTenant().getTenantId(),
        expiresAt);

    return new MintResult(plainToken, expiresAt, admin);
  }

  /**
   * Revokes all active {@link AdminTokenPurpose#EVALUATOR_TEMP} tokens for the admin linked to a
   * newly VERIFIED enrollment (supersede — force fresh SESSION login).
   *
   * @param enrollmentId enrollment that just reached VERIFIED
   * @return number of tokens revoked
   */
  @Transactional
  public int supersedeOnVerifiedBind(Integer enrollmentId) {
    if (enrollmentId == null) {
      return 0;
    }
    return adminRepository
        .findByEnrollmentId(enrollmentId)
        .map(
            admin -> {
              int revoked =
                  tokenRepository.deactivateTokensForAdminByPurpose(
                      admin.getAdminId(), AdminTokenPurpose.EVALUATOR_TEMP);
              if (revoked > 0) {
                logger.info(
                    "Superseded {} EVALUATOR_TEMP token(s) after VERIFIED bind for adminId={}",
                    revoked,
                    admin.getAdminId());
              }
              return revoked;
            })
        .orElse(0);
  }

  /**
   * Processes expired active {@link AdminTokenPurpose#EVALUATOR_TEMP} tokens.
   *
   * <p>Predicate: another ACTIVE tenant admin (≠ TEMP identity) with MFA enrollment VERIFIED →
   * revoke TEMP + deactivate TEMP identity only. Otherwise soft deactivateTenant + revoke +
   * deactivate TEMP identity.
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
      Enrollment enrollment = peer.getEnrollment();
      if (enrollment != null
          && EnrollmentStatus.VERIFIED.equals(enrollment.getStatus())
          && Boolean.TRUE.equals(enrollment.getActive())) {
        return true;
      }
    }
    return false;
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
