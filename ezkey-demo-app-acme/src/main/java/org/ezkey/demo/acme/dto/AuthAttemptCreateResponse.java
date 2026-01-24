/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptCreateResponse
 * Description: Response DTO from creating an auth attempt.
 */

package org.ezkey.demo.acme.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * Response DTO containing created authentication attempt details.
 *
 * @param authAttemptId unique identifier of the created authentication attempt
 * @param authAttemptChallenge optional challenge code (2 digits) if challenge was requested
 * @param timeoutSeconds maximum time in seconds the user has to respond to the authentication
 *     request
 * @param expiresAt absolute expiration timestamp when the authentication attempt will expire
 */
public record AuthAttemptCreateResponse(
    @JsonProperty("authAttemptId") Integer authAttemptId,
    @JsonProperty("authAttemptChallenge") Integer authAttemptChallenge,
    @JsonProperty("timeoutSeconds") Integer timeoutSeconds,
    @JsonProperty("expiresAt") OffsetDateTime expiresAt) {}
