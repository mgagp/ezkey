/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: EnrollmentBindResponse
 * Description: Domain object for enrollment binding information in the core domain.
 */

package org.ezkey.enrollment.domain;

public class EnrollmentBindRequest {

    private Integer enrollmentId;

    /**
     * The language code (e.g., "en", "fr") requested for i18n fields.
     * Used to fetch localized integration/application names.
     */
    private String language;

    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    /**
     * Gets the language code for i18n.
     *
     * @return the language code (e.g., "en", "fr")
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Sets the language code for i18n.
     *
     * @param language the language code to set
     */
    public void setLanguage(String language) {
        this.language = language;
    }

}