/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptPendingRequestDto
 * Description: Request DTO for checking pending authentication attempts in auth API.
 */

package org.ezkey.authattempt.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for checking pending authentication attempts in auth API.
 * <p>
 * This DTO represents the request data sent by mobile devices to check for
 * pending authentication attempts. It contains cryptographic signatures that
 * prove the authenticity of the request and ensure that only legitimate
 * enrolled devices can access pending authentication requests.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by mobile devices to poll the auth-api for pending
 * authentication requests. The mobile app calls this endpoint with cryptographic
 * proof to retrieve authentication challenges waiting for user approval.
 * </p>
 *
 * <p>
 * <b>Security Model:</b> Contains cryptographic signatures that validate the
 * device's identity and ensure request authenticity. This prevents unauthorized
 * access to pending authentication attempts.
 * </p>
 *
 * <p>
 * <b>Fields:</b>
 * <ul>
 * <li><b>enrollmentId:</b> Target enrollment to check for pending attempts</li>
 * <li><b>deviceProofToken:</b> Device's proof token for authentication</li>
 * <li><b>deviceProofTokenSigned:</b> Cryptographically signed device proof token</li>
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
 * @see org.ezkey.authattempt.domain.AuthAttemptPendingRequest
 * @see AuthAttemptPendingResponseDto
 */
@Schema(description = "Request DTO for checking pending authentication attempts")
public class AuthAttemptPendingRequestDto {

    /**
     * The enrollment ID to check for pending authentication attempts.
     * <p>
     * Must reference an existing and active enrollment. Used to identify
     * which device enrollment is requesting pending authentication attempts.
     * </p>
     */
    @Schema(description = "Enrollment ID to check for pending authentication attempts",example = "123",required = true)
    private Integer enrollmentId;

    /**
     * The device's proof token for authentication.
     * <p>
     * Contains the device-specific proof token used to identify and
     * authenticate the requesting device during the authentication flow.
     * Generated during enrollment and unique to each device.
     * </p>
     */
    @Schema(description = "Device proof token for authentication",example = "eyJhbGciOiJSUzI1NiJ9...",required = true)
    private String deviceProofToken;

    /**
     * Cryptographically signed device proof token.
     * <p>
     * Contains the signed version of the device proof token, providing
     * cryptographic proof of device authenticity and preventing request
     * forgery or unauthorized access to pending authentication attempts.
     * </p>
     */
    @Schema(description = "Cryptographically signed device proof token",example = "eyJhbGciOiJSUzI1NiJ9...",required = true)
    private String deviceProofTokenSigned;

    /**
     * Device private key for simulation purposes only.
     * <p>
     * Contains the simulated device's private key for testing authentication
     * flows. Only included when simulation mode is enabled.
     * </p>
     * <p>
     * <b>Security Warning:</b> This field contains highly sensitive cryptographic
     * material and should never be present in production environments.
     * </p>
     */
    @Schema(
            description = "Device private key (simulation mode only - NEVER in production)",
            example = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDL...",
            nullable = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String simulationDevicePrivateKey;

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
     * Gets the signed device proof token.
     *
     * @return the signed device proof token
     */
    public String getDeviceProofTokenSigned() {
        return deviceProofTokenSigned;
    }

    /**
     * Sets the signed device proof token.
     *
     * @param deviceProofTokenSigned the signed device proof token to set
     */
    public void setDeviceProofTokenSigned(String deviceProofTokenSigned) {
        this.deviceProofTokenSigned = deviceProofTokenSigned;
    }

    /**
     * Gets the simulation device private key.
     *
     * @return the simulation device private key
     */
    public String getSimulationDevicePrivateKey() {
        return simulationDevicePrivateKey;
    }

    /**
     * Sets the simulation device private key.
     *
     * @param simulationDevicePrivateKey the simulation device private key to set
     */
    public void setSimulationDevicePrivateKey(String simulationDevicePrivateKey) {
        this.simulationDevicePrivateKey = simulationDevicePrivateKey;
    }

}