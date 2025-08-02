/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentCreateResponseDto
 * Description: Response DTO for enrollment creation in admin API.
 */

package org.ezkey.enrollment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for enrollment creation in admin API.
 * <p>
 * This DTO represents the response data returned when an enrollment
 * is successfully created through the admin API. It contains the essential
 * information needed to identify and use the newly created enrollment,
 * including simulation data for testing purposes.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Returned by admin API when creating enrollments.
 * The enrollment ID can be used for subsequent operations, and the challenge
 * can be used for enrollment verification processes. Simulation fields are
 * only included in the JSON response when simulation mode is enabled (they 
 * are completely excluded when null).
 * </p>
 *
 * <p>
 * <b>Core Fields:</b>
 * <ul>
 * <li><b>enrollmentId:</b> Unique identifier of the created enrollment</li>
 * <li><b>enrollmentChallenge:</b> Challenge number for enrollment verification</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Simulation Fields (Testing/Development Only):</b>
 * <ul>
 * <li><b>simulationEnrollmentProofTokenSigned:</b> Signed proof token for simulation</li>
 * <li><b>simulationDevicePublicKey:</b> Device public key for simulation</li>
 * <li><b>simulationDevicePrivateKey:</b> Device private key for simulation</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>JSON Serialization:</b> Simulation fields use {@code @JsonInclude(NON_NULL)}
 * annotation, meaning they are completely excluded from the JSON response when
 * simulation mode is disabled, providing cleaner API responses.
 * </p>
 *
 * <p>
 * <b>Security Note:</b> Simulation fields containing private keys should never 
 * be populated in production environments and are strictly for testing and 
 * development purposes only.
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
 * @see org.ezkey.enrollment.domain.EnrollmentCreateResponse
 * @see EnrollmentCreateRequestDto
 */
@Schema(description = "Response DTO containing created enrollment details")
public class EnrollmentCreateResponseDto {

    /**
     * Unique identifier of the created enrollment.
     * Used to reference this enrollment in subsequent operations.
     */
    @Schema(description = "Unique identifier of the created enrollment", example = "21")
    private Integer enrollmentId;

    /**
     * Challenge number generated for enrollment verification.
     * Used during the enrollment binding and verification process.
     */
    @Schema(description = "Challenge number for enrollment verification", example = "154982")
    private Integer enrollmentChallenge;

    /**
     * Cryptographically signed enrollment proof token for simulation purposes.
     * <p>
     * Contains the signed enrollment proof token that simulates device responses
     * during enrollment flows. Only included in JSON when simulation mode is enabled,
     * allowing comprehensive testing of enrollment verification processes.
     * </p>
     */
    @Schema(description = "Signed enrollment proof token (simulation mode only)", 
            example = "eyJhbGciOiJSUzI1NiJ9...", 
            nullable = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String simulationEnrollmentProofTokenSigned;

    /**
     * Device public key for simulation purposes.
     * <p>
     * Contains the simulated device's public key for cryptographic operations
     * during testing. Only included in JSON when simulation mode is enabled,
     * providing realistic cryptographic interaction simulation.
     * </p>
     */
    @Schema(description = "Device public key (simulation mode only)", 
            example = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...", 
            nullable = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String simulationDevicePublicKey;

    /**
     * Device private key for simulation purposes.
     * <p>
     * Contains the simulated device's private key for signing operations during
     * testing. Only included in JSON when simulation mode is enabled, enabling
     * end-to-end cryptographic testing of enrollment flows.
     * </p>
     * <p>
     * <b>Security Warning:</b> This field contains highly sensitive cryptographic
     * material and should never be populated in production environments. It is
     * exclusively for development and testing purposes.
     * </p>
     */
    @Schema(description = "Device private key (simulation mode only - NEVER in production)", 
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
     * Gets the enrollment challenge.
     *
     * @return the enrollment challenge
     */
    public Integer getEnrollmentChallenge() {
        return enrollmentChallenge;
    }

    /**
     * Sets the enrollment challenge.
     *
     * @param enrollmentChallenge the enrollment challenge to set
     */
    public void setEnrollmentChallenge(Integer enrollmentChallenge) {
        this.enrollmentChallenge = enrollmentChallenge;
    }

    /**
     * Gets the simulation enrollment proof token signed.
     * This method is used only in test/development environments.
     *
     * @return the simulation enrollment proof token signed, or null if not in simulation mode
     */
    public String getSimulationEnrollmentProofTokenSigned() {
        return simulationEnrollmentProofTokenSigned;
    }

    /**
     * Sets the simulation enrollment proof token signed.
     * This method should only be used in test/development environments.
     *
     * @param simulationEnrollmentProofTokenSigned the simulation enrollment proof token signed to set
     */
    public void setSimulationEnrollmentProofTokenSigned(String simulationEnrollmentProofTokenSigned) {
        this.simulationEnrollmentProofTokenSigned = simulationEnrollmentProofTokenSigned;
    }

    /**
     * Gets the simulation device public key.
     * This method is used only in test/development environments.
     *
     * @return the simulation device public key, or null if not in simulation mode
     */
    public String getSimulationDevicePublicKey() {
        return simulationDevicePublicKey;
    }

    /**
     * Sets the simulation device public key.
     * This method should only be used in test/development environments.
     *
     * @param simulationDevicePublicKey the simulation device public key to set
     */
    public void setSimulationDevicePublicKey(String simulationDevicePublicKey) {
        this.simulationDevicePublicKey = simulationDevicePublicKey;
    }

    /**
     * Gets the simulation device private key.
     * This method is used only in test/development environments.
     *
     * @return the simulation device private key, or null if not in simulation mode
     */
    public String getSimulationDevicePrivateKey() {
        return simulationDevicePrivateKey;
    }

    /**
     * Sets the simulation device private key.
     * This method should only be used in test/development environments.
     * <p>
     * <b>Security Warning:</b> This should never be called in production environments.
     * </p>
     *
     * @param simulationDevicePrivateKey the simulation device private key to set
     */
    public void setSimulationDevicePrivateKey(String simulationDevicePrivateKey) {
        this.simulationDevicePrivateKey = simulationDevicePrivateKey;
    }

}