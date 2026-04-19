/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.domain;

/**
 * Explicit lifecycle states for integrations.
 *
 * <p>The integration lifecycle is modeled as a two-state enum: {@code ACTIVE} integrations
 * participate in day-to-day operations (API key validation, enrollment creation). {@code RETIRED}
 * integrations are removed from normal operations while their historical data is preserved.
 */
public enum IntegrationLifecycleStatus {
  /** Integration is operational and may be used for day-to-day flows. */
  ACTIVE,

  /** Integration is retired from day-to-day use but retained for historical traceability. */
  RETIRED
}
