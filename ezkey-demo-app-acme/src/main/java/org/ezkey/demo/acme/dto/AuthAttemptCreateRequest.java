/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptCreateRequest
 * Description: Request DTO for creating an auth attempt via Admin API.
 */

package org.ezkey.demo.acme.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for creating an authentication attempt.
 *
 * @param enrollmentId the enrollment ID for which the authentication attempt is requested
 * @param challengeRequested indicates whether a challenge is requested
 */
public record AuthAttemptCreateRequest(
    @JsonProperty("enrollmentId") Integer enrollmentId,
    @JsonProperty("challengeRequested") Boolean challengeRequested) {}
