/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptCreateResponse
 * Description: Response from creating an authentication attempt.
 */

package org.ezkey.sdk;

/**
 * Response from creating an authentication attempt via the Admin API.
 *
 * @param authAttemptId unique identifier of the created authentication attempt
 * @param authAttemptChallenge optional challenge code (2 digits), {@code null} if not requested
 * @param timeoutSeconds maximum time in seconds the user has to respond
 * @param expiresAt ISO-8601 expiration timestamp (kept as String to avoid date-time dependencies)
 * @since 2025
 */
public record AuthAttemptCreateResponse(
    int authAttemptId, Integer authAttemptChallenge, int timeoutSeconds, String expiresAt) {}
