/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain IntegrationCreateRequest
 * Description: Request for creating new Integration entities.
 */

package org.ezkey.integration.domain;

import java.util.List;

/**
 * Request for creating new Integration entities.
 * <p>
 * This domain object contains the data required to create a new Integration in the system.
 * It includes the basic integration information and optional internationalization data.
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
 */
public class IntegrationCreateRequest {

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
    private List<IntegrationI18nCreate> i18n;

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
    public List<IntegrationI18nCreate> getI18n(){
        return i18n;
    }

    /**
     * Sets the list of internationalization entries.
     *
     * @param i18n the list of i18n entries to set
     */
    public void setI18n(List<IntegrationI18nCreate> i18n){
        this.i18n = i18n;
    }
}