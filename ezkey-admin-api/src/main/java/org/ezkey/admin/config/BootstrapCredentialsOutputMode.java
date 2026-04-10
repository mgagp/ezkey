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
 * <p><b>full</b> — Enrollment proof token, challenge, ASCII QR, recovery codes, and CLI hints (for
 * dev/demo and Docker automation via {@code bootstrap-credentials.json}).
 *
 * <p><b>recovery_primary</b> — Recovery codes and operator instructions only; omits enrollment
 * secrets and ASCII QR from logs and skips bootstrap JSON file export (production-oriented).
 *
 * @since 2026
 */
public enum BootstrapCredentialsOutputMode {
  /** Default: full enrollment material for local dev and clean-start automation. */
  FULL,

  /**
   * Recovery-first bootstrap: plaintext recovery codes and guidance; no enrollment proof token,
   * challenge, or ASCII QR in logs; no {@code bootstrap-credentials.json} export.
   */
  RECOVERY_PRIMARY
}
