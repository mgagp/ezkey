/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.util;

import jakarta.servlet.http.HttpServletRequest;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService.OnboardingCredentialsResult;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.audit.util.ClientContext;

/**
 * Audit row for the reveal of an administrator's bind material (#1313, rule 5c).
 *
 * <p>Rule: audit when a secret that is enough to become someone is shown to someone else. Admin
 * sign-in is passwordless, so an administrator's enrollment is the whole identity until a device
 * binds. The row answers one question: who saw this material before an unexpected bind? It carries
 * the caller, the target administrator, the enrollment, the time and the client IP, never the
 * token, the challenge or the QR. Ordinary enrollments are not audited on reveal.
 *
 * <p>Audit writes are fail-open: a failed write is logged by the audit service and the reveal goes
 * on.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public final class AdminBindMaterialRevealAudit {

  private static final String CHANNEL_JSON = "json";
  private static final String CHANNEL_QR = "qr";

  private AdminBindMaterialRevealAudit() {}

  /**
   * Writes the row for {@code GET /admins/{id}/onboarding} when it returned a token or a challenge.
   *
   * @param auditLogService the audit service
   * @param httpRequest the request, for the client context (IP, user agent)
   * @param principal the operator who sees the material
   * @param targetAdminId the administrator whose material is shown
   * @param result the onboarding result that was returned
   */
  public static void json(
      AuditLogService auditLogService,
      HttpServletRequest httpRequest,
      AdminPrincipal principal,
      Integer targetAdminId,
      OnboardingCredentialsResult result) {
    log(auditLogService, httpRequest, principal, targetAdminId, result, CHANNEL_JSON);
  }

  /**
   * Writes the row for {@code GET /admins/{id}/onboarding/qrcode} when it returned the QR image.
   *
   * @param auditLogService the audit service
   * @param httpRequest the request, for the client context (IP, user agent)
   * @param principal the operator who sees the material
   * @param targetAdminId the administrator whose material is shown
   * @param result the onboarding result that was returned
   */
  public static void qr(
      AuditLogService auditLogService,
      HttpServletRequest httpRequest,
      AdminPrincipal principal,
      Integer targetAdminId,
      OnboardingCredentialsResult result) {
    log(auditLogService, httpRequest, principal, targetAdminId, result, CHANNEL_QR);
  }

  /** Writes one {@link EventType#ADMIN_BIND_MATERIAL_REVEALED} row. */
  private static void log(
      AuditLogService auditLogService,
      HttpServletRequest httpRequest,
      AdminPrincipal principal,
      Integer targetAdminId,
      OnboardingCredentialsResult result,
      String channel) {
    auditLogService.log(
        AuditHelper.createAdminAudit(
                ClientContext.from(httpRequest),
                EventType.ADMIN_BIND_MATERIAL_REVEALED,
                AdminAuditConstants.ADMIN_BIND_MATERIAL_REVEALED,
                result.adminTenantId())
            .eventStatus(EventStatus.SUCCESS)
            .adminId(principal.adminId())
            .targetAdminId(targetAdminId)
            .enrollmentId(result.enrollmentId())
            .eventDetails(
                AuditDetailsBuilder.builder()
                    .custom("channel", channel)
                    .custom(
                        "enrollment_status",
                        result.enrollmentStatus() != null ? result.enrollmentStatus().name() : null)
                    .custom("proof_token_shown", result.enrollmentProofToken() != null)
                    .custom("challenge_shown", result.enrollmentChallenge() != null)
                    .toJson())
            .build());
  }
}
