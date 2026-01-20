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

/**
 * Response DTO containing created authentication attempt details.
 *
 * @param authAttemptId unique identifier of the created authentication attempt
 */
public record AuthAttemptCreateResponse(@JsonProperty("authAttemptId") Integer authAttemptId) {}