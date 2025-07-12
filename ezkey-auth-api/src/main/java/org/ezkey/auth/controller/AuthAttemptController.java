/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AuthAttemptController
 * Description: REST controller for authorization attempt API v1 using JPA service.
 */

package org.ezkey.auth.controller;

import java.util.List;

import org.ezkey.authattempt.domain.AuthAttemptCompleteResponse;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptInitiateResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptCompleteRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCompleteResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.dto.AuthAttemptInitiateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptInitiateResponseDto;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authorization attempt API v1 using JPA service.
 * <p>
 * This controller provides REST endpoints for authorization attempt management operations,
 * using the JPA-based service and DTOs for clean API responses. It follows
 * RESTful conventions and provides proper HTTP status codes and error handling.
 * </p>
 *
 * <p>
 * <b>API Endpoints:</b>
 * <ul>
 * <li><b>GET /api/v1/auth-attempts</b> - Get all authorization attempts</li>
 * <li><b>GET /api/v1/auth-attempts/{id}</b> - Get authorization attempt by ID</li>
 * <li><b>POST /api/v1/auth-attempts</b> - Create new authorization attempt</li>
 * <li><b>PUT /api/v1/auth-attempts/{id}</b> - Update authorization attempt</li>
 * <li><b>DELETE /api/v1/auth-attempts/{id}</b> - Delete authorization attempt</li>
 * <li><b>POST /api/v1/auth-attempts/initiate</b> - Initiate authorization attempt</li>
 * <li><b>POST /api/v1/auth-attempts/complete</b> - Complete authorization attempt</li>
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
 * <b>Usage:</b> Authorization attempt API v1 endpoints
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EzkeyAuthAttemptService
 * @see EzkeyAuthAttemptDto
 * @see EzkeyAuthAttemptCreateDtoRequest
 */
@RestController
@RequestMapping("/api/v1/authattempts")
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
     * Retrieves all authorization attempts.
     * <p>
     * Returns a list of all authorization attempts in the system as DTOs.
     * </p>
     *
     * @return ResponseEntity containing list of authorization attempt DTOs
     */
    @GetMapping
    public ResponseEntity<List<AuthAttemptDto>> getAll(){
        List<AuthAttemptDto> authAttempts = authAttemptMapper.toDtoList(authAttemptService.getAll());
        return ResponseEntity.ok(authAttempts);
    }

    /**
     * Retrieves an authorization attempt by its ID.
     * <p>
     * Returns the authorization attempt data as a DTO, or 404 if not found.
     * </p>
     *
     * @param id the authorization attempt ID
     * @return ResponseEntity containing authorization attempt DTO or 404 error
     */
    @GetMapping("/{id}")
    public ResponseEntity<AuthAttemptDto> getById(@PathVariable Integer id){
        try{
            AuthAttempt authAttempt = authAttemptService.getById(id);
            AuthAttemptDto response = authAttemptMapper.toDto(authAttempt);
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e){
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Creates a new authorization attempt.
     * <p>
     * Creates a new authorization attempt with the provided data and returns the created attempt.
     * </p>
     *
     * @param request the authorization attempt creation request
     * @return ResponseEntity containing created authorization attempt response with 201 status
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
     * Updates an authorization attempt by its ID.
     * <p>
     * Updates an existing authorization attempt with the provided data.
     * </p>
     *
     * @param id the authorization attempt ID to update
     * @param authAttempt the updated authorization attempt data
     * @return ResponseEntity containing updated authorization attempt DTO
     */
    @PutMapping("/{id}")
    public ResponseEntity<AuthAttemptDto> update(@PathVariable Integer id,@RequestBody AuthAttempt authAttempt){
        try{
            authAttempt.setAuthAttemptId(id);
            AuthAttempt updatedAuthAttempt = authAttemptService.update(authAttempt);
            AuthAttemptDto response = authAttemptMapper.toDto(updatedAuthAttempt);
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e){
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Deletes an authorization attempt by its ID.
     * <p>
     * Removes an authorization attempt from the system.
     * </p>
     *
     * @param id the authorization attempt ID to delete
     * @return ResponseEntity with 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id){
        try{
            authAttemptService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e){
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Initiates an authorization attempt.
     * <p>
     * Handles the authorization attempt initiation process and returns initiation information.
     * </p>
     *
     * @param request the initiation request
     * @return ResponseEntity containing initiation response
     */
    @PostMapping("/initiate/{id}")
    public ResponseEntity<AuthAttemptInitiateResponseDto> initiate(@PathVariable Integer id,@RequestBody AuthAttemptInitiateRequestDto request){
        try{
            request.setEnrollmentId(id);
            AuthAttemptInitiateResponse response = authAttemptService.initiate(authAttemptMapper.toAuthAttemptInitiateRequest(request));
            return ResponseEntity.ok(authAttemptMapper.toAuthAttemptInitiateResponseDto(response));
        } catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e){
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Completes an authorization attempt.
     * <p>
     * Handles the authorization attempt completion process and returns completion information.
     * </p>
     *
     * @param request the completion request
     * @return ResponseEntity containing completion response
     */
    @PostMapping("/complete/{id}")
    public ResponseEntity<AuthAttemptCompleteResponseDto> complete(@PathVariable Integer id,@RequestBody AuthAttemptCompleteRequestDto request){
        try{
            request.setAuthAttemptId(id);
            AuthAttemptCompleteResponse response = authAttemptService.complete(authAttemptMapper.toAuthAttemptCompleteRequest(request));
            return ResponseEntity.ok(authAttemptMapper.toAuthAttemptCompleteResponseDto(response));
        } catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e){
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
