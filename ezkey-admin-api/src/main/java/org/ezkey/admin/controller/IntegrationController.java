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
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.dto.request.IntegrationCreateDtoRequest;
import org.ezkey.integration.dto.response.IntegrationCreateDtoResponse;
import org.ezkey.integration.dto.response.IntegrationDtoResponse;
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

/**
 * REST controller for integration API v1 using JPA service.
 * <p>
 * This controller provides REST endpoints for integration management operations, using the JPA-based service and DTOs
 * for clean API responses. It follows RESTful conventions and provides proper HTTP status codes and error handling.
 * </p>
 *
 * <p>
 * <b>API Endpoints:</b>
 * <ul>
 * <li><b>GET /api/v1/integrations</b> - Get all integrations</li>
 * <li><b>GET /api/v1/integrations/{id}</b> - Get integration by ID</li>
 * <li><b>POST /api/v1/integrations</b> - Create new integration</li>
 * <li><b>DELETE /api/v1/integrations/{id}</b> - Delete integration</li>
 * <li><b>GET /api/v1/integrations/{id}/i18n</b> - Get integration i18n data</li>
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
 * <b>Usage:</b> Integration API v1 endpoints
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see IntegrationService
 * @see IntegrationResponse
 * @see IntegrationCreateRequest
 */
@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController{

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
     * Retrieves all Integration entities.
     * <p>
     * Returns a list of all available integrations in the system.
     * </p>
     *
     * @return ResponseEntity containing a list of IntegrationResponse objects with HTTP 200 status
     */
    @GetMapping
    public ResponseEntity<List<IntegrationDtoResponse>> getAll(){
        List<Integration> integrations = service.getAll();
        List<IntegrationDtoResponse> integrationResponses = mapper.toResponseList(integrations);
        return ResponseEntity.ok(integrationResponses);
    }

    /**
     * Retrieves a specific Integration by its unique identifier.
     * <p>
     * Returns the integration if found, or throws ResourceNotFoundException if not found.
     * </p>
     *
     * @param id the unique identifier of the Integration to retrieve
     * @return ResponseEntity containing the IntegrationResponse with HTTP 200 status if found
     * @throws ResourceNotFoundException if the Integration with the given id is not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<IntegrationDtoResponse> getById(@PathVariable("id") Integer id){
        Integration integration = service.getById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Integration",id));
        return ResponseEntity.ok(mapper.toResponse(integration));
    }

    /**
     * Creates a new Integration entity.
     * <p>
     * Accepts a IntegrationCreateRequest and creates a new Integration. The service layer handles all business logic
     * including default values, relationship management, and validation.
     * </p>
     *
     * @param request the request containing integration data to create
     * @return ResponseEntity containing the created IntegrationResponse with HTTP 201 status and Location header
     */
    @PostMapping
    public ResponseEntity<IntegrationCreateDtoResponse> create(@RequestBody IntegrationCreateDtoRequest request){
        IntegrationCreateRequest integrationRequest = mapper.toCreateRequest(request);
        IntegrationCreateResponse savedIntegration = service.createIntegration(integrationRequest);
        URI location = URI.create("/api/v1/integrations/" + savedIntegration.getId());
        return ResponseEntity.created(location).body(mapper.toResponse(savedIntegration));
    }

    /**
     * Deletes an Integration entity by its ID.
     * <p>
     * Removes an integration from the system.
     * </p>
     *
     * @param id the integration ID to delete
     * @return ResponseEntity with 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Integer id){
        // Check if integration exists before deleting
        service.getById(id).orElseThrow(() -> new ResourceNotFoundException("Integration",id));
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
