/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: HomeController
 * Description: Main controller for ACME demo application home page and navigation.
 */

package org.ezkey.demo.acme.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Main controller for ACME demo application home page and navigation.
 * <p>
 * This controller handles the primary user interface for the ACME demo application,
 * providing the home page and main navigation structure. It serves as the entry
 * point for users to explore Ezkey integration capabilities.
 * </p>
 *
 * <p>
 * <b>Responsibilities:</b>
 * <ul>
 * <li>Serve the home page with ACME branding</li>
 * <li>Provide navigation to demo features</li>
 * <li>Display application status and information</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Design Philosophy:</b> Implements Neo Brutalism UI principles with
 * bold typography, high contrast colors, and geometric layouts to create
 * a modern, professional appearance for the ACME brand.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Controller
public class HomeController {

    /**
     * Displays the home page of the ACME demo application.
     * <p>
     * Renders the main landing page featuring the ACME logo, company information,
     * and introduction to Ezkey integration capabilities. This page serves as
     * the starting point for the complete demo workflow.
     * </p>
     *
     * @param model the Spring MVC model for passing data to the view
     * @return the name of the Thymeleaf template to render
     */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("pageTitle", "ACME Inc - Protected Application");
        model.addAttribute("applicationName", "ACME Inc");
        model.addAttribute("integrationStatus", "Ezkey MFA Integration Active");
        model.addAttribute("version", "Demo v1.0");
        
        return "index";
    }

    /**
     * Displays the about page with information about the demo.
     * <p>
     * Provides detailed information about the ACME demo application,
     * its purpose, and how it demonstrates Ezkey integration capabilities.
     * </p>
     *
     * @param model the Spring MVC model for passing data to the view
     * @return the name of the Thymeleaf template to render
     */
    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("pageTitle", "About - ACME Inc Demo");
        return "about";
    }
}