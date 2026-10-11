/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: BootstrapCredentialsOutputMode
 * Description: Controls what global-admin bootstrap emits to logs and optional file export.
 */

package org.ezkey.admin.config;

/**
 * Controls how initial global-admin bootstrap credentials are surfaced at startup.
 *
 * <p><b>full</b> — Enrollment proof token, challenge, and ASCII QR appear in startup logs (by
 * design for the initial enrollment wizard). Recovery codes are never logged; they are written to
 * {@code bootstrap-credentials.json} ({@code 0600}) when export runs.
 *
 * <p><b>recovery_primary</b> — Omits enrollment proof token, challenge, and ASCII QR from logs.
 * Recovery codes are written to {@code bootstrap-credentials.json} ({@code 0600}) only (no
 * enrollment secrets in the file). Operators enroll via the recovery funnel.
 *
 * @since 2026
 */
public enum BootstrapCredentialsOutputMode {
  /** Default: full enrollment material for local dev and clean-start automation. */
  FULL,

  /**
   * Recovery-first bootstrap: no enrollment proof token, challenge, or ASCII QR in logs; recovery
   * codes go to the credentials file only.
   */
  RECOVERY_PRIMARY
}
