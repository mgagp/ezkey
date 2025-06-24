package org.ezkey.enrollment;

import java.util.List;

import org.ezkey.enrollment.dto.EzkeyEnrollmentBindRequest;
import org.ezkey.enrollment.dto.EzkeyEnrollmentBindResponse;
import org.ezkey.enrollment.dto.EzkeyEnrollmentConfirmRequest;
import org.ezkey.enrollment.dto.EzkeyEnrollmentConfirmResponse;
import org.ezkey.enrollment.dto.EzkeyEnrollmentCreateDtoRequest;
import org.ezkey.enrollment.dto.EzkeyEnrollmentCreateDtoResponse;
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
@RequestMapping("/api/v1/enrollments")
public class EzkeyEnrollmentController {

	private final EzkeyEnrollmentService service;

	public EzkeyEnrollmentController(EzkeyEnrollmentService service) {
		this.service = service;
	}

	@GetMapping
	public ResponseEntity<List<EzkeyEnrollment>> getAll() {
		return ResponseEntity.ok(service.getAll());
	}

	@GetMapping("/{id}")
	public ResponseEntity<EzkeyEnrollment> getById(@PathVariable Integer id) {
		return ResponseEntity.ok(service.getById(id));
	}

	@PostMapping
	public ResponseEntity<EzkeyEnrollmentCreateDtoResponse> create(@RequestBody EzkeyEnrollmentCreateDtoRequest request) {
		return ResponseEntity.ok(service.create(request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<Integer> update(@PathVariable Integer id, @RequestBody EzkeyEnrollment enrollment) {
		enrollment.setEnrollmentId(id);
		return ResponseEntity.ok(service.update(enrollment));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Integer> delete(@PathVariable Integer id) {
		return ResponseEntity.ok(service.delete(id));
	}

	@PostMapping("/{id}/read")
	public ResponseEntity<Integer> setDeviceReadTrue(@PathVariable Integer id) {
		return ResponseEntity.ok(service.setDeviceReadTrue(id));
	}

	@GetMapping("/bind/{id}")
	public EzkeyEnrollmentBindResponse bind(@PathVariable Integer id) {
		EzkeyEnrollmentBindRequest req = new EzkeyEnrollmentBindRequest();
		req.setId(id);
		return service.bind(req);
	}

	@PostMapping("/confirm")
	public EzkeyEnrollmentConfirmResponse confirm(@RequestBody EzkeyEnrollmentConfirmRequest req) {
		return service.confirm(req);
	}
}