package org.ezkey.authattempt;

import java.util.List;

import org.ezkey.authattempt.dto.EzkeyAuthAttemptCompleteRequestDto;
import org.ezkey.authattempt.dto.EzkeyAuthAttemptCompleteResponseDto;
import org.ezkey.authattempt.dto.EzkeyAuthAttemptCreateDtoRequest;
import org.ezkey.authattempt.dto.EzkeyAuthAttemptCreateDtoResponse;
import org.ezkey.authattempt.dto.EzkeyAuthAttemptInitiateRequestDto;
import org.ezkey.authattempt.dto.EzkeyAuthAttemptInitiateResponseDto;
import org.ezkey.enrollment.EzkeyEnrollmentV0;
import org.ezkey.enrollment.EzkeyEnrollmentMapper;
import org.ezkey.signature.SignatureService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EzkeyAuthAttemptService {
	//private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EzkeyAuthAttemptService.class);
	private final EzkeyAuthAttemptMapper mapper;
	private final EzkeyEnrollmentMapper enrollmentMapper;
	private final SignatureService signatureService;

	@Value("${ezkey.simulation.mode:false}")
	private boolean simulationMode;

	public EzkeyAuthAttemptService(EzkeyAuthAttemptMapper mapper, EzkeyEnrollmentMapper enrollmentMapper, SignatureService signatureService) {
		this.mapper = mapper;
		this.enrollmentMapper = enrollmentMapper;
		this.signatureService = signatureService;
	}

	public EzkeyAuthAttempt getById(Integer id) {
		return mapper.findById(id);
	}

	public List<EzkeyAuthAttempt> getAll() {
		return mapper.findAll();
	}

	public EzkeyAuthAttemptCreateDtoResponse create(EzkeyAuthAttemptCreateDtoRequest authRequest) {
		var enrollment = enrollmentMapper.findById(authRequest.getEnrollmentId());

		if (enrollment == null) {
			throw new IllegalArgumentException("Enrollment not found for ID: " + authRequest.getEnrollmentId());
		}

		if (Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired()) && Boolean.FALSE.equals(authRequest.getChallengeRequested())) {
			throw new IllegalArgumentException("Challenge for device is required for this enrollment");
		}

		EzkeyAuthAttempt authAttempt = new EzkeyAuthAttempt();
		authAttempt.setEnrollmentId(enrollment.getEnrollmentId());
		authAttempt.setAuthAttemptRead(false);
		authAttempt.setAuthAttemptReplied(false);
		authAttempt.setAuthAttemptAccepted(false);

		if (Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())) {
			java.util.Random random = new java.util.Random();
			authAttempt.setAuthAttemptChallenge(100000 + random.nextInt(900000));
		} else {
			authAttempt.setAuthAttemptChallenge(null);
		}

		authAttempt.setCreatedAt(java.time.LocalDateTime.now());
		authAttempt.setAuthAttemptCode(java.util.UUID.randomUUID().toString());

		int result = mapper.insert(authAttempt);
		if (result == 0) {
			throw new RuntimeException("Failed to create auth attempt record");
		}

		EzkeyAuthAttemptCreateDtoResponse response = new EzkeyAuthAttemptCreateDtoResponse();
		response.setAuthAttemptId(authAttempt.getAuthAttemptId());

		if (simulationMode) {
			response.setSimulationAuthAttemptEnrolleeCode(java.util.UUID.randomUUID().toString());
			response.setSimulationAuthAttemptEnrolleeCodeSigned(
					signatureService.generateSignature(response.getSimulationAuthAttemptEnrolleeCode(), authRequest.getSimulationDevicePrivateKey()));
			response.setSimulationAuthAttemptChallengeResponse(authAttempt.getAuthAttemptChallenge());
		}

		return response;
	}

	public int update(EzkeyAuthAttempt authAttempt) {
		return mapper.update(authAttempt);
	}

	public int delete(Integer id) {
		return mapper.delete(id);
	}

	public EzkeyAuthAttemptInitiateResponseDto initiate(EzkeyAuthAttemptInitiateRequestDto request) {
		EzkeyAuthAttempt authAttempt = mapper.findMostRecentByEnrollmentId(request.getEnrollmentId());
		if (authAttempt == null) {
			throw new IllegalArgumentException("Auth attempt record not found for this enrollment");
		}

		if (Boolean.TRUE.equals(authAttempt.getAuthAttemptRead())) {
			throw new IllegalStateException("Auth attempt already read by device");
		}

		int updated = mapper.setDeviceReadTrueIfNotRead(authAttempt.getAuthAttemptId());
		if (updated == 0) {
			throw new IllegalStateException("Failed to mark auth attempt as read");
		}

		EzkeyEnrollmentV0 enrollment = enrollmentMapper.findById(request.getEnrollmentId());
		if (enrollment == null) {
			throw new IllegalArgumentException("Enrollment not found");
		}

		String devicePublicKey = enrollment.getAuthAttemptPublicKey();
		if (devicePublicKey == null) {
			throw new IllegalStateException("Device public key not found for this enrollment");
		}

		boolean isValid = signatureService.validateSignature(request.getAuthAttemptEnrolleeCode(), request.getAuthAttemptEnrolleeCodeSigned(), devicePublicKey);
		if (!isValid) {
			throw new IllegalArgumentException("Invalid signature for auth attempt code");
		}

		mapper.update(authAttempt);

		EzkeyAuthAttemptInitiateResponseDto response = new EzkeyAuthAttemptInitiateResponseDto();
		response.setAuthAttemptId(authAttempt.getAuthAttemptId());
		response.setAuthAttemptCode(authAttempt.getAuthAttemptCode());
		response.setAuthAttemptCodeSigned(signatureService.generateSignature(authAttempt.getAuthAttemptCode(), enrollment.getIntegrationPrivateKey()));
		response.setAuthAttemptChallengeRequired(enrollment.getAuthAttemptChallengeRequired());
		return response;
	}

	public EzkeyAuthAttemptCompleteResponseDto complete(EzkeyAuthAttemptCompleteRequestDto request) {
		EzkeyAuthAttemptCompleteResponseDto response = new EzkeyAuthAttemptCompleteResponseDto();

		EzkeyAuthAttempt authAttempt = mapper.findById(request.getAuthAttemptId());
		if (authAttempt == null) {
			response.setSuccess(false);
			response.setMessage("Auth attempt record not found");
			return response;
		}
		int updated = mapper.setDeviceRepliedTrueIfNotRead(authAttempt.getAuthAttemptId());
		if (updated == 0) {
			throw new IllegalStateException("Failed to mark auth attempt as replied");
		}
		if (!Boolean.TRUE.equals(authAttempt.getAuthAttemptRead())) {
			response.setSuccess(false);
			response.setMessage("Auth attempt not read by device");
			return response;
		}

		if (!authAttempt.getAuthAttemptCode().equals(request.getAuthAttemptCode())) {
			response.setSuccess(false);
			response.setMessage("Integration code mismatch");
			return response;
		}

		if (!authAttempt.getEnrollmentId().equals(request.getEnrollmentId())) {
			response.setSuccess(false);
			response.setMessage("Enrollment id mismatch");
			return response;
		}

		EzkeyEnrollmentV0 enrollment = enrollmentMapper.findById(authAttempt.getEnrollmentId());
		if (enrollment == null) {
			response.setSuccess(false);
			response.setMessage("Enrollment record not found");
			return response;
		}

		String devicePublicKey = enrollment.getAuthAttemptPublicKey();
		if (devicePublicKey == null) {
			response.setSuccess(false);
			response.setMessage("Device public key not found");
			return response;
		}

		boolean isValid = signatureService.validateSignature(request.getAuthAttemptEnrolleeCode(), request.getAuthAttemptEnrolleeCodeSigned(), devicePublicKey);
		if (!isValid) {
			response.setSuccess(false);
			response.setMessage("Invalid signature for auth attempt code");
			return response;
		}

		String integrationPublicKey = enrollment.getIntegrationPublicKey();
		if (integrationPublicKey == null) {
			response.setSuccess(false);
			response.setMessage("Integration public key not found");
			return response;
		}

		boolean isAppCodeValid = signatureService.validateSignature(request.getAuthAttemptCode(), request.getAuthAttemptCodeSigned(), integrationPublicKey);
		if (!isAppCodeValid) {
			response.setSuccess(false);
			response.setMessage("Invalid signature for integration code");
			return response;
		}

		if (Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())) {
			if (request.getAuthAttemptChallengeResponse() == null || !request.getAuthAttemptChallengeResponse().equals(authAttempt.getAuthAttemptChallenge())) {
				authAttempt.setAuthAttemptAccepted(false);
				mapper.update(authAttempt);
				response.setSuccess(false);
				response.setMessage("Challenge value mismatch");
				return response;
			}
		}

		authAttempt.setAuthAttemptReplied(true);
		authAttempt.setAuthAttemptAccepted(request.getAuthAttemptAccepted());
		mapper.update(authAttempt);

		response.setSuccess(true);
		response.setMessage("Auth attempt completed");
		return response;
	}
}