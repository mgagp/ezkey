/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: BusinessApprovalController
 * Description: Demonstrates contextual authentication — a logged-in user triggers an approval
 *              request addressed to a designated approver (identified by their EZKey user
 *              identifier). The approver's mobile device displays a rich context card so they
 *              understand exactly what they are authorizing before tapping approve or deny.
 */

package org.ezkey.demo.acme.controller;

import jakarta.servlet.http.HttpSession;
import org.ezkey.demo.acme.config.EzkeyClientProvider;
import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.ezkey.sdk.AuthAttemptContext;
import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that demonstrates EZKey contextual authentication in a business-approval flow.
 *
 * <p>The demo scenario: a logged-in ACME employee (e.g. John) initiates a financial operation that
 * requires explicit authorisation from a designated approver (e.g. Jane). John enters Jane's EZKey
 * {@code userIdentifier} in the dashboard, selects a scenario, and clicks <em>Request</em>. Jane's
 * enrolled mobile device immediately receives a contextual push showing exactly what she is being
 * asked to authorise — title and message — before she taps approve or deny.
 *
 * <p>This controller exposes two endpoints consumed by the dashboard JavaScript:
 *
 * <ul>
 *   <li>{@code POST /api/business-approval} — creates a contextual auth attempt targeted at the
 *       specified approver and returns the attempt details to the browser.
 *   <li>{@code GET /api/business-approval-status?authAttemptId={id}} — performs a fast spot-check
 *       on the attempt and returns its current status. No long-polling; the browser calls this
 *       on-demand when the user clicks the <em>Check Status</em> button.
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@RestController
public class BusinessApprovalController {

  private static final Logger logger = LoggerFactory.getLogger(BusinessApprovalController.class);

  /** Session key used to store the most recently created business-approval attempt ID. */
  static final String SESSION_BIZ_ATTEMPT_ID = "bizApprovalAuthAttemptId";

  /** Session key used to store the scenario key for logging purposes. */
  static final String SESSION_BIZ_SCENARIO = "bizApprovalScenario";

  private final EzkeyClientProvider ezkeyClientProvider;

  /**
   * Constructs the controller with the required EZKey client provider.
   *
   * @param ezkeyClientProvider supplies the configured EZKey SDK client
   */
  public BusinessApprovalController(EzkeyClientProvider ezkeyClientProvider) {
    this.ezkeyClientProvider = ezkeyClientProvider;
  }

  // ---------------------------------------------------------------------------
  // Start a business-approval request
  // ---------------------------------------------------------------------------

  /**
   * Creates a contextual authentication attempt addressed to the specified approver.
   *
   * <p>Resolves the approver via their {@code userIdentifier} registered in EZKey, builds the
   * scenario context, and submits the auth attempt through the EZKey SDK. The attempt ID is stored
   * in the HTTP session for fallback use by the status endpoint.
   *
   * @param request the scenario and approver identifier from the browser
   * @param session the HTTP session (must contain an authenticated {@code user} attribute)
   * @return JSON with {@code authAttemptId}, {@code expiresAt}, and {@code timeoutSeconds}, or an
   *     error body with HTTP 401 / 400 / 503
   */
  @PostMapping("/api/business-approval")
  public ResponseEntity<BusinessApprovalStartResponse> startBusinessApproval(
      @RequestBody BusinessApprovalRequest request, HttpSession session) {

    AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("user");
    if (user == null) {
      return ResponseEntity.status(401)
          .body(new BusinessApprovalStartResponse(null, null, null, "Not authenticated."));
    }

    EzkeyClient client = ezkeyClientProvider.getClient();
    if (client == null) {
      return ResponseEntity.status(503)
          .body(
              new BusinessApprovalStartResponse(
                  null, null, null, "EZKey SDK not configured — set credentials first."));
    }

    String scenario = request.scenario() != null ? request.scenario().trim().toUpperCase() : "";
    String approverIdentifier =
        request.approverIdentifier() != null ? request.approverIdentifier().trim() : "";

    if (approverIdentifier.isBlank()) {
      return ResponseEntity.status(400)
          .body(
              new BusinessApprovalStartResponse(
                  null, null, null, "Approver identifier must not be blank."));
    }

    AuthAttemptContext context = buildContextForScenario(scenario);

    session.removeAttribute(SESSION_BIZ_ATTEMPT_ID);
    session.removeAttribute(SESSION_BIZ_SCENARIO);

    try {
      var createResponse =
          client.createAuthAttemptByUserIdentifier(approverIdentifier, false, context);

      session.setAttribute(SESSION_BIZ_ATTEMPT_ID, createResponse.authAttemptId());
      session.setAttribute(SESSION_BIZ_SCENARIO, scenario);

      logger.info(
          "Business approval attempt created: id={}, scenario={}, requestedBy={}, approver={},"
              + " contextTitle={}",
          createResponse.authAttemptId(),
          scenario,
          user.username(),
          approverIdentifier,
          createResponse.contextTitle());

      return ResponseEntity.ok(
          new BusinessApprovalStartResponse(
              createResponse.authAttemptId(),
              createResponse.expiresAt(),
              createResponse.timeoutSeconds(),
              null));

    } catch (EzkeyException e) {
      logger.error(
          "Failed to create business-approval attempt: scenario={}, approver={}, requestedBy={}",
          scenario,
          approverIdentifier,
          user.username(),
          e);
      String msg =
          e.getMessage() != null && !e.getMessage().isBlank()
              ? e.getMessage()
              : "Failed to create approval request.";
      return ResponseEntity.status(400)
          .body(new BusinessApprovalStartResponse(null, null, null, msg));
    }
  }

  // ---------------------------------------------------------------------------
  // Spot-check the result
  // ---------------------------------------------------------------------------

