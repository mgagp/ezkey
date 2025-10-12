/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: LogoCreateRequestDto
 * Description: Request DTO for creating/updating Logo entities via admin API.
 */

package org.ezkey.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for creating/updating Logo entities via admin API.
 * <p>
 * This DTO is used by the admin API to create or update logos.
 * At least one of logoUrl or logoData must be provided.
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
 */
@Schema(description = "Request DTO for creating or updating a logo")
public class LogoCreateRequestDto {

    /**
     * Unique name/identifier for the logo.
     */
    @Schema(description = "Unique name/identifier for the logo", example = "acme-corp", required = true)
    private String name;

    /**
     * External URL to the logo image (optional if logoData is provided).
     */
    @Schema(description = "External URL to the logo image", example = "https://example.com/logo.png", required = false)
    private String logoUrl;

    /**
     * Base64-encoded logo image data (optional if logoUrl is provided).
     */
    @Schema(description = "Base64-encoded logo image data", example = "data:image/png;base64,iVBORw0KG...", required = false)
    private String logoData;

    /**
     * MIME type for the logo.
     */
    @Schema(description = "MIME type for the logo", example = "image/png", required = false)
    private String contentType;

    /**
     * Gets the logo name.
     *
     * @return the logo name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the logo name.
     *
     * @param name the logo name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the logo URL.
     *
     * @return the logo URL
     */
    public String getLogoUrl() {
        return logoUrl;
    }

    /**
     * Sets the logo URL.
     *
     * @param logoUrl the logo URL to set
     */
    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    /**
     * Gets the logo data.
     *
     * @return the logo data
     */
    public String getLogoData() {
        return logoData;
    }

    /**
     * Sets the logo data.
     *
     * @param logoData the logo data to set
     */
    public void setLogoData(String logoData) {
        this.logoData = logoData;
    }

    /**
     * Gets the content type.
     *
     * @return the content type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Sets the content type.
     *
     * @param contentType the content type to set
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
