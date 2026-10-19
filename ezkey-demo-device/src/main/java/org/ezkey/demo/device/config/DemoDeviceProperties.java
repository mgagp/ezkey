/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Config: DemoDeviceProperties
 * Description: Demo-device allowlist and related lab-only settings (CodeQL #9/#10, 2026-10-13).
 */

package org.ezkey.demo.device.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration for the demo device lab simulator.
 *
 * @since 2026
 */
@ConfigurationProperties(prefix = "ezkey.demo-device")
public class DemoDeviceProperties {

  /**
   * Hostnames allowed as Auth API targets for QR {@code authUrl} and bind overrides. Empty means
   * derive the default allowlist from {@code ezkey.auth.api.url} plus {@code localhost} and {@code
   * 127.0.0.1}.
   */
  private List<String> allowedAuthApiHosts = new ArrayList<>();

  /**
   * Fallback duration for claim {@code expiresAt} when the pending JSON has no server {@code
   * expiresAt} (#834 / #895). Prefer the server field when present. Default matches {@code
   * ezkey.core.auth-attempt.ttl-seconds} (120). Lower this when initiator TTLs are shorter (e.g.
   * PAM 90 s) to avoid a stale Approve/Deny window; raise it when the Auth API TTL is higher, or
   * Approve/Deny may disappear while the attempt is still valid (read-once). Cap remains {@link
   * org.ezkey.demo.device.service.PendingAuthClaimSession#DEFAULT_TTL} (10 minutes).
   */
  private Duration claimedPendingAttemptTtl = Duration.ofSeconds(120);

  /**
   * Returns the configured host allowlist (may be empty when defaults should apply).
   *
   * @return mutable list bound by Spring (never {@code null} after construction)
   */
  public List<String> getAllowedAuthApiHosts() {
    return allowedAuthApiHosts;
  }

  /**
   * Sets the host allowlist from configuration.
   *
   * @param allowedAuthApiHosts hostnames only (no scheme or port)
   */
  public void setAllowedAuthApiHosts(List<String> allowedAuthApiHosts) {
    this.allowedAuthApiHosts =
        allowedAuthApiHosts != null ? new ArrayList<>(allowedAuthApiHosts) : new ArrayList<>();
  }

  /**
   * Returns the session claim expiry bound for a successful pending pull.
   *
   * @return duration added to {@code claimedAt} to form {@code expiresAt}
   */
  public Duration getClaimedPendingAttemptTtl() {
    return claimedPendingAttemptTtl;
  }

  /**
   * Sets the session claim expiry bound.
   *
   * @param claimedPendingAttemptTtl duration aligned with Auth API auth-attempt TTL; {@code null}
   *     resets to 120 seconds
   */
  public void setClaimedPendingAttemptTtl(Duration claimedPendingAttemptTtl) {
    this.claimedPendingAttemptTtl =
        claimedPendingAttemptTtl != null ? claimedPendingAttemptTtl : Duration.ofSeconds(120);
  }
}
