/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptWaitResponse
 * Description: Response DTO from waiting for auth attempt completion.
 */

package org.ezkey.demo.acme.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for authentication wait operation.
 *
 * @param status calculated authentication status (PENDING, READ, INVALID, REJECTED, ACCEPTED)
 * @param completed whether authentication process is complete
 * @param timeoutReached whether wait ended due to timeout
 */
public record AuthAttemptWaitResponse(
    String status,
    @JsonProperty("completed") Boolean completed,
    @JsonProperty("timeoutReached") Boolean timeoutReached) {}