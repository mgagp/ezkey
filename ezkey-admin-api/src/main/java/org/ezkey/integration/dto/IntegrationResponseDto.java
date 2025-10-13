/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationResponseDto
 * Description: Response DTO for integration data in admin API including internationalization support.
 */

package org.ezkey.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for integration data in admin API including internationalization support.
 *
 * <p>This DTO represents the complete integration information returned by the admin API for
 * administrative purposes. It includes comprehensive integration details, configuration, metadata,
 * and localized content for multiple languages while excluding sensitive cryptographic material for
 * security purposes.
 *
 * <p><b>Usage Context:</b> Used by admin API endpoints to return integration information to
 * administrators for monitoring and management purposes. Contains all non-sensitive data needed for
 * integration administration and client consumption.
 *
 * <p><b>Security Note:</b> This DTO excludes sensitive cryptographic keys and provides only the
 * information necessary for administrative operations and client display.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.integration.domain.entity.Integration
 * @see IntegrationCreateRequestDto
 * @see IntegrationI18nResponseDto
 */
@Schema(description = "Response DTO containing complete integration details")
public class IntegrationResponseDto {

  /** Unique identifier for the integration. Auto-generated primary key from the database. */
  @Schema(description = "Unique identifier for the integration", example = "1")
  private Integer id;

  /**
   * URL or path to the integration logo image. Used for displaying the integration brand in user
   * interfaces.
   */
  @Schema(
      description = "URL or path to the integration logo image",
      example = "https://example.com/logo.png")
  private String logo;

  /**
   * Integration status flag. Indicates whether the integration is currently active and available
   * for use.
   */
  @Schema(description = "Integration status flag", example = "true")
  private Boolean active;

  /** Timestamp when the integration was created. Used for audit trails and sorting purposes. */
  @Schema(
      description = "Timestamp when the integration was created",
      example = "2025-01-15T10:30:00")
  private LocalDateTime createdAt;

  /**
   * List of internationalized content for the integration. Contains localized names and
   * descriptions in multiple languages.
   */
  @Schema(description = "List of internationalized content for multiple languages")
  private List<IntegrationI18nResponseDto> i18n;

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
   * Gets the URL or path to the integration logo image.
   *
   * @return the logo URL/path
   */
  public String getLogo() {
    return logo;
  }

  /**
   * Sets the URL or path to the integration logo image.
   *
   * @param logo the logo URL/path to set
   */
  public void setLogo(String logo) {
    this.logo = logo;
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
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * Sets the timestamp when the integration was created.
   *
   * @param createdAt the creation timestamp to set
   */
  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * Gets the list of internationalized content for the integration.
   *
   * @return the list of i18n responses
   */
  public List<IntegrationI18nResponseDto> getI18n() {
    return i18n;
  }

  /**
   * Sets the list of internationalized content for the integration.
   *
   * @param i18n the list of i18n responses to set
   */
  public void setI18n(List<IntegrationI18nResponseDto> i18n) {
    this.i18n = i18n;
  }
}
