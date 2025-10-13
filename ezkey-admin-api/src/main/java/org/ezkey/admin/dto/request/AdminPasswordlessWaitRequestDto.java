/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminPasswordlessWaitRequestDto
 * Description: Request DTO for waiting for passwordless authentication completion.
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for waiting for passwordless authentication completion.
 * <p>
 * Used in the two-step passwordless flow when challenge verification is required.
 * The client first calls /login with challengeRequested=true, receives the
 * authAttemptId and challengeCode, displays the challenge to the user, then
 * calls this endpoint to wait for device approval.
 * </p>
 * <p>
 * <b>Security:</b> The challengeCode acts as proof that the client legitimately
 * initiated the authentication request, preventing enumeration attacks on
 * authAttemptId. Only the client that received the challenge from /login can proceed.
 * </p>
 * <p>
 * <b>Anti-Enumeration Protection:</b>
 * Without the challengeCode requirement, an attacker could enumerate authAttemptId
 * values (1, 2, 3...) and attempt to hijack ongoing authentication attempts.
 * The challengeCode provides cryptographic proof of legitimacy with 1/1,000,000
 * probability of guessing (6 digits).
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
 */
public class AdminPasswordlessWaitRequestDto {

    /**
     * Authentication attempt ID from the login response.
     * <p>
     * This ID identifies the specific authentication attempt created
     * during the passwordless login call.
     * </p>
     */
    @NotNull(message = "Auth attempt ID is required")
    private Integer authAttemptId;

    /**
     * Challenge code from the login response.
     * <p>
     * This code proves that the client legitimately initiated the authentication
     * and prevents enumeration attacks. Must match the challenge code returned
     * in the login response.
     * </p>
     * <p>
     * The challenge code is a 6-digit number (000000-999999) providing 1/1,000,000
     * protection against guessing attacks.
     * </p>
     */
    @NotNull(message = "Challenge code is required for verification")
    private Integer challengeCode;

    /**
     * Default constructor for JSON deserialization.
     */
    public AdminPasswordlessWaitRequestDto() {
        // Default constructor
    }

    /**
     * Constructs a new passwordless wait request.
     *
     * @param authAttemptId the authentication attempt ID
     * @param challengeCode the challenge code for verification
     */
    public AdminPasswordlessWaitRequestDto(Integer authAttemptId, Integer challengeCode) {
        this.authAttemptId = authAttemptId;
        this.challengeCode = challengeCode;
    }

    /**
     * Gets the authentication attempt ID.
     *
     * @return the auth attempt ID
     */
    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    /**
     * Sets the authentication attempt ID.
     *
     * @param authAttemptId the auth attempt ID
     */
    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    /**
     * Gets the challenge code.
     *
     * @return the challenge code
     */
    public Integer getChallengeCode() {
        return challengeCode;
    }

    /**
     * Sets the challenge code.
     *
     * @param challengeCode the challenge code
     */
    public void setChallengeCode(Integer challengeCode) {
        this.challengeCode = challengeCode;
    }

    /**
     * Returns a string representation of the passwordless wait request.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "AdminPasswordlessWaitRequestDto{" +
                "authAttemptId=" + authAttemptId +
                ", challengeCode=[PROTECTED]" +
                '}';
    }
}

