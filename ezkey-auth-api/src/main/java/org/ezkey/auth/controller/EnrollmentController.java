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
import org.ezkey.enrollment.domain.EnrollmentConfirmResponse;
import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.dto.EnrollmentBindResponseDto;
import org.ezkey.enrollment.dto.EnrollmentConfirmRequestDto;
import org.ezkey.enrollment.dto.EnrollmentConfirmResponseDto;
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
 * REST controller for enrollment API v1 using JPA service.
 * <p>
 * This controller provides REST endpoints for enrollment management operations,
 * using the JPA-based service and new DTOs for clean API responses. It follows
 * RESTful conventions and provides proper HTTP status codes and error handling.
 * </p>
 *
 * <p>
 * <b>API Endpoints:</b>
 * <ul>
 * <li><b>GET /api/v1/enrollments</b> - Get all enrollments</li>
 * <li><b>GET /api/v1/enrollments/{id}</b> - Get enrollment by ID</li>
 * <li><b>POST /api/v1/enrollments</b> - Create new enrollment</li>
 * <li><b>PUT /api/v1/enrollments/{id}</b> - Update enrollment</li>
 * <li><b>DELETE /api/v1/enrollments/{id}</b> - Delete enrollment</li>
 * <li><b>POST /api/v1/enrollments/{id}/read</b> - Mark enrollment as read</li>
 * <li><b>GET /api/v1/enrollments/bind/{id}</b> - Bind enrollment to device</li>
 * <li><b>POST /api/v1/enrollments/confirm</b> - Confirm enrollment</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 * <p>
 * <b>Usage:</b> Enrollment API v1 endpoints
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EzkeyEnrollmentService
 * @see EnrollmentResponseDto
 * @see EnrollmentCreateRequest
 */
@RestController
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    private final EnrollmentAuthMapper enrollmentMapper;

    /**
     * Constructs the enrollment controller with required dependencies.
     *
     * @param enrollmentService the JPA-based enrollment service
     * @param enrollmentMapper the MapStruct mapper for entity-DTO conversions
     */
    @Autowired
    public EnrollmentController(EnrollmentService enrollmentService,EnrollmentAuthMapper enrollmentMapper){
        this.enrollmentService = enrollmentService;
        this.enrollmentMapper = enrollmentMapper;
    }

    /**
     * Binds an enrollment to a device.
     * <p>
     * Handles the enrollment binding process and returns binding information.
     * </p>
     *
     * @param id the enrollment ID to bind
     * @return ResponseEntity containing bind response
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
     * Handles the enrollment confirmation process and returns confirmation information.
     * </p>
     *
     * @param req the confirmation request
     * @return ResponseEntity containing confirmation response
     */
    @PostMapping("/confirm")
    public ResponseEntity<EnrollmentConfirmResponseDto> confirm(@RequestBody EnrollmentConfirmRequestDto req){
        try{
            EnrollmentConfirmResponse response = enrollmentService.confirm(enrollmentMapper.toEnrollmentConfirmRequest(req));
            return ResponseEntity.ok(enrollmentMapper.toEnrollmentConfirmResponseDto(response));
        } catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e){
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
