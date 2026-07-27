/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AuditChainHeartbeatDegradedException
 * Description: Peripheral API refuses work because Admin chain checkpoints appear stalled.
 */

package org.ezkey.exception.audit;

/**
 * Raised when peripheral APIs enforce fail-closed behavior due to checkpoint heartbeat degradation.
 *
 * <p>Mapped to HTTP 503 by API-specific exception handlers with RFC 9457 {@link
 * org.springframework.http.ProblemDetail}.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@SuppressWarnings("serial")
public class AuditChainHeartbeatDegradedException extends RuntimeException {

  /**
   * Constructs without message leakage (handlers supply safe Problem Details).
   *
   * @since 2026
   */
  public AuditChainHeartbeatDegradedException() {
    super("Audit chain heartbeat degraded");
  }
}
