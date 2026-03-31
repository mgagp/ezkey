/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationCreateRequest
 * Description: Request DTO for creating new Integration entities.
 */

package org.ezkey.integration.domain;

/**
 * Request DTO for creating new Integration entities.
 *
 * <p>This DTO contains the data required to create a new Integration in the system. It includes the
 * basic integration information: code, name, and optional description.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Usage:</b> POST /api/v1/integrations
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class IntegrationCreateRequest {

  /**
   * Unique business identifier code for the integration within a tenant. Must be unique per tenant.
   */
  private String code;

  /** Display name for the integration. Shown in admin interfaces and to users during enrollment. */
  private String name;

  /**
   * Optional description of the integration. Shown in admin interfaces and to users during
   * enrollment.
   */
  private String description;

  /**
   * Gets the unique business identifier code for the integration.
   *
   * @return the integration code
   */
  public String getCode() {
    return code;
  }

  /**
   * Sets the unique business identifier code for the integration.
   *
   * @param code the integration code to set
   */
  public void setCode(String code) {
    this.code = code;
  }

  /**
   * Gets the display name for the integration.
   *
   * @return the integration name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the display name for the integration.
   *
   * @param name the integration name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the optional description of the integration.
   *
   * @return the integration description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the optional description of the integration.
   *
   * @param description the integration description to set
   */
  public void setDescription(String description) {
    this.description = description;
  }
}
