/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: EnrollmentController
 * Description: REST controller for mobile enrollment API v1.
 */

package org.ezkey.auth.controller;

import org.ezkey.enrollment.domain.EnrollmentBindRequest;
import org.ezkey.enrollment.domain.EnrollmentBindResponse;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.dto.EnrollmentBindResponseDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyRequestDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyResponseDto;
import org.ezkey.enrollment.mapper.EnrollmentAuthMapper;
import org.ezkey.enrollment.service.EnrollmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for mobile enrollment API v1.
 * <p>
 * This controller provides REST endpoints for mobile device enrollment operations
 * in the auth-api (external). It handles the enrollment binding and verification
 * process where mobile devices link themselves to user accounts and complete
 * the cryptographic enrollment setup.
 * </p>
 *
 * <p>
 * <b>Auth API Endpoints (Mobile):</b>
 * <ul>
 * <li><b>GET /api/v1/enrollments/bind/{enrollmentId}</b> - Initiate device binding to enrollment</li>
 * <li><b>POST /api/v1/enrollments/verify</b> - Complete enrollment verification process</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> This is part of the auth-api (port 8080) for mobile device
 * consumption. Mobile apps use these endpoints to complete the enrollment process
 * by linking devices to user accounts through cryptographic key exchange.
 * </p>
 *
 * <p>
 * <b>Enrollment Flow:</b>
 * <ol>
 * <li>Mobile device scans QR code or deep link containing enrollmentId</li>
 * <li>Device calls bind endpoint to retrieve enrollment details and challenges</li>
 * <li>Device generates cryptographic keys and signs enrollment code</li>
 * <li>Device calls verify endpoint to complete enrollment with signatures</li>
 * </ol>
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
 * @see EnrollmentService
 * @see EnrollmentBindResponseDto
 * @see EnrollmentVerifyRequestDto
 * @see EnrollmentVerifyResponseDto
 */
@RestController
@RequestMapping("/api/v1/enrollments")
@Tag(name = "Enrollments", description = "Mobile device enrollment operations for binding devices to user accounts and completing verification")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    private final EnrollmentAuthMapper enrollmentMapper;

    /**
     * Constructs the mobile enrollment controller with required dependencies.
     *
     * @param enrollmentService JPA-based enrollment service
     * @param enrollmentMapper MapStruct mapper for entity-DTO conversions
     */
    @Autowired
    public EnrollmentController(EnrollmentService enrollmentService,EnrollmentAuthMapper enrollmentMapper){
        this.enrollmentService = enrollmentService;
        this.enrollmentMapper = enrollmentMapper;
    }

    /**
     * Initiates the device binding process for mobile enrollment.
     * <p>
     * The mobile device calls this endpoint to start the enrollment binding process.
     * It retrieves enrollment information including the integration public key,
     * proof token the device must use, and challenge data needed to complete the enrollment.
     * This is typically called after scanning a QR code or following a deep link.
     * </p>
     *
     * @param enrollmentId the enrollment ID to bind the device to
     * @param acceptLanguage the preferred language for i18n fields (from Accept-Language header)
     * @return ResponseEntity containing enrollment binding information with HTTP 200,
     * or 400 for invalid enrollment ID, or 409 if enrollment is already bound
     */
    @GetMapping("/bind/{enrollmentId}")
    public ResponseEntity<EnrollmentBindResponseDto> bind(
            @PathVariable("enrollmentId") Integer enrollmentId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        try{
            String language = (acceptLanguage != null && !acceptLanguage.isEmpty())
                ? acceptLanguage.split(",")[0].split("-")[0]
                : "en";
            EnrollmentBindRequest request = new EnrollmentBindRequest();
            request.setEnrollmentId(enrollmentId);
            request.setLanguage(language);
            EnrollmentBindResponse response = enrollmentService.bind(request);
            return ResponseEntity.ok(enrollmentMapper.toEnrollmentBindResponseDto(response));
        } catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e){
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Completes the enrollment verification process for mobile devices.
     * <p>
     * The mobile device submits its cryptographic keys and signed proof token
     * to finalize the enrollment process. The device generates a public/private key pair,
     * signs the proof token with its private key, and submits the public key
     * and signature for verification. Once verified, the enrollment becomes active
     * and the device can authenticate users. The proof token must be the one obtained from the bind endpoint.
     * </p>
     *
     * @param req the verification request DTO containing device keys and signatures
     * @return ResponseEntity containing verification confirmation with HTTP 200,
     * or 400 for invalid verification data, or 409 if enrollment state conflicts
     */
    @PostMapping("/verify")
    public ResponseEntity<EnrollmentVerifyResponseDto> verify(@RequestBody EnrollmentVerifyRequestDto req) {
        try{
            EnrollmentVerifyResponse response = enrollmentService.verify(enrollmentMapper.toEnrollmentVerifyRequest(req));
            return ResponseEntity.ok(enrollmentMapper.toEnrollmentVerifyResponseDto(response));
        } catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e){
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}