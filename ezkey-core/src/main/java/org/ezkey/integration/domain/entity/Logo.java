/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: Logo
 * Description: JPA entity representing a logo in the Ezkey system.
 */

package org.ezkey.integration.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity representing a logo in the Ezkey system.
 * <p>
 * A logo can be stored either as an external URL or as base64-encoded image data.
 * This entity provides centralized management of logos that can be referenced by integrations.
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
 * <b>Table:</b> ezkey_logo
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Entity
@Table(name = "ezkey_logo")
public class Logo {

    /**
     * Unique identifier for the logo.
     * Auto-generated using database identity.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "logo_id")
    private Integer id;

    /**
     * Unique name/identifier for the logo.
     * Used to reference the logo from integrations.
     * Example: "acme-corp", "bank-logo", "admin-portal"
     */
    @Column(name = "logo_name", nullable = false, unique = true, length = 100)
    private String name;

    /**
     * External URL to the logo image.
     * Optional if logoData is provided.
     */
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /**
     * Base64-encoded logo image data.
     * Optional if logoUrl is provided.
     * Allows self-hosted logos without external dependencies.
     */
    @Column(name = "logo_data", columnDefinition = "TEXT")
    private String logoData;

    /**
     * MIME type for the logo.
     * Examples: "image/png", "image/jpeg", "image/svg+xml"
     * Required for proper rendering of the logo.
     */
    @Column(name = "logo_content_type", length = 50)
    private String contentType;

    /**
     * Timestamp when the logo was created.
     * Automatically set when the entity is persisted.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the logo was last updated.
     * Useful for cache invalidation.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Gets the unique identifier of the logo.
     *
     * @return the logo ID
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the unique identifier of the logo.
     *
     * @param id the logo ID to set
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Gets the unique name of the logo.
     *
     * @return the logo name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the unique name of the logo.
     *
     * @param name the logo name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the external URL to the logo image.
     *
     * @return the logo URL
     */
    public String getLogoUrl() {
        return logoUrl;
    }

    /**
     * Sets the external URL to the logo image.
     *
     * @param logoUrl the logo URL to set
     */
    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    /**
     * Gets the base64-encoded logo image data.
     *
     * @return the logo data
     */
    public String getLogoData() {
        return logoData;
    }

    /**
     * Sets the base64-encoded logo image data.
     *
     * @param logoData the logo data to set
     */
    public void setLogoData(String logoData) {
        this.logoData = logoData;
    }

    /**
     * Gets the MIME type of the logo.
     *
     * @return the content type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Sets the MIME type of the logo.
     *
     * @param contentType the content type to set
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Gets the creation timestamp of the logo.
     *
     * @return the creation timestamp
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp of the logo.
     *
     * @param createdAt the creation timestamp to set
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Gets the last update timestamp of the logo.
     *
     * @return the update timestamp
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the last update timestamp of the logo.
     *
     * @param updatedAt the update timestamp to set
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
