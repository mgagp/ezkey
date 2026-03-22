/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License.
 *
 * See LICENSE file in the project root for full license information.
 *
 * Enum: ApiName Description: Enumeration of API names for audit log source identification.
 */

package org.ezkey.audit.domain;

/**
 * Enumeration of API names.
 *
 * <p>Identifies which API originated an audit event for security monitoring and analysis.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public enum ApiName {
  /** Admin API (port 9080) - internal administration. */
  ADMIN_API,

  /** Auth API (port 8080) - mobile device operations. */
  AUTH_API,

  /** Integration API (port 7080) - API key authentication for integrated applications. */
  INTEGRATION_API
}
