/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: LoginController
 * Description: Handles login POST requests and coordinates EZKey authentication flow.
 */

package org.ezkey.demo.acme.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.ezkey.demo.acme.config.EzkeyClientProvider;
import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.ezkey.demo.acme.security.DemoRateLimitService;
import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller handling login POST requests and coordinating EZKey authentication flow.
 *
 * <p>This controller processes login form submissions, creates auth attempts via Admin API, waits
 * for device approval, and creates HTTP sessions upon successful authentication.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Controller
public class LoginController {

  private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

  private static final String SDK_NOT_CONFIGURED_MSG =
      "Ezkey SDK is not configured. Set credentials via config file or use the 'Apply API Key' "
          + "dialog in the About This Demo section.";

  private static final String RATE_LIMIT_LOGIN_MSG =
      "Too many login attempts from your network. Please wait a moment and try again.";

  private static final String RATE_LIMIT_APPLY_API_KEY_MSG =
      "Too many API key apply attempts from your network. Please wait and try again.";

  private final EzkeyClientProvider ezkeyClientProvider;
  private final DemoApiKeyConfigService demoApiKeyConfigService;
  private final DemoRateLimitService demoRateLimitService;

  public LoginController(
      EzkeyClientProvider ezkeyClientProvider,
      DemoApiKeyConfigService demoApiKeyConfigService,
      DemoRateLimitService demoRateLimitService) {
    this.ezkeyClientProvider = ezkeyClientProvider;
    this.demoApiKeyConfigService = demoApiKeyConfigService;
    this.demoRateLimitService = demoRateLimitService;
  }

  /**
   * Handles POST /login form submission.
   *
   * <p>Processes login request:
   *
   * <ol>
   *   <li>Creates auth attempt via Admin API using username as userIdentifier
   *   <li>Waits for device approval
   *   <li>Creates HTTP session on success
   *   <li>Redirects to dashboard
   * </ol>
   *
   * <p>When the user has no verified enrollment for this integration, or multiple enrollments, the
   * API returns an error and the user is redirected to login with an error message.
   *
   * @param username the username (used as userIdentifier for API lookup)
   * @param challengeRequested whether challenge code is requested
   * @param session the HTTP session
   * @param redirectAttributes for flash messages
   * @return redirect to dashboard on success, back to login on error
   */
  @PostMapping("/login")
  public String login(
      @RequestParam("username") String username,
      @RequestParam(value = "challengeRequested", required = false) Boolean challengeRequested,
      HttpServletRequest request,
      HttpSession session,
      RedirectAttributes redirectAttributes) {

    DemoRateLimitService.RateLimitDecision rateLimitDecision =
        demoRateLimitService.checkLogin(request);
    if (!rateLimitDecision.allowed()) {
      logger.warn(
          "Login rate limit exceeded for clientIp={} retryAfterSeconds={}",
          rateLimitDecision.clientId(),
          rateLimitDecision.retryAfterSeconds());
      redirectAttributes.addFlashAttribute("error", RATE_LIMIT_LOGIN_MSG);
      return "redirect:/login?error=ratelimited";
    }

    logger.info("Login attempt for username: {}", username);

    // Clear any previous final status flag and pending attributes when starting a
    // new login attempt
    session.removeAttribute("authAttemptFinalStatus");
    session.removeAttribute("pendingAuthAttemptId");
    session.removeAttribute("pendingChallengeCode");
    session.removeAttribute("pendingUsername");
    session.removeAttribute("pendingDisplayName");
    session.removeAttribute("pendingEnrollmentId");
    session.removeAttribute("pendingTimeoutSeconds");
    session.removeAttribute("pendingExpiresAt");

    EzkeyClient client = ezkeyClientProvider.getClient(session);
    if (client == null) {
      logger.error("Login attempt rejected — Ezkey SDK not configured");
      redirectAttributes.addFlashAttribute("error", SDK_NOT_CONFIGURED_MSG);
      return "redirect:/login?error=authfailed";
    }

    try {
      boolean challengeMode = Boolean.TRUE.equals(challengeRequested);
      var createResponse = client.createAuthAttemptByUserIdentifier(username.trim(), challengeMode);

      logger.info(
          "Auth attempt created: authAttemptId={}, userIdentifier={}, challenge={}",
          createResponse.authAttemptId(),
          username,
          createResponse.authAttemptChallenge() != null
              ? "%02d".formatted(createResponse.authAttemptChallenge())
              : "none");

      // Store auth attempt info in session and redirect to wait page
      session.setAttribute("pendingAuthAttemptId", createResponse.authAttemptId());
      session.setAttribute("pendingChallengeCode", createResponse.authAttemptChallenge());
      session.setAttribute("pendingUsername", username);
      session.setAttribute("pendingDisplayName", username);
      session.setAttribute("pendingEnrollmentId", null);
      session.setAttribute("pendingTimeoutSeconds", createResponse.timeoutSeconds());
      session.setAttribute("pendingExpiresAt", createResponse.expiresAt());

      if (challengeMode && createResponse.authAttemptChallenge() != null) {
        logger.info(
            "Challenge mode: redirecting to wait page with code: {}",
            createResponse.authAttemptChallenge());
      } else {
        logger.info("Non-challenge mode: redirecting to wait page");
      }

      return "redirect:/challenge-wait";

    } catch (EzkeyException e) {
      logger.error("EZKey authentication error for username: {}", username, e);
      // Use API error message when available (e.g. user not found, multiple enrollments)
      String errorMsg =
          e.getMessage() != null && !e.getMessage().isBlank()
              ? e.getMessage()
              : "Authentication error. Check that the user is enrolled with this integration.";
      redirectAttributes.addFlashAttribute("error", errorMsg);
      return "redirect:/login?error=authfailed";
    }
  }

