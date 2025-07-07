/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationResponse
 * Description: Response DTO for integration data including internationalization support.
 */

package org.ezkey.integration.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for integration data including internationalization support.
 * <p>
 * This DTO represents the complete integration information returned by the API,
 * including basic integration details and localized content for multiple languages.
 * It is used in GET operations to return integration data to clients.
 * </p>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 * <p><b>Usage:</b> Integration API responses</p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class IntegrationDtoResponse {
    
    /**
     * Unique identifier for the integration.
     * Auto-generated primary key from the database.
     */
    private Integer id;
    
    /**
     * Integration code identifier.
     * Used as a unique business identifier for the integration (e.g., "google", "microsoft").
     */
    private String code;
    
    /**
     * URL or path to the integration logo image.
     * Used for displaying the integration brand in user interfaces.
     */
    private String logo;
    
    /**
     * Integration status flag.
     * Indicates whether the integration is currently active and available for use.
     */
    private Boolean active;
    
    /**
     * Timestamp when the integration was created.
     * Used for audit trails and sorting purposes.
     */
    private LocalDateTime createdAt;
    
    /**
     * List of internationalized content for the integration.
     * Contains localized names and descriptions in multiple languages.
     */
    private List<IntegrationI18nResponse> i18n;

    /**
     * Gets the unique identifier for the integration.
     *
     * @return the integration ID
     */
    public Integer getId() { 
        return id; 
    }
    
    /**
     * Sets the unique identifier for the integration.
     *
     * @param id the integration ID to set
     */
    public void setId(Integer id) { 
        this.id = id; 
    }
    
    /**
     * Gets the integration code identifier.
     *
     * @return the integration code
     */
    public String getCode() { 
        return code; 
    }
    
    /**
     * Sets the integration code identifier.
     *
     * @param code the integration code to set
     */
    public void setCode(String code) { 
        this.code = code; 
    }
    
    /**
     * Gets the URL or path to the integration logo image.
     *
     * @return the logo URL/path
     */
    public String getLogo() { 
        return logo; 
    }
    
    /**
     * Sets the URL or path to the integration logo image.
     *
     * @param logo the logo URL/path to set
     */
    public void setLogo(String logo) { 
        this.logo = logo; 
    }
    
    /**
     * Gets the integration status flag.
     *
     * @return true if the integration is active, false otherwise
     */
    public Boolean getActive() { 
        return active; 
    }
    
    /**
     * Sets the integration status flag.
     *
     * @param active the active status to set
     */
    public void setActive(Boolean active) { 
        this.active = active; 
    }
    
    /**
     * Gets the timestamp when the integration was created.
     *
     * @return the creation timestamp
     */
    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }
    
    /**
     * Sets the timestamp when the integration was created.
     *
     * @param createdAt the creation timestamp to set
     */
    public void setCreatedAt(LocalDateTime createdAt) { 
        this.createdAt = createdAt; 
    }
    
    /**
     * Gets the list of internationalized content for the integration.
     *
     * @return the list of i18n responses
     */
    public List<IntegrationI18nResponse> getI18n() { 
        return i18n; 
    }
    
    /**
     * Sets the list of internationalized content for the integration.
     *
     * @param i18n the list of i18n responses to set
     */
    public void setI18n(List<IntegrationI18nResponse> i18n) { 
        this.i18n = i18n; 
    }
} 