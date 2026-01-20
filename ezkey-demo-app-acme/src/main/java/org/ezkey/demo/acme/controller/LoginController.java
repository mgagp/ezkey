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
import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.ezkey.demo.acme.dto.UserMapping;
import org.ezkey.demo.acme.service.EzkeyAuthService;
import org.ezkey.demo.acme.service.UserMappingService;
import org.ezkey.demo.acme.config.AcmeProperties;
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
    UserMapping.UserEntry userEntry =
        userMappingService
            .findByUsername(username)
            .orElse(null);

    if (userEntry == null) {
      logger.warn("User not found in mapping: {}", username);
      redirectAttributes.addFlashAttribute("error", "User not found");
      return "redirect:/login?error=notfound";
    }

    // Step 2: Create auth attempt
    try {
      var createResponse =
          ezkeyAuthService.createAuthAttempt(
              userEntry.enrollmentId(), Boolean.TRUE.equals(challengeRequested));

      logger.info(
          "Auth attempt created: authAttemptId={}, enrollmentId={}",
          createResponse.authAttemptId(),
          userEntry.enrollmentId());

      // Step 3: Wait for device approval
      var waitResponse =
          ezkeyAuthService.waitForAuthAttempt(createResponse.authAttemptId(), 30, 2);

      if (!"ACCEPTED".equals(waitResponse.status())) {
        logger.warn(
            "Auth attempt not accepted: status={}, authAttemptId={}",
            waitResponse.status(),
            createResponse.authAttemptId());
        redirectAttributes.addFlashAttribute(
            "error", "Authentication failed: " + waitResponse.status());
        return "redirect:/login?error=authfailed";
      }

      // Step 4: Create session
      AuthenticatedUser authenticatedUser =
          new AuthenticatedUser(
              username,
              userEntry.displayName() != null ? userEntry.displayName() : username,
              userEntry.enrollmentId());

      session.setAttribute("user", authenticatedUser);
      logger.info("Login successful for username: {}", username);

      return "redirect:/dashboard";

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
   *       AcmeProperties), allowing external configuration changes in /app/config/application.properties
   *       to be applied without restarting. This includes API key credentials (integrationKey,
   *       secretKey).
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
        message.append("Application properties reloaded (").append(refreshedKeys.size())
            .append(" keys refreshed: ").append(refreshedKeys).append("). ");
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
   * Response DTO for configuration reload operation.
   *
   * @param success whether the reload was successful
   * @param message status message
   */
  public record ReloadConfigResponse(boolean success, String message) {}
}