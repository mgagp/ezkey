/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: SystemIntegrationRevocationException
 * Description: Thrown when a bulk revocation operation targets a system integration,
 *              which is not permitted to protect administrator MFA infrastructure.
 */

package org.ezkey.admin.exception;

/**
 * Exception thrown when a bulk revocation operation targets a system integration.
 *
 * <p>System integrations ({@code is_system_integration = true}) host all administrator MFA
 * enrollments. Allowing bulk revocation on a system integration would simultaneously revoke all
 * admin accounts, causing a complete system lockout. This protection is a hard safety gate with no
 * override.
 *
 * <p>Individual enrollment revocation within a system integration is still permitted, subject to
 * the self-revocation guard.
 *
 * <p><b>HTTP Status:</b> 403 Forbidden
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI {@code
 * https://ezkey.io/problems/enrollment/system-integration-revocation}.
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 *   "type": "https://ezkey.io/problems/enrollment/system-integration-revocation",
 *   "title": "System Integration Revocation Not Allowed",
 *   "status": 403,
 *   "detail": "Bulk revocation cannot be applied to a system integration.",
 *   "path": "/api/v1/integrations/1/enrollments/revoke-all"
 * }
 * }</pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class SystemIntegrationRevocationException extends RuntimeException {

  /**
   * Constructs a SystemIntegrationRevocationException with the specified detail message.
   *
   * @param message the detail message
   */
  public SystemIntegrationRevocationException(String message) {
    super(message);
  }

  /**
   * Constructs a SystemIntegrationRevocationException with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public SystemIntegrationRevocationException(String message, Throwable cause) {
    super(message, cause);
  }
}
