package org.ezkey.demo.acme.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Controller for managing authentication attempts in the ACME demo application.
 * 
 * @since 2025
 */
@Controller
@RequestMapping("/auth-attempts")
public class AuthAttemptController {

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
        
        // Mock data for demonstration
        List<Map<String, Object>> authAttempts = generateMockAuthAttempts();
        
        // Apply filters if provided
        if (enrollmentId != null) {
            authAttempts = authAttempts.stream()
                    .filter(attempt -> enrollmentId.equals(attempt.get("enrollmentId")))
                    .toList();
        }
        
        if (integrationId != null) {
            authAttempts = authAttempts.stream()
                    .filter(attempt -> integrationId.equals(attempt.get("integrationId")))
                    .toList();
        }
        
        model.addAttribute("authAttempts", authAttempts);
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
        
        // Mock data for demonstration
        Map<String, Object> authAttempt = generateMockAuthAttempt(id);
        
        if (authAttempt == null) {
            model.addAttribute("error", "Authentication attempt not found");
            return "error";
        }
        
        model.addAttribute("authAttempt", authAttempt);
        model.addAttribute("pageTitle", "Authentication Attempt Details");
        
        return "auth-attempts/details";
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
        
        // Mock enrollments for dropdown
        List<Map<String, Object>> enrollments = generateMockEnrollments();
        
        model.addAttribute("enrollments", enrollments);
        model.addAttribute("selectedEnrollmentId", enrollmentId);
        model.addAttribute("pageTitle", "Create Authentication Attempt");
        
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
        
        // Mock creation - in real implementation, this would call the Ezkey API
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
        
        // Mock deletion - in real implementation, this would call the Ezkey API
        System.out.println("Deleting auth attempt " + id);
        
        // Redirect back to the appropriate list
        if (enrollmentId != null) {
            return "redirect:/auth-attempts?enrollmentId=" + enrollmentId;
        } else {
            return "redirect:/auth-attempts";
        }
    }

    /**
     * Generate mock authentication attempts for demonstration.
     * 
     * @return list of mock auth attempts
     */
    private List<Map<String, Object>> generateMockAuthAttempts() {
        List<Map<String, Object>> attempts = new ArrayList<>();
        
        // Mock data with various statuses
        attempts.add(createMockAuthAttempt(1, 1, 1, "PENDING", LocalDateTime.now().minusMinutes(5)));
        attempts.add(createMockAuthAttempt(2, 1, 1, "APPROVED", LocalDateTime.now().minusMinutes(30)));
        attempts.add(createMockAuthAttempt(3, 1, 2, "DENIED", LocalDateTime.now().minusHours(1)));
        attempts.add(createMockAuthAttempt(4, 2, 3, "PENDING", LocalDateTime.now().minusMinutes(2)));
        attempts.add(createMockAuthAttempt(5, 2, 3, "APPROVED", LocalDateTime.now().minusHours(2)));
        
        return attempts;
    }

    /**
     * Generate a mock authentication attempt.
     * 
     * @param id auth attempt ID
     * @param integrationId integration ID
     * @param enrollmentId enrollment ID
     * @param status status
     * @param createdAt creation timestamp
     * @return mock auth attempt
     */
    private Map<String, Object> createMockAuthAttempt(Integer id, Integer integrationId, Integer enrollmentId, 
                                                     String status, LocalDateTime createdAt) {
        Map<String, Object> attempt = new HashMap<>();
        attempt.put("id", id);
        attempt.put("integrationId", integrationId);
        attempt.put("enrollmentId", enrollmentId);
        attempt.put("status", status);
        attempt.put("createdAt", createdAt);
        attempt.put("challenge", "123456");
        attempt.put("challengeRequired", true);
        attempt.put("integrationName", "ACME Admin Portal");
        attempt.put("enrollmentName", "John's iPhone");
        
        return attempt;
    }

    /**
     * Generate a specific mock authentication attempt by ID.
     * 
     * @param id auth attempt ID
     * @return mock auth attempt or null if not found
     */
    private Map<String, Object> generateMockAuthAttempt(Integer id) {
        return generateMockAuthAttempts().stream()
                .filter(attempt -> id.equals(attempt.get("id")))
                .findFirst()
                .orElse(null);
    }

    /**
     * Generate mock enrollments for dropdown selection.
     * 
     * @return list of mock enrollments
     */
    private List<Map<String, Object>> generateMockEnrollments() {
        List<Map<String, Object>> enrollments = new ArrayList<>();
        
        Map<String, Object> enrollment1 = new HashMap<>();
        enrollment1.put("enrollmentId", 1);
        enrollment1.put("integrationId", 1);
        enrollment1.put("enrollmentName", "John's iPhone");
        enrollment1.put("integrationName", "ACME Admin Portal");
        enrollment1.put("enrollmentActive", true);
        enrollments.add(enrollment1);
        
        Map<String, Object> enrollment2 = new HashMap<>();
        enrollment2.put("enrollmentId", 2);
        enrollment2.put("integrationId", 1);
        enrollment2.put("enrollmentName", "Jane's Android");
        enrollment2.put("integrationName", "ACME Admin Portal");
        enrollment2.put("enrollmentActive", true);
        enrollments.add(enrollment2);
        
        Map<String, Object> enrollment3 = new HashMap<>();
        enrollment3.put("enrollmentId", 3);
        enrollment3.put("integrationId", 2);
        enrollment3.put("enrollmentName", "Bob's iPad");
        enrollment3.put("integrationName", "ACME Customer Portal");
        enrollment3.put("enrollmentActive", true);
        enrollments.add(enrollment3);
        
        return enrollments;
    }
}
