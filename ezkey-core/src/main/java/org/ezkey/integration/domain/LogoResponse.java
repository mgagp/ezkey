/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: LogoResponse
 * Description: Response DTO for Logo entities.
 */

package org.ezkey.integration.domain;

import java.time.LocalDateTime;

/**
 * Response DTO for Logo entities.
 * <p>
 * This DTO contains the complete logo information returned to clients.
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
public class LogoResponse {

    /**
     * The logo ID.
     */
    private Integer id;

    /**
     * The logo name.
     */
    private String name;

    /**
     * The logo URL (if stored externally).
     */
    private String logoUrl;

    /**
     * The base64-encoded logo data (if stored locally).
     */
    private String logoData;

    /**
     * The MIME type of the logo.
     */
    private String contentType;

    /**
     * The creation timestamp.
     */
    private LocalDateTime createdAt;

    /**
     * The last update timestamp.
     */
    private LocalDateTime updatedAt;

    /**
     * Gets the logo ID.
     *
     * @return the logo ID
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the logo ID.
     *
     * @param id the logo ID to set
     */
    public void setId(Integer id) {
        this.id = id;
    }

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

    /**
     * Gets the creation timestamp.
     *
     * @return the creation timestamp
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp.
     *
     * @param createdAt the creation timestamp to set
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Gets the update timestamp.
     *
     * @return the update timestamp
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the update timestamp.
     *
     * @param updatedAt the update timestamp to set
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
