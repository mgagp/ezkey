/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: PublicEvaluatorSignupController
 * Description: Unauthenticated evaluator self-registration for EXP1 preview instances.
 */
package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.request.EvaluatorSelfRegistrationRequestDto;
import org.ezkey.admin.dto.response.EvaluatorSelfRegistrationResponseDto;
import org.ezkey.admin.security.AdminCsrfTokenService;
import org.ezkey.admin.security.AdminSessionCookieService;
import org.ezkey.admin.service.EvaluatorSelfRegistrationService;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.audit.util.ClientContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public anonymous evaluator signup for experimental preview instances (EXP1).
 *
 * <p>Responds with HTTP 404 when the installation-scoped feature flag is disabled. When enabled,
 * also mints a BOOTSTRAP Admin UI session (JSON and optional HttpOnly cookie). After activation,
 * incomplete-enrollment resume uses {@code POST /api/v1/admin/auth/onboarding-resume} (capability
 * secret) — not a public username oracle.
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(
    name = "Public",
    description =
        "Unauthenticated instance metadata, evaluator preview signup, and related public endpoints")
public class PublicEvaluatorSignupController {

  private final EvaluatorSelfRegistrationService evaluatorSelfRegistrationService;
  private final AuditLogService auditLogService;
  private final AdminBrowserSessionCookieProperties browserSessionCookieProperties;
  private final AdminSessionCookieService sessionCookieService;
  private final AdminCsrfTokenService csrfTokenService;

  public PublicEvaluatorSignupController(
      EvaluatorSelfRegistrationService evaluatorSelfRegistrationService,
      AuditLogService auditLogService,
      AdminBrowserSessionCookieProperties browserSessionCookieProperties,
      AdminSessionCookieService sessionCookieService,
      AdminCsrfTokenService csrfTokenService) {
    this.evaluatorSelfRegistrationService = evaluatorSelfRegistrationService;
    this.auditLogService = auditLogService;
    this.browserSessionCookieProperties = browserSessionCookieProperties;
    this.sessionCookieService = sessionCookieService;
    this.csrfTokenService = csrfTokenService;
  }

  /**
   * Creates an empty preview tenant and pending Tenant Admin with an activation code.
   *
   * @param request optional tenant label
   * @param httpRequest HTTP request for client context
   * @param httpResponse HTTP response for optional session cookie
   * @return activation material when enabled and within limits
   */
  @Operation(
      summary = "Anonymous evaluator self-registration",
      description =
          "Creates an empty preview tenant and a pending Tenant Admin with a one-time activation"
              + " code and an opaque Admin UI BOOTSTRAP session (community/alpha when enabled)."
              + " Available only on installations with evaluator self-registration enabled.",
      security = {})
  @ApiResponse(
      responseCode = "201",
      description = "Preview tenant, activation code, and bootstrap session created",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = EvaluatorSelfRegistrationResponseDto.class)))
  @ApiResponse(responseCode = "404", description = "Feature disabled on this installation")
  @ApiResponse(responseCode = "429", description = "Preview capacity or client limit reached")
  @PostMapping("/evaluator-signup")
  public ResponseEntity<EvaluatorSelfRegistrationResponseDto> evaluatorSignup(
      @Valid @RequestBody(required = false) EvaluatorSelfRegistrationRequestDto request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    if (!evaluatorSelfRegistrationService.isEnabled()) {
      return ResponseEntity.notFound().build();
    }

    ClientContext context = ClientContext.from(httpRequest);
    String tenantLabel = request == null ? null : request.tenantLabel();

    try {
      EvaluatorSelfRegistrationResponseDto response =
          evaluatorSelfRegistrationService.register(tenantLabel, context.clientIp());

      // Never put the session token in audit payloads (security guard).
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.EVALUATOR_SELF_REGISTRATION,
                  AdminAuditConstants.EVALUATOR_SELF_REGISTRATION_COMPLETED)
              .eventStatus(EventStatus.SUCCESS)
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .custom("tenant_label", response.tenantLabel())
                      .custom("username", response.username())
                      .custom("bootstrap_session", true)
                      .toJson())
              .build());

      EvaluatorSelfRegistrationResponseDto body =
          maybeAttachBrowserSessionCookie(response, httpResponse);
      return ResponseEntity.status(201).body(body);
    } catch (RuntimeException ex) { // CHECKSTYLE IGNORE IllegalCatch
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.EVALUATOR_SELF_REGISTRATION,
                  AdminAuditConstants.EVALUATOR_SELF_REGISTRATION_FAILED)
              .eventStatus(EventStatus.FAILURE)
              .errorMessage(ex.getMessage())
              .build());
      throw ex;
    }
  }

  /**
   * When HttpOnly browser session cookies are enabled, store the bootstrap token in the cookie and
   * omit the secret from the JSON body (Mode B). Mode A callers keep {@code sessionToken} in JSON.
   */
  private EvaluatorSelfRegistrationResponseDto maybeAttachBrowserSessionCookie(
      EvaluatorSelfRegistrationResponseDto response, HttpServletResponse httpResponse) {
    if (!browserSessionCookieProperties.isBrowserSessionCookieEnabled()
        || response.sessionToken() == null
        || response.sessionExpiresAt() == null) {
      return response;
    }
    sessionCookieService.addSessionCookie(
        httpResponse, response.sessionToken(), response.sessionExpiresAt());
    String csrf = csrfTokenService.createToken(response.sessionToken());
    sessionCookieService.addCsrfCookie(httpResponse, csrf, response.sessionExpiresAt());
    return new EvaluatorSelfRegistrationResponseDto(
        response.activationCode(),
        response.activationCodeExpiresAt(),
        response.adminUiUrl(),
        response.guidedTourUrl(),
        response.tenantLabel(),
        null,
        response.sessionExpiresAt(),
        response.username());
  }
}
