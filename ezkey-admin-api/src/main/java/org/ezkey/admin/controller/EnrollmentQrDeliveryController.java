/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: EnrollmentQrDeliveryController
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.ezkey.admin.connector.DeliveryCapabilityService;
import org.ezkey.admin.connector.EnrollmentQrDeliveryService;
import org.ezkey.admin.connector.QrDeliveryRefusedException;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sends an enrollment QR through an optional delivery connector (ADR-0018). Integrated delivery
 * keeps working with no connector: these routes then report it as unavailable.
 *
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/enrollments")
@Tag(name = "Enrollments", description = "Enrollment management API")
@SecurityRequirement(name = "bearerAuth")
public class EnrollmentQrDeliveryController {

  private final EnrollmentQrDeliveryService deliveryService;
  private final AccessControlService accessControlService;

  /**
   * Builds the controller.
   *
   * @param deliveryService send path
   * @param accessControlService tenant access checks
   */
  public EnrollmentQrDeliveryController(
      EnrollmentQrDeliveryService deliveryService, AccessControlService accessControlService) {
    this.deliveryService = deliveryService;
    this.accessControlService = accessControlService;
  }

  /**
   * Says whether the send action can be offered to this caller.
   *
   * @return the capability, with no connector detail beyond what an operator needs
   */
  @Operation(
      summary = "Read QR delivery capability",
      description =
          "Says whether a delivery connector can be used by this caller, without contacting a"
              + " provider. An admin scoped to a self-registered tenant sees it unavailable.")
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/qr-delivery/capability")
  public QrDeliveryCapabilityDto capability() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    DeliveryCapabilityService.Capability capability =
        deliveryService.capabilityFor(AdminProvisioningService.extractAdminPrincipal(auth));
    return new QrDeliveryCapabilityDto(
        capability.available(), capability.testMode(), capability.locales());
  }

  /**
   * Sends the QR of a {@code CREATED} enrollment to its contact email, as one attempt.
   *
   * @param id enrollment id
   * @param request template locale
   * @param httpRequest the HTTP request
   * @return the outcome: {@code accepted}, {@code rejected} or {@code unknown}
   */
  @Operation(
      summary = "Send an enrollment QR by email",
      description =
          "One attempt through the delivery connector. The challenge is never sent. A 200 carries"
              + " the outcome: accepted means the provider took the message, and unknown means it"
              + " may have left. A refusal before any call returns a problem with a typed code.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Connector called; see the outcome"),
        @ApiResponse(responseCode = "404", description = "Enrollment not found or not visible"),
        @ApiResponse(responseCode = "409", description = "Enrollment cannot be sent in this state"),
        @ApiResponse(responseCode = "422", description = "Missing or invalid sending data"),
        @ApiResponse(responseCode = "429", description = "Daily cap reached"),
        @ApiResponse(responseCode = "503", description = "No usable connector")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{id}/qr-delivery")
  public QrDeliveryResponseDto send(
      @PathVariable("id") Integer id,
      @Valid @RequestBody QrDeliveryRequestDto request,
      HttpServletRequest httpRequest) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!accessControlService.canAccessEnrollment(auth, id)) {
      throw new ResourceNotFoundException("Enrollment", id);
    }
    EnrollmentQrDeliveryService.SendReport report =
        deliveryService.send(
            id,
            request.locale(),
            AdminProvisioningService.extractAdminPrincipal(auth),
            ClientContext.from(httpRequest));
    return new QrDeliveryResponseDto(
        report.outcome().name().toLowerCase(Locale.ROOT),
        report.sendId(),
        report.recipient(),
        report.code(),
        report.testMode());
  }

  /**
   * Maps a refusal to a problem with a typed code.
   *
   * @param refused the refusal
   * @param request the HTTP request
   * @return the problem
   */
  @ExceptionHandler(QrDeliveryRefusedException.class)
  public ResponseEntity<ProblemDetail> refused(
      QrDeliveryRefusedException refused, HttpServletRequest request) {
    QrDeliveryRefusedException.Reason reason = refused.getReason();
    HttpStatus status = HttpStatus.valueOf(reason.httpStatus());
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setType(URI.create("https://ezkey.io/problems/domain/qr-delivery-" + reason.code()));
    problem.setTitle("QR delivery refused");
    problem.setDetail(reason.code());
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", reason.code());
    return ResponseEntity.status(status).body(problem);
  }

  /**
   * Send request.
   *
   * @param locale template locale, the operator's language
   */
  public record QrDeliveryRequestDto(
      @NotNull @Pattern(regexp = "en|fr") @Schema(example = "fr") String locale) {}

  /**
   * Send result.
   *
   * @param outcome accepted, rejected or unknown
   * @param sendId links logs and audit
   * @param recipient the address used
   * @param code typed error code when not accepted
   * @param testMode true when a test double answered
   */
  public record QrDeliveryResponseDto(
      String outcome, String sendId, String recipient, String code, boolean testMode) {}

  /**
   * Capability.
   *
   * @param available true when a send can be attempted
   * @param testMode true for a test double
   * @param locales locales offered
   */
  public record QrDeliveryCapabilityDto(
      boolean available, boolean testMode, List<String> locales) {}
}
