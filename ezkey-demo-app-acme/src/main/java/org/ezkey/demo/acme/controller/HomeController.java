/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: HomeController
 * Description: Main controller for ACME demo application - dashboard and logout.
 */

package org.ezkey.demo.acme.controller;

import jakarta.servlet.http.HttpSession;
import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Main controller for ACME demo application handling dashboard and logout.
 *
 * <p>This controller handles protected routes that require authentication. Login is handled by
 * LoginController.
 *
 * <p><b>Routes:</b>
 *
 * <ul>
 *   <li>{@code /} - Redirects to login page
 *   <li>{@code /dashboard} - Post-login dashboard (requires valid session)
 *   <li>{@code /logout} - Clears session and redirects to login
 * </ul>
 *
 * <p><b>Design Philosophy:</b> Implements Neo Brutalism UI principles with bold typography, high
 * contrast colors, and geometric layouts.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Controller
public class HomeController {

  private static final String USER_ATTRIBUTE = "user";
  private static final String AUTH_ATTEMPT_FINAL_STATUS_ATTRIBUTE = "authAttemptFinalStatus";
  private static final String PENDING_AUTH_ATTEMPT_ID_ATTRIBUTE = "pendingAuthAttemptId";
  private static final String PENDING_CHALLENGE_CODE_ATTRIBUTE = "pendingChallengeCode";
  private static final String PENDING_USERNAME_ATTRIBUTE = "pendingUsername";
  private static final String PENDING_DISPLAY_NAME_ATTRIBUTE = "pendingDisplayName";
  private static final String PENDING_ENROLLMENT_ID_ATTRIBUTE = "pendingEnrollmentId";
  private static final String PENDING_TIMEOUT_SECONDS_ATTRIBUTE = "pendingTimeoutSeconds";
  private static final String PENDING_EXPIRES_AT_ATTRIBUTE = "pendingExpiresAt";

  /**
   * Redirects root URL to login page.
   *
   * @return redirect to login page
   */
  @GetMapping("/")
  public String home() {
    return "redirect:/login";
  }

  /**
   * Displays the dashboard after successful login.
   *
   * <p>The dashboard shows user information and explains how EZKey login works. Authentication is
   * validated server-side via HTTP session.
   *
   * @param session the HTTP session
   * @param model the Spring MVC model for passing data to the view
   * @return redirect to login if not authenticated, otherwise dashboard template
   */
  @GetMapping("/dashboard")
  public String dashboard(HttpSession session, Model model) {
    AuthenticatedUser user = (AuthenticatedUser) session.getAttribute(USER_ATTRIBUTE);

    if (user == null) {
      return "redirect:/login";
    }

    model.addAttribute("pageTitle", "Dashboard - ACME Inc");
    model.addAttribute("user", user);
    return "dashboard";
  }

  /**
   * Handles logout by clearing authenticated-user state while preserving demo API key configuration
   * for the current browser session.
   *
   * @param session the HTTP session
   * @return redirect to login page
   */
  @GetMapping("/logout")
  public String logout(HttpSession session) {
    clearAuthenticationState(session);
    return "redirect:/login?logout=true";
  }

  private void clearAuthenticationState(HttpSession session) {
    session.removeAttribute(USER_ATTRIBUTE);
    session.removeAttribute(AUTH_ATTEMPT_FINAL_STATUS_ATTRIBUTE);
    session.removeAttribute(PENDING_AUTH_ATTEMPT_ID_ATTRIBUTE);
    session.removeAttribute(PENDING_CHALLENGE_CODE_ATTRIBUTE);
    session.removeAttribute(PENDING_USERNAME_ATTRIBUTE);
    session.removeAttribute(PENDING_DISPLAY_NAME_ATTRIBUTE);
    session.removeAttribute(PENDING_ENROLLMENT_ID_ATTRIBUTE);
    session.removeAttribute(PENDING_TIMEOUT_SECONDS_ATTRIBUTE);
    session.removeAttribute(PENDING_EXPIRES_AT_ATTRIBUTE);
  }
}
