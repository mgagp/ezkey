/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: IntegrityBootstrapService
 * Description: Builds the thin Integrity atelier bootstrap payload.
 */
package org.ezkey.admin.service;

import org.ezkey.admin.dto.response.IntegrityBootstrapResponseDto;
import org.ezkey.audit.integrity.AuditChainProperties;
import org.ezkey.audit.integrity.NightlyIntegrityProperties;
import org.springframework.stereotype.Service;

/**
 * Builds the thin Integrity atelier bootstrap used by Admin UI honesty chrome.
 *
 * <p>Keeps {@code runtimeProfile} (Spring profile → product label) separate from monitoring enable
 * flags (config). Do not invent a job matrix or second journal here.
 *
 * @since 2026
 */
@Service
public class IntegrityBootstrapService {

  private final EzkeyRuntimeProfileResolver runtimeProfileResolver;
  private final AuditChainProperties auditChainProperties;
  private final NightlyIntegrityProperties nightlyIntegrityProperties;

  /**
   * Constructs the bootstrap service.
   *
   * @param runtimeProfileResolver product runtime profile resolver
   * @param auditChainProperties rolling checkpoint configuration
   * @param nightlyIntegrityProperties nightly validation configuration
   */
  public IntegrityBootstrapService(
      EzkeyRuntimeProfileResolver runtimeProfileResolver,
      AuditChainProperties auditChainProperties,
      NightlyIntegrityProperties nightlyIntegrityProperties) {
    this.runtimeProfileResolver = runtimeProfileResolver;
    this.auditChainProperties = auditChainProperties;
    this.nightlyIntegrityProperties = nightlyIntegrityProperties;
  }

  /**
   * Returns the Integrity bootstrap snapshot for the current Admin API process.
   *
   * @return runtime profile plus chain/nightly enable flags
   */
  public IntegrityBootstrapResponseDto build() {
    return new IntegrityBootstrapResponseDto(
        runtimeProfileResolver.resolve(),
        auditChainProperties.isEnabled(),
        nightlyIntegrityProperties.isEnabled());
  }
}
