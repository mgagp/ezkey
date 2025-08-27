package org.ezkey.demo.acme.controller;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(AuthAttemptController.class);

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
            
            // Always get all auth attempts first, then filter client-side if needed
            authAttempts = authAttemptService.getAllAuthAttemptsSync();
            
            if (enrollmentId != null) {
                // Filter by enrollment ID on client side
                List<AuthAttemptDto> filteredAttempts = authAttempts.stream()
                    .filter(attempt -> Objects.equals(attempt.getEnrollmentId(), enrollmentId))
                    .collect(Collectors.toList());
                
                authAttempts = filteredAttempts;
                logger.debug("Client-side filtered auth attempts for enrollment {}: found {} attempts out of {} total", 
                    enrollmentId, authAttempts.size(), authAttemptService.getAllAuthAttemptsSync().size());
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
            
            // If enrollmentId is provided but integrationId is not, try to get integrationId from enrollment
            Integer resolvedIntegrationId = integrationId;
            if (enrollmentId != null && integrationId == null) {
                EnrollmentResponseDto enrollment = enrollmentMap.get(enrollmentId);
                if (enrollment != null) {
                    resolvedIntegrationId = enrollment.getIntegrationId();
                }
            }
            
            model.addAttribute("authAttempts", enrichedAuthAttempts);
            model.addAttribute("integrationNames", integrationNames);
            model.addAttribute("enrollmentId", enrollmentId);
            model.addAttribute("integrationId", resolvedIntegrationId);
            model.addAttribute("pageTitle", "Authentication Attempts");
            
        } catch (Exception e) {
            // Log error and show empty list
            model.addAttribute("authAttempts", new ArrayList<>());
            model.addAttribute("integrationNames", new HashMap<>());
            model.addAttribute("enrollmentId", enrollmentId);
            
            // Try to resolve integrationId from enrollmentId even in error case
            Integer resolvedIntegrationId = integrationId;
            if (enrollmentId != null && integrationId == null) {
                try {
                    List<EnrollmentResponseDto> enrollments = enrollmentService.getAllEnrollments()
                            .collectList()
                            .block(Duration.ofSeconds(5));
                    if (enrollments != null) {
                        for (EnrollmentResponseDto enrollment : enrollments) {
                            if (enrollment.getEnrollmentId().equals(enrollmentId)) {
                                resolvedIntegrationId = enrollment.getIntegrationId();
                                break;
                            }
                        }
                    }
                } catch (Exception resolveException) {
                    // Ignore resolution errors in error handling
                }
            }
            model.addAttribute("integrationId", resolvedIntegrationId);
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
            
            // Find the selected enrollment if enrollmentId is provided
            EnrollmentResponseDto selectedEnrollment = null;
            if (enrollmentId != null) {
                selectedEnrollment = enrollments.stream()
                    .filter(e -> Objects.equals(e.getEnrollmentId(), enrollmentId))
                    .findFirst()
                    .orElse(null);
            }
            
            model.addAttribute("enrollments", enrollments);
            model.addAttribute("integrationNames", integrationNames);
            model.addAttribute("selectedEnrollmentId", enrollmentId);
            model.addAttribute("selectedEnrollment", selectedEnrollment);
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
            @RequestParam(value = "challengeRequested", required = false) Boolean challengeRequested,
            Model model) {
        
        try {
            // Get enrollment to check if challenge is required
            List<EnrollmentResponseDto> enrollments = enrollmentService.getAllEnrollments()
                    .collectList()
                    .block(Duration.ofSeconds(10));
            
            EnrollmentResponseDto selectedEnrollment = null;
            if (enrollments != null) {
                selectedEnrollment = enrollments.stream()
                    .filter(e -> Objects.equals(e.getEnrollmentId(), enrollmentId))
                    .findFirst()
                    .orElse(null);
            }
            
            // Determine if challenge is requested
            if (selectedEnrollment != null) {
                // If enrollment requires challenge, force challengeRequested to true
                if (Boolean.TRUE.equals(selectedEnrollment.getAuthAttemptChallengeRequired())) {
                    challengeRequested = true;
                }
                // If challengeRequested is null (not checked), set to false
                else if (challengeRequested == null) {
                    challengeRequested = false;
                }
            } else {
                // Default to false if no enrollment found
                challengeRequested = challengeRequested != null ? challengeRequested : false;
            }
            
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
                // Reload form data for error display
                return reloadCreateFormData(model, enrollmentId);
            }
            
        } catch (Exception e) {
            model.addAttribute("error", "Failed to create authentication attempt: " + e.getMessage());
            // Reload form data for error display
            return reloadCreateFormData(model, enrollmentId);
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

    /**
     * Helper method to reload form data for error display.
     * 
     * @param model Spring model
     * @param selectedEnrollmentId the enrollment ID that was selected
     * @return template name
     */
    private String reloadCreateFormData(Model model, Integer selectedEnrollmentId) {
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
            
            // Find the selected enrollment if enrollmentId is provided
            EnrollmentResponseDto selectedEnrollment = null;
            if (selectedEnrollmentId != null) {
                selectedEnrollment = enrollments.stream()
                    .filter(e -> Objects.equals(e.getEnrollmentId(), selectedEnrollmentId))
                    .findFirst()
                    .orElse(null);
            }
            
            model.addAttribute("enrollments", enrollments);
            model.addAttribute("integrationNames", integrationNames);
            model.addAttribute("selectedEnrollmentId", selectedEnrollmentId);
            model.addAttribute("selectedEnrollment", selectedEnrollment);
            model.addAttribute("pageTitle", "Create Authentication Attempt");
            
        } catch (Exception e) {
            // Log error and show empty list
            System.err.println("Error reloading form data: " + e.getMessage());
            model.addAttribute("enrollments", new ArrayList<>());
            model.addAttribute("integrationNames", new HashMap<>());
            model.addAttribute("selectedEnrollmentId", selectedEnrollmentId);
            model.addAttribute("pageTitle", "Create Authentication Attempt");
        }
        
        return "auth-attempts/create";
    }
}
