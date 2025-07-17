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
     * Retrieves all enrollments.
     * <p>
     * Returns a list of all enrollments in the system as response DTOs.
     * </p>
     *
     * @return ResponseEntity containing list of enrollment responses
     */
    @GetMapping
    public ResponseEntity<List<EnrollmentResponseDto>> getAll(){
        List<EnrollmentResponseDto> enrollments = enrollmentMapper.toResponseList(enrollmentService.getAll());
        return ResponseEntity.ok(enrollments);
    }

    /**
     * Retrieves an enrollment by its ID.
     * <p>
     * Returns the enrollment data as a response DTO, or 404 if not found.
     * </p>
     *
     * @param id the enrollment ID
     * @return ResponseEntity containing enrollment response or 404 error
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
     * Creates a new enrollment.
     * <p>
     * Creates a new enrollment with the provided data and returns the created enrollment.
     * </p>
     *
     * @param request the enrollment creation request
     * @return ResponseEntity containing created enrollment response with 201 status
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
     * Deletes an enrollment by its ID.
     * <p>
     * Removes an enrollment from the system.
     * </p>
     *
     * @param id the enrollment ID to delete
     * @return ResponseEntity with 204 No Content on success
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
