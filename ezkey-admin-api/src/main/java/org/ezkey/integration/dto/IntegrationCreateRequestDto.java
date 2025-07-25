/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationCreateRequestDto
 * Description: Request DTO for creating new Integration entities in admin API.
 */

package org.ezkey.integration.dto;

import java.util.List;

/**
 * Request DTO for creating new Integration entities in admin API.
 * <p>
 * This DTO contains the data required to create a new Integration through the admin API.
 * Integrations represent applications or systems that will be protected by Ezkey MFA.
 * It includes basic integration information and optional internationalization data
 * for multi-language support.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by administrators to create new integrations that
 * will use Ezkey for MFA authentication. Contains all necessary data for
 * integration setup including branding and localization.
 * </p>
 *
 * <p>
 * <b>Fields:</b>
 * <ul>
 * <li><b>code:</b> Unique business identifier for the integration</li>
 * <li><b>logo:</b> URL or path to the integration's logo image</li>
 * <li><b>i18n:</b> Optional internationalization data for multi-language support</li>
 * </ul>
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
 * @see org.ezkey.integration.domain.IntegrationCreateRequest
 * @see IntegrationI18nCreateDto
 */
public class IntegrationCreateRequestDto {

    /**
     * Unique code identifier for the integration.
     * Used for API identification and routing.
     */
    private String code;

    /**
     * URL or path to the integration's logo image.
     * Displayed in the mobile app and web interfaces.
     */
    private String logo;

    /**
     * Optional list of internationalization entries.
     * Contains localized name and description for different languages.
     */
    private List<IntegrationI18nCreateDto> i18n;

    /**
     * Gets the unique code identifier for the integration.
     *
     * @return the integration code
     */
    public String getCode(){
        return code;
    }

    /**
     * Sets the unique code identifier for the integration.
     *
     * @param code the integration code to set
     */
    public void setCode(String code){
        this.code = code;
    }

    /**
     * Gets the URL or path to the integration's logo.
     *
     * @return the logo URL/path
     */
    public String getLogo(){
        return logo;
    }

    /**
     * Sets the URL or path to the integration's logo.
     *
     * @param logo the logo URL/path to set
     */
    public void setLogo(String logo){
        this.logo = logo;
    }

    /**
     * Gets the list of internationalization entries.
     *
     * @return the list of i18n entries
     */
    public List<IntegrationI18nCreateDto> getI18n(){
        return i18n;
    }

    /**
     * Sets the list of internationalization entries.
     *
     * @param i18n the list of i18n entries to set
     */
    public void setI18n(List<IntegrationI18nCreateDto> i18n){
        this.i18n = i18n;
    }
}