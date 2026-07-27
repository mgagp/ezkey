/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: IntegrationCodeAlreadyExistsException
 * Description: Thrown when attempting to create an integration with a code that already exists for the tenant.
 */

package org.ezkey.integration.exception;

/**
 * Exception thrown when attempting to create an integration with a code that already exists for the
 * tenant.
 *
 * <p>This exception is raised during integration creation if the provided code is already used by
 * another integration in the same tenant. Codes must be unique per tenant to prevent duplicates.
 *
 * <p><b>Handling:</b> This exception should be caught and mapped to an HTTP 409 Conflict status
 * code by the global exception handler.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@SuppressWarnings("serial")
public class IntegrationCodeAlreadyExistsException extends RuntimeException {

  private final String code;
  private final String tenantName;

  /**
   * Constructs a new IntegrationCodeAlreadyExistsException with the specified code and tenant name.
   *
   * @param code the integration code that already exists
   * @param tenantName the name of the tenant where the code already exists
   */
  public IntegrationCodeAlreadyExistsException(String code, String tenantName) {
    super(
        String.format(
            "Integration with code '%s' already exists for tenant '%s'", code, tenantName));
    this.code = code;
    this.tenantName = tenantName;
  }

  /**
   * Gets the integration code that already exists.
   *
   * @return the code
   */
  public String getCode() {
    return code;
  }

  /**
   * Gets the tenant name where the code already exists.
   *
   * @return the tenant name
   */
  public String getTenantName() {
    return tenantName;
  }
}
