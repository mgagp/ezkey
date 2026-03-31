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
    AuthenticatedUser user = (AuthenticatedUser) session.getAttribute("user");

    if (user == null) {
      return "redirect:/login";
    }

    model.addAttribute("pageTitle", "Dashboard - ACME Inc");
    model.addAttribute("user", user);
    return "dashboard";
  }

  /**
   * Handles logout by invalidating session and redirecting to login page.
   *
   * @param session the HTTP session
   * @return redirect to login page
   */
  @GetMapping("/logout")
  public String logout(HttpSession session) {
    session.invalidate();
    return "redirect:/login?logout=true";
  }
}
