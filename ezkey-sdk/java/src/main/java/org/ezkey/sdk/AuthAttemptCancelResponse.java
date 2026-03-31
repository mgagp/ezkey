/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptCancelResponse
 * Description: Response from cancelling an authentication attempt.
 */

package org.ezkey.sdk;

/**
 * Response from cancelling a pending authentication attempt.
 *
 * @param authAttemptId the ID of the cancelled authentication attempt
 * @param authAttemptStatus the final status after cancellation (typically EXPIRED)
 * @since 2025
 */
public record AuthAttemptCancelResponse(int authAttemptId, String authAttemptStatus) {}
