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

import org.ezkey.authattempt.domain.AuthAttemptCompleteResponse;
import org.ezkey.authattempt.domain.AuthAttemptInitiateResponse;
import org.ezkey.authattempt.dto.AuthAttemptCompleteRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCompleteResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptInitiateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptInitiateResponseDto;
import org.ezkey.authattempt.mapper.AuthAttemptMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
     * Initiates an authorization attempt.
     * <p>
     * Handles the authorization attempt initiation process and returns initiation information.
     * </p>
     *
     * @param request the initiation request
     * @return ResponseEntity containing initiation response
     */
    @PostMapping("/initiate/{id}")
    public ResponseEntity<AuthAttemptInitiateResponseDto> initiate(@PathVariable("id") Integer id,@RequestBody AuthAttemptInitiateRequestDto request){
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
    public ResponseEntity<AuthAttemptCompleteResponseDto> complete(@PathVariable("id") Integer id,@RequestBody AuthAttemptCompleteRequestDto request){
        try{
            request.setEnrollmentId(id);
            AuthAttemptCompleteResponse response = authAttemptService.complete(authAttemptMapper.toAuthAttemptCompleteRequest(request));
            return ResponseEntity.ok(authAttemptMapper.toAuthAttemptCompleteResponseDto(response));
        } catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e){
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
