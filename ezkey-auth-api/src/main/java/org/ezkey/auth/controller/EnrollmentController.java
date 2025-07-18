/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: EnrollmentController
 * Description: REST controller for enrollment API v1 using JPA service.
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for enrollment management operations (API v1).
 * <p>
 * Provides endpoints for binding, confirming, and managing enrollments using JPA-based services and DTOs.
 * Follows RESTful conventions, returns appropriate HTTP status codes, and handles errors gracefully.
 * </p>
 *
 * <b>Endpoints:</b>
 * <ul>
 * <li><b>GET /api/v1/enrollments/bind/{id}</b> - Bind an enrollment to a device</li>
 * <li><b>POST /api/v1/enrollments/verify</b> - Confirm an enrollment</li>
 * </ul>
 *
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative<br>
 * <b>License:</b> MIT<br>
 * <b>Usage:</b> Enrollment API v1 endpoints
 *
 * @author Ezkey contributors
 * @since 2025
 */
@RestController
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    private final EnrollmentAuthMapper enrollmentMapper;

    /**
     * Constructs the EnrollmentController with required dependencies.
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
     * Binds an enrollment to a device.
     * <p>
     * Initiates the enrollment binding process and returns binding information as a DTO.
     * Returns HTTP 400 if the request is invalid, or 409 if the enrollment is in a conflicting state.
     * </p>
     *
     * @param id the enrollment ID to bind
     * @return ResponseEntity containing the binding response DTO, or error status
     */
    @GetMapping("/bind/{id}")
    public ResponseEntity<EnrollmentBindResponseDto> bind(@PathVariable("id") Integer id){
        try{
            EnrollmentBindRequest req = new EnrollmentBindRequest();
            req.setId(id);
            EnrollmentBindResponse response = enrollmentService.bind(req);
            return ResponseEntity.ok(enrollmentMapper.toEnrollmentBindResponseDto(response));
        } catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e){
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Confirms an enrollment.
     * <p>
     * Processes the enrollment confirmation and returns the result as a DTO.
     * Returns HTTP 400 if the request is invalid, or 409 if the enrollment is in a conflicting state.
     * </p>
     *
     * @param req the confirmation request DTO
     * @return ResponseEntity containing the confirmation response DTO, or error status
     */
    @PostMapping("/verify")
    public ResponseEntity<EnrollmentVerifyResponseDto> verify(@RequestBody EnrollmentVerifyRequestDto req){
        try{
            EnrollmentVerifyResponse response = enrollmentService.confirm(enrollmentMapper.toEnrollmentConfirmRequest(req));
            return ResponseEntity.ok(enrollmentMapper.toEnrollmentConfirmResponseDto(response));
        } catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e){
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}