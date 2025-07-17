/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationI18nResponse
 * Description: Response DTO for integration internationalization data.
 */

package org.ezkey.integration.dto;

/**
 * Response DTO for integration internationalization data.
 * <p>
 * This DTO represents localized content for integrations, providing
 * translated names and descriptions in different languages.
 * It is used as part of the IntegrationResponse to support multi-language interfaces.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 * <p>
 * <b>Usage:</b> Integration i18n API responses
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class IntegrationI18nResponseDto {

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