  /**
   * Handles GET /login (redirected from POST on error).
   *
   * @param error optional error parameter
   * @param logout optional logout parameter
   * @param model the Spring MVC model
   * @return login page template
   */
  @GetMapping("/login")
  public String loginPage(
      @RequestParam(value = "error", required = false) String error,
      @RequestParam(value = "logout", required = false) String logout,
      CsrfToken csrfToken,
      Model model) {

    model.addAttribute("pageTitle", "Login - ACME Inc");
    model.addAttribute("csrfToken", csrfToken);

    if (error != null) {
      model.addAttribute("hasError", true);
      // Set specific error messages based on error type
      switch (error) {
        case "rejected":
          model.addAttribute("error", "Authentication rejected by user. Please try again.");
          break;
        case "expired":
          model.addAttribute("error", "Authentication request expired. Please try again.");
          break;
        case "invalid":
          model.addAttribute("error", "Authentication invalid. Please try again.");
          break;
        case "authfailed":
          model.addAttribute("error", "Authentication failed. Please try again.");
          break;
        case "sessionexpired":
          model.addAttribute("error", "Session expired. Please try again.");
          break;
        case "notfound":
          model.addAttribute("error", "User not found. Please check your username.");
          break;
        case "ratelimited":
          model.addAttribute("error", RATE_LIMIT_LOGIN_MSG);
          break;
        default:
          model.addAttribute("error", "Authentication failed. Please try again.");
      }
    }
    if (logout != null) {
      model.addAttribute(
          "logoutMessage",
          "You have been logged out. The demo API key remains available in this browser session.");
    }

    return "login";
  }

  /**
   * Applies API key credentials at runtime for demo testing.
   *
   * <p>Updates the in-memory credentials used when building {@link EzkeyClient}. Takes effect
   * immediately for subsequent login attempts. Demo only — credentials are not persisted.
   *
   * @param request the API key credentials (integrationKey, secretKey)
   * @return JSON response indicating success or failure
   */
  @PostMapping("/api/apply-api-key")
  public ResponseEntity<ApplyApiKeyResponse> applyApiKey(
      @RequestBody ApplyApiKeyRequest body, HttpServletRequest request, HttpSession session) {
    DemoRateLimitService.RateLimitDecision rateLimitDecision =
        demoRateLimitService.checkApplyApiKey(request);
    if (!rateLimitDecision.allowed()) {
      logger.warn(
          "Apply API key rate limit exceeded for clientIp={} retryAfterSeconds={}",
          rateLimitDecision.clientId(),
          rateLimitDecision.retryAfterSeconds());
      return ResponseEntity.status(429)
          .header("Retry-After", String.valueOf(rateLimitDecision.retryAfterSeconds()))
          .body(new ApplyApiKeyResponse(false, RATE_LIMIT_APPLY_API_KEY_MSG));
    }

    if (body == null || body.integrationKey() == null || body.secretKey() == null) {
      return ResponseEntity.badRequest()
          .body(new ApplyApiKeyResponse(false, "Integration key and secret key are required."));
    }
    boolean applied =
        demoApiKeyConfigService.applyApiKey(session, body.integrationKey(), body.secretKey());
    if (applied) {
      return ResponseEntity.ok(
          new ApplyApiKeyResponse(
              true,
              "API key applied. You can now test multiple login attempts in this browser"
                  + " session."));
    }
    return ResponseEntity.badRequest()
        .body(
            new ApplyApiKeyResponse(
                false, "Both integration key and secret key must be non-blank."));
  }