  /**
   * Performs a fast spot-check on a business-approval auth attempt and returns its current status.
   *
   * <p>Called by the dashboard JavaScript when the user clicks the <em>Check Status</em> button.
   * Uses the {@code /wait} endpoint with the minimum acceptable parameters (timeout=2s,
   * polling=1s); if the attempt is already in a terminal state the server returns immediately with
   * the final status, otherwise it waits up to 2 seconds before returning the current state.
   *
   * <p>The {@code authAttemptId} query parameter is preferred; if omitted, the endpoint falls back
   * to the attempt ID stored in the HTTP session by {@link #startBusinessApproval}.
   *
   * @param authAttemptId optional attempt ID to check (takes precedence over session)
   * @param session the HTTP session
   * @return JSON with {@code status} ({@code pending}, {@code accepted}, {@code rejected}, {@code
   *     expired}, {@code error}) and an optional {@code message}
   */
  @GetMapping("/api/business-approval-status")
  public ResponseEntity<BusinessApprovalStatusResponse> checkBusinessApprovalStatus(
      @RequestParam(required = false) Integer authAttemptId, HttpSession session) {

    AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("user");
    if (user == null) {
      return ResponseEntity.ok(new BusinessApprovalStatusResponse("error", "Session expired."));
    }

    Integer id = authAttemptId;
    if (id == null) {
      id = (Integer) session.getAttribute(SESSION_BIZ_ATTEMPT_ID);
    }
    if (id == null) {
      return ResponseEntity.ok(
          new BusinessApprovalStatusResponse("error", "No pending approval request."));
    }

    EzkeyClient client = ezkeyClientProvider.getClient();
    if (client == null) {
      return ResponseEntity.ok(
          new BusinessApprovalStatusResponse("error", "EZKey SDK not configured."));
    }

    try {
      var waitResponse = client.waitForAuthAttempt(id, 2, 1);
      String normalized =
          waitResponse.status() != null ? waitResponse.status().trim().toUpperCase() : "";

      logger.debug(
          "Business approval spot-check: id={}, status={}, completed={}",
          id,
          normalized,
          waitResponse.completed());

      return switch (normalized) {
        case "ACCEPTED" -> {
          logger.info("Business approval ACCEPTED: id={}, user={}", id, user.username());
          yield ResponseEntity.ok(new BusinessApprovalStatusResponse("accepted", null));
        }
        case "REJECTED" ->
            ResponseEntity.ok(
                new BusinessApprovalStatusResponse("rejected", "Request denied on device."));
        case "EXPIRED" ->
            ResponseEntity.ok(
                new BusinessApprovalStatusResponse("expired", "Approval request timed out."));
        case "INVALID" ->
            ResponseEntity.ok(
                new BusinessApprovalStatusResponse("error", "Authentication invalid."));
        default -> ResponseEntity.ok(new BusinessApprovalStatusResponse("pending", null));
      };

    } catch (EzkeyException e) {
      logger.error("Error checking business-approval status for id={}: {}", id, e.getMessage());
      return ResponseEntity.ok(new BusinessApprovalStatusResponse("error", e.getMessage()));
    }
  }

  // ---------------------------------------------------------------------------
  // Scenario builder
  // ---------------------------------------------------------------------------

  /**
   * Builds an {@link AuthAttemptContext} for the given scenario key.
   *
   * <p>The context is displayed on the approver's mobile device so they understand exactly what
   * operation they are being asked to authorise.
   *
   * @param scenario the scenario key ({@code "PAYMENT"} or {@code "BATCH_PAYMENT"})
   * @return the corresponding {@link AuthAttemptContext}
   */
  static AuthAttemptContext buildContextForScenario(String scenario) {
    return switch (scenario) {
      case "PAYMENT" ->
          new AuthAttemptContext(
              "Payment Authorization",
              "Authorize payment of $5,000 to Suppliers Ltd. for invoice INV-2025-042."
                  + " Submitted by ACME Finance.");

      case "BATCH_PAYMENT" ->
          new AuthAttemptContext(
              "Batch Payment Authorization",
              "Release batch payment run B-2025-18 — $18,400 across 6 pending suppliers."
                  + " Submitted by ACME Finance.");

      default ->
          new AuthAttemptContext(
              "Business Approval Request", "Please review and approve this request.");
    };
  }

  // ---------------------------------------------------------------------------
  // Inner DTOs
  // ---------------------------------------------------------------------------

  /**
   * Request body for {@code POST /api/business-approval}.
   *
   * @param scenario the scenario key selected by the user ({@code "PAYMENT"} or {@code
   *     "BATCH_PAYMENT"})
   * @param approverIdentifier the EZKey {@code userIdentifier} of the person who must approve (e.g.
   *     {@code "jane"})
   */
  public record BusinessApprovalRequest(String scenario, String approverIdentifier) {}

  /**
   * Response body for {@code POST /api/business-approval}.
   *
   * @param authAttemptId the created auth attempt ID, or {@code null} on error
   * @param expiresAt ISO-8601 expiration timestamp, or {@code null} on error
   * @param timeoutSeconds server-side timeout in seconds, or {@code null} on error
   * @param error error message, or {@code null} on success
   */
  public record BusinessApprovalStartResponse(
      Integer authAttemptId, String expiresAt, Integer timeoutSeconds, String error) {}

  /**
   * Response body for {@code GET /api/business-approval-status}.
   *
   * @param status current status: {@code pending}, {@code accepted}, {@code rejected}, {@code
   *     expired}, {@code error}
   * @param message optional human-readable detail for non-pending statuses
   */
  public record BusinessApprovalStatusResponse(String status, String message) {}
}
