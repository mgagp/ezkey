/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationCreateRequestDto
 * Description: Request DTO for creating new integrations via Ezkey Admin API.
 */

package org.ezkey.demo.acme.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request DTO for creating new integrations via Ezkey Admin API.
 * <p>
 * This DTO is used to send integration creation requests to the Ezkey Admin API.
 * It contains the necessary information to create a new integration including
 * the base details and optional internationalization data.
 * </p>
 *
 * <p>
 * <b>Validation:</b> Client-side validation is performed using Alpine.js
 * to ensure required fields are provided before submission.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class IntegrationCreateRequestDto {

    @JsonProperty("name")
    private String integrationName;

    @JsonProperty("description")
    private String integrationDescription;

    @JsonProperty("i18n")
    private List<IntegrationI18nCreateDto> integrationI18n;

    /**
     * Default constructor for JSON serialization.
     */
    public IntegrationCreateRequestDto() {
    }

    /**
     * Constructor for creating a basic integration request.
     *
     * @param integrationName the name of the integration
     * @param integrationDescription the description of the integration
     */
    public IntegrationCreateRequestDto(String integrationName, String integrationDescription) {
        this.integrationName = integrationName;
        this.integrationDescription = integrationDescription;
    }

    // Getters and Setters

    public String getIntegrationName() {
        return integrationName;
    }

    public void setIntegrationName(String integrationName) {
        this.integrationName = integrationName;
    }

    public String getIntegrationDescription() {
        return integrationDescription;
    }

    public void setIntegrationDescription(String integrationDescription) {
        this.integrationDescription = integrationDescription;
    }

    public List<IntegrationI18nCreateDto> getIntegrationI18n() {
        return integrationI18n;
    }

    public void setIntegrationI18n(List<IntegrationI18nCreateDto> integrationI18n) {
        this.integrationI18n = integrationI18n;
    }

    @Override
    public String toString() {
        return "IntegrationCreateRequestDto{" +
                "integrationName='" + integrationName + '\'' +
                ", integrationDescription='" + integrationDescription + '\'' +
                ", integrationI18n=" + integrationI18n +
                '}';
    }
}