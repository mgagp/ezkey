/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationI18nCreateDto
 * Description: Create DTO for integration internationalization data in admin API.
 */

package org.ezkey.integration.dto;

/**
 * Create DTO for integration internationalization data in admin API.
 * <p>
 * This DTO represents the localized content data needed to create internationalization
 * records for integrations. It provides translated names and descriptions for different
 * languages, enabling multi-language support in client applications.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used as part of integration creation requests to provide
 * localized content for multiple languages. This enables the mobile app and other
 * clients to display integration information in the user's preferred language.
 * </p>
 *
 * <p>
 * <b>Internationalization:</b> Follows standard ISO language codes for consistent
 * language identification across the system.
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
 * @see IntegrationCreateRequestDto
 * @see IntegrationI18nResponseDto
 */
public class IntegrationI18nCreateDto {

    /**
     * Unique identifier for the internationalization record.
     * Auto-generated primary key from the database.
     */
    private Integer id;

    /**
     * Language code for the localized content.
     * Uses standard ISO language codes (e.g., "en", "fr", "es").
     */
    private String language;

    /**
     * Localized name of the integration.
     * Display name in the specified language.
     */
    private String name;

    /**
     * Localized description of the integration.
     * Detailed description in the specified language.
     */
    private String description;

    /**
     * Gets the unique identifier for the internationalization record.
     *
     * @return the i18n record ID
     */
    public Integer getId(){
        return id;
    }

    /**
     * Sets the unique identifier for the internationalization record.
     *
     * @param id the i18n record ID to set
     */
    public void setId(Integer id){
        this.id = id;
    }

    /**
     * Gets the language code for the localized content.
     *
     * @return the language code
     */
    public String getLanguage(){
        return language;
    }

    /**
     * Sets the language code for the localized content.
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