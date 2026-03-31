/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptWaitResponse
 * Description: Response from waiting for an authentication attempt to complete.
 */

package org.ezkey.sdk;

/**
 * Response from the wait endpoint after polling for authentication attempt completion.
 *
 * @param status the authentication status (PENDING, READ, ACCEPTED, REJECTED, EXPIRED, INVALID)
 * @param completed whether the authentication process has reached a final state
 * @param timeoutReached whether the wait ended because the server-side timeout was reached
 * @since 2025
 */
public record AuthAttemptWaitResponse(String status, boolean completed, boolean timeoutReached) {}
