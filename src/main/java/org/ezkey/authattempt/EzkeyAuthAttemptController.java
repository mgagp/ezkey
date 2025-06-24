package org.ezkey.authattempt;

import java.util.List;

import org.ezkey.authattempt.dto.EzkeyAuthAttemptCompleteRequestDto;
import org.ezkey.authattempt.dto.EzkeyAuthAttemptCompleteResponseDto;
import org.ezkey.authattempt.dto.EzkeyAuthAttemptCreateDtoRequest;
import org.ezkey.authattempt.dto.EzkeyAuthAttemptCreateDtoResponse;
import org.ezkey.authattempt.dto.EzkeyAuthAttemptInitiateRequestDto;
import org.ezkey.authattempt.dto.EzkeyAuthAttemptInitiateResponseDto;
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
@RequestMapping("/api/v1/authattempts")
public class EzkeyAuthAttemptController {

	private final EzkeyAuthAttemptService service;

	public EzkeyAuthAttemptController(EzkeyAuthAttemptService service) {
		this.service = service;
	}

	@GetMapping
	public ResponseEntity<List<EzkeyAuthAttempt>> getAll() {
		return ResponseEntity.ok(service.getAll());
	}

	@GetMapping("/{id}")
	public ResponseEntity<EzkeyAuthAttempt> getById(@PathVariable Integer id) {
		return ResponseEntity.ok(service.getById(id));
	}

	@PostMapping
	public ResponseEntity<EzkeyAuthAttemptCreateDtoResponse> create(@RequestBody EzkeyAuthAttemptCreateDtoRequest request) {
		return ResponseEntity.ok(service.create(request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<Integer> update(@PathVariable Integer id, @RequestBody EzkeyAuthAttempt authAttempt) {
		authAttempt.setEnrollmentId(id);
		return ResponseEntity.ok(service.update(authAttempt));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Integer> delete(@PathVariable Integer id) {
		return ResponseEntity.ok(service.delete(id));
	}

	@PostMapping("/initiate/{id}")
	public ResponseEntity<EzkeyAuthAttemptInitiateResponseDto> initiate(@PathVariable Integer id, @RequestBody EzkeyAuthAttemptInitiateRequestDto request) {
		request.setEnrollmentId(id);
		return ResponseEntity.ok(service.initiate(request));
	}

	@PostMapping("/complete/{id}")
	public ResponseEntity<EzkeyAuthAttemptCompleteResponseDto> complete(@PathVariable Integer id, @RequestBody EzkeyAuthAttemptCompleteRequestDto request) {
		request.setEnrollmentId(id);
		return ResponseEntity.ok(service.complete(request));
	}
}