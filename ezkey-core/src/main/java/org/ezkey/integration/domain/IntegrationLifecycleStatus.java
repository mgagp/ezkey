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
 * <p>The integration lifecycle is modeled as a single enum instead of multiple boolean flags to
 * preserve conceptual integrity. Operators can distinguish between integrations that are currently
 * usable, temporarily unavailable, or retired from day-to-day operations while preserving their
 * historical footprint.
 */
public enum IntegrationLifecycleStatus {
  /** Integration is operational and may be used for day-to-day flows. */
  ACTIVE,

  /** Integration exists but is temporarily unavailable for normal operations. */
  INACTIVE,

  /** Integration is retired from day-to-day use but retained for historical traceability. */
  RETIRED
}
