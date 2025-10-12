/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationCreateRequest
 * Description: Request DTO for creating new Integration entities.
 */

package org.ezkey.integration.domain;

import java.util.List;

/**
 * Request DTO for creating new Integration entities.
 * <p>
 * This DTO contains the data required to create a new Integration in the system.
 * It includes the basic integration information and optional internationalization data.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 * <p>
 * <b>Usage:</b> POST /api/v1/integrations
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class IntegrationCreateRequest {

    /**
     * URL or path to the integration's logo image.
     * Displayed in the mobile app and web interfaces.
     * @deprecated Use logoId instead to reference a managed logo
     */
    @Deprecated
    private String logo;

    /**
     * ID of the logo to use for this integration.
     * References a logo from the ezkey_logo table.
     */
    private Integer logoId;

    /**
     * Optional list of internationalization entries.
     * Contains localized name and description for different languages.
     */
    private List<IntegrationI18nCreate> i18n;

    /**
     * Gets the URL or path to the integration's logo.
     *
     * @return the logo URL/path
     * @deprecated Use getLogoId() instead
     */
    @Deprecated
    public String getLogo() {
        return logo;
    }

    /**
     * Sets the URL or path to the integration's logo.
     *
     * @param logo the logo URL/path to set
     * @deprecated Use setLogoId() instead
     */
    @Deprecated
    public void setLogo(String logo) {
        this.logo = logo;
    }

    /**
     * Gets the logo ID.
     *
     * @return the logo ID
     */
    public Integer getLogoId() {
        return logoId;
    }

    /**
     * Sets the logo ID.
     *
     * @param logoId the logo ID to set
     */
    public void setLogoId(Integer logoId) {
        this.logoId = logoId;
    }

    /**
     * Gets the list of internationalization entries.
     *
     * @return the list of i18n entries
     */
    public List<IntegrationI18nCreate> getI18n() {
        return i18n;
    }

    /**
     * Sets the list of internationalization entries.
     *
     * @param i18n the list of i18n entries to set
     */
    public void setI18n(List<IntegrationI18nCreate> i18n) {
        this.i18n = i18n;
    }
}