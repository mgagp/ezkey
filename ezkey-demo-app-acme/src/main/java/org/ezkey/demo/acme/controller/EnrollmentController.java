/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: EnrollmentController
 * Description: Web controller for enrollment management in ACME demo application.
 */

package org.ezkey.demo.acme.controller;

import java.util.List;

import org.ezkey.demo.acme.generated.dto.EnrollmentCreateRequestDto;
import org.ezkey.demo.acme.generated.dto.EnrollmentCreateResponseDto;
import org.ezkey.demo.acme.generated.dto.EnrollmentResponseDto;
import org.ezkey.demo.acme.service.EnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Web controller for enrollment management in ACME demo application.
 * <p>
 * This controller provides the web interface for managing device enrollments
 * through a Neo Brutalism styled user interface. It demonstrates the complete
 * enrollment lifecycle including creation, viewing, and deletion of enrollments
 * within the ACME demo environment.
 * </p>
 *
 * <p>
 * <b>Enrollment Management Features:</b>
 * <ul>
 * <li>List enrollments filtered by integration</li>
 * <li>Create new device enrollments with validation</li>
 * <li>View detailed enrollment information and status</li>
 * <li>Delete enrollments (demo cleanup)</li>
 * <li>Integration with authentication attempt creation</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Integration Flow:</b> Enrollments are created within the context of
 * specific integrations, allowing the demo to show how multiple applications
 * can have separate enrollment contexts while using the same Ezkey infrastructure.
 * </p>
 *
 * <p>
 * <b>UI Technology Stack:</b>
 * <ul>
 * <li><b>Templates:</b> Thymeleaf with Neo Brutalism styling</li>
 * <li><b>Interactivity:</b> htmx for dynamic form submission</li>
 * <li><b>Styling:</b> Custom CSS with bold, geometric design</li>
 * <li><b>Navigation:</b> Seamless integration with auth attempt workflow</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.demo.acme.service.EnrollmentService
 * @see org.ezkey.demo.acme.controller.AuthAttemptController
 * @see org.ezkey.demo.acme.controller.IntegrationController
 */
@Controller
@RequestMapping("/enrollments")
public class EnrollmentController {

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentController.class);

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    /**
     * Lists enrollments for a given integration.
     *
     * @param integrationId the integration identifier
     * @param model the view model
     * @return template name
     */
    @GetMapping
    public String listByIntegration(@RequestParam("integrationId") Integer integrationId, Model model) {
        logger.debug("Displaying enrollment list for integration: {}", integrationId);
        List<EnrollmentResponseDto> enrollments = enrollmentService.getEnrollmentsByIntegrationSync(integrationId);
        model.addAttribute("pageTitle", "Enrollments - ACME Inc");
        model.addAttribute("integrationId", integrationId);
        model.addAttribute("enrollments", enrollments);
        model.addAttribute("enrollmentsCount", enrollments != null ? enrollments.size() : 0);
        return "enrollments/list";
    }

    /**
     * Shows enrollment details.
     */
    @GetMapping("/{id}")
    public String details(@PathVariable("id") Integer enrollmentId, Model model) {
        logger.debug("Displaying enrollment details: {}", enrollmentId);
        var enrollment = enrollmentService.getEnrollmentById(enrollmentId).block();
        if (enrollment == null) {
            model.addAttribute("pageTitle", "Enrollment Not Found - ACME Inc");
            model.addAttribute("error", "Enrollment not found");
            return "integrations/error";
        }
        model.addAttribute("pageTitle", "Enrollment Details - ACME Inc");
        model.addAttribute("enrollment", enrollment);
        return "enrollments/details";
    }

    /**
     * Displays the enrollment creation form.
     *
     * @param integrationId the integration identifier to pre-fill
     * @param model the view model
     * @return template name
     */
    @GetMapping("/create")
    public String createForm(@RequestParam("integrationId") Integer integrationId, Model model) {
        logger.debug("Displaying enrollment creation form for integration: {}", integrationId);
        EnrollmentCreateRequestDto request = new EnrollmentCreateRequestDto();
        request.setIntegrationId(integrationId);
        request.setAuthAttemptChallengeRequired(Boolean.TRUE);
        model.addAttribute("pageTitle", "Create Enrollment - ACME Inc");
        model.addAttribute("enrollmentRequest", request);
        return "enrollments/create";
    }

    /**
     * Creates a new enrollment (future work - form to be added later).
     */
    @PostMapping
    public String create(@ModelAttribute EnrollmentCreateRequestDto request, RedirectAttributes redirectAttributes) {
        try {
            EnrollmentCreateResponseDto response = enrollmentService.createEnrollment(request).block();
            if (response != null) {
                redirectAttributes.addFlashAttribute("success", "Enrollment created successfully");
                return "redirect:/enrollments/" + response.getEnrollmentId();
            }
            redirectAttributes.addFlashAttribute("error", "Failed to create enrollment");
            return "redirect:/enrollments?integrationId=" + request.getIntegrationId();
        } catch (Exception e) {
            logger.error("Error creating enrollment", e);
            redirectAttributes.addFlashAttribute("error", "Failed to create enrollment: " + e.getMessage());
            return "redirect:/enrollments?integrationId=" + request.getIntegrationId();
        }
    }

    /**
     * Deletes an enrollment.
     */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Integer enrollmentId, @RequestParam("integrationId") Integer integrationId,
            RedirectAttributes redirectAttributes) {
        try {
            enrollmentService.deleteEnrollment(enrollmentId).block();
            redirectAttributes.addFlashAttribute("success", "Enrollment deleted successfully");
        } catch (Exception e) {
            logger.error("Error deleting enrollment {}", enrollmentId, e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete enrollment: " + e.getMessage());
        }
        return "redirect:/enrollments?integrationId=" + integrationId;
    }
}