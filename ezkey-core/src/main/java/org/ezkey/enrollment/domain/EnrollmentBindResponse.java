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

/**
 * Domain object for enrollment binding information in the core domain.
 * <p>
 * This class represents the enrollment binding response used internally by the core domain
 * and service layers. It contains all the information needed to complete the enrollment process,
 * including cryptographic keys, enrollment codes, and integration metadata.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by the core and service layers to transfer enrollment binding data
 * to the API layer, which then exposes it to mobile devices via DTOs. This object is mapped to
 * {@link org.ezkey.enrollment.dto.EnrollmentBindResponseDto} for API responses.
 * </p>
 *
 * <p>
 * <b>Cryptographic Flow:</b> Contains the integration's public key and signed enrollment code
 * that the mobile device must validate and respond to with its own generated keys and signature.
 * </p>
 *
 * <p>
 * <b>Integration Metadata:</b> Includes logo, name, and description to provide a better user
 * experience during the enrollment process.
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
 * @see org.ezkey.enrollment.dto.EnrollmentBindResponseDto
 */
public class EnrollmentBindResponse {
    /**
     * The enrollment ID for this binding operation.
     * <p>
     * Confirms the enrollment ID that was successfully bound to the mobile device.
     * Used for reference in subsequent verification requests.
     * </p>
     */
    private Integer enrollmentId;

    /**
     * The integration's public key for cryptographic operations.
     * <p>
     * Contains the public key of the integration that created this enrollment.
     * Used by the mobile device to verify signatures and validate integration
     * authenticity during the enrollment process.
     * </p>
     */
    private String integrationPublicKey;

    /**
     * The enrollment proof token that needs to be signed by the device.
     * <p>
     * Contains the challenge data that the mobile device must sign with its
     * private key to complete enrollment verification. This token proves that
     * the device possesses the cryptographic keys it claims to have.
     * </p>
     */
    private String enrollmentProofToken;

    /**
     * The logo URL or base64-encoded image for the integration.
     * <p>
     * Provides a visual identifier for the integration, allowing the mobile device
     * to display the integration's logo during the enrollment process.
     * </p>
     */
    private String integrationLogo;

    /**
     * The display name of the integration.
     * <p>
     * Human-readable name of the integration, shown to the user during enrollment
     * to help identify the service or application being enrolled.
     * </p>
     */
    private String integrationName;

    /**
     * The description of the integration.
     * <p>
     * Provides additional context or information about the integration, such as
     * its purpose or features, to assist the user during the enrollment process.
     * </p>
     */
    private String integrationDescription;

    /**
     * Gets the enrollment ID.
     *
     * @return the enrollment ID
     */
    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    /**
     * Sets the enrollment ID.
     *
     * @param enrollmentId the enrollment ID to set
     */
    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    /**
     * Gets the integration's public key.
     *
     * @return the integration's public key
     */
    public String getIntegrationPublicKey() {
        return integrationPublicKey;
    }

    /**
     * Sets the integration's public key.
     *
     * @param integrationPublicKey the integration's public key to set
     */
    public void setIntegrationPublicKey(String integrationPublicKey) {
        this.integrationPublicKey = integrationPublicKey;
    }

    /**
     * Gets the enrollment proof token.
     *
     * @return the enrollment proof token
     */
    public String getEnrollmentProofToken() {
        return enrollmentProofToken;
    }

    /**
     * Sets the enrollment proof token.
     *
     * @param enrollmentProofToken the enrollment proof token to set
     */
    public void setEnrollmentProofToken(String enrollmentProofToken) {
        this.enrollmentProofToken = enrollmentProofToken;
    }

    /**
     * Gets the integration logo.
     *
     * @return the integration logo (URL or base64-encoded image)
     */
    public String getIntegrationLogo() {
        return integrationLogo;
    }

    /**
     * Sets the integration logo.
     *
     * @param integrationLogo the integration logo to set (URL or base64-encoded image)
     */
    public void setIntegrationLogo(String integrationLogo) {
        this.integrationLogo = integrationLogo;
    }

    /**
     * Gets the integration name.
     *
     * @return the integration name
     */
    public String getIntegrationName() {
        return integrationName;
    }

    /**
     * Sets the integration name.
     *
     * @param integrationName the integration name to set
     */
    public void setIntegrationName(String integrationName) {
        this.integrationName = integrationName;
    }

    /**
     * Gets the integration description.
     *
     * @return the integration description
     */
    public String getIntegrationDescription() {
        return integrationDescription;
    }

    /**
     * Sets the integration description.
     *
     * @param integrationDescription the integration description to set
     */
    public void setIntegrationDescription(String integrationDescription) {
        this.integrationDescription = integrationDescription;
    }

}
