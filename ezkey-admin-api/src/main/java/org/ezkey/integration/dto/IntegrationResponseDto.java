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

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for integration data in admin API including internationalization support.
 * <p>
 * This DTO represents the complete integration information returned by the admin API
 * for administrative purposes. It includes comprehensive integration details, configuration,
 * metadata, and localized content for multiple languages while excluding sensitive
 * cryptographic material for security purposes.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by admin API endpoints to return integration information
 * to administrators for monitoring and management purposes. Contains all non-sensitive
 * data needed for integration administration and client consumption.
 * </p>
 *
 * <p>
 * <b>Security Note:</b> This DTO excludes sensitive cryptographic keys and provides
 * only the information necessary for administrative operations and client display.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.integration.domain.entity.Integration
 * @see IntegrationCreateRequestDto
 * @see IntegrationI18nResponseDto
 */
@Schema(description = "Response DTO containing complete integration details")
public class IntegrationResponseDto {

    /**
     * Unique identifier for the integration.
     * Auto-generated primary key from the database.
     */
    @Schema(description = "Unique identifier for the integration", example = "1")
    private Integer id;

    /**
     * URL or path to the integration logo image.
     * Used for displaying the integration brand in user interfaces.
     * @deprecated This field is computed from logoRef and kept for backward compatibility
     */
    @Deprecated
    @Schema(description = "URL or path to the integration logo image (computed from logoRef)", 
            example = "https://example.com/logo.png", deprecated = true)
    private String logo;

    /**
     * ID of the logo referenced by this integration.
     * References a logo from the ezkey_logo table.
     */
    @Schema(description = "ID of the logo referenced by this integration", example = "1")
    private Integer logoId;

    /**
     * Integration status flag.
     * Indicates whether the integration is currently active and available for use.
     */
    @Schema(description = "Integration status flag", example = "true")
    private Boolean active;

    /**
     * Timestamp when the integration was created.
     * Used for audit trails and sorting purposes.
     */
    @Schema(description = "Timestamp when the integration was created", 
            example = "2025-01-15T10:30:00")
    private LocalDateTime createdAt;

    /**
     * List of internationalized content for the integration.
     * Contains localized names and descriptions in multiple languages.
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
     * @deprecated This field is computed for backward compatibility
     */
    @Deprecated
    public String getLogo() {
        return logo;
    }

    /**
     * Sets the URL or path to the integration logo image.
     *
     * @param logo the logo URL/path to set
     * @deprecated This field is computed for backward compatibility
     */
    @Deprecated
    public void setLogo(String logo) {
        this.logo = logo;
    }

    /**
     * Gets the logo ID.
     *
     * @return the logo ID
     */
    public Integer getLogoId() {
        return logoId;
    }

    /**
     * Sets the logo ID.
     *
     * @param logoId the logo ID to set
     */
    public void setLogoId(Integer logoId) {
        this.logoId = logoId;
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