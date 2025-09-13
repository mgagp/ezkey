/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AuthAttemptController
 * Description: REST controller for mobile authentication attempt API v1.
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for mobile authentication attempt API v1.
 * <p>
 * This controller provides REST endpoints for mobile device authentication operations
 * in the auth-api (external). It handles the mobile authentication flow where devices
 * check for pending authentication requests and submit their responses.
 * Uses cryptographic signatures for secure authentication validation.
 * </p>
 *
 * <p>
 * <b>Auth API Endpoints (Mobile):</b>
 * <ul>
 * <li><b>POST /api/v1/auth-attempts/pending/{enrollmentId}</b> - Check for pending authentication requests</li>
 * <li><b>POST /api/v1/auth-attempts/respond/{authAttemptId}</b> - Submit authentication response</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> This is part of the auth-api (port 8080) for mobile device
 * consumption. The mobile app uses these endpoints to implement the pull-based
 * authentication model with cryptographic signature validation.
 * </p>
 *
 * <p>
 * <b>Security Model:</b> All requests include cryptographic signatures in the body
 * to ensure request authenticity and prevent unauthorized access.
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
 * @see AuthAttemptPendingRequestDto
 * @see AuthAttemptPendingResponseDto
 * @see AuthAttemptRespondRequestDto
 * @see AuthAttemptRespondResponseDto
 */
@RestController
@RequestMapping("/api/v1/auth-attempts")
@Tag(name = "Authentication Attempts", description = "Mobile authentication attempt operations for checking pending requests and submitting responses")
public class AuthAttemptController {

    // Rate limiting endpoint constants
    public static final String ENDPOINT_PENDING = "/pending";
    public static final String FULL_PATH_PENDING = "/api/v1/auth-attempts" + ENDPOINT_PENDING;

    private final AuthAttemptService authAttemptService;

    private final AuthAttemptMapper authAttemptMapper;

    /**
     * Constructs the mobile authentication attempt controller with required dependencies.
     *
     * @param authAttemptService the JPA-based authorization attempt service
     * @param authAttemptMapper the MapStruct mapper for entity-DTO conversions
     */
    public AuthAttemptController(AuthAttemptService authAttemptService,AuthAttemptMapper authAttemptMapper){
        this.authAttemptService = authAttemptService;
        this.authAttemptMapper = authAttemptMapper;
    }

    /**
     * Checks for pending authentication requests for a mobile device.
     * <p>
     * The mobile device polls this endpoint to check if there are pending authentication
     * requests for its enrollment. The request body contains a cryptographic signature
     * proving the authenticity of the request. Returns 200 with the pending request
     * details, or 204 No Content if no pending requests exist.
     * </p>
     *
     * <p>
     * <b>MFA Security Context:</b> This endpoint implements the pull-based authentication
     * model where devices regularly poll for pending authentication requests. The absence
     * of pending requests (204 No Content) is a normal operational state, not an error.
     * </p>
     *
     * @param id the enrollment ID to check for pending requests
     * @param request the pending request DTO containing cryptographic signature
     * @return ResponseEntity containing pending authentication details with HTTP 200,
     * or 204 No Content if no pending requests, or 400 for invalid requests
     */
    @PostMapping("/pending/{enrollmentId}")
    @Operation(summary = "Check for pending authentication requests", 
               description = "Mobile device polls for pending authentication requests. Returns 200 with request details or 204 if no pending requests exist.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pending authentication request found", 
                    content = @Content(schema = @Schema(implementation = AuthAttemptPendingResponseDto.class))),
        @ApiResponse(responseCode = "204", description = "No pending authentication requests found (normal state)"),
        @ApiResponse(responseCode = "400", description = "Invalid request (enrollment not found, invalid signature)"),
        @ApiResponse(responseCode = "409", description = "State conflict (auth attempt already read)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AuthAttemptPendingResponseDto> pending(@PathVariable("enrollmentId") Integer id,@RequestBody AuthAttemptPendingRequestDto request) {
        request.setEnrollmentId(id);
        AuthAttemptPendingResponse response = authAttemptService.pending(authAttemptMapper.toAuthAttemptPendingRequest(request));
        return ResponseEntity.ok(authAttemptMapper.toAuthAttemptPendingResponseDto(response));
    }

    /**
     * Submits the mobile device's response to an authentication request.
     * <p>
     * The mobile device uses this endpoint to submit the user's authentication response
     * (approved, denied, or signature) for a specific authentication attempt.
     * The response includes cryptographic signatures for validation.
     * Returns 200 with the response status, or appropriate error codes for invalid requests.
     * </p>
     *
     * @param id the authentication attempt ID to respond to
     * @param request the response request DTO containing user's decision and signatures
     * @return ResponseEntity containing response confirmation with HTTP 200,
     * or 400 for invalid requests, or 409 for conflicting states
     */
    @PostMapping("/respond/{authAttemptId}")
    @Operation(summary = "Submit authentication response", 
               description = "Submits mobile device's response to an authentication request")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Authentication response submitted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid response data or validation failed"),
        @ApiResponse(responseCode = "409", description = "Authentication attempt state conflict"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AuthAttemptRespondResponseDto> respond(@PathVariable("authAttemptId") Integer id,@RequestBody AuthAttemptRespondRequestDto request) {
        request.setAuthAttemptId(id);
        AuthAttemptRespondResponse response = authAttemptService.respond(authAttemptMapper.toAuthAttemptRespondRequest(request));
        return ResponseEntity.ok(authAttemptMapper.toAuthAttemptRespondResponseDto(response));
    }
}
