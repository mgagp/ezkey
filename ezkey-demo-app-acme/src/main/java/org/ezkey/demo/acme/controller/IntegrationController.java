/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: IntegrationController
 * Description: Web controller for integration management in ACME demo application.
 */

package org.ezkey.demo.acme.controller;

import java.util.List;

import org.ezkey.demo.acme.generated.dto.IntegrationCreateRequestDto;
import org.ezkey.demo.acme.generated.dto.IntegrationResponseDto;
import org.ezkey.demo.acme.service.IntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Web controller for integration management in ACME demo application.
 * <p>
 * This controller provides the web interface for managing Ezkey integrations
 * through a Neo Brutalism styled user interface. It demonstrates the complete
 * integration lifecycle including creation, viewing, and deletion of integrations.
 * </p>
 *
 * <p>
 * <b>Integration Management Features:</b>
 * <ul>
 * <li>List all existing integrations</li>
 * <li>Create new integrations with validation</li>
 * <li>View detailed integration information</li>
 * <li>Delete integrations (demo cleanup)</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>UI Technology Stack:</b>
 * <ul>
 * <li><b>Templates:</b> Thymeleaf with Neo Brutalism styling</li>
 * <li><b>Interactivity:</b> htmx for dynamic form submission</li>
 * <li><b>Validation:</b> Alpine.js for client-side validation</li>
 * <li><b>Design:</b> ACME brand colors and geometric layouts</li>
 * </ul>
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Controller
@RequestMapping("/integrations")
public class IntegrationController {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationController.class);

    private final IntegrationService integrationService;

    /**
     * Constructor with dependency injection of IntegrationService.
     *
     * @param integrationService the service for integration operations
     */
    public IntegrationController(IntegrationService integrationService){
        this.integrationService = integrationService;
    }

    /**
     * Displays the integrations list page.
     * <p>
     * Renders the main integrations page showing all configured integrations
     * in a Neo Brutalism styled table. Users can view details, create new
     * integrations, or delete existing ones from this page.
     * </p>
     *
     * @param model the Spring MVC model for passing data to the view
     * @return the name of the Thymeleaf template to render
     */
    @GetMapping
    public String listIntegrations(Model model) {
        logger.debug("Displaying integrations list page");
        try{
            List<IntegrationResponseDto> integrations = integrationService.getAllIntegrationsSync();

            model.addAttribute("pageTitle","Ezkey Integrations - ACME Inc");
            model.addAttribute("integrations",integrations);
            model.addAttribute("integrationsCount",integrations.size());

            logger.debug("Found {} integrations to display",integrations.size());
        } catch (Exception e){
            logger.error("Error loading integrations list",e);
            model.addAttribute("pageTitle","Error - ACME Inc");
            model.addAttribute("integrations",List.of());
            model.addAttribute("integrationsCount",0);
            model.addAttribute("error","Unable to load integrations. Please check that Ezkey Admin API is running.");
        }
        return "integrations/list";
    }

    /**
     * Displays the integration creation form.
     * <p>
     * Renders the form for creating new integrations with Neo Brutalism styling
     * and Alpine.js client-side validation. The form uses htmx for dynamic
     * submission without page reload.
     * </p>
     *
     * @param model the Spring MVC model for passing data to the view
     * @return the name of the Thymeleaf template to render
     */
    @GetMapping("/create")
    public String createIntegrationForm(Model model) {
        logger.debug("Displaying integration creation form");

        model.addAttribute("pageTitle","Create Integration - ACME Inc");
        model.addAttribute("integrationRequest",new IntegrationCreateRequestDto());

        return "integrations/create";
    }

    /**
     * Processes the integration creation form submission.
     * <p>
     * Handles the POST request from the integration creation form, validates
     * the input, creates the integration via the Ezkey Admin API, and redirects
     * to the appropriate page with success or error messages.
     * </p>
     *
     * @param integrationRequest the integration creation request from the form
     * @param redirectAttributes attributes for passing messages to the redirect
     * @return redirect URL based on success or failure
     */
    @PostMapping("/create")
    public String createIntegration(@ModelAttribute IntegrationCreateRequestDto integrationRequest,RedirectAttributes redirectAttributes) {
        logger.debug("Processing integration creation: {}", 
            integrationRequest.getI18n() != null && !integrationRequest.getI18n().isEmpty() 
                ? integrationRequest.getI18n().get(0).getName() 
                : "No name provided");
        try{
            // Create the integration
            IntegrationResponseDto createdIntegration = integrationService.createIntegrationSync(integrationRequest);
            if (createdIntegration != null){
                redirectAttributes.addFlashAttribute("success","Integration created successfully!");

                return "redirect:/integrations/" + createdIntegration.getId();
            } else{
                redirectAttributes.addFlashAttribute("error","Failed to create integration");
                return "redirect:/integrations/create";
            }
        } catch (Exception e){
            logger.error("Error creating integration",e);
            redirectAttributes.addFlashAttribute("error","Failed to create integration: " + e.getMessage());
            return "redirect:/integrations/create";
        }
    }

    /**
     * Displays detailed information about a specific integration.
     * <p>
     * Renders the integration details page showing complete information about
     * the integration including its configuration, QR code for enrollment,
     * and associated enrollments. Uses Neo Brutalism styling consistent
     * with the application design.
     * </p>
     *
     * @param integrationId the unique identifier of the integration
     * @param model the Spring MVC model for passing data to the view
     * @return the name of the Thymeleaf template to render
     */
    @GetMapping("/{id}")
    public String integrationDetails(@PathVariable("id") Integer integrationId,Model model) {
        logger.debug("Displaying details for integration ID: {}",integrationId);
        try{
            IntegrationResponseDto integration = integrationService.getIntegrationByIdSync(integrationId);
            if (integration != null){
                model.addAttribute("pageTitle","ACME Inc");
                model.addAttribute("integration",integration);

                // Generate QR code data for enrollment (placeholder for now)
                String enrollmentUrl = "ezkey://enroll/" + integration.getId();
                model.addAttribute("enrollmentQrData",enrollmentUrl);

                logger.debug("Displaying details for integration: {}", 
                    integration.getI18n() != null && !integration.getI18n().isEmpty() 
                        ? integration.getI18n().get(0).getName() 
                        : integration.getId());

                return "integrations/details";
            } else{
                logger.warn("Integration not found: {}",integrationId);
                model.addAttribute("pageTitle","Integration Not Found - ACME Inc");
                model.addAttribute("error","Integration not found");
                return "integrations/error";
            }
        } catch (Exception e){
            logger.error("Error loading integration details: {}",integrationId,e);
            model.addAttribute("pageTitle","Error - ACME Inc");
            model.addAttribute("error","Unable to load integration details: " + e.getMessage());
            return "integrations/error";
        }
    }

    /**
     * Deletes an integration.
     * <p>
     * Handles the deletion of an integration from the Ezkey system.
     * This is primarily used for demo cleanup purposes. The operation
     * will remove the integration and all associated data.
     * </p>
     *
     * @param integrationId the unique identifier of the integration to delete
     * @param redirectAttributes attributes for passing messages to the redirect
     * @return redirect to integrations list with success or error message
     */
    @PostMapping("/{id}/delete")
    public String deleteIntegration(@PathVariable("id") Integer integrationId,RedirectAttributes redirectAttributes) {
        logger.debug("Deleting integration ID: {}",integrationId);
        try{
            integrationService.deleteIntegration(integrationId).block();

            logger.info("Successfully deleted integration: {}",integrationId);
            redirectAttributes.addFlashAttribute("success","Integration deleted successfully");
        } catch (Exception e){
            logger.error("Error deleting integration: {}",integrationId,e);
            redirectAttributes.addFlashAttribute("error","Failed to delete integration: " + e.getMessage());
        }
        return "redirect:/integrations";
    }

    /**
     * HTMX endpoint for dynamic integration creation.
     * <p>
     * This endpoint supports htmx-powered form submission for creating
     * integrations without full page reload. Returns appropriate HTTP
     * status codes and responses for dynamic UI updates.
     * </p>
     *
     * @param integrationRequest the integration creation request
     * @param model the Spring MVC model for response data
     * @return fragment template for htmx response
     */
    @PostMapping(value = "/create",headers = "HX-Request")
    public String createIntegrationHtmx(@ModelAttribute IntegrationCreateRequestDto integrationRequest,Model model) {
        try{
            // Create the integration
            IntegrationResponseDto createdIntegration = integrationService.createIntegrationSync(integrationRequest);
            if (createdIntegration != null){
                model.addAttribute("success","Integration created successfully!");
                model.addAttribute("createdIntegration",createdIntegration);
                return "integrations/create :: success";
            } else{
                model.addAttribute("error","Failed to create integration");
                return "integrations/create :: form";
            }
        } catch (Exception e){
            logger.error("Error in htmx integration creation",e);
            model.addAttribute("error","Failed to create integration: " + e.getMessage());
            return "integrations/create :: form";
        }
    }
}