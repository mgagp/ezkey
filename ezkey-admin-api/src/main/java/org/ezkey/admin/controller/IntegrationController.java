/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the project root for full
 * license information.
 *
 * Controller: IntegrationController Description: REST controller for managing Integration entities through HTTP
 * endpoints.
 */

package org.ezkey.admin.controller;

import java.net.URI;
import java.util.List;

import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.domain.IntegrationResponse;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.dto.IntegrationCreateRequestDto;
import org.ezkey.integration.dto.IntegrationCreateResponseDto;
import org.ezkey.integration.dto.IntegrationResponseDto;
import org.ezkey.integration.mapper.IntegrationControllerMapper;
import org.ezkey.integration.service.IntegrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for integration administration API v1.
 * <p>
 * This controller provides REST endpoints for integration management operations
 * in the admin API (internal). It handles CRUD operations for integrations,
 * which represent applications or systems to be protected by MFA.
 * Uses JPA-based service and DTOs for clean API responses with proper HTTP status codes.
 * </p>
 *
 * <p>
 * <b>Admin API Endpoints (Internal):</b>
 * <ul>
 * <li><b>GET /api/v1/integrations</b> - List all integrations</li>
 * <li><b>GET /api/v1/integrations/{id}</b> - Get integration by ID</li>
 * <li><b>POST /api/v1/integrations</b> - Create new integration</li>
 * <li><b>DELETE /api/v1/integrations/{id}</b> - Delete integration</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> This is part of the admin-api (port 9080) for internal
 * administration purposes. Integrations represent applications that will use Ezkey for MFA.
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
 * @see IntegrationService
 * @see IntegrationResponse
 * @see IntegrationCreateRequest
 * @see IntegrationResponseDto
 * @see IntegrationCreateRequestDto
 */
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations",description = "Integration management API")
public class IntegrationController {

    private final IntegrationService service;

    private final IntegrationControllerMapper mapper;

    /**
     * Constructs the controller with required dependencies.
     *
     * @param service the service layer for Integration operations
     * @param mapper the mapper for converting between entities and DTOs
     */
    public IntegrationController(IntegrationService service,IntegrationControllerMapper mapper){
        this.service = service;
        this.mapper = mapper;
    }

    /**
     * Retrieves all integration entities for administrative purposes.
     * <p>
     * Returns a list of all available integrations in the system.
     * This endpoint is used by administrators to view and manage all applications protected by Ezkey.
     * </p>
     *
     * @return ResponseEntity containing a list of IntegrationResponseDto objects with HTTP 200 status
     */
    @Operation(summary = "Retrieve all integrations",description = "Returns the complete list of integrations configured in the system")
    @ApiResponses(
            value = { @ApiResponse(responseCode = "200",description = "List retrieved successfully"),
                    @ApiResponse(responseCode = "500",description = "Internal server error") })
    @GetMapping
    public ResponseEntity<List<IntegrationResponseDto>> getAll() {
        List<Integration> integrations = service.getAll();
        List<IntegrationResponseDto> integrationResponses = mapper.toResponseList(integrations);
        return ResponseEntity.ok(integrationResponses);
    }

    /**
     * Retrieves a specific integration by its unique identifier for administrative purposes.
     * <p>
     * Returns the integration if found, or throws ResourceNotFoundException if not found.
     * This endpoint provides detailed information about a specific application integration.
     * </p>
     *
     * @param id the unique identifier of the Integration to retrieve
     * @return ResponseEntity containing the IntegrationResponse with HTTP 200 status if found
     * @throws ResourceNotFoundException if the Integration with the given id is not found (returns HTTP 404)
     */
    @Operation(summary = "Retrieve integration by ID",description = "Returns details of a specific integration")
    @ApiResponses(
            value = { @ApiResponse(responseCode = "200",description = "Integration found"),@ApiResponse(responseCode = "404",description = "Integration not found"),
                    @ApiResponse(responseCode = "500",description = "Internal server error") })
    @GetMapping("/{id}")
    public ResponseEntity<IntegrationResponseDto> getById(@Parameter(description = "Unique integration ID",example = "1") @PathVariable("id") Integer id) {
        Integration integration = service.getById(id).orElseThrow(() -> new ResourceNotFoundException("Integration",id));
        return ResponseEntity.ok(mapper.toResponse(integration));
    }

    /**
     * Creates a new integration entity for administrative purposes.
     * <p>
     * Accepts an IntegrationCreateRequestDto and creates a new Integration representing
     * an application or system to be protected by Ezkey MFA. The service layer handles
     * all business logic including default values, cryptographic key generation, and validation.
     * Returns 201 Created with Location header pointing to the created resource.
     * </p>
     *
     * @param request the request DTO containing integration data to create
     * @return ResponseEntity containing the created IntegrationCreateResponse with HTTP 201 status and Location header
     */
    @Operation(summary = "Create new integration",description = "Creates a new integration with the provided data")
    @ApiResponses(
            value = { @ApiResponse(responseCode = "201",description = "Integration created successfully"),@ApiResponse(responseCode = "400",description = "Invalid data"),
                    @ApiResponse(responseCode = "500",description = "Internal server error") })
    @PostMapping
    public ResponseEntity<IntegrationCreateResponseDto> create(
            @Parameter(description = "Integration creation data",required = true) @RequestBody IntegrationCreateRequestDto request) {
        IntegrationCreateResponse savedIntegration = service.createIntegration(mapper.toCreateRequest(request));
        URI location = URI.create("/api/v1/integrations/" + savedIntegration.getId());
        return ResponseEntity.created(location).body(mapper.toCreateResponseDto(savedIntegration));
    }

    /**
     * Deletes an integration entity by its ID for administrative purposes.
     * <p>
     * Removes an integration from the system, effectively disabling MFA protection
     * for the associated application. Returns 204 No Content on successful deletion,
     * or 404 if the integration is not found.
     * </p>
     *
     * @param id the integration ID to delete
     * @return ResponseEntity with HTTP 204 No Content on success, or 404 if not found
     */
    @Operation(summary = "Delete integration",description = "Removes an integration from the system")
    @ApiResponses(
            value = { @ApiResponse(responseCode = "204",description = "Integration deleted successfully"),
                    @ApiResponse(responseCode = "404",description = "Integration not found"),@ApiResponse(responseCode = "500",description = "Internal server error") })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "Integration ID to delete",example = "1") @PathVariable("id") Integer id) {
        // Check if integration exists before deleting
        service.getById(id).orElseThrow(() -> new ResourceNotFoundException("Integration",id));
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
