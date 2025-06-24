package org.ezkey.integration;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations")
public class EzkeyIntegrationController {
	private final EzkeyIntegrationService service;

	public EzkeyIntegrationController(EzkeyIntegrationService service) {
		this.service = service;
	}

	@GetMapping
	public ResponseEntity<List<EzkeyIntegration>> getAll() {
		return ResponseEntity.ok(service.getAll());
	}

	@GetMapping("/{id}")
	public ResponseEntity<EzkeyIntegration> getById(@PathVariable Integer id) {
		return ResponseEntity.ok(service.getById(id));
	}

	@PostMapping
	public ResponseEntity<EzkeyIntegrationCreateResponse> create(@RequestBody EzkeyIntegrationCreateRequest app) {
		EzkeyIntegration application = new EzkeyIntegration();
		application.setCode(app.getCode());
		application.setLogo(app.getLogo());
		application.setI18n(app.getI18n());
		application.setActive(true);
		application.setCreatedAt(LocalDateTime.now());
		return ResponseEntity.ok(service.create(application));
	}

	@PutMapping("/{id}")
	public ResponseEntity<Integer> update(@PathVariable Integer id, @RequestBody EzkeyIntegration integration) {
		integration.setId(id);
		return ResponseEntity.ok(service.update(integration));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Integer> delete(@PathVariable Integer id) {
		return ResponseEntity.ok(service.delete(id));
	}
}