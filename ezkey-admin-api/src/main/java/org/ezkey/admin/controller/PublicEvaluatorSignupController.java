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
import jakarta.validation.Valid;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.request.EvaluatorSelfRegistrationRequestDto;
import org.ezkey.admin.dto.response.EvaluatorSelfRegistrationResponseDto;
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
 * <p>Responds with HTTP 404 when the installation-scoped feature flag is disabled.
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

  public PublicEvaluatorSignupController(
      EvaluatorSelfRegistrationService evaluatorSelfRegistrationService,
      AuditLogService auditLogService) {
    this.evaluatorSelfRegistrationService = evaluatorSelfRegistrationService;
    this.auditLogService = auditLogService;
  }

  /**
   * Creates an empty preview tenant and pending Tenant Admin with an activation code.
   *
   * @param request optional tenant label
   * @param httpRequest HTTP request for client context
   * @return activation material when enabled and within limits
   */
  @Operation(
      summary = "Anonymous evaluator self-registration",
      description =
          "Creates an empty preview tenant and a pending Tenant Admin with a one-time activation"
              + " code. Available only on installations with evaluator self-registration enabled.")
  @ApiResponse(
      responseCode = "201",
      description = "Preview tenant and activation code created",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = EvaluatorSelfRegistrationResponseDto.class)))
  @ApiResponse(responseCode = "404", description = "Feature disabled on this installation")
  @ApiResponse(responseCode = "429", description = "Preview capacity or client limit reached")
  @PostMapping("/evaluator-signup")
  public ResponseEntity<EvaluatorSelfRegistrationResponseDto> evaluatorSignup(
      @Valid @RequestBody(required = false) EvaluatorSelfRegistrationRequestDto request,
      HttpServletRequest httpRequest) {
    if (!evaluatorSelfRegistrationService.isEnabled()) {
      return ResponseEntity.notFound().build();
    }

    ClientContext context = ClientContext.from(httpRequest);
    String tenantLabel = request == null ? null : request.tenantLabel();

    try {
      EvaluatorSelfRegistrationResponseDto response =
          evaluatorSelfRegistrationService.register(tenantLabel, context.clientIp());

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.EVALUATOR_SELF_REGISTRATION,
                  AdminAuditConstants.EVALUATOR_SELF_REGISTRATION_COMPLETED)
              .eventStatus(EventStatus.SUCCESS)
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .custom("tenant_label", response.tenantLabel())
                      .toJson())
              .build());

      return ResponseEntity.status(201).body(response);
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
}
