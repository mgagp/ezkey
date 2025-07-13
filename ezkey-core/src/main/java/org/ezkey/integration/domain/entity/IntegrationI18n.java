/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: EzkeyIntegrationI18n
 * Description: JPA entity representing internationalization entries for integrations.
 */

package org.ezkey.integration.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * JPA entity representing internationalization entries for integrations.
 * <p>
 * This entity stores localized information (name and description) for integrations
 * in different languages. Each integration can have multiple i18n entries for
 * different language codes.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 * <p>
 * <b>Table:</b> ezkey_integration_i18n
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Entity
@Table(name = "ezkey_integration_i18n")
public class IntegrationI18n {

    /**
     * Unique identifier for the i18n entry.
     * Auto-generated using database identity.
     */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "integration_i18n_id")
    private Integer id;

    /**
     * Reference to the parent integration.
     * Many-to-one relationship with lazy loading for performance.
     */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "integration_id")
    private Integration integration;

    /**
     * Language code for this i18n entry.
     * Should follow ISO 639-1 standard (e.g., "en", "fr", "es").
     */
    @Column(name = "integration_i18n_lang")
    private String language;

    /**
     * Localized name of the integration.
     * Displayed in the user interface for the specified language.
     */
    @Column(name = "integration_i18n_name")
    private String name;

    /**
     * Localized description of the integration.
     * Provides additional information about the integration in the specified language.
     */
    @Column(name = "integration_i18n_description")
    private String description;

    /**
     * Gets the unique identifier of the i18n entry.
     *
     * @return the i18n entry ID
     */
    public Integer getId(){
        return id;
    }

    /**
     * Sets the unique identifier of the i18n entry.
     *
     * @param id the i18n entry ID to set
     */
    public void setId(Integer id){
        this.id = id;
    }

    /**
     * Gets the parent integration reference.
     *
     * @return the parent integration
     */
    public Integration getIntegration(){
        return integration;
    }

    /**
     * Sets the parent integration reference.
     *
     * @param integration the parent integration to set
     */
    public void setIntegration(Integration integration){
        this.integration = integration;
    }

    /**
     * Gets the language code for this i18n entry.
     *
     * @return the language code (e.g., "en", "fr", "es")
     */
    public String getLanguage(){
        return language;
    }

    /**
     * Sets the language code for this i18n entry.
     *
     * @param language the language code to set
     */
    public void setLanguage(String language){
        this.language = language;
    }

    /**
     * Gets the localized name of the integration.
     *
     * @return the localized name
     */
    public String getName(){
        return name;
    }

    /**
     * Sets the localized name of the integration.
     *
     * @param name the localized name to set
     */
    public void setName(String name){
        this.name = name;
    }

    /**
     * Gets the localized description of the integration.
     *
     * @return the localized description
     */
    public String getDescription(){
        return description;
    }

    /**
     * Sets the localized description of the integration.
     *
     * @param description the localized description to set
     */
    public void setDescription(String description){
        this.description = description;
    }
}