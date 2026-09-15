/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Catalog: IntegrationApiProblemCatalog
 * Description: Stable RFC 9457 problem types for Integration API degradation scenarios.
 */

package org.ezkey.integration.api.exception;

/**
 * Stable problem {@code type} URIs for Integration API responses where Auth API catalog types do
 * not apply.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public final class IntegrationApiProblemCatalog {

  /** Shared with Auth API — audit-chain heartbeat supervision stalled. */
  public static final String TYPE_AUDIT_CHAIN_HEARTBEAT_DEGRADED =
      "https://ezkey.io/problems/system/audit-chain-heartbeat-degraded";

  public static final String TYPE_METHOD_NOT_ALLOWED =
      "https://ezkey.io/problems/http/method-not-allowed";

  public static final String TITLE_SERVICE_UNAVAILABLE = "Service unavailable";
  public static final String TITLE_METHOD_NOT_ALLOWED = "Method not allowed";

  public static final String DETAIL_METHOD_NOT_ALLOWED =
      "The HTTP method is not allowed for this resource.";

  public static final String DETAIL_AUDIT_CHAIN_HEARTBEAT_DEGRADED =
      "The service is temporarily unavailable because audit-chain supervision cannot confirm "
          + "checkpoint progress. Retry later.";

  private IntegrationApiProblemCatalog() {}
}
