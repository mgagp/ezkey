/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: EnrollmentController
 * Description: REST controller for enrollment API v1 using JPA service.
 */

package org.ezkey.admin.controller;

import java.util.List;

import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.dto.EnrollmentCreateRequestDto;
import org.ezkey.enrollment.dto.EnrollmentCreateResponseDto;
import org.ezkey.enrollment.dto.EnrollmentResponseDto;
import org.ezkey.enrollment.mapper.EnrollmentAdminMapper;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for enrollment administration API v1.
 * <p>
 * This controller provides REST endpoints for enrollment management operations
 * in the admin API (internal). It handles CRUD operations for device enrollments,
 * allowing administrators to create, view, and delete enrollments.
 * Uses JPA-based service and DTOs for clean API responses with proper HTTP status codes.
 * </p>
 *
 * <p>
 * <b>Admin API Endpoints (Internal):</b>
 * <ul>
 * <li><b>GET /api/v1/enrollments</b> - List all enrollments</li>
 * <li><b>GET /api/v1/enrollments/{id}</b> - Get enrollment by ID</li>
 * <li><b>POST /api/v1/enrollments</b> - Create new enrollment</li>
 * <li><b>DELETE /api/v1/enrollments/{id}</b> - Delete enrollment</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> This is part of the admin-api (port 9080) for internal 
 * administration purposes. For mobile enrollment binding and verification, see auth-api endpoints.
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
 * @see EnrollmentResponseDto
 * @see EnrollmentCreateRequestDto
 * @see EnrollmentCreateResponseDto
 */
@RestController
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    private final EnrollmentAdminMapper enrollmentMapper;

    /**
     * Constructs the enrollment controller with required dependencies.
     *
     * @param enrollmentService the JPA-based enrollment service
     * @param enrollmentMapper the MapStruct mapper for entity-DTO conversions
     */
    @Autowired
    public EnrollmentController(EnrollmentService enrollmentService,EnrollmentAdminMapper enrollmentMapper){
        this.enrollmentService = enrollmentService;
        this.enrollmentMapper = enrollmentMapper;
    }

    /**
     * Retrieves all enrollments for administrative purposes.
     * <p>
     * Returns a list of all enrollments in the system as response DTOs.
     * This endpoint is used by administrators to monitor and manage device enrollments.
     * </p>
     *
     * @return ResponseEntity containing list of enrollment responses with HTTP 200 status
     */
    @GetMapping
    public ResponseEntity<List<EnrollmentResponseDto>> getAll(){
        List<EnrollmentResponseDto> enrollments = enrollmentMapper.toResponseList(enrollmentService.getAll());
        return ResponseEntity.ok(enrollments);
    }

    /**
     * Retrieves an enrollment by its ID for administrative purposes.
     * <p>
     * Returns the enrollment data as a response DTO for administrative review.
     * Returns 404 if the enrollment is not found.
     * </p>
     *
     * @param id the enrollment ID
     * @return ResponseEntity containing enrollment response with HTTP 200 status, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<EnrollmentResponseDto> getById(@PathVariable Integer id){
        try{
            var enrollment = enrollmentService.getById(id);
            EnrollmentResponseDto response = enrollmentMapper.toResponse(enrollment);
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e){
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Creates a new enrollment for administrative purposes.
     * <p>
     * Creates a new enrollment with the provided data and returns the created enrollment.
     * This generates an enrollment that can later be bound to a mobile device.
     * Returns 201 Created with the created enrollment data including enrollment code and challenge.
     * </p>
     *
     * @param request the enrollment creation request DTO
     * @return ResponseEntity containing created enrollment response with HTTP 201 status
     */
    @PostMapping
    public ResponseEntity<EnrollmentCreateResponseDto> create(@RequestBody EnrollmentCreateRequestDto request){
        try{
            EnrollmentCreateResponse response = enrollmentService.create(enrollmentMapper.toCreateRequest(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentMapper.toCreateResponseDto(response));
        } catch (IllegalArgumentException e){
            // Return 400 Bad Request with validation error message
            return ResponseEntity.badRequest().build();
        } catch (Exception e){
            // Return 500 Internal Server Error for unexpected errors
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Deletes an enrollment by its ID for administrative purposes.
     * <p>
     * Removes an enrollment from the system, effectively unlinking the device from the integration.
     * Returns 204 No Content on successful deletion, or 404 if not found.
     * </p>
     *
     * @param id the enrollment ID to delete
     * @return ResponseEntity with HTTP 204 No Content on success, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id){
        try{
            enrollmentService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e){
            return ResponseEntity.notFound().build();
        }
    }

}
