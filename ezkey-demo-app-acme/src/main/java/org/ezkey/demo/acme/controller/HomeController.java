/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: HomeController
 * Description: Main controller for ACME demo application - login and dashboard.
 */

package org.ezkey.demo.acme.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Main controller for ACME demo application handling login and dashboard.
 *
 * <p>This controller demonstrates EZKey passwordless login integration using a frontend-first
 * approach where JavaScript calls the Admin API directly from the browser.
 *
 * <p><b>Routes:</b>
 *
 * <ul>
 *   <li>{@code /} - Redirects to login page
 *   <li>{@code /login} - Login page with username form
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

  @Value("${ezkey.admin.api.url}")
  private String adminApiUrl;

  @Value("${ezkey.login.mode}")
  private String loginMode;

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
   * Displays the login page with EZKey passwordless authentication.
   *
   * <p>The login page contains a username field and optional challenge checkbox. JavaScript handles
   * the API calls to Admin API for authentication.
   *
   * @param model the Spring MVC model for passing data to the view
   * @return the name of the Thymeleaf template to render
   */
  @GetMapping("/login")
  public String login(Model model) {
    model.addAttribute("pageTitle", "Login - ACME Inc");
    model.addAttribute("adminApiUrl", adminApiUrl);
    model.addAttribute("loginMode", loginMode);
    return "login";
  }

  /**
   * Displays the dashboard after successful login.
   *
   * <p>The dashboard shows user information and explains how EZKey login works. Authentication is
   * validated client-side via sessionStorage token.
   *
   * @param model the Spring MVC model for passing data to the view
   * @return the name of the Thymeleaf template to render
   */
  @GetMapping("/dashboard")
  public String dashboard(Model model) {
    model.addAttribute("pageTitle", "Dashboard - ACME Inc");
    model.addAttribute("adminApiUrl", adminApiUrl);
    model.addAttribute("loginMode", loginMode);
    return "dashboard";
  }

  /**
   * Handles logout by redirecting to login page.
   *
   * <p>Token cleanup is handled client-side by clearing sessionStorage before redirect.
   *
   * @return redirect to login page
   */
  @GetMapping("/logout")
  public String logout() {
    return "redirect:/login?logout=true";
  }
}
