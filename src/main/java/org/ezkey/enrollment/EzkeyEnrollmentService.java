package org.ezkey.enrollment;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.ezkey.enrollment.dto.EzkeyEnrollmentBindRequest;
import org.ezkey.enrollment.dto.EzkeyEnrollmentBindResponse;
import org.ezkey.enrollment.dto.EzkeyEnrollmentConfirmRequest;
import org.ezkey.enrollment.dto.EzkeyEnrollmentConfirmResponse;
import org.ezkey.enrollment.dto.EzkeyEnrollmentCreateDtoRequest;
import org.ezkey.enrollment.dto.EzkeyEnrollmentCreateDtoResponse;
import org.ezkey.signature.SignatureService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EzkeyEnrollmentService {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EzkeyEnrollmentService.class);

	private final EzkeyEnrollmentMapper mapper;
	private final SignatureService signatureService;

	@Value("${ezkey.simulation.mode:false}")
	private boolean simulationMode;

	public EzkeyEnrollmentService(EzkeyEnrollmentMapper mapper, SignatureService signatureService) {
		this.mapper = mapper;
		this.signatureService = signatureService;
	}

	public EzkeyEnrollment getById(Integer id) {
		return mapper.findById(id);
	}

	public List<EzkeyEnrollment> getAll() {
		return mapper.findAll();
	}

	public EzkeyEnrollmentCreateDtoResponse create(EzkeyEnrollmentCreateDtoRequest req) {
		var enrollment = new EzkeyEnrollment();
		enrollment.setIntegrationId(req.getIntegrationId());
		enrollment.setAuthAttemptChallengeRequired(req.getAuthAttemptChallengeRequired());
		enrollment.setEnrollmentRead(false);
		enrollment.setEnrollmentConfirmed(false);
		enrollment.setEnrollmentActive(false);
		enrollment.setCreatedAt(LocalDateTime.now());
		enrollment.setEnrollmentName(req.getName());

		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			keyGen.initialize(2048);
			KeyPair kp = keyGen.generateKeyPair();
			enrollment.setIntegrationPrivateKey(java.util.Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
			enrollment.setIntegrationPublicKey(java.util.Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Key generation failed", e);
		}

		enrollment.setAuthAttemptPublicKey(null);
		java.util.Random random = new java.util.Random();
		enrollment.setEnrollmentChallenge(100000 + random.nextInt(900000)); // 6 digits
		enrollment.setEnrollmentCode(UUID.randomUUID().toString());

		mapper.insert(enrollment);

		EzkeyEnrollmentCreateDtoResponse response = new EzkeyEnrollmentCreateDtoResponse();
		response.setId(enrollment.getEnrollmentId());
		response.setChallenge(enrollment.getEnrollmentChallenge());
		return response;
	}

	public int update(EzkeyEnrollment enrollment) {
		return mapper.update(enrollment);
	}

	public int setDeviceReadTrue(Integer id) {
		return mapper.setDeviceReadTrue(id);
	}

	public EzkeyEnrollmentBindResponse bind(EzkeyEnrollmentBindRequest req) {
		EzkeyEnrollment pair = mapper.findById(req.getId());
		if (pair == null) {
			throw new IllegalArgumentException("Pair not found");
		}
		if (Boolean.TRUE.equals(pair.getEnrollmentRead())) {
			throw new IllegalStateException("Pair already bound by a device");
		}
		mapper.setDeviceReadTrue(req.getId());
		EzkeyEnrollmentBindResponse response = new EzkeyEnrollmentBindResponse();
		response.setEnrollmentId(pair.getEnrollmentId());
		response.setIntegrationPublicKey(pair.getIntegrationPublicKey());
		response.setEnrollmentCode(pair.getEnrollmentCode());
		response.setEnrollmentCodeSigned(signatureService.generateSignature(pair.getEnrollmentCode(), pair.getIntegrationPrivateKey()));
		if (simulationMode) {
			try {
				KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
				keyGen.initialize(2048);
				KeyPair kp = keyGen.generateKeyPair();
				response.setSimulationDevicePrivateKey(java.util.Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
				response.setSimulationDevicePublicKey(java.util.Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException("Key generation failed", e);
			}
			String signature = signatureService.generateSignature(pair.getEnrollmentCode(), response.getSimulationDevicePrivateKey());
			response.setSimulationEnrollmentCodeSigned(signature);
			//
			boolean valid = signatureService.validateSignature(pair.getEnrollmentCode(), signature, response.getSimulationDevicePublicKey());
			logger.info("Bind code signature valid: {}", valid);
		}
		return response;
	}

	public EzkeyEnrollmentConfirmResponse confirm(EzkeyEnrollmentConfirmRequest req) {
		EzkeyEnrollment enrollment = mapper.findById(req.getEnrollmentId());
		if (enrollment == null) {
			throw new IllegalArgumentException("Pair not found");
		}
		if (!Boolean.TRUE.equals(enrollment.getEnrollmentRead())) {
			throw new IllegalStateException("Pair must be read before confirmation");
		}
		if (Boolean.TRUE.equals(enrollment.getEnrollmentConfirmed())) {
			throw new IllegalStateException("Pair already confirmed");
		}
		if (Boolean.TRUE.equals(enrollment.getEnrollmentActive())) {
			throw new IllegalStateException("Pair already active");
		}
		{
			boolean valid = req.getEnrollmentCodeSigned() != null
					&& signatureService.validateSignature(enrollment.getEnrollmentCode(), req.getEnrollmentCodeSigned(), req.getDevicePublicKey());
			if (!valid) {
				enrollment.setEnrollmentConfirmed(false);
				enrollment.setEnrollmentChallenge(null);
				mapper.update(enrollment);
				throw new IllegalArgumentException("Invalid bind code signature");
			}
		}
		if (!req.getChallengeResponse().equals(enrollment.getEnrollmentChallenge())) {
			enrollment.setEnrollmentConfirmed(false);
			enrollment.setEnrollmentChallenge(null);
			mapper.update(enrollment);
			throw new IllegalArgumentException("Invalid challenge response");
		}
		enrollment.setEnrollmentConfirmed(true);
		enrollment.setEnrollmentActive(true);
		enrollment.setAuthAttemptPublicKey(req.getDevicePublicKey());
		mapper.update(enrollment);
		EzkeyEnrollmentConfirmResponse response = new EzkeyEnrollmentConfirmResponse();
		response.setActive(true);
		return response;
	}

	public int delete(Integer id) {
		return mapper.delete(id);
	}

	public String generateUuidV4() {
		return java.util.UUID.randomUUID().toString();
	}
}