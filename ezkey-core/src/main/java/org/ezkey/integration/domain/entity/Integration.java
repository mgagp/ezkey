/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: EzkeyIntegration
 * Description: JPA entity representing an integration in the Ezkey system.
 */

package org.ezkey.integration.domain.entity;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * JPA entity representing an integration in the Ezkey system.
 * <p>
 * An integration represents an application or system that is protected by Ezkey MFA.
 * Each integration can have multiple internationalization entries (i18n) for different languages.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 * <p>
 * <b>Table:</b> ezkey_integration
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Entity
@Table(name = "ezkey_integration")
public class Integration {

    /**
     * Unique identifier for the integration.
     * Auto-generated using database identity.
     */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "integration_id")
    private Integer id;

    /**
     * URL or path to the integration's logo image.
     * Displayed in the mobile app and web interfaces.
     */
    @Column(name = "integration_logo")
    private String logo;

    /**
     * Flag indicating whether the integration is active and available for use.
     * Inactive integrations cannot be used for authentication.
     */
    @Column(name = "integration_active")
    private Boolean active;

    /**
     * Timestamp when the integration was created.
     * Automatically set when the entity is persisted.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Collection of internationalization entries for this integration.
     * Each entry contains localized name and description for different languages.
     * Uses lazy loading for performance optimization.
     */
    @OneToMany(mappedBy = "integration",cascade = CascadeType.ALL,orphanRemoval = true,fetch = FetchType.LAZY)
    private List<IntegrationI18n> i18n;

    /**
     * Gets the unique identifier of the integration.
     *
     * @return the integration ID
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the unique identifier of the integration.
     *
     * @param id the integration ID to set
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Gets the URL or path to the integration's logo.
     *
     * @return the logo URL/path
     */
    public String getLogo() {
        return logo;
    }

    /**
     * Sets the URL or path to the integration's logo.
     *
     * @param logo the logo URL/path to set
     */
    public void setLogo(String logo) {
        this.logo = logo;
    }

    /**
     * Gets the active status of the integration.
     *
     * @return true if the integration is active, false otherwise
     */
    public Boolean getActive() {
        return active;
    }

    /**
     * Sets the active status of the integration.
     *
     * @param active the active status to set
     */
    public void setActive(Boolean active) {
        this.active = active;
    }

    /**
     * Gets the creation timestamp of the integration.
     *
     * @return the creation timestamp
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp of the integration.
     *
     * @param createdAt the creation timestamp to set
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Gets the collection of internationalization entries for this integration.
     *
     * @return the list of i18n entries
     */
    public List<IntegrationI18n> getI18n() {
        return i18n;
    }

    /**
     * Sets the collection of internationalization entries for this integration.
     *
     * @param i18n the list of i18n entries to set
     */
    public void setI18n(List<IntegrationI18n> i18n) {
        this.i18n = i18n;
    }
}