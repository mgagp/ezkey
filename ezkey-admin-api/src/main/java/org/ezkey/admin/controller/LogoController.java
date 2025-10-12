/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: LogoController
 * Description: REST controller for managing Logo entities through HTTP endpoints.
 */

package org.ezkey.admin.controller;

import java.net.URI;
import java.util.List;

import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.LogoResponse;
import org.ezkey.integration.dto.LogoCreateRequestDto;
import org.ezkey.integration.dto.LogoResponseDto;
import org.ezkey.integration.mapper.LogoControllerMapper;
import org.ezkey.integration.service.LogoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for logo administration API v1.
 * <p>
 * This controller provides REST endpoints for logo management operations
 * in the admin API (internal). It handles CRUD operations for logos,
 * which can be referenced by integrations to provide consistent branding.
 * </p>
 *
 * <p>
 * <b>Admin API Endpoints (Internal):</b>
 * <ul>
 * <li><b>GET /api/v1/logos</b> - List all logos</li>
 * <li><b>GET /api/v1/logos/{id}</b> - Get logo by ID</li>
 * <li><b>POST /api/v1/logos</b> - Create new logo</li>
 * <li><b>PUT /api/v1/logos/{id}</b> - Update existing logo</li>
 * <li><b>DELETE /api/v1/logos/{id}</b> - Delete logo</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> This is part of the admin-api (port 9080) for internal
 * administration purposes. Logos are used by integrations for branding purposes.
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
 */
@RestController
@RequestMapping("/api/v1/logos")
@Tag(name = "Logos", description = "Logo management API")
public class LogoController {

    private final LogoService logoService;
    private final LogoControllerMapper mapper;

    /**
     * Constructs the controller with required dependencies.
     *
     * @param logoService the service layer for Logo operations
     * @param mapper the mapper for converting between service and controller DTOs
     */
    public LogoController(LogoService logoService, LogoControllerMapper mapper) {
        this.logoService = logoService;
        this.mapper = mapper;
    }

    /**
     * Retrieves all logo entities for administrative purposes.
     * <p>
     * Returns a list of all available logos in the system.
     * This endpoint is used by administrators to view and manage all logos.
     * </p>
     *
     * @return ResponseEntity containing a list of LogoResponseDto objects with HTTP 200 status
     */
    @Operation(summary = "Retrieve all logos", description = "Returns the complete list of logos configured in the system")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "List retrieved successfully"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    @GetMapping
    public ResponseEntity<List<LogoResponseDto>> getAll() {
        List<LogoResponse> logos = logoService.getAllLogos();
        return ResponseEntity.ok(mapper.toResponseDtoList(logos));
    }

    /**
     * Retrieves a specific logo by its unique identifier for administrative purposes.
     * <p>
     * Returns the logo if found, or throws ResourceNotFoundException if not found.
     * This endpoint provides detailed information about a specific logo.
     * </p>
     *
     * @param id the unique identifier of the Logo to retrieve
     * @return ResponseEntity containing the LogoResponseDto with HTTP 200 status if found
     * @throws ResourceNotFoundException if the Logo with the given id is not found (returns HTTP 404)
     */
    @Operation(summary = "Retrieve logo by ID", description = "Returns details of a specific logo")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "Logo found"),
                    @ApiResponse(responseCode = "404", description = "Logo not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    @GetMapping("/{id}")
    public ResponseEntity<LogoResponseDto> getById(
            @Parameter(description = "Unique logo ID", example = "1") @PathVariable("id") Integer id) {
        LogoResponse logo = logoService.getLogoById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Logo", id));
        return ResponseEntity.ok(mapper.toResponseDto(logo));
    }

    /**
     * Creates a new logo entity for administrative purposes.
     * <p>
     * Accepts a LogoCreateRequestDto and creates a new Logo that can be referenced
     * by integrations. At least one of logoUrl or logoData must be provided.
     * Returns 201 Created with Location header pointing to the created resource.
     * </p>
     *
     * @param request the request DTO containing logo data to create
     * @return ResponseEntity containing the created LogoResponseDto with HTTP 201 status and Location header
     */
    @Operation(summary = "Create new logo", description = "Creates a new logo with the provided data")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "201", description = "Logo created successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid data"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    @PostMapping
    public ResponseEntity<LogoResponseDto> create(
            @Parameter(description = "Logo creation data", required = true) @RequestBody LogoCreateRequestDto request) {
        LogoResponse savedLogo = logoService.createLogo(mapper.toCreateRequest(request));
        URI location = URI.create("/api/v1/logos/" + savedLogo.getId());
        return ResponseEntity.created(location).body(mapper.toResponseDto(savedLogo));
    }

    /**
     * Updates an existing logo entity for administrative purposes.
     * <p>
     * Accepts a LogoCreateRequestDto and updates the specified logo.
     * At least one of logoUrl or logoData must be provided.
     * Returns 200 OK with the updated logo information.
     * </p>
     *
     * @param id the unique identifier of the Logo to update
     * @param request the request DTO containing logo data to update
     * @return ResponseEntity containing the updated LogoResponseDto with HTTP 200 status
     * @throws ResourceNotFoundException if the Logo with the given id is not found (returns HTTP 404)
     */
    @Operation(summary = "Update existing logo", description = "Updates an existing logo with the provided data")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "Logo updated successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid data"),
                    @ApiResponse(responseCode = "404", description = "Logo not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    @PutMapping("/{id}")
    public ResponseEntity<LogoResponseDto> update(
            @Parameter(description = "Unique logo ID", example = "1") @PathVariable("id") Integer id,
            @Parameter(description = "Logo update data", required = true) @RequestBody LogoCreateRequestDto request) {
        try {
            LogoResponse updatedLogo = logoService.updateLogo(id, mapper.toCreateRequest(request));
            return ResponseEntity.ok(mapper.toResponseDto(updatedLogo));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("not found")) {
                throw new ResourceNotFoundException("Logo", id);
            }
            throw e;
        }
    }

    /**
     * Deletes a logo entity by its ID for administrative purposes.
     * <p>
     * Removes a logo from the system. Note that integrations referencing this logo
     * will have their logo_id set to NULL (ON DELETE SET NULL).
     * Returns 204 No Content on successful deletion, or 404 if the logo is not found.
     * </p>
     *
     * @param id the logo ID to delete
     * @return ResponseEntity with HTTP 204 No Content on success, or 404 if not found
     * @throws ResourceNotFoundException if the Logo with the given id is not found (returns HTTP 404)
     */
    @Operation(summary = "Delete logo", description = "Removes a logo from the system")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "204", description = "Logo deleted successfully"),
                    @ApiResponse(responseCode = "404", description = "Logo not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Logo ID to delete", example = "1") @PathVariable("id") Integer id) {
        try {
            logoService.deleteLogo(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Logo", id);
        }
    }
}
