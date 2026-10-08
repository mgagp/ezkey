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
import org.ezkey.demo.acme.DemoAuthMessages;
import org.ezkey.demo.acme.config.EzkeyClientProvider;
import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.ezkey.demo.acme.security.DemoRateLimitService;
import org.ezkey.demo.acme.service.AccessCodeService;
import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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
 * <p>This controller processes login form submissions, creates auth attempts via Integration API,
 * waits for device approval, and creates HTTP sessions upon successful authentication. Challenge
 * mode is always requested server-side.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Controller
public class LoginController {

  private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

  private static final String SDK_NOT_CONFIGURED_MSG =
      "Ezkey SDK is not configured. Set credentials via an access link, config file, or use the"
          + " 'Apply API Key' dialog in the About This Demo section.";

  private final EzkeyClientProvider ezkeyClientProvider;
  private final DemoApiKeyConfigService demoApiKeyConfigService;
  private final DemoRateLimitService demoRateLimitService;
  private final AccessCodeService accessCodeService;

  /**
   * Creates the login controller.
   *
   * @param ezkeyClientProvider SDK client factory
   * @param demoApiKeyConfigService credential resolution
   * @param demoRateLimitService rate limiting
   * @param accessCodeService access-code slot labels
   */
  public LoginController(
      EzkeyClientProvider ezkeyClientProvider,
      DemoApiKeyConfigService demoApiKeyConfigService,
      DemoRateLimitService demoRateLimitService,
      AccessCodeService accessCodeService) {
    this.ezkeyClientProvider = ezkeyClientProvider;
    this.demoApiKeyConfigService = demoApiKeyConfigService;
    this.demoRateLimitService = demoRateLimitService;
    this.accessCodeService = accessCodeService;
  }

  /**
   * Handles POST /login form submission.
   *
   * <p>Always requests a challenge server-side ({@code challengeRequested=true}), ignoring any
   * client-supplied flag.
   *
   * @param username the username (used as userIdentifier for API lookup)
   * @param request the HTTP request
   * @param session the HTTP session
   * @param redirectAttributes for flash messages
   * @return redirect to challenge-wait on success, back to login on error
   */
  @PostMapping("/login")
  public String login(
      @RequestParam("username") String username,
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
      redirectAttributes.addFlashAttribute("error", DemoAuthMessages.RATE_LIMIT_LOGIN);
      return "redirect:/login?error=ratelimited";
    }

    logger.info("Login attempt for username: {}", username);

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
      // Challenge always on (server-enforced; client checkbox removed).
      var createResponse = client.createAuthAttemptByUserIdentifier(username.trim(), true);

      logger.info(
          "Auth attempt created: authAttemptId={}, userIdentifier={}",
          createResponse.authAttemptId(),
          username);

      session.setAttribute("pendingAuthAttemptId", createResponse.authAttemptId());
      session.setAttribute("pendingChallengeCode", createResponse.authAttemptChallenge());
      session.setAttribute("pendingUsername", username);
      session.setAttribute("pendingDisplayName", username);
      session.setAttribute("pendingEnrollmentId", null);
      session.setAttribute("pendingTimeoutSeconds", createResponse.timeoutSeconds());
      session.setAttribute("pendingExpiresAt", createResponse.expiresAt());

      logger.info("Redirecting to wait page for authAttemptId={}", createResponse.authAttemptId());

      return "redirect:/challenge-wait";

    } catch (EzkeyException e) {
      logger.error("EZKey authentication error for username: {}", username, e);
      redirectAttributes.addFlashAttribute("error", DemoAuthMessages.GENERIC_SIGN_IN_FAILED);
      return "redirect:/login?error=authfailed";
    }
  }

  /**
   * Handles GET /login.
   *
   * @param error optional error parameter
   * @param logout optional logout parameter
   * @param session the HTTP session (for active slot label)
   * @param model the Spring MVC model
   * @return login page template
   */
  @GetMapping("/login")
  public String loginPage(
      @RequestParam(value = "error", required = false) String error,
      @RequestParam(value = "logout", required = false) String logout,
      HttpSession session,
      Model model) {

    model.addAttribute("pageTitle", "Login - ACME Inc");

    String slotId = demoApiKeyConfigService.getActiveSlotId(session);
    if (slotId != null) {
      String label = accessCodeService.getLabel(slotId);
      if (label != null && !label.isBlank()) {
        model.addAttribute("slotLabel", label);
        model.addAttribute("loginHeading", "Sign in to " + label);
      }
    }

    if (error != null) {
      model.addAttribute("hasError", true);
      switch (error) {
        case "rejected":
          model.addAttribute("error", "Authentication rejected by user. Please try again.");
          break;
        case "expired":
          model.addAttribute("error", "Authentication request expired. Please try again.");
          break;
        case "ratelimited":
          model.addAttribute("error", DemoAuthMessages.RATE_LIMIT_LOGIN);
          break;
        case "sessionexpired":
          model.addAttribute("error", "Session expired. Please try again.");
          break;
        default:
          model.addAttribute("error", DemoAuthMessages.GENERIC_SIGN_IN_FAILED);
      }
    }
    if (logout != null) {
      model.addAttribute("logoutMessage", "You have been logged out.");
    }

    return "login";
  }

  /**
   * Applies API key credentials at runtime for temporary evaluator console access.
   *
   * <p>Invalidates the previous session (clears slot or prior paste), opens a new session, and
   * stores the pasted keys. The client must reload to pick up the new CSRF token.
   *
   * @param body the API key credentials (integrationKey, secretKey)
   * @param request the HTTP request
   * @return JSON response indicating success or failure
   */
  @PostMapping("/api/apply-api-key")
  public ResponseEntity<ApplyApiKeyResponse> applyApiKey(
      @RequestBody ApplyApiKeyRequest body, HttpServletRequest request) {
    DemoRateLimitService.RateLimitDecision rateLimitDecision =
        demoRateLimitService.checkApplyApiKey(request);
    if (!rateLimitDecision.allowed()) {
      logger.warn(
          "Apply API key rate limit exceeded for clientIp={} retryAfterSeconds={}",
          rateLimitDecision.clientId(),
          rateLimitDecision.retryAfterSeconds());
      return ResponseEntity.status(429)
          .header("Retry-After", String.valueOf(rateLimitDecision.retryAfterSeconds()))
          .body(new ApplyApiKeyResponse(false, DemoAuthMessages.RATE_LIMIT_APPLY_API_KEY));
    }

    if (body == null || body.integrationKey() == null || body.secretKey() == null) {
      return ResponseEntity.badRequest()
          .body(new ApplyApiKeyResponse(false, "Integration key and secret key are required."));
    }

    HttpSession existing = request.getSession(false);
    if (existing != null) {
      existing.invalidate();
    }
    HttpSession session = request.getSession(true);

    boolean applied =
        demoApiKeyConfigService.applyApiKey(session, body.integrationKey(), body.secretKey());
    if (applied) {
      return ResponseEntity.ok(
          new ApplyApiKeyResponse(
              true,
              "API key applied. Reload the page, then you can test login attempts in this browser"
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
   * @param request the HTTP request (for session id change on ACCEPTED)
   * @param session the HTTP session
   * @return JSON response with status and redirect URL if approved
   */
  @GetMapping("/api/auth-status")
  public ResponseEntity<AuthStatusResponse> checkAuthStatus(
      HttpServletRequest request, HttpSession session) {
    AuthenticatedUser existingUser = (AuthenticatedUser) session.getAttribute("user");
    if (existingUser != null) {
      return ResponseEntity.ok(
          new AuthStatusResponse("accepted", "/dashboard", "Authentication successful"));
    }

    String finalStatus = (String) session.getAttribute("authAttemptFinalStatus");
    if (finalStatus != null) {
      logger.info(
          "Final status already returned: {}, returning same status to prevent race condition",
          finalStatus);
      if ("REJECTED".equals(finalStatus)) {
        return ResponseEntity.ok(
            new AuthStatusResponse("rejected", "/login?error=rejected", "Rejected"));
      } else if ("EXPIRED".equals(finalStatus)) {
        return ResponseEntity.ok(
            new AuthStatusResponse("expired", "/login?error=expired", "Expired"));
      } else if ("INVALID".equals(finalStatus)) {
        return ResponseEntity.ok(
            new AuthStatusResponse(
                "error", "/login?error=authfailed", DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
      } else if ("UNKNOWN".equals(finalStatus) || "ERROR".equals(finalStatus)) {
        return ResponseEntity.ok(
            new AuthStatusResponse(
                "error", "/login?error=authfailed", DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
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
      var waitResponse = client.waitForAuthAttempt(authAttemptId, 30, 2);

      String status = waitResponse.status();
      boolean completed = waitResponse.completed();

      logger.info(
          "Received auth attempt status: status='{}', completed={}, authAttemptId={}, username={}",
          status,
          completed,
          authAttemptId,
          username);

      String normalizedStatus = status != null ? status.trim().toUpperCase() : null;

      if (normalizedStatus == null || normalizedStatus.isEmpty()) {
        logger.warn(
            "Received null or empty status for authAttemptId={}, username={}, completed={}",
            authAttemptId,
            username,
            completed);
        if (completed) {
          session.removeAttribute("pendingAuthAttemptId");
          session.removeAttribute("pendingChallengeCode");
          session.removeAttribute("pendingUsername");
          session.removeAttribute("pendingDisplayName");
          session.removeAttribute("pendingEnrollmentId");
          session.removeAttribute("pendingTimeoutSeconds");
          session.removeAttribute("pendingExpiresAt");
          return ResponseEntity.ok(
              new AuthStatusResponse(
                  "error", "/login?error=authfailed", DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
        } else {
          return ResponseEntity.ok(
              new AuthStatusResponse("pending", null, "Waiting for device approval..."));
        }
      }

      if ("ACCEPTED".equals(normalizedStatus)) {
        AuthenticatedUser authenticatedUser =
            new AuthenticatedUser(
                username, displayName != null ? displayName : username, enrollmentId);

        session.setAttribute("user", authenticatedUser);
        request.changeSessionId();

        session.removeAttribute("pendingAuthAttemptId");
        session.removeAttribute("pendingChallengeCode");
        session.removeAttribute("pendingUsername");
        session.removeAttribute("pendingDisplayName");
        session.removeAttribute("pendingEnrollmentId");

        logger.info("Challenge authentication successful for username: {}", username);
        return ResponseEntity.ok(
            new AuthStatusResponse("accepted", "/dashboard", "Authentication successful"));
      } else if ("REJECTED".equals(normalizedStatus)) {
        session.setAttribute("authAttemptFinalStatus", "REJECTED");

        logger.info("Challenge authentication rejected for username: {}", username);
        return ResponseEntity.ok(
            new AuthStatusResponse("rejected", "/login?error=rejected", "Rejected"));
      } else if ("EXPIRED".equals(normalizedStatus)) {
        session.setAttribute("authAttemptFinalStatus", "EXPIRED");

        logger.info("Challenge authentication expired for username: {}", username);
        return ResponseEntity.ok(
            new AuthStatusResponse("expired", "/login?error=expired", "Expired"));
      } else if ("INVALID".equals(normalizedStatus)) {
        session.setAttribute("authAttemptFinalStatus", "INVALID");

        logger.info("Challenge authentication invalid for username: {}", username);
        return ResponseEntity.ok(
            new AuthStatusResponse(
                "error", "/login?error=authfailed", DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
      } else if (completed) {
        session.setAttribute("authAttemptFinalStatus", "UNKNOWN");

        logger.warn(
            "Challenge authentication completed with unknown status: '{}' (normalized: '{}') for"
                + " username: {}",
            status,
            normalizedStatus,
            username);
        return ResponseEntity.ok(
            new AuthStatusResponse(
                "error", "/login?error=authfailed", DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
      } else {
        return ResponseEntity.ok(
            new AuthStatusResponse("pending", null, "Waiting for device approval..."));
      }
    } catch (EzkeyException e) {
      logger.error("Error checking auth status for authAttemptId={}", authAttemptId);
      session.setAttribute("authAttemptFinalStatus", "ERROR");
      return ResponseEntity.ok(
          new AuthStatusResponse(
              "error", "/login?error=authfailed", DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
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