  /**
   * Displays the challenge wait page with the challenge code.
   *
   * <p>This page shows the challenge code to the user and polls for authentication completion.
   *
   * @param session the HTTP session
   * @param model the Spring MVC model
   * @return challenge-wait page template
   */
  @GetMapping("/challenge-wait")
  public String challengeWaitPage(HttpSession session, Model model) {
    Integer authAttemptId = (Integer) session.getAttribute("pendingAuthAttemptId");
    Integer challengeCode = (Integer) session.getAttribute("pendingChallengeCode");
    String username = (String) session.getAttribute("pendingUsername");
    Integer timeoutSeconds = (Integer) session.getAttribute("pendingTimeoutSeconds");
    String expiresAt = (String) session.getAttribute("pendingExpiresAt");

    if (authAttemptId == null || username == null) {
      logger.warn("Challenge wait page accessed without pending auth attempt");
      return "redirect:/login?error=sessionexpired";
    }

    // Format challenge code as zero-padded 2-digit string if present
    String challengeCodeFormatted = null;
    if (challengeCode != null) {
      challengeCodeFormatted = "%02d".formatted(challengeCode);
    }

    model.addAttribute(
        "pageTitle",
        challengeCode != null ? "Enter Challenge Code - ACME Inc" : "Awaiting Approval - ACME Inc");
    model.addAttribute("challengeCode", challengeCodeFormatted);
    model.addAttribute("authAttemptId", authAttemptId);
    model.addAttribute("username", username);
    model.addAttribute("timeoutSeconds", timeoutSeconds);
    model.addAttribute("expiresAt", expiresAt);

    return "challenge-wait";
  }

  /**
   * Checks the status of a pending authentication attempt (for polling).
   *
   * <p>This endpoint is called by the challenge-wait page to check if the authentication attempt
   * has been approved.
   *
   * @param session the HTTP session
   * @return JSON response with status and redirect URL if approved
   */
  @GetMapping("/api/auth-status")
  public ResponseEntity<AuthStatusResponse> checkAuthStatus(HttpSession session) {
    // Check if user is already authenticated (prevents "expired" glitch after
    // successful auth)
    AuthenticatedUser existingUser = (AuthenticatedUser) session.getAttribute("user");
    if (existingUser != null) {
      // User is already authenticated, return success immediately
      return ResponseEntity.ok(
          new AuthStatusResponse("accepted", "/dashboard", "Authentication successful"));
    }

    // Check if a final status was already returned (prevents race condition)
    // This handles the case where a poll arrives after we've already returned a
    // final status
    // and cleaned up session attributes, preventing "session expired" from being
    // returned
    String finalStatus = (String) session.getAttribute("authAttemptFinalStatus");
    if (finalStatus != null) {
      logger.info(
          "Final status already returned: {}, returning same status to prevent race condition",
          finalStatus);
      // A final status was already returned, return the same status to prevent race
      // condition
      if ("REJECTED".equals(finalStatus)) {
        return ResponseEntity.ok(
            new AuthStatusResponse("rejected", "/login?error=rejected", "Rejected"));
      } else if ("EXPIRED".equals(finalStatus)) {
        return ResponseEntity.ok(
            new AuthStatusResponse("expired", "/login?error=expired", "Expired"));
      } else if ("INVALID".equals(finalStatus)) {
        return ResponseEntity.ok(
            new AuthStatusResponse("error", "/login?error=invalid", "Invalid"));
      } else if ("UNKNOWN".equals(finalStatus) || "ERROR".equals(finalStatus)) {
        return ResponseEntity.ok(
            new AuthStatusResponse(
                "error", "/login?error=authfailed", "Authentication error occurred"));
      }
    }

    Integer authAttemptId = (Integer) session.getAttribute("pendingAuthAttemptId");
    String username = (String) session.getAttribute("pendingUsername");
    String displayName = (String) session.getAttribute("pendingDisplayName");
    Integer enrollmentId = (Integer) session.getAttribute("pendingEnrollmentId");

    if (authAttemptId == null || username == null) {
      return ResponseEntity.ok(new AuthStatusResponse("expired", null, "Session expired"));
    }

    EzkeyClient client = ezkeyClientProvider.getClient(session);
    if (client == null) {
      return ResponseEntity.ok(
          new AuthStatusResponse("error", "/login?error=authfailed", SDK_NOT_CONFIGURED_MSG));
    }

    try {
      // Check auth attempt status
      var waitResponse = client.waitForAuthAttempt(authAttemptId, 30, 2);

      String status = waitResponse.status();
      boolean completed = waitResponse.completed();

      // Log received status for debugging
      logger.info(
          "Received auth attempt status: status='{}', completed={}, authAttemptId={}, username={}",
          status,
          completed,
          authAttemptId,
          username);

      // Normalize status (trim and uppercase) to handle any whitespace or case issues
      String normalizedStatus = status != null ? status.trim().toUpperCase() : null;

      // Handle null or empty status
      if (normalizedStatus == null || normalizedStatus.isEmpty()) {
        logger.warn(
            "Received null or empty status for authAttemptId={}, username={}, completed={}",
            authAttemptId,
            username,
            completed);
        if (completed) {
          // If completed but status is null/empty, treat as error
          session.removeAttribute("pendingAuthAttemptId");
          session.removeAttribute("pendingChallengeCode");
          session.removeAttribute("pendingUsername");
          session.removeAttribute("pendingDisplayName");
          session.removeAttribute("pendingEnrollmentId");
          session.removeAttribute("pendingTimeoutSeconds");
          session.removeAttribute("pendingExpiresAt");
          return ResponseEntity.ok(
              new AuthStatusResponse(
                  "error",
                  "/login?error=authfailed",
                  "Authentication completed with invalid status"));
        } else {
          // Still pending
          return ResponseEntity.ok(
              new AuthStatusResponse("pending", null, "Waiting for device approval..."));
        }
      }

      if ("ACCEPTED".equals(normalizedStatus)) {
        // Authentication successful - create session FIRST, then clear pending
        // attributes
        AuthenticatedUser authenticatedUser =
            new AuthenticatedUser(
                username, displayName != null ? displayName : username, enrollmentId);

        session.setAttribute("user", authenticatedUser);

        // Clear pending auth data AFTER creating user session
        // This prevents "expired" glitch if another poll arrives before redirect
        session.removeAttribute("pendingAuthAttemptId");
        session.removeAttribute("pendingChallengeCode");
        session.removeAttribute("pendingUsername");
        session.removeAttribute("pendingDisplayName");
        session.removeAttribute("pendingEnrollmentId");

        logger.info("Challenge authentication successful for username: {}", username);
        return ResponseEntity.ok(
            new AuthStatusResponse("accepted", "/dashboard", "Authentication successful"));
      } else if ("REJECTED".equals(normalizedStatus)) {
        // Authentication rejected by user
        // Mark as final status to prevent race condition with subsequent polls
        // Don't clear session attributes immediately - let them be cleared on next
        // request
        // This prevents a race condition where a poll arrives after cleanup and returns
        // "expired"
        session.setAttribute("authAttemptFinalStatus", "REJECTED");

        logger.info("Challenge authentication rejected for username: {}", username);
        return ResponseEntity.ok(
            new AuthStatusResponse("rejected", "/login?error=rejected", "Rejected"));
      } else if ("EXPIRED".equals(normalizedStatus)) {
        // Authentication expired
        // Mark as final status to prevent race condition with subsequent polls
        session.setAttribute("authAttemptFinalStatus", "EXPIRED");

        logger.info("Challenge authentication expired for username: {}", username);
        return ResponseEntity.ok(
            new AuthStatusResponse("expired", "/login?error=expired", "Expired"));
      } else if ("INVALID".equals(normalizedStatus)) {
        // Authentication invalid (wrong signature, challenge, etc.)
        // Mark as final status to prevent race condition with subsequent polls
        session.setAttribute("authAttemptFinalStatus", "INVALID");

        logger.info("Challenge authentication invalid for username: {}", username);
        return ResponseEntity.ok(
            new AuthStatusResponse("error", "/login?error=invalid", "Invalid"));
      } else if (completed) {
        // Completed but unknown status
        // Mark as final status to prevent race condition
        session.setAttribute("authAttemptFinalStatus", "UNKNOWN");

        logger.warn(
            "Challenge authentication completed with unknown status: '{}' (normalized: '{}') for"
                + " username: {}",
            status,
            normalizedStatus,
            username);
        return ResponseEntity.ok(
            new AuthStatusResponse(
                "error",
                "/login?error=authfailed",
                "Authentication completed with unknown status: " + status));
      } else {
        // Still pending (PENDING, READ, etc.) - keep session attributes
        return ResponseEntity.ok(
            new AuthStatusResponse("pending", null, "Waiting for device approval..."));
      }
    } catch (EzkeyException e) {
      logger.error("Error checking auth status: {}", e.getMessage());
      // Mark as error to prevent race condition
      session.setAttribute("authAttemptFinalStatus", "ERROR");
      return ResponseEntity.ok(
          new AuthStatusResponse("error", "/login?error=authfailed", e.getMessage()));
    }
  }

  /**
   * Request DTO for apply API key operation.
   *
   * @param integrationKey the integration key (e.g. ezkey_ikey_xxx)
   * @param secretKey the secret key (e.g. ezkey_skey_xxx)
   */
  public record ApplyApiKeyRequest(String integrationKey, String secretKey) {}

  /**
   * Response DTO for apply API key operation.
   *
   * @param success whether the operation was successful
   * @param message status message
   */
  public record ApplyApiKeyResponse(boolean success, String message) {}

  /**
   * Response DTO for authentication status check.
   *
   * @param status current status (pending, accepted, rejected, expired, error)
   * @param redirectUrl URL to redirect to if status is final
   * @param message status message
   */
  public record AuthStatusResponse(String status, String redirectUrl, String message) {}
}
