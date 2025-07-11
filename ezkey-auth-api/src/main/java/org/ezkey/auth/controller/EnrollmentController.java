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

import java.util.List;

import org.ezkey.enrollment.dto.EnrollmentBindRequest;
import org.ezkey.enrollment.dto.EnrollmentBindResponse;
import org.ezkey.enrollment.dto.EnrollmentConfirmRequest;
import org.ezkey.enrollment.dto.EnrollmentConfirmResponse;
import org.ezkey.enrollment.dto.request.EnrollmentCreateRequest;
import org.ezkey.enrollment.dto.response.EnrollmentCreateResponse;
import org.ezkey.enrollment.dto.response.EnrollmentResponse;
import org.ezkey.enrollment.mapper.EnrollmentMapper;
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
 * @see EnrollmentResponse
 * @see EnrollmentCreateRequest
 */
@RestController
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    private final EnrollmentMapper enrollmentMapper;

    /**
     * Constructs the enrollment controller with required dependencies.
     *
     * @param enrollmentService the JPA-based enrollment service
     * @param enrollmentMapper the MapStruct mapper for entity-DTO conversions
     */
    @Autowired
    public EnrollmentController(EnrollmentService enrollmentService,EnrollmentMapper enrollmentMapper){
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
    public ResponseEntity<List<EnrollmentResponse>> getAll(){
        List<EnrollmentResponse> enrollments = enrollmentMapper.toResponseList(enrollmentService.getAll());
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
    public ResponseEntity<EnrollmentResponse> getById(@PathVariable Integer id){
        try{
            var enrollment = enrollmentService.getById(id);
            EnrollmentResponse response = enrollmentMapper.toResponse(enrollment);
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
    public ResponseEntity<EnrollmentCreateResponse> create(@RequestBody EnrollmentCreateRequest request){
        try{
            EnrollmentCreateResponse response = enrollmentService.create(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

    /**
     * Marks an enrollment as read by the device.
     * <p>
     * Updates the enrollment_read flag to true, indicating the enrollment has been read.
     * </p>
     *
     * @param id the enrollment ID to mark as read
     * @return ResponseEntity with 200 OK on success
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> setDeviceReadTrue(@PathVariable Integer id){
        try{
            enrollmentService.setDeviceReadTrue(id);
            return ResponseEntity.ok().build();
        } catch (ResourceNotFoundException e){
            return ResponseEntity.notFound().build();
        }
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
    public ResponseEntity<EnrollmentBindResponse> bind(@PathVariable Integer id){
        try{
            EnrollmentBindRequest req = new EnrollmentBindRequest();
            req.setId(id);
            EnrollmentBindResponse response = enrollmentService.bind(req);
            return ResponseEntity.ok(response);
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
    public ResponseEntity<EnrollmentConfirmResponse> confirm(@RequestBody EnrollmentConfirmRequest req){
        try{
            EnrollmentConfirmResponse response = enrollmentService.confirm(req);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e){
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
