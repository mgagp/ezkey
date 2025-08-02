/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentBindRequestDto
 * Description: Request DTO for enrollment binding initiation in auth API.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for enrollment binding initiation in auth API.
 * <p>
 * This DTO represents the minimal request data needed to initiate the enrollment
 * binding process for mobile devices. It contains only the enrollment identifier
 * that the mobile device obtained through QR code scanning or deep links.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by mobile devices to start the enrollment binding
 * process with the auth-api. This is typically the first step after a user scans
 * a QR code or follows an enrollment deep link in the mobile app.
 * </p>
 *
 * <p>
 * <b>Enrollment Flow:</b> This request initiates the binding process which will
 * return enrollment details, integration information, and cryptographic data
 * needed for the mobile device to complete enrollment verification.
 * </p>
 *
 * <p>
 * <b>Fields:</b>
 * <ul>
 * <li><b>id:</b> The enrollment ID to bind to the mobile device</li>
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
 * @see org.ezkey.enrollment.domain.EnrollmentBindRequest
 * @see EnrollmentBindResponseDto
 */
@Schema(description = "Request DTO for enrollment binding initiation")
public class EnrollmentBindRequestDto {

    /**
     * The enrollment ID to bind to the mobile device.
     * <p>
     * Must reference an existing enrollment created through the admin API.
     * This ID is typically obtained by the mobile device through QR code
     * scanning or deep link navigation from the integration website.
     * </p>
     */
    @Schema(description = "Enrollment ID to bind to the mobile device", 
            example = "123", 
            required = true)
    private Integer id;

    /**
     * Gets the enrollment ID.
     *
     * @return the enrollment ID
     */
    public Integer getId(){
        return id;
    }

    /**
     * Sets the enrollment ID.
     *
     * @param id the enrollment ID to set
     */
    public void setId(Integer id){
        this.id = id;
    }
}
