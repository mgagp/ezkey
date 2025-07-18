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

import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.dto.AuthAttemptPendingRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptPendingResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondResponseDto;
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

    @PostMapping("/pending/{enrollmentId}")
    public ResponseEntity<AuthAttemptPendingResponseDto> pending(@PathVariable("enrollmentId") Integer id,@RequestBody AuthAttemptPendingRequestDto request){
        try{
            request.setEnrollmentId(id);
            AuthAttemptPendingResponse response = authAttemptService.pending(authAttemptMapper.toAuthAttemptPendingRequest(request));
            return ResponseEntity.ok(authAttemptMapper.toAuthAttemptPendingResponseDto(response));
        } catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e){
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Respond to an authorization attempt.
     * <p>
     * Handles the authorization attempt respond process and returns respond information.
     * </p>
     *
     * @param request the respond request
     * @return ResponseEntity containing respond response
     */
    @PostMapping("/respond/{authAttemptId}")
    public ResponseEntity<AuthAttemptRespondResponseDto> respond(@PathVariable("authAttemptId") Integer id,@RequestBody AuthAttemptRespondRequestDto request){
        try{
            request.setAuthAttemptId(id);
            AuthAttemptRespondResponse response = authAttemptService.complete(authAttemptMapper.toAuthAttemptRespondRequest(request));
            return ResponseEntity.ok(authAttemptMapper.toAuthAttemptRespondResponseDto(response));
        } catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e){
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
