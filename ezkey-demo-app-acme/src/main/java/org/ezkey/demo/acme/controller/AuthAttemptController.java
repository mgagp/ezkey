package org.ezkey.demo.acme.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ezkey.demo.acme.generated.dto.EnrollmentResponseDto;
import org.ezkey.demo.acme.generated.dto.IntegrationResponseDto;
import org.ezkey.demo.acme.service.EnrollmentService;
import org.ezkey.demo.acme.service.IntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Flux;

/**
 * Controller for managing authentication attempts in the ACME demo application.
 * 
 * @since 2025
 */
@Controller
@RequestMapping("/auth-attempts")
public class AuthAttemptController {

    private final EnrollmentService enrollmentService;
    private final IntegrationService integrationService;

    /**
     * Constructs the auth attempt controller with required dependencies.
     *
     * @param enrollmentService the enrollment service for API calls
     * @param integrationService the integration service for API calls
     */
    @Autowired
    public AuthAttemptController(EnrollmentService enrollmentService, IntegrationService integrationService) {
        this.enrollmentService = enrollmentService;
        this.integrationService = integrationService;
    }

    /**
     * Display the list of authentication attempts.
     * 
     * @param enrollmentId optional filter by enrollment ID
     * @param integrationId optional filter by integration ID
     * @param model Spring model
     * @return template name
     */
    @GetMapping
    public String listAuthAttempts(
            @RequestParam(value = "enrollmentId", required = false) Integer enrollmentId,
            @RequestParam(value = "integrationId", required = false) Integer integrationId,
            Model model) {
        
        // TODO: Implement real API calls to get auth attempts
        // For now, show empty list
        model.addAttribute("authAttempts", new ArrayList<>());
        model.addAttribute("enrollmentId", enrollmentId);
        model.addAttribute("integrationId", integrationId);
        model.addAttribute("pageTitle", "Authentication Attempts");
        
        return "auth-attempts/list";
    }

    /**
     * Display details of a specific authentication attempt.
     * 
     * @param id authentication attempt ID
     * @param model Spring model
     * @return template name
     */
    @GetMapping("/{id}")
    public String viewAuthAttempt(@PathVariable(value = "id") Integer id, Model model) {
        
        // TODO: Implement real API call to get auth attempt details
        model.addAttribute("error", "Authentication attempt not found");
        return "error";
    }

    /**
     * Display form to create a new authentication attempt.
     * 
     * @param enrollmentId pre-selected enrollment ID
     * @param model Spring model
     * @return template name
     */
    @GetMapping("/create")
    public String createAuthAttemptForm(
            @RequestParam(value = "enrollmentId", required = false) Integer enrollmentId,
            Model model) {
        
        try {
            // Get all enrollments from the API
            Flux<EnrollmentResponseDto> enrollmentsFlux = enrollmentService.getAllEnrollments();
            List<EnrollmentResponseDto> enrollments = enrollmentsFlux.collectList().block();
            
            if (enrollments == null) {
                enrollments = new ArrayList<>();
            }
            
            // Get all integrations to map integration names
            List<IntegrationResponseDto> integrations = integrationService.getAllIntegrationsSync();
            Map<Integer, String> integrationNames = new HashMap<>();
            
            for (IntegrationResponseDto integration : integrations) {
                if (integration.getI18n() != null && !integration.getI18n().isEmpty()) {
                    // Get the name from the first i18n entry
                    String integrationName = integration.getI18n().get(0).getName();
                    integrationNames.put(integration.getId(), integrationName);
                }
            }
            
            model.addAttribute("enrollments", enrollments);
            model.addAttribute("integrationNames", integrationNames);
            model.addAttribute("selectedEnrollmentId", enrollmentId);
            model.addAttribute("pageTitle", "Create Authentication Attempt");
            
        } catch (Exception e) {
            // Log error and show empty list
            System.err.println("Error fetching enrollments: " + e.getMessage());
            model.addAttribute("enrollments", new ArrayList<>());
            model.addAttribute("integrationNames", new HashMap<>());
            model.addAttribute("selectedEnrollmentId", enrollmentId);
            model.addAttribute("pageTitle", "Create Authentication Attempt");
        }
        
        return "auth-attempts/create";
    }

    /**
     * Handle creation of a new authentication attempt.
     * 
     * @param enrollmentId enrollment ID
     * @param challenge challenge code (optional)
     * @param model Spring model
     * @return redirect to auth attempts list
     */
    @PostMapping("/create")
    public String createAuthAttempt(
            @RequestParam(value = "enrollmentId") Integer enrollmentId,
            @RequestParam(value = "challenge", required = false) String challenge,
            Model model) {
        
        // TODO: Implement real API call to create auth attempt
        System.out.println("Creating auth attempt for enrollment " + enrollmentId + " with challenge: " + challenge);
        
        // Redirect to the auth attempts list filtered by enrollment
        return "redirect:/auth-attempts?enrollmentId=" + enrollmentId;
    }

    /**
     * Delete an authentication attempt.
     * 
     * @param id authentication attempt ID
     * @param enrollmentId enrollment ID for redirect
     * @param model Spring model
     * @return redirect to auth attempts list
     */
    @PostMapping("/{id}/delete")
    public String deleteAuthAttempt(
            @PathVariable(value = "id") Integer id,
            @RequestParam(value = "enrollmentId", required = false) Integer enrollmentId,
            Model model) {
        
        // TODO: Implement real API call to delete auth attempt
        System.out.println("Deleting auth attempt " + id);
        
        // Redirect back to the appropriate list
        if (enrollmentId != null) {
            return "redirect:/auth-attempts?enrollmentId=" + enrollmentId;
        } else {
            return "redirect:/auth-attempts";
        }
    }
}
