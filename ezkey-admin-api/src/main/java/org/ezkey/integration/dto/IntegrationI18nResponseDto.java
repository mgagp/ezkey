/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationI18nResponseDto
 * Description: Response DTO for integration internationalization data in admin API.
 */

package org.ezkey.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for integration internationalization data in admin API.
 * <p>
 * This DTO represents the complete localized content information for integrations
 * returned by the admin API. It provides translated names and descriptions in
 * different languages, enabling multi-language support in client applications.
 * Contains all internationalization data for administrative purposes.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by admin API endpoints to return internationalization
 * information as part of integration responses. This data enables clients to
 * display integration information in the user's preferred language.
 * </p>
 *
 * <p>
 * <b>Internationalization:</b> Follows standard ISO language codes for consistent
 * language identification across the system. Supports complete localization
 * for integration names and descriptions.
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
 * @see org.ezkey.integration.domain.entity.IntegrationI18n
 * @see IntegrationResponseDto
 * @see IntegrationI18nCreateDto
 */
@Schema(description = "Response DTO for integration internationalization data")
public class IntegrationI18nResponseDto {

    /**
     * Unique identifier for the internationalization record.
     * Auto-generated primary key from the database.
     */
    @Schema(description = "Unique identifier for the internationalization record", example = "1")
    private Integer id;

    /**
     * Language code for the localized content.
     * Uses standard ISO language codes (e.g., "en", "fr", "es").
     */
    @Schema(description = "Language code for the localized content", example = "en")
    private String language;

    /**
     * Localized name of the integration.
     * Display name in the specified language.
     */
    @Schema(description = "Localized name of the integration", example = "ACME Corporation")
    private String name;

    /**
     * Localized description of the integration.
     * Detailed description in the specified language.
     */
    @Schema(description = "Localized description of the integration", 
            example = "Secure authentication system for ACME applications")
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