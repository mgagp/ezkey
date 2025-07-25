/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AuthAttemptController
 * Description: REST controller for authorization attempt API v1 using JPA service.
 */

package org.ezkey.admin.controller;

import java.util.List;

import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.mapper.AuthAttemptMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
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
 * REST controller for authorization attempt administration API v1.
 * <p>
 * This controller provides REST endpoints for authorization attempt management operations
 * in the admin API (internal). It handles CRUD operations for authentication attempts,
 * allowing administrators to create, view, and delete authentication requests.
 * Uses JPA-based service and DTOs for clean API responses with proper HTTP status codes.
 * </p>
 *
 * <p>
 * <b>Admin API Endpoints (Internal):</b>
 * <ul>
 * <li><b>GET /api/v1/auth-attempts</b> - List all authorization attempts</li>
 * <li><b>GET /api/v1/auth-attempts/{id}</b> - Get authorization attempt by ID</li>
 * <li><b>POST /api/v1/auth-attempts</b> - Create new authorization attempt</li>
 * <li><b>DELETE /api/v1/auth-attempts/{id}</b> - Delete authorization attempt</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> This is part of the admin-api (port 9080) for internal 
 * administration purposes. For mobile authentication consumption, see auth-api endpoints.
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
 * @see AuthAttemptService
 * @see AuthAttemptDto
 * @see AuthAttemptCreateRequestDto
 * @see AuthAttemptCreateResponseDto
 */
@RestController
@RequestMapping("/api/v1/auth-attempts")
public class AuthAttemptController {

    private final AuthAttemptService authAttemptService;

    private final AuthAttemptMapper authAttemptMapper;

    /**
     * Constructs the authorization attempt controller with required dependencies.
     *
     * @param authAttemptService the JPA-based authorization attempt service
     * @param authAttemptMapper the MapStruct mapper for entity-DTO conversions
     */
    @Autowired
    public AuthAttemptController(AuthAttemptService authAttemptService,AuthAttemptMapper authAttemptMapper){
        this.authAttemptService = authAttemptService;
        this.authAttemptMapper = authAttemptMapper;
    }

    /**
     * Retrieves all authorization attempts for administrative purposes.
     * <p>
     * Returns a list of all authorization attempts in the system as DTOs.
     * This endpoint is used by administrators to monitor and manage authentication requests.
     * </p>
     *
     * @return ResponseEntity containing list of authorization attempt DTOs with HTTP 200 status
     */
    @GetMapping
    public ResponseEntity<List<AuthAttemptDto>> getAll(){
        List<AuthAttemptDto> authAttempts = authAttemptMapper.toDtoList(authAttemptService.getAll());
        return ResponseEntity.ok(authAttempts);
    }

    /**
     * Retrieves an authorization attempt by its ID for administrative purposes.
     * <p>
     * Returns the authorization attempt data as a DTO for administrative review.
     * Returns 404 if the authorization attempt is not found.
     * </p>
     *
     * @param id the authorization attempt ID
     * @return ResponseEntity containing authorization attempt DTO with HTTP 200 status, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<AuthAttemptDto> getById(@PathVariable("id") Integer id){
        try{
            AuthAttempt authAttempt = authAttemptService.getById(id);
            AuthAttemptDto response = authAttemptMapper.toDto(authAttempt);
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e){
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Creates a new authorization attempt for administrative purposes.
     * <p>
     * Creates a new authorization attempt with the provided data and returns the created attempt.
     * This is typically used by integrating applications to initiate MFA authentication requests.
     * Returns 201 Created with the created authorization attempt data.
     * </p>
     *
     * @param request the authorization attempt creation request DTO
     * @return ResponseEntity containing created authorization attempt response with HTTP 201 status
     */
    @PostMapping
    public ResponseEntity<AuthAttemptCreateResponseDto> create(@RequestBody AuthAttemptCreateRequestDto request){
        try{
            AuthAttemptCreateResponse response = authAttemptService.create(authAttemptMapper.toAuthAttemptCreateRequest(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(authAttemptMapper.toAuthAttemptCreateResponseDto(response));
        } catch (IllegalArgumentException e){
            // Return 400 Bad Request with validation error message
            return ResponseEntity.badRequest().build();
        } catch (Exception e){
            // Return 500 Internal Server Error for unexpected errors
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Deletes an authorization attempt by its ID for administrative purposes.
     * <p>
     * Removes an authorization attempt from the system.
     * Returns 204 No Content on successful deletion, or 404 if not found.
     * </p>
     *
     * @param id the authorization attempt ID to delete
     * @return ResponseEntity with HTTP 204 No Content on success, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Integer id){
        try{
            authAttemptService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e){
            return ResponseEntity.notFound().build();
        }
    }

}
