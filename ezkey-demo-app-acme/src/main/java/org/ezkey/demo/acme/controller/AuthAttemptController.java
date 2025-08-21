package org.ezkey.demo.acme.controller;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ezkey.demo.acme.generated.dto.AuthAttemptCreateRequestDto;
import org.ezkey.demo.acme.generated.dto.AuthAttemptCreateResponseDto;
import org.ezkey.demo.acme.generated.dto.AuthAttemptDto;
import org.ezkey.demo.acme.generated.dto.EnrollmentResponseDto;
import org.ezkey.demo.acme.generated.dto.IntegrationResponseDto;
import org.ezkey.demo.acme.service.AuthAttemptService;
import org.ezkey.demo.acme.service.EnrollmentService;
import org.ezkey.demo.acme.service.IntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    private final AuthAttemptService authAttemptService;

    /**
     * Constructs the auth attempt controller with required dependencies.
     *
     * @param enrollmentService the enrollment service for API calls
     * @param integrationService the integration service for API calls
     * @param authAttemptService the auth attempt service for API calls
     */
    @Autowired
    public AuthAttemptController(EnrollmentService enrollmentService, IntegrationService integrationService, AuthAttemptService authAttemptService) {
        this.enrollmentService = enrollmentService;
        this.integrationService = integrationService;
        this.authAttemptService = authAttemptService;
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
        
        try {
            List<AuthAttemptDto> authAttempts;
            
            if (enrollmentId != null) {
                // Filter by enrollment ID
                authAttempts = authAttemptService.getAuthAttemptsByEnrollmentIdSync(enrollmentId);
            } else {
                // Get all auth attempts
                authAttempts = authAttemptService.getAllAuthAttemptsSync();
            }
            
            if (authAttempts == null) {
                authAttempts = new ArrayList<>();
            }
            
            // Get enrollments to map enrollment names and integration IDs
            List<EnrollmentResponseDto> enrollments = enrollmentService.getAllEnrollments()
                    .collectList()
                    .block(Duration.ofSeconds(10));
            Map<Integer, EnrollmentResponseDto> enrollmentMap = new HashMap<>();
            
            if (enrollments != null) {
                for (EnrollmentResponseDto enrollment : enrollments) {
                    enrollmentMap.put(enrollment.getEnrollmentId(), enrollment);
                }
            }
            
            // Get integration names for display
            List<IntegrationResponseDto> integrations = integrationService.getAllIntegrationsSync();
            Map<Integer, String> integrationNames = new HashMap<>();
            
            if (integrations != null) {
                for (IntegrationResponseDto integration : integrations) {
                    if (integration.getI18n() != null && !integration.getI18n().isEmpty()) {
                        integrationNames.put(integration.getId(), integration.getI18n().get(0).getName());
                    }
                }
            }
            
            // Create enriched data for the template
            List<Map<String, Object>> enrichedAuthAttempts = new ArrayList<>();
            
            for (AuthAttemptDto attempt : authAttempts) {
                Map<String, Object> enrichedAttempt = new HashMap<>();
                
                // Copy all properties from the original DTO
                enrichedAttempt.put("id", attempt.getAuthAttemptId());
                enrichedAttempt.put("enrollmentId", attempt.getEnrollmentId());
                enrichedAttempt.put("createdAt", attempt.getCreatedAt());
                enrichedAttempt.put("challenge", attempt.getAuthAttemptChallenge());
                
                // Get enrollment info
                EnrollmentResponseDto enrollment = enrollmentMap.get(attempt.getEnrollmentId());
                if (enrollment != null) {
                    enrichedAttempt.put("integrationId", enrollment.getIntegrationId());
                    
                    // Get integration name
                    String integrationName = integrationNames.get(enrollment.getIntegrationId());
                    enrichedAttempt.put("enrollmentName", integrationName != null ? 
                        integrationName + " Enrollment" : "Unknown Enrollment");
                } else {
                    enrichedAttempt.put("integrationId", "Unknown");
                    enrichedAttempt.put("enrollmentName", "Unknown Enrollment");
                }
                
                // Determine status based on DTO properties according to ENDPOINT.md rules
                String status;
                
                // #1: authAttemptRead null ou false : PENDING
                if (!Boolean.TRUE.equals(attempt.getAuthAttemptRead())) {
                    status = "PENDING";
                }
                // #2: authAttemptRead et authAttemptResponded null ou false : READ
                else if (!Boolean.TRUE.equals(attempt.getAuthAttemptResponded())) {
                    status = "READ";
                }
                // #3: authAttemptValid null ou false : INVALID
                else if (!Boolean.TRUE.equals(attempt.getAuthAttemptValid())) {
                    status = "INVALID";
                }
                // #4: authAttemptAccepted null ou false : REJECTED sinon ACCEPTED
                else if (Boolean.TRUE.equals(attempt.getAuthAttemptAccepted())) {
                    status = "ACCEPTED";
                } else {
                    status = "REJECTED";
                }
                enrichedAttempt.put("status", status);
                
                // Determine if challenge is required
                boolean challengeRequired = attempt.getAuthAttemptChallenge() != null;
                enrichedAttempt.put("challengeRequired", challengeRequired);
                
                enrichedAuthAttempts.add(enrichedAttempt);
            }
            
            model.addAttribute("authAttempts", enrichedAuthAttempts);
            model.addAttribute("integrationNames", integrationNames);
            model.addAttribute("enrollmentId", enrollmentId);
            model.addAttribute("integrationId", integrationId);
            model.addAttribute("pageTitle", "Authentication Attempts");
            
        } catch (Exception e) {
            // Log error and show empty list
            model.addAttribute("authAttempts", new ArrayList<>());
            model.addAttribute("integrationNames", new HashMap<>());
            model.addAttribute("enrollmentId", enrollmentId);
            model.addAttribute("integrationId", integrationId);
            model.addAttribute("pageTitle", "Authentication Attempts");
            model.addAttribute("error", "Failed to load authentication attempts: " + e.getMessage());
        }
        
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
        
        try {
            // Get the authentication attempt details
            Mono<AuthAttemptDto> authAttemptMono = authAttemptService.getAuthAttemptById(id);
            AuthAttemptDto authAttempt = authAttemptMono.block();
            
            if (authAttempt != null) {
                // Get enrollments to map enrollment names and integration IDs
                List<EnrollmentResponseDto> enrollments = enrollmentService.getAllEnrollments()
                        .collectList()
                        .block(Duration.ofSeconds(10));
                Map<Integer, EnrollmentResponseDto> enrollmentMap = new HashMap<>();
                
                if (enrollments != null) {
                    for (EnrollmentResponseDto enrollment : enrollments) {
                        enrollmentMap.put(enrollment.getEnrollmentId(), enrollment);
                    }
                }
                
                // Get integration names for display
                List<IntegrationResponseDto> integrations = integrationService.getAllIntegrationsSync();
                Map<Integer, String> integrationNames = new HashMap<>();
                
                if (integrations != null) {
                    for (IntegrationResponseDto integration : integrations) {
                        if (integration.getI18n() != null && !integration.getI18n().isEmpty()) {
                            integrationNames.put(integration.getId(), integration.getI18n().get(0).getName());
                        }
                    }
                }
                
                // Create enriched data for the template
                Map<String, Object> enrichedAttempt = new HashMap<>();
                
                // Copy all properties from the original DTO
                enrichedAttempt.put("id", authAttempt.getAuthAttemptId());
                enrichedAttempt.put("enrollmentId", authAttempt.getEnrollmentId());
                enrichedAttempt.put("createdAt", authAttempt.getCreatedAt());
                enrichedAttempt.put("challenge", authAttempt.getAuthAttemptChallenge());
                
                // Get enrollment info
                EnrollmentResponseDto enrollment = enrollmentMap.get(authAttempt.getEnrollmentId());
                if (enrollment != null) {
                    enrichedAttempt.put("integrationId", enrollment.getIntegrationId());
                    
                    // Get integration name
                    String integrationName = integrationNames.get(enrollment.getIntegrationId());
                    enrichedAttempt.put("enrollmentName", integrationName != null ? 
                        integrationName + " Enrollment" : "Unknown Enrollment");
                } else {
                    enrichedAttempt.put("integrationId", "Unknown");
                    enrichedAttempt.put("enrollmentName", "Unknown Enrollment");
                }
                
                // Determine status based on DTO properties according to ENDPOINT.md rules
                String status;
                
                // #1: authAttemptRead null ou false : PENDING
                if (!Boolean.TRUE.equals(authAttempt.getAuthAttemptRead())) {
                    status = "PENDING";
                }
                // #2: authAttemptRead et authAttemptResponded null ou false : READ
                else if (!Boolean.TRUE.equals(authAttempt.getAuthAttemptResponded())) {
                    status = "READ";
                }
                // #3: authAttemptValid null ou false : INVALID
                else if (!Boolean.TRUE.equals(authAttempt.getAuthAttemptValid())) {
                    status = "INVALID";
                }
                // #4: authAttemptAccepted null ou false : REJECTED sinon ACCEPTED
                else if (Boolean.TRUE.equals(authAttempt.getAuthAttemptAccepted())) {
                    status = "ACCEPTED";
                } else {
                    status = "REJECTED";
                }
                enrichedAttempt.put("status", status);
                
                // Determine if challenge is required
                boolean challengeRequired = authAttempt.getAuthAttemptChallenge() != null;
                enrichedAttempt.put("challengeRequired", challengeRequired);
                
                model.addAttribute("authAttempt", enrichedAttempt);
                model.addAttribute("pageTitle", "Authentication Attempt Details");
                
                return "auth-attempts/details";
            } else {
                model.addAttribute("error", "Authentication attempt not found");
                return "error";
            }
            
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load authentication attempt: " + e.getMessage());
            return "error";
        }
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
     * Create a new authentication attempt.
     * 
     * @param enrollmentId the enrollment ID for the authentication attempt
     * @param challengeRequested whether a challenge is requested
     * @param model Spring model
     * @return template name
     */
    @PostMapping("/create")
    public String createAuthAttempt(
            @RequestParam("enrollmentId") Integer enrollmentId,
            @RequestParam(value = "challengeRequested", defaultValue = "false") Boolean challengeRequested,
            Model model) {
        
        try {
            // Create the authentication attempt request
            AuthAttemptCreateRequestDto request = new AuthAttemptCreateRequestDto();
            request.setEnrollmentId(enrollmentId);
            request.setChallengeRequested(challengeRequested);
            
            // Call the API to create the authentication attempt
            Mono<AuthAttemptCreateResponseDto> responseMono = authAttemptService.createAuthAttempt(request);
            AuthAttemptCreateResponseDto response = responseMono.block();
            
            if (response != null) {
                model.addAttribute("success", "Authentication attempt created successfully with ID: " + response.getAuthAttemptId());
                model.addAttribute("authAttemptId", response.getAuthAttemptId());
                
                // Redirect to the auth attempts list
                return "redirect:/auth-attempts?success=created&id=" + response.getAuthAttemptId();
            } else {
                model.addAttribute("error", "Failed to create authentication attempt: No response from API");
                return "auth-attempts/create";
            }
            
        } catch (Exception e) {
            model.addAttribute("error", "Failed to create authentication attempt: " + e.getMessage());
            return "auth-attempts/create";
        }
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
        
        try {
            // Call the API to delete the authentication attempt
            Mono<Void> deleteMono = authAttemptService.deleteAuthAttempt(id);
            deleteMono.block();
            
            // Redirect back to the appropriate list
            if (enrollmentId != null) {
                return "redirect:/auth-attempts?enrollmentId=" + enrollmentId + "&success=deleted";
            } else {
                return "redirect:/auth-attempts?success=deleted";
            }
            
        } catch (Exception e) {
            // Redirect back with error
            if (enrollmentId != null) {
                return "redirect:/auth-attempts?enrollmentId=" + enrollmentId + "&error=Failed to delete: " + e.getMessage();
            } else {
                return "redirect:/auth-attempts?error=Failed to delete: " + e.getMessage();
            }
        }
    }
}
