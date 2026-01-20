/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: LoginController
 * Description: Handles login POST requests and coordinates EZKey authentication flow.
 */

package org.ezkey.demo.acme.controller;

import jakarta.servlet.http.HttpSession;
import org.ezkey.demo.acme.config.AcmeProperties;
import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.ezkey.demo.acme.dto.UserMapping;
import org.ezkey.demo.acme.service.EzkeyAuthService;
import org.ezkey.demo.acme.service.UserMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

  private final UserMappingService userMappingService;
  private final EzkeyAuthService ezkeyAuthService;
  private final ContextRefresher contextRefresher;
  private final AcmeProperties acmeProperties;

  public LoginController(
      UserMappingService userMappingService,
      EzkeyAuthService ezkeyAuthService,
      ContextRefresher contextRefresher,
      AcmeProperties acmeProperties) {
    this.userMappingService = userMappingService;
    this.ezkeyAuthService = ezkeyAuthService;
    this.contextRefresher = contextRefresher;
    this.acmeProperties = acmeProperties;
  }

  /**
   * Handles POST /login form submission.
   *
   * <p>Processes login request:
   *
   * <ol>
   *   <li>Validates username exists in mapping
   *   <li>Creates auth attempt via Admin API
   *   <li>Waits for device approval
   *   <li>Creates HTTP session on success
   *   <li>Redirects to dashboard
   * </ol>
   *
   * @param username the username
   * @param challengeRequested whether challenge code is requested
   * @param session the HTTP session
   * @param redirectAttributes for flash messages
   * @return redirect to dashboard on success, back to login on error
   */
  @PostMapping("/login")
  public String login(
      @RequestParam("username") String username,
      @RequestParam(value = "challengeRequested", required = false) Boolean challengeRequested,
      HttpSession session,
      RedirectAttributes redirectAttributes) {

    logger.info("Login attempt for username: {}", username);

    // Step 1: Lookup user in mapping
    UserMapping.UserEntry userEntry = userMappingService.findByUsername(username).orElse(null);

    if (userEntry == null) {
      logger.warn("User not found in mapping: {}", username);
      redirectAttributes.addFlashAttribute("error", "User not found");
      return "redirect:/login?error=notfound";
    }

    // Step 2: Create auth attempt
    try {
      boolean challengeMode = Boolean.TRUE.equals(challengeRequested);
      var createResponse =
          ezkeyAuthService.createAuthAttempt(userEntry.enrollmentId(), challengeMode);

      logger.info(
          "Auth attempt created: authAttemptId={}, enrollmentId={}, challenge={}",
          createResponse.authAttemptId(),
          userEntry.enrollmentId(),
          createResponse.authAttemptChallenge() != null
              ? String.format("%02d", createResponse.authAttemptChallenge())
              : "none");

      // Step 3: Store auth attempt info in session and redirect to wait page (unified for both
      // modes)
      session.setAttribute("pendingAuthAttemptId", createResponse.authAttemptId());
      session.setAttribute("pendingChallengeCode", createResponse.authAttemptChallenge());
      session.setAttribute("pendingUsername", username);
      session.setAttribute("pendingDisplayName", userEntry.displayName());
      session.setAttribute("pendingEnrollmentId", userEntry.enrollmentId());

      if (challengeMode && createResponse.authAttemptChallenge() != null) {
        logger.info(
            "Challenge mode: redirecting to wait page with code: {}",
            createResponse.authAttemptChallenge());
      } else {
        logger.info("Non-challenge mode: redirecting to wait page");
      }

      return "redirect:/challenge-wait";

    } catch (EzkeyAuthService.EzkeyAuthException e) {
      logger.error("EZKey authentication error for username: {}", username, e);
      redirectAttributes.addFlashAttribute("error", "Authentication error: " + e.getMessage());
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
      Model model) {

    model.addAttribute("pageTitle", "Login - ACME Inc");

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
        default:
          model.addAttribute("error", "Authentication failed. Please try again.");
      }
    }
    if (logout != null) {
      model.addAttribute("logoutMessage", "You have been logged out successfully.");
    }

    return "login";
  }

  /**
   * Reloads application configuration via Spring Cloud ContextRefresher.
   *
   * <p>This endpoint performs two types of reload:
   *
   * <ol>
   *   <li><b>Application Properties Reload:</b> Triggers refresh of @RefreshScope beans (like
   *       AcmeProperties), allowing external configuration changes in
   *       /app/config/application.properties to be applied without restarting. This includes API
   *       key credentials (integrationKey, secretKey).
   *   <li><b>Users Mapping File Reload:</b> Manually triggers reload of acme-users.json file by
   *       calling UserMappingService.checkAndReload(). This complements the automatic @Scheduled
   *       reload, allowing immediate refresh on demand.
   * </ol>
   *
   * <p><b>What gets reloaded:</b>
   *
   * <ul>
   *   <li>API Key credentials (ezkey.integration.key, ezkey.secret.key) - via @RefreshScope refresh
   *   <li>Admin API URL (ezkey.admin.api.url) - via @RefreshScope refresh
   *   <li>Users mapping file (acme-users.json) - via manual file reload
   *   <li>RestTemplate bean - recreated with new credentials via @RefreshScope
   * </ul>
   *
   * @return JSON response indicating success or failure
   */
  @PostMapping("/api/reload-config")
  public ResponseEntity<ReloadConfigResponse> reloadConfig() {
    try {
      logger.info("Configuration reload requested via /api/reload-config");

      // Step 1: Reload application.properties (API keys, URLs, etc.)
      // This refreshes @RefreshScope beans like AcmeProperties and RestTemplate
      java.util.Set<String> refreshedKeys = contextRefresher.refresh();

      // Step 2: Manually trigger users file reload (complements @Scheduled automatic reload)
      // This allows immediate refresh of acme-users.json on demand
      try {
        userMappingService.checkAndReload();
        logger.info("Users mapping file reload triggered");
      } catch (Exception e) {
        logger.warn("Error triggering users file reload: {}", e.getMessage());
      }

      // Build response message
      StringBuilder message = new StringBuilder();
      if (refreshedKeys != null && !refreshedKeys.isEmpty()) {
        message
            .append("Application properties reloaded (")
            .append(refreshedKeys.size())
            .append(" keys refreshed: ")
            .append(refreshedKeys)
            .append("). ");
      } else {
        message.append("Application properties reloaded (no changes detected). ");
      }
      message.append("Users mapping file reload triggered.");

      logger.info("Configuration reload completed successfully");
      return ResponseEntity.ok(new ReloadConfigResponse(true, message.toString()));

    } catch (Exception e) {
      logger.error("Error reloading configuration", e);
      return ResponseEntity.ok(new ReloadConfigResponse(false, "Error: " + e.getMessage()));
    }
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

    if (authAttemptId == null || username == null) {
      logger.warn("Challenge wait page accessed without pending auth attempt");
      return "redirect:/login?error=sessionexpired";
    }

    // Format challenge code as zero-padded 2-digit string if present
    String challengeCodeFormatted = null;
    if (challengeCode != null) {
      challengeCodeFormatted = String.format("%02d", challengeCode);
    }

    model.addAttribute(
        "pageTitle",
        challengeCode != null ? "Enter Challenge Code - ACME Inc" : "Awaiting Approval - ACME Inc");
    model.addAttribute("challengeCode", challengeCodeFormatted);
    model.addAttribute("authAttemptId", authAttemptId);
    model.addAttribute("username", username);

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
    // Check if user is already authenticated (prevents "expired" glitch after successful auth)
    AuthenticatedUser existingUser = (AuthenticatedUser) session.getAttribute("user");
    if (existingUser != null) {
      // User is already authenticated, return success immediately
      return ResponseEntity.ok(
          new AuthStatusResponse("accepted", "/dashboard", "Authentication successful"));
    }

    Integer authAttemptId = (Integer) session.getAttribute("pendingAuthAttemptId");
    String username = (String) session.getAttribute("pendingUsername");
    String displayName = (String) session.getAttribute("pendingDisplayName");
    Integer enrollmentId = (Integer) session.getAttribute("pendingEnrollmentId");

    if (authAttemptId == null || username == null) {
      return ResponseEntity.ok(new AuthStatusResponse("expired", null, "Session expired"));
    }

    try {
      // Check auth attempt status
      var waitResponse = ezkeyAuthService.waitForAuthAttempt(authAttemptId, 30, 2);

      String status = waitResponse.status();
      boolean completed = Boolean.TRUE.equals(waitResponse.completed());

      if ("ACCEPTED".equals(status)) {
        // Authentication successful - create session FIRST, then clear pending attributes
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
      } else if ("REJECTED".equals(status)) {
        // Authentication rejected by user
        // Clear pending attributes AFTER returning response to prevent "expired" glitch
        session.removeAttribute("pendingAuthAttemptId");
        session.removeAttribute("pendingChallengeCode");
        session.removeAttribute("pendingUsername");
        session.removeAttribute("pendingDisplayName");
        session.removeAttribute("pendingEnrollmentId");

        logger.info("Challenge authentication rejected for username: {}", username);
        return ResponseEntity.ok(
            new AuthStatusResponse(
                "rejected", "/login?error=rejected", "Authentication rejected by user"));
      } else if ("EXPIRED".equals(status)) {
        // Authentication expired
        session.removeAttribute("pendingAuthAttemptId");
        session.removeAttribute("pendingChallengeCode");
        session.removeAttribute("pendingUsername");
        session.removeAttribute("pendingDisplayName");
        session.removeAttribute("pendingEnrollmentId");

        logger.info("Challenge authentication expired for username: {}", username);
        return ResponseEntity.ok(
            new AuthStatusResponse(
                "expired", "/login?error=expired", "Authentication request expired"));
      } else if ("INVALID".equals(status)) {
        // Authentication invalid (wrong signature, challenge, etc.)
        session.removeAttribute("pendingAuthAttemptId");
        session.removeAttribute("pendingChallengeCode");
        session.removeAttribute("pendingUsername");
        session.removeAttribute("pendingDisplayName");
        session.removeAttribute("pendingEnrollmentId");

        logger.info("Challenge authentication invalid for username: {}", username);
        return ResponseEntity.ok(
            new AuthStatusResponse("error", "/login?error=invalid", "Authentication invalid"));
      } else if (completed) {
        // Completed but unknown status
        session.removeAttribute("pendingAuthAttemptId");
        session.removeAttribute("pendingChallengeCode");
        session.removeAttribute("pendingUsername");
        session.removeAttribute("pendingDisplayName");
        session.removeAttribute("pendingEnrollmentId");

        logger.warn(
            "Challenge authentication completed with unknown status: {} for username: {}",
            status,
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
    } catch (EzkeyAuthService.EzkeyAuthException e) {
      logger.error("Error checking auth status: {}", e.getMessage());
      // Clear session on error
      session.removeAttribute("pendingAuthAttemptId");
      session.removeAttribute("pendingChallengeCode");
      session.removeAttribute("pendingUsername");
      session.removeAttribute("pendingDisplayName");
      session.removeAttribute("pendingEnrollmentId");
      return ResponseEntity.ok(
          new AuthStatusResponse("error", "/login?error=authfailed", e.getMessage()));
    }
  }

  /**
   * Response DTO for configuration reload operation.
   *
   * @param success whether the reload was successful
   * @param message status message
   */
  public record ReloadConfigResponse(boolean success, String message) {}

  /**
   * Response DTO for authentication status check.
   *
   * @param status current status (pending, accepted, rejected, expired, error)
   * @param redirectUrl URL to redirect to if status is final
   * @param message status message
   */
  public record AuthStatusResponse(String status, String redirectUrl, String message) {}
}
