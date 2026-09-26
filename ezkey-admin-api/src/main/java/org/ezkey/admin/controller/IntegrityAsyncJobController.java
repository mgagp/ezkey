/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: IntegrityAsyncJobController
 * Description: REST API for Integrity async jobs (single global slot).
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.audit.asyncjob.IntegrityAsyncJobService;
import org.ezkey.audit.dto.IntegrityAsyncJobAcceptedResponse;
import org.ezkey.audit.dto.IntegrityAsyncJobResponse;
import org.ezkey.audit.dto.IntegrityAsyncJobStartRequest;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global Admin Integrity async job API: start (202), current status, by id, abandon/free-slot.
 *
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/audit-logs/integrity/jobs")
@Tag(
    name = "Integrity Async Jobs",
    description =
        "Single global slot for long Integrity operator jobs (chain/HMAC range verify, run"
            + " validation). Global Admin only.")
public class IntegrityAsyncJobController {

  private final IntegrityAsyncJobService integrityAsyncJobService;
  private final EzkeyAdminRepository adminRepository;

  /**
   * Constructs the controller.
   *
   * @param integrityAsyncJobService job orchestration
   * @param adminRepository username enrichment
   */
  public IntegrityAsyncJobController(
      IntegrityAsyncJobService integrityAsyncJobService, EzkeyAdminRepository adminRepository) {
    this.integrityAsyncJobService = integrityAsyncJobService;
    this.adminRepository = adminRepository;
  }

  /**
   * Starts an Integrity async job on the global slot.
   *
   * @param request job type and scope
   * @return 202 with opaque job id
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @PostMapping
  @Operation(
      summary = "Start Integrity async job",
      description =
          "Accepts a long Integrity operation on the single global slot and returns immediately."
              + " Second start while occupied returns 409 with the current job summary. Global"
              + " Admin only.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "202",
            description = "Job accepted",
            content =
                @Content(
                    schema = @Schema(implementation = IntegrityAsyncJobAcceptedResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "409",
            description =
                "Slot busy, or nightly validation inactive for RUN_VALIDATION"
                    + " (integrity-validation-disabled)",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<IntegrityAsyncJobAcceptedResponse> start(
      @Valid @RequestBody IntegrityAsyncJobStartRequest request) {
    Integer adminId = requireAdminId();
    String username =
        adminRepository.findById(adminId).map(EzkeyAdmin::getUsername).orElse("admin-" + adminId);
    IntegrityAsyncJobAcceptedResponse body =
        integrityAsyncJobService.start(request, adminId, username);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
  }

  /**
   * Returns the current Integrity async job for the banner (RUNNING or latest non-abandoned).
   *
   * @return 200 with job, or 204 when idle / never
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @GetMapping("/current")
  @Operation(
      summary = "Get current Integrity async job",
      description =
          "Any Global Admin may read the current slot status and last result. Applies TTL expiry"
              + " for stale RUNNING heartbeats. Returns 204 when idle.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Current or last visible job",
            content = @Content(schema = @Schema(implementation = IntegrityAsyncJobResponse.class))),
        @ApiResponse(responseCode = "204", description = "No job to show"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<IntegrityAsyncJobResponse> getCurrent() {
    return integrityAsyncJobService
        .getCurrent()
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  /**
   * Returns a job by opaque id.
   *
   * @param jobId job UUID
   * @return job status
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @GetMapping("/{jobId}")
  @Operation(summary = "Get Integrity async job by id")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Job found",
            content = @Content(schema = @Schema(implementation = IntegrityAsyncJobResponse.class))),
        @ApiResponse(responseCode = "404", description = "Unknown job id"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<IntegrityAsyncJobResponse> getById(@PathVariable UUID jobId) {
    return integrityAsyncJobService
        .getById(jobId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Abandons / frees the sticky EXPIRED or CANCELLED slot (UI « Abandon and restart »). Does not
   * kill a healthy RUNNING job. Crash/restart INTERRUPTED is auto-abandoned at boot.
   *
   * @return abandoned job summary
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @PostMapping("/current/abandon")
  @Operation(
      summary = "Abandon Integrity async job slot",
      description =
          "Frees sticky EXPIRED / CANCELLED state so Starts can be used cleanly."
              + " Crash/restart INTERRUPTED is auto-abandoned (not Escape-sticky). Refuses healthy"
              + " RUNNING. Does not erase prior FAILED evidence. Global Admin only.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Slot abandoned",
            content = @Content(schema = @Schema(implementation = IntegrityAsyncJobResponse.class))),
        @ApiResponse(
            responseCode = "409",
            description = "Abandon not allowed for current status",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<IntegrityAsyncJobResponse> abandon() {
    return ResponseEntity.ok(integrityAsyncJobService.abandon(requireAdminId()));
  }

  private Integer requireAdminId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AdminPrincipal principal) {
      return principal.adminId();
    }
    throw new IllegalStateException("Authenticated Global Admin principal required");
  }
}
