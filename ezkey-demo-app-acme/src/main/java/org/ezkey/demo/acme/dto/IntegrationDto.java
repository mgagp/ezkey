/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationDto
 * Description: Data Transfer Object for Integration entities from Ezkey Admin API.
 */

package org.ezkey.demo.acme.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for Integration entities from Ezkey Admin API.
 * <p>
 * This DTO represents an integration record as returned by the Ezkey Admin API.
 * An integration defines a protected application or system that uses Ezkey
 * for multi-factor authentication.
 * </p>
 *
 * <p>
 * <b>Integration Concept:</b> Represents a specific application or service
 * that has been configured to use Ezkey MFA. Each integration has its own
 * cryptographic keys and can have multiple enrollments (users/devices).
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class IntegrationDto {

    @JsonProperty("id")
    private Integer integrationId;

    @JsonProperty("name")
    private String integrationName;

    @JsonProperty("description")
    private String integrationDescription;

    @JsonProperty("publicKey")
    private String integrationPublicKey;

    @JsonProperty("privateKey")
    private String integrationPrivateKey;

    @JsonProperty("active")
    private Boolean integrationActive;

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime createdAt;

    @JsonProperty("i18n")
    private List<IntegrationI18nDto> integrationI18n;

    /**
     * Default constructor for JSON deserialization.
     */
    public IntegrationDto() {
    }

    /**
     * Constructor for creating a new integration DTO.
     *
     * @param integrationName the name of the integration
     * @param integrationDescription the description of the integration
     */
    public IntegrationDto(String integrationName, String integrationDescription) {
        this.integrationName = integrationName;
        this.integrationDescription = integrationDescription;
        this.integrationActive = true;
    }

    // Getters and Setters

    public Integer getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(Integer integrationId) {
        this.integrationId = integrationId;
    }

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

    public String getIntegrationPublicKey() {
        return integrationPublicKey;
    }

    public void setIntegrationPublicKey(String integrationPublicKey) {
        this.integrationPublicKey = integrationPublicKey;
    }

    public String getIntegrationPrivateKey() {
        return integrationPrivateKey;
    }

    public void setIntegrationPrivateKey(String integrationPrivateKey) {
        this.integrationPrivateKey = integrationPrivateKey;
    }

    public Boolean getIntegrationActive() {
        return integrationActive;
    }

    public void setIntegrationActive(Boolean integrationActive) {
        this.integrationActive = integrationActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<IntegrationI18nDto> getIntegrationI18n() {
        return integrationI18n;
    }

    public void setIntegrationI18n(List<IntegrationI18nDto> integrationI18n) {
        this.integrationI18n = integrationI18n;
    }

    @Override
    public String toString() {
        return "IntegrationDto{" +
                "integrationId=" + integrationId +
                ", integrationName='" + integrationName + '\'' +
                ", integrationDescription='" + integrationDescription + '\'' +
                ", integrationActive=" + integrationActive +
                ", createdAt=" + createdAt +
                '}';
    }
}