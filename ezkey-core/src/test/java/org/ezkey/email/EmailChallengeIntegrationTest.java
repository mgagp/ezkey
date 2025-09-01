/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EmailChallengeIntegrationTest
 * Description: Integration test for email challenge functionality in enrollment process.
 */

package org.ezkey.email;

import static org.junit.jupiter.api.Assertions.*;

import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.IntegrationI18n;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Integration test for email challenge functionality in enrollment process.
 * <p>
 * This test verifies that the email challenge feature works correctly when enabled,
 * including email generation, challenge creation, and integration with the enrollment
 * service. Uses simulation mode to avoid actual email sending during tests.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@SpringBootTest
@TestPropertySource(properties = {
    "ezkey.enrollment.email-challenge.enabled=true",
    "ezkey.enrollment.email-challenge.simulate=true"
})
@Transactional
public class EmailChallengeIntegrationTest {

    @Autowired
    private EmailService emailService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private IntegrationRepository integrationRepository;

    @Test
    public void testEmailChallengeEnabledConfiguration() {
        assertTrue(emailService.isEmailChallengeEnabled(), 
            "Email challenge should be enabled in test configuration");
    }

    @Test
    public void testEnrollmentCreationWithEmailChallenge() {
        // Create test integration
        Integration integration = createTestIntegration();
        
        // Create enrollment request with email
        EnrollmentCreateRequest request = new EnrollmentCreateRequest();
        request.setIntegrationId(integration.getId());
        request.setName("Test Email Enrollment");
        request.setEmail("test@example.com");
        request.setAuthAttemptChallengeRequired(false);
        
        // Create enrollment - should generate email challenge and send email
        EnrollmentCreateResponse response = enrollmentService.create(request);
        
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getEnrollmentId(), "Enrollment ID should be generated");
        assertNotNull(response.getEnrollmentChallenge(), "Regular challenge should be generated");
        
        // Verify enrollment was created with email data
        var enrollment = enrollmentService.getById(response.getEnrollmentId());
        assertNotNull(enrollment, "Enrollment should be created");
        assertEquals("test@example.com", enrollment.getEnrollmentEmail(), "Email should be stored");
        assertNotNull(enrollment.getEnrollmentChallengeEmail(), "Email challenge should be generated");
        assertEquals(6, enrollment.getEnrollmentChallengeEmail().toString().length(), 
            "Email challenge should be 6 digits");
    }

    @Test
    public void testEnrollmentCreationWithoutEmailWhenEnabled() {
        // Create test integration
        Integration integration = createTestIntegration();
        
        // Create enrollment request without email when email challenge is enabled
        EnrollmentCreateRequest request = new EnrollmentCreateRequest();
        request.setIntegrationId(integration.getId());
        request.setName("Test No Email Enrollment");
        // No email set
        request.setAuthAttemptChallengeRequired(false);
        
        // Should throw exception since email is required when email challenge is enabled
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> enrollmentService.create(request));
        
        assertTrue(exception.getMessage().contains("Email address is required"), 
            "Should require email when email challenge is enabled");
    }

    @Test
    public void testEmailServiceSendChallenge() {
        // Test email service directly
        assertDoesNotThrow(() -> {
            emailService.sendEnrollmentChallenge(
                "test@example.com", 
                "Test Enrollment", 
                123456, 
                "Test Integration"
            );
        }, "Email service should not throw exception in simulation mode");
    }

    private Integration createTestIntegration() {
        Integration integration = new Integration();
        integration.setActive(true);
        integration.setCreatedAt(LocalDateTime.now());
        integration.setLogo("test-logo.png");
        integration.setI18n(new ArrayList<>());
        
        IntegrationI18n i18n = new IntegrationI18n();
        i18n.setLanguage("en");
        i18n.setName("Test Integration");
        i18n.setDescription("Test integration for email challenge testing");
        i18n.setIntegration(integration);
        integration.getI18n().add(i18n);
        
        return integrationRepository.save(integration);
    }
}