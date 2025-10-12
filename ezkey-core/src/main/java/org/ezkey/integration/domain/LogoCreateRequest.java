/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: LogoCreateRequest
 * Description: Request DTO for creating new Logo entities.
 */

package org.ezkey.integration.domain;

/**
 * Request DTO for creating new Logo entities.
 * <p>
 * This DTO contains the data required to create a new Logo in the system.
 * At least one of logoUrl or logoData must be provided.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 * <p>
 * <b>Usage:</b> POST /api/v1/logos
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class LogoCreateRequest {

    /**
     * Unique name/identifier for the logo.
     * Example: "acme-corp", "bank-logo"
     */
    private String name;

    /**
     * External URL to the logo image (optional if logoData is provided).
     */
    private String logoUrl;

    /**
     * Base64-encoded logo image data (optional if logoUrl is provided).
     */
    private String logoData;

    /**
     * MIME type for the logo (e.g., "image/png", "image/jpeg", "image/svg+xml").
     */
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
