/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationI18nDto
 * Description: Data Transfer Object for Integration internationalization data.
 */

package org.ezkey.demo.acme.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for Integration internationalization data.
 * <p>
 * This DTO represents the internationalization information for an integration,
 * allowing the integration to display localized names and descriptions
 * in multiple languages.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class IntegrationI18nDto {

    @JsonProperty("language")
    private String language;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    /**
     * Default constructor for JSON deserialization.
     */
    public IntegrationI18nDto() {
    }

    /**
     * Constructor for creating internationalization data.
     *
     * @param language the language code (e.g., "en", "fr")
     * @param name the localized name
     * @param description the localized description
     */
    public IntegrationI18nDto(String language, String name, String description) {
        this.language = language;
        this.name = name;
        this.description = description;
    }

    // Getters and Setters

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "IntegrationI18nDto{" +
                "language='" + language + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}