/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EmailService
 * Description: Service for sending enrollment email challenges with simulation support.
 */

package org.ezkey.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Service for sending enrollment email challenges with simulation support.
 * <p>
 * This service handles sending email challenges during the enrollment process.
 * It supports both real email sending and simulation mode for testing and
 * development purposes. When simulation mode is enabled, emails are logged
 * instead of being sent to actual recipients.
 * </p>
 *
 * <p>
 * <b>Configuration:</b>
 * <ul>
 * <li><b>ezkey.enrollment.email-challenge.enabled:</b> Enables/disables email challenge feature</li>
 * <li><b>ezkey.enrollment.email-challenge.simulate:</b> Uses simulation mode instead of real emails</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 * <p>
 * <b>Usage:</b> Email enrollment challenge operations
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${ezkey.enrollment.email-challenge.enabled:false}")
    private boolean emailChallengeEnabled;

    @Value("${ezkey.enrollment.email-challenge.simulate:true}")
    private boolean simulateEmail;

    /**
     * Checks if email challenge feature is enabled.
     *
     * @return true if email challenge is enabled, false otherwise
     */
    public boolean isEmailChallengeEnabled() {
        return emailChallengeEnabled;
    }

    /**
     * Sends an enrollment email challenge to the specified email address.
     * <p>
     * This method sends an email containing the enrollment challenge information
     * to help verify the user's identity during the enrollment process. In
     * simulation mode, the email content is logged instead of being sent.
     * </p>
     *
     * @param emailAddress the recipient email address
     * @param enrollmentName the name of the enrollment
     * @param challenge the 6-digit email challenge code
     * @param integrationName the name of the integration
     * @throws IllegalArgumentException if required parameters are null or empty
     */
    public void sendEnrollmentChallenge(String emailAddress, String enrollmentName, Integer challenge, String integrationName) {
        if (emailAddress == null || emailAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required");
        }
        if (challenge == null) {
            throw new IllegalArgumentException("Challenge is required");
        }
        if (enrollmentName == null || enrollmentName.trim().isEmpty()) {
            throw new IllegalArgumentException("Enrollment name is required");
        }

        try {
            String emailContent = loadEmailTemplate(enrollmentName, challenge, integrationName);
            
            if (simulateEmail) {
                log.info("=== EMAIL SIMULATION MODE ===");
                log.info("To: {}", emailAddress);
                log.info("Subject: Ezkey Enrollment Challenge - {}", integrationName != null ? integrationName : "Unknown Integration");
                log.info("Body:\n{}", emailContent);
                log.info("=== END EMAIL SIMULATION ===");
            } else {
                // TODO: Implement real email sending when needed
                // For now, we'll use simulation mode as the default implementation
                log.warn("Real email sending not implemented yet. Using simulation mode.");
                log.info("Email challenge sent to: {} for enrollment: {} with challenge: {}", 
                    emailAddress, enrollmentName, challenge);
            }
        } catch (Exception e) {
            log.error("Failed to send enrollment email challenge to: {}", emailAddress, e);
            throw new RuntimeException("Failed to send enrollment email challenge", e);
        }
    }

    /**
     * Loads and processes the email template for enrollment challenges.
     *
     * @param enrollmentName the name of the enrollment
     * @param challenge the 6-digit challenge code
     * @param integrationName the name of the integration
     * @return the processed email content
     * @throws IOException if template loading fails
     */
    private String loadEmailTemplate(String enrollmentName, Integer challenge, String integrationName) throws IOException {
        try {
            ClassPathResource resource = new ClassPathResource("email-templates/enrollment-challenge.txt");
            String template = resource.getContentAsString(StandardCharsets.UTF_8);
            
            // Replace template variables
            return template
                .replace("{{enrollmentName}}", enrollmentName != null ? enrollmentName : "Unknown Enrollment")
                .replace("{{challenge}}", challenge.toString())
                .replace("{{integrationName}}", integrationName != null ? integrationName : "Unknown Integration");
        } catch (IOException e) {
            log.warn("Failed to load email template, using fallback content", e);
            // Fallback template
            return String.format("""
                Subject: Ezkey Enrollment Challenge - %s
                
                Hello,
                
                You are enrolling a new device for %s in the Ezkey MFA system.
                
                Your enrollment challenge code is: %s
                
                Please enter this 6-digit code in your device to complete the enrollment process.
                
                If you did not initiate this enrollment, please ignore this email.
                
                Best regards,
                Ezkey MFA System
                """, 
                integrationName != null ? integrationName : "Unknown Integration",
                enrollmentName != null ? enrollmentName : "Unknown Enrollment",
                challenge);
        }
    }
}