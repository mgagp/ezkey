/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationI18nCreateDto
 * Description: DTO for creating integration internationalization data.
 */

package org.ezkey.demo.acme.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for creating integration internationalization data.
 * <p>
 * This DTO is used when creating new integrations to provide
 * localized names and descriptions in multiple languages.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class IntegrationI18nCreateDto {

    @JsonProperty("language")
    private String language;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    /**
     * Default constructor for JSON serialization.
     */
    public IntegrationI18nCreateDto() {
    }

    /**
     * Constructor for creating internationalization data.
     *
     * @param language the language code (e.g., "en", "fr")
     * @param name the localized name
     * @param description the localized description
     */
    public IntegrationI18nCreateDto(String language, String name, String description) {
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
        return "IntegrationI18nCreateDto{" +
                "language='" + language + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}