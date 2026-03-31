/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Factory: AdminAuthAttemptWaitRequestFactory
 * Description: Builds {@link org.ezkey.authattempt.domain.AuthAttemptWaitRequest} for admin
 *     passwordless flows, aligned with persisted auth attempt TTL.
 */

package org.ezkey.admin.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.service.AuthAttemptWaitService;

/**
 * Builds {@link AuthAttemptWaitRequest} values for admin passwordless login and wait endpoints.
 *
 * <p>Timeouts are derived from {@code ezkey.core.auth-attempt.ttl-seconds} (or remaining time until
 * {@link AuthAttempt#getExpiresAt()}) plus a small slack, capped at 300 seconds — the maximum
 * accepted by {@link AuthAttemptWaitService#waitForResponse}.
 *
 * <p><b>Operator note:</b> If {@code ttl-seconds} is set above 300 (allowed up to 600 in core
 * configuration), the wait loop may return HTTP 408 while the attempt row is still valid in the
 * database. In that case, clients should retry or poll status by ID.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public final class AdminAuthAttemptWaitRequestFactory {

  /**
   * Polling interval for {@link AuthAttemptWaitService} (seconds). Must be {@code <=} wait timeout.
   */
  public static final int POLLING_SECONDS = 2;

  /** Maximum wait duration accepted by {@link AuthAttemptWaitService} validation (seconds). */
  public static final int TIMEOUT_MAX_SECONDS = 300;

  /**
   * Extra seconds added to TTL or remaining time so the wait loop does not stop just before DB
   * expiry. Capped with {@link #TIMEOUT_MAX_SECONDS}.
   */
  public static final int TIMEOUT_SLACK_SECONDS = 15;

  private AdminAuthAttemptWaitRequestFactory() {}

  /**
   * Wait request right after {@link org.ezkey.authattempt.service.AuthAttemptService#create}:
   * {@code min(300, ttlSeconds + slack)}.
   *
   * @param ttlSeconds from {@link org.ezkey.authattempt.domain.AuthAttemptCreateResponse
   *     #getTimeoutSeconds()} — null or non-positive falls back to {@link #TIMEOUT_MAX_SECONDS}
   */
  public static AuthAttemptWaitRequest forNewAttempt(Integer ttlSeconds) {
    int ttl = ttlSeconds != null && ttlSeconds > 0 ? ttlSeconds : TIMEOUT_MAX_SECONDS;
    int timeout = Math.min(TIMEOUT_MAX_SECONDS, Math.max(1, ttl + TIMEOUT_SLACK_SECONDS));
    if (timeout < POLLING_SECONDS) {
      timeout = POLLING_SECONDS;
    }
    return new AuthAttemptWaitRequest(timeout, POLLING_SECONDS);
  }

  /**
   * Wait request for {@code /passwordless-wait}: remaining time until {@link
   * AuthAttempt#getExpiresAt()} plus slack, capped at {@link #TIMEOUT_MAX_SECONDS}.
   */
  public static AuthAttemptWaitRequest forLoadedAttempt(AuthAttempt authAttempt) {
    OffsetDateTime expiresAt = authAttempt.getExpiresAt();
    if (expiresAt == null) {
      return new AuthAttemptWaitRequest(TIMEOUT_MAX_SECONDS, POLLING_SECONDS);
    }
    long remaining = Duration.between(OffsetDateTime.now(), expiresAt).getSeconds();
    int timeout =
        (int) Math.min(TIMEOUT_MAX_SECONDS, Math.max(1, remaining + TIMEOUT_SLACK_SECONDS));
    if (timeout < POLLING_SECONDS) {
      timeout = POLLING_SECONDS;
    }
    return new AuthAttemptWaitRequest(timeout, POLLING_SECONDS);
  }
}
