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

@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {
	private final EzkeyIntegrationService service;
	private final IntegrationMapper mapper;
	
	public IntegrationController(EzkeyIntegrationService service, IntegrationMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@GetMapping
	public ResponseEntity<List<IntegrationResponse>> getAll() {
		List<EzkeyIntegration> integrations = service.getAll();
		List<IntegrationResponse> integrationResponses = mapper.toResponseList(integrations);
		return ResponseEntity.ok(integrationResponses);
	}

	@GetMapping("/{id}")
	public ResponseEntity<IntegrationResponse> getById(@PathVariable Integer id) {
		EzkeyIntegration integration = service.getById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Integration", id));
		return ResponseEntity.ok(mapper.toResponse(integration));
	}

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