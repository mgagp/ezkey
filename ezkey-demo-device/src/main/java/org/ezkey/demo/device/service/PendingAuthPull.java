/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.demo.device.service;

import java.time.Instant;
import org.ezkey.demodevice.generated.dto.AuthAttemptPendingResponseDto;

/**
 * Result of a successful Auth API pending pull for Demo Device.
 *
 * <p>{@code expiresAt} is taken from the pending JSON when present; otherwise {@code null} so the
 * controller can apply the local TTL fallback ({@link ClaimExpiresAtResolver}).
 *
 * @param response typed pending body (generated DTO; may omit {@code expiresAt})
 * @param expiresAt server absolute expiry from JSON, or {@code null} when absent
 * @since 2026
 */
public record PendingAuthPull(AuthAttemptPendingResponseDto response, Instant expiresAt) {}
