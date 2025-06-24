/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: IntegrationController
 * Description: REST controller for managing Integration entities through HTTP endpoints.
 */

package org.ezkey.integration.controller;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.ezkey.integration.domain.entity.EzkeyIntegration;
import org.ezkey.integration.dto.request.CreateIntegrationRequest;
import org.ezkey.integration.dto.response.IntegrationResponse;
import org.ezkey.integration.mapper.IntegrationMapper;
import org.ezkey.integration.service.EzkeyIntegrationService;
import org.ezkey.integration.exception.ResourceNotFoundException;

/**
 * REST controller for managing Integration entities.
 * <p>
 * This controller provides HTTP endpoints for CRUD operations on Integration entities.
 * It follows REST conventions and returns appropriate HTTP status codes.
 * </p>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 * <p><b>Base Path:</b> /api/v1/integrations</p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {
	private final EzkeyIntegrationService service;
	private final IntegrationMapper mapper;
	
	/**
	 * Constructs the controller with required dependencies.
	 *
	 * @param service the service layer for Integration operations
	 * @param mapper the mapper for converting between entities and DTOs
	 */
	public IntegrationController(EzkeyIntegrationService service, IntegrationMapper mapper) {
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
	public ResponseEntity<List<IntegrationResponse>> getAll() {
		List<EzkeyIntegration> integrations = service.getAll();
		List<IntegrationResponse> integrationResponses = mapper.toResponseList(integrations);
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
	public ResponseEntity<IntegrationResponse> getById(@PathVariable Integer id) {
		EzkeyIntegration integration = service.getById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Integration", id));
		return ResponseEntity.ok(mapper.toResponse(integration));
	}

	/**
	 * Creates a new Integration entity.
	 * <p>
	 * Accepts a CreateIntegrationRequest and creates a new Integration with default values
	 * for active status and creation timestamp. Returns the created entity with HTTP 201 status
	 * and Location header pointing to the new resource.
	 * </p>
	 *
	 * @param request the request containing integration data to create
	 * @return ResponseEntity containing the created IntegrationResponse with HTTP 201 status and Location header
	 */
	@PostMapping
	public ResponseEntity<IntegrationResponse> create(@RequestBody CreateIntegrationRequest request) {
		EzkeyIntegration integration = mapper.toEntity(request);
		integration.setActive(true);
		integration.setCreatedAt(LocalDateTime.now());
		
		// Set the parent reference for each i18n if present
		if (integration.getI18n() != null) {
			integration.getI18n().forEach(i18n -> i18n.setIntegration(integration));
		}
		
		EzkeyIntegration savedIntegration = service.save(integration);
		URI location = URI.create("/api/v1/integrations/" + savedIntegration.getId());
		return ResponseEntity.created(location).body(mapper.toResponse(savedIntegration));
	}
} 