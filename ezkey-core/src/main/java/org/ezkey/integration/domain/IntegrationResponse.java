/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationResponse
 * Description: Response DTO for integration data.
 */

package org.ezkey.integration.domain;

import java.time.OffsetDateTime;

/**
 * Response DTO for integration data.
 *
 * <p>This DTO represents the complete integration information returned by the API, including basic
 * integration details, name, and description. It is used in GET operations to return integration
 * data to clients.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Usage:</b> Integration API responses
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class IntegrationResponse {

  /** Unique identifier for the integration. Auto-generated primary key from the database. */
  private Integer id;

  /**
   * Integration status flag. Indicates whether the integration is currently active and available
   * for use.
   */
  private Boolean active;

  /** Timestamp when the integration was created. Used for audit trails and sorting purposes. */
  private OffsetDateTime createdAt;

  /** Display name for the integration. */
  private String name;

  /** Optional description of the integration. */
  private String description;

  /**
   * Gets the unique identifier for the integration.
   *
   * @return the integration ID
   */
  public Integer getId() {
    return id;
  }

  /**
   * Sets the unique identifier for the integration.
   *
   * @param id the integration ID to set
   */
  public void setId(Integer id) {
    this.id = id;
  }

  /**
   * Gets the integration status flag.
   *
   * @return true if the integration is active, false otherwise
   */
  public Boolean getActive() {
    return active;
  }

  /**
   * Sets the integration status flag.
   *
   * @param active the active status to set
   */
  public void setActive(Boolean active) {
    this.active = active;
  }

  /**
   * Gets the timestamp when the integration was created.
   *
   * @return the creation timestamp
   */
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * Sets the timestamp when the integration was created.
   *
   * @param createdAt the creation timestamp to set
   */
  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * Gets the display name of the integration.
   *
   * @return the integration name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the display name of the integration.
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
