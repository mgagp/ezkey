/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: AuthAttemptPendingRequest
 * Description: Domain request object for retrieving pending authentication attempts.
 */

package org.ezkey.authattempt.domain;

/**
 * Domain request object for retrieving pending authentication attempts.
 * <p>
 * This domain object represents the request data used internally by the service layer
 * to check for pending authentication attempts for a specific enrollment. It contains
 * device authentication credentials and simulation data needed to validate device
 * identity and retrieve appropriate pending authentication requests.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by the AuthAttemptService when mobile devices poll
 * for pending authentication requests. The service layer transforms API DTOs into
 * this domain object for business logic processing and device validation.
 * </p>
 *
 * <p>
 * <b>Authentication Flow:</b> Mobile devices use this request to periodically check
 * for authentication attempts that require user approval. The device must provide
 * cryptographic proof of identity through device proof tokens to access pending
 * authentication challenges.
 * </p>
 *
 * <p>
 * <b>Security Model:</b> Contains cryptographic signatures that validate the
 * device's identity and ensure only legitimate enrolled devices can access
 * pending authentication attempts. This prevents unauthorized access to
 * authentication challenges.
 * </p>
 *
 * <p>
 * <b>Simulation Support:</b> Includes simulation data for testing and development
 * environments, allowing end-to-end authentication flow testing without requiring
 * actual mobile devices. Simulation fields are never populated in production.
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
 * @see org.ezkey.authattempt.service.AuthAttemptService
 * @see org.ezkey.authattempt.domain.AuthAttemptPendingResponse
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
public class AuthAttemptPendingRequest {

    /**
     * The enrollment ID to check for pending authentication attempts.
     * <p>
     * Must reference an existing and active enrollment. Used to identify
     * which device enrollment is requesting pending authentication attempts
     * and to filter authentication requests to the appropriate device.
     * </p>
     */
    private Integer enrollmentId;

    /**
     * The device's proof token for authentication.
     * <p>
     * Contains the device-specific proof token used to identify and
     * authenticate the requesting device during the authentication flow.
     * Generated during enrollment and unique to each device. This token
     * is validated against the enrolled device's stored credentials.
     * </p>
     */
    private String deviceProofToken;

    /**
     * Cryptographically signed device proof token.
     * <p>
     * Contains the signed version of the device proof token, providing
     * cryptographic proof of device authenticity and preventing request
     * forgery or unauthorized access to pending authentication attempts.
     * The signature is verified using the device's public key.
     * </p>
     */
    private String deviceProofTokenSigned;

    /**
     * Device private key for simulation purposes only.
     * <p>
     * Contains the simulated device's private key for testing authentication
     * flows in development and testing environments. This field enables
     * end-to-end testing without requiring actual mobile devices and allows
     * automated verification of authentication flows.
     * </p>
     * <p>
     * <b>Security Warning:</b> This field contains highly sensitive cryptographic
     * material and should never be populated in production environments. It is
     * exclusively for development and testing purposes.
     * </p>
     */
    private String simulationDevicePrivateKey;

    /**
     * Gets the enrollment ID for this pending authentication request.
     *
     * @return the enrollment ID
     */
    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    /**
     * Sets the enrollment ID for this pending authentication request.
     *
     * @param enrollmentId the enrollment ID to set
     */
    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    /**
     * Gets the device proof token.
     *
     * @return the device proof token
     */
    public String getDeviceProofToken() {
        return deviceProofToken;
    }

    /**
     * Sets the device proof token.
     *
     * @param deviceProofToken the device proof token to set
     */
    public void setDeviceProofToken(String deviceProofToken) {
        this.deviceProofToken = deviceProofToken;
    }

    /**
     * Gets the cryptographically signed device proof token.
     *
     * @return the signed device proof token
     */
    public String getDeviceProofTokenSigned() {
        return deviceProofTokenSigned;
    }

    /**
     * Sets the cryptographically signed device proof token.
     *
     * @param deviceProofTokenSigned the signed device proof token to set
     */
    public void setDeviceProofTokenSigned(String deviceProofTokenSigned) {
        this.deviceProofTokenSigned = deviceProofTokenSigned;
    }

    /**
     * Gets the simulation device private key.
     * <p>
     * <b>Security Warning:</b> This method should only be used in testing
     * and development environments. Never call this in production code.
     * </p>
     *
     * @return the simulation device private key
     */
    public String getSimulationDevicePrivateKey() {
        return simulationDevicePrivateKey;
    }

    /**
     * Sets the simulation device private key.
     * <p>
     * <b>Security Warning:</b> This method should only be used in testing
     * and development environments. Never call this in production code.
     * </p>
     *
     * @param simulationDevicePrivateKey the simulation device private key to set
     */
    public void setSimulationDevicePrivateKey(String simulationDevicePrivateKey) {
        this.simulationDevicePrivateKey = simulationDevicePrivateKey;
    }

}