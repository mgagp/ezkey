package org.ezkey.demo.device.controller;

import java.util.Optional;
import org.ezkey.demo.device.service.AuthApiService;
import org.ezkey.demo.device.service.DeviceCryptoService;
import org.ezkey.demo.device.service.DeviceCryptoService.ECP256DeviceKeyPair;
import org.ezkey.demo.device.service.EnrollmentStoreService;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;
import org.ezkey.demodevice.generated.dto.AuthAttemptPendingRequestDto;
import org.ezkey.demodevice.generated.dto.AuthAttemptPendingResponseDto;
import org.ezkey.demodevice.generated.dto.AuthAttemptRespondRequestDto;
import org.ezkey.demodevice.generated.dto.AuthAttemptRespondResponseDto;
import org.ezkey.demodevice.generated.dto.EnrollmentBindResponseDto;
import org.ezkey.demodevice.generated.dto.EnrollmentVerifyRequestDto;
import org.ezkey.demodevice.generated.dto.EnrollmentVerifyResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Simulated Ezkey mobile app controller.
 *
 * @since 2025
 */
@Controller
@RequestMapping("/phone/ezkey")
public class EzkeyAppController {

  private static final Logger logger = LoggerFactory.getLogger(EzkeyAppController.class);

  private final AuthApiService authApiService;

  private final DeviceCryptoService cryptoService;

  private final EnrollmentStoreService storeService;

  public EzkeyAppController(
      AuthApiService authApiService,
      DeviceCryptoService cryptoService,
      EnrollmentStoreService storeService) {
    this.authApiService = authApiService;
    this.cryptoService = cryptoService;
    this.storeService = storeService;
  }

  @GetMapping
  public String appHome(Model model) {
    model.addAttribute("pageTitle", "Ezkey App");
    model.addAttribute("enrollments", storeService.list());
    return "phone/ezkey/home";
  }

  @GetMapping("/enrollment/new")
  public String newEnrollment(Model model) {
    model.addAttribute("pageTitle", "New Enrollment");
    return "phone/ezkey/new_enrollment";
  }

  @PostMapping("/enrollment/bind")
  public String bindEnrollment(
      @RequestParam("enrollmentId") Integer enrollmentId,
      @RequestParam("enrollmentProofToken") String enrollmentProofToken,
      @RequestParam(value = "language", defaultValue = "en") String language,
      Model model) {
    model.addAttribute("pageTitle", "Enrollment - Bind");
    model.addAttribute("enrollmentId", enrollmentId);
    try {
      // Call the bind API with proof token
      EnrollmentBindResponseDto bindResponse =
          authApiService.bind(enrollmentId, enrollmentProofToken, language).block();
      if (bindResponse != null) {
        // Generate device keys
        ECP256DeviceKeyPair keyPair = cryptoService.generateDeviceKeyPair();
        String devicePublicKeyB64 = keyPair.base64PublicKey();
        String devicePrivateKeyB64 = keyPair.base64PrivateKey();

        String integrationPublicKey = bindResponse.getIntegrationPublicKey();
        String responseProofToken = bindResponse.getEnrollmentProofToken();

        // Get integration information from the response
        String integrationName = bindResponse.getIntegrationName();
        String integrationDescription = bindResponse.getIntegrationDescription();
        String integrationLogo = bindResponse.getIntegrationLogo();
        String enrollmentName = bindResponse.getEnrollmentName();

        logger.info(
            "Using integration info for enrollment {} with language {}: name={}, description={},"
                + " logo={}, enrollmentName={}",
            enrollmentId,
            language,
            integrationName,
            integrationDescription,
            integrationLogo,
            enrollmentName);

        // Save interim record before verify with integration information
        Record record =
            new Record(
                enrollmentId,
                null, // integrationId - would be set if available
                enrollmentName,
                null, // enrollmentUrl
                integrationPublicKey,
                responseProofToken, // Store the proof token (not signed)
                devicePublicKeyB64,
                devicePrivateKeyB64,
                null,
                "Device",
                null,
                integrationName,
                integrationDescription,
                integrationLogo);
        storeService.save(record);

        model.addAttribute("enrollmentId", enrollmentId);
        model.addAttribute("enrollmentName", enrollmentName);
        model.addAttribute("enrollmentProofToken", responseProofToken);
        model.addAttribute("integrationPublicKey", integrationPublicKey);
        model.addAttribute("integrationName", integrationName);
        model.addAttribute("integrationDescription", integrationDescription);
        model.addAttribute("integrationLogo", integrationLogo);
        model.addAttribute("language", language);
        model.addAttribute("success", "Bind successful! Enter the challenge code to verify.");
      } else {
        model.addAttribute("error", "Bind failed: No response from server");
      }
    } catch (Exception e) {
      logger.error("Bind failed for enrollment {}", enrollmentId, e);
      model.addAttribute("error", "Bind failed: " + e.getMessage());
    }
    return "phone/ezkey/bind_enrollment";
  }

  @PostMapping("/enrollment/verify")
  public String verifyEnrollment(
      @RequestParam("enrollmentId") Integer enrollmentId,
      @RequestParam("challengeResponse") String challengeResponse,
      Model model) {
    model.addAttribute("pageTitle", "Enrollment - Verify");
    try {
      Optional<Record> recOpt = storeService.load(enrollmentId);
      if (recOpt.isEmpty()) {
        model.addAttribute("error", "Enrollment not found in local store");
        return "phone/ezkey/bind_enrollment";
      }
      Record rec = recOpt.get();

      // Sign the enrollment proof token with device private key
      String enrollmentProofTokenSigned =
          cryptoService.signStringToBase64(rec.enrollmentProofToken(), rec.devicePrivateKey());

      // Create typed request DTO
      EnrollmentVerifyRequestDto requestDto =
          new EnrollmentVerifyRequestDto()
              .enrollmentId(enrollmentId)
              .challengeResponse(Integer.parseInt(challengeResponse))
              .devicePublicKey(rec.devicePublicKey())
              .enrollmentProofTokenSigned(enrollmentProofTokenSigned);

      // Log the request for debugging
      logger.info("Verify request for enrollment {}: {}", enrollmentId, requestDto);
      logger.info("Enrollment proof token being signed: {}", rec.enrollmentProofToken());
      logger.info("Device public key: {}", rec.devicePublicKey());
      logger.info("Signed enrollment proof token: {}", enrollmentProofTokenSigned);

      EnrollmentVerifyResponseDto verifyResponse = authApiService.verify(requestDto).block();
      if (verifyResponse != null) {
        Boolean active = verifyResponse.getActive();
        if (Boolean.TRUE.equals(active)) {
          // Update the record with verification status
          Record updatedRecord =
              new Record(
                  rec.enrollmentId(),
                  rec.integrationId(),
                  rec.enrollmentName(),
                  rec.enrollmentUrl(),
                  rec.integrationPublicKey(),
                  rec.enrollmentProofToken(),
                  rec.devicePublicKey(),
                  rec.devicePrivateKey(),
                  true, // authAttemptChallengeRequired
                  rec.deviceLabel(),
                  rec.createdAt(),
                  rec.integrationName(),
                  rec.integrationDescription(),
                  rec.integrationLogo());
          storeService.save(updatedRecord);

          model.addAttribute("success", "Enrollment verified successfully!");
          model.addAttribute("enrollmentId", enrollmentId);
          return "phone/ezkey/verify_success";
        } else {
          // Verification failed - remove enrollment from store
          storeService.delete(enrollmentId);
          logger.info("Removed enrollment {} from store due to failed verification", enrollmentId);
          model.addAttribute("enrollmentId", enrollmentId);
          model.addAttribute("error", "Verification failed: Enrollment is not active. Please start over from the beginning.");
        }
      } else {
        // Verification failed - remove enrollment from store
        storeService.delete(enrollmentId);
        logger.info("Removed enrollment {} from store due to failed verification (no response)", enrollmentId);
        model.addAttribute("enrollmentId", enrollmentId);
        model.addAttribute("error", "Verification failed: No response from server. Please start over from the beginning.");
      }
    } catch (Exception e) {
      // Verification failed - remove enrollment from store
      storeService.delete(enrollmentId);
      logger.error("Verify failed for enrollment {} - removed from store", enrollmentId, e);
      model.addAttribute("enrollmentId", enrollmentId);
      model.addAttribute("error", "Verification failed: " + e.getMessage() + ". Please start over from the beginning.");
    }
    return "phone/ezkey/bind_enrollment";
  }

  @GetMapping("/enrollments/{enrollmentId}/auth")
  public String enrollmentAuth(@PathVariable("enrollmentId") Integer enrollmentId, Model model) {
    model.addAttribute("pageTitle", "Authentication");
    model.addAttribute("enrollmentId", enrollmentId);
    try {
      // Load enrollment record
      Optional<Record> recOpt = storeService.load(enrollmentId);
      if (recOpt.isEmpty()) {
        model.addAttribute("error", "Enrollment not found");
        return "phone/ezkey/auth";
      }
      Record rec = recOpt.get();

      // Add integration information to model
      model.addAttribute("enrollmentName", rec.enrollmentName());
      model.addAttribute("integrationName", rec.integrationName());
      model.addAttribute("integrationDescription", rec.integrationDescription());
      model.addAttribute("integrationLogo", rec.integrationLogo());

      // Generate device proof token for pending request
      String deviceProofToken = cryptoService.generateProofToken();
      String deviceProofTokenSigned =
          cryptoService.signStringToBase64(deviceProofToken, rec.devicePrivateKey());

      // Create pending request with enrollmentProofToken
      AuthAttemptPendingRequestDto pendingRequest =
          new AuthAttemptPendingRequestDto()
              .enrollmentId(enrollmentId)
              .enrollmentProofToken(rec.enrollmentProofToken())
              .deviceProofToken(deviceProofToken)
              .deviceProofTokenSigned(deviceProofTokenSigned);

      logger.info(
          "Checking for pending auth attempts for enrollment {}: {}", enrollmentId, pendingRequest);

      // Check for pending authentication attempts
      AuthAttemptPendingResponseDto pendingResponse =
          authApiService.pending(pendingRequest).block();
      if (pendingResponse != null) {
        // There's a pending authentication attempt
        logger.info("Found pending auth attempt: {}", pendingResponse);

        // Validate the integration signature
        boolean signatureValid =
            cryptoService.validateSignature(
                pendingResponse.getAuthAttemptProofToken(),
                pendingResponse.getAuthAttemptProofTokenSignedByIntegration(),
                rec.integrationPublicKey());
        if (!signatureValid) {
          model.addAttribute("error", "Invalid integration signature");
          return "phone/ezkey/auth";
        }
        // Store auth attempt info in session for respond
        model.addAttribute("authAttemptId", pendingResponse.getAuthAttemptId());
        model.addAttribute("authAttemptProofToken", pendingResponse.getAuthAttemptProofToken());
        model.addAttribute("challengeRequired", pendingResponse.getAuthAttemptChallengeRequired());
        model.addAttribute("hasPendingAuth", true);

        return "phone/ezkey/auth_pending";
      } else {
        // No pending authentication attempts
        model.addAttribute("message", "No pending authentication requests");
        model.addAttribute("hasPendingAuth", false);
        return "phone/ezkey/auth";
      }
    } catch (Exception e) {
      logger.error("Authentication check failed for enrollment {}", enrollmentId, e);

      // Check if it's an expired authentication attempt
      if (e.getMessage() != null && e.getMessage().contains("No pending authentication request")) {
        model.addAttribute(
            "message", "Demande d'authentification expirée. Veuillez en créer une nouvelle.");
      } else {
        model.addAttribute("error", "Authentication check failed: " + e.getMessage());
      }
      model.addAttribute("hasPendingAuth", false);
      return "phone/ezkey/auth";
    }
  }

  @PostMapping("/enrollments/{enrollmentId}/auth/respond")
  public String respondToAuth(
      @PathVariable("enrollmentId") Integer enrollmentId,
      @RequestParam("authAttemptId") Integer authAttemptId,
      @RequestParam("approved") Boolean approved,
      @RequestParam(value = "challengeResponse", required = false) String challengeResponse,
      @RequestParam("authAttemptProofToken") String authAttemptProofToken,
      Model model) {
    model.addAttribute("pageTitle", "Authentication Response");
    model.addAttribute("enrollmentId", enrollmentId);
    try {
      // Load enrollment record
      Optional<Record> recOpt = storeService.load(enrollmentId);
      if (recOpt.isEmpty()) {
        model.addAttribute("error", "Enrollment not found");
        return "phone/ezkey/auth";
      }
      Record rec = recOpt.get();

      // Add integration information to model
      model.addAttribute("enrollmentName", rec.enrollmentName());
      model.addAttribute("integrationName", rec.integrationName());
      model.addAttribute("integrationDescription", rec.integrationDescription());
      model.addAttribute("integrationLogo", rec.integrationLogo());

      // Sign the auth attempt proof token for the response (security: one-time use token)
      String responseSignature =
          cryptoService.signStringToBase64(authAttemptProofToken, rec.devicePrivateKey());

      // Create respond request with authAttemptId explicitly set
      AuthAttemptRespondRequestDto respondRequest =
          new AuthAttemptRespondRequestDto()
              .authAttemptId(authAttemptId)
              .authAttemptAccepted(approved)
              .authAttemptProofTokenSignedByDevice(responseSignature);

      // Add challenge response if provided
      if (challengeResponse != null && !challengeResponse.trim().isEmpty()) {
        try {
          Integer challengeResponseInt = Integer.parseInt(challengeResponse.trim());
          respondRequest.authAttemptChallengeResponse(challengeResponseInt);
          logger.info("Including challenge response: {}", challengeResponseInt);
        } catch (NumberFormatException e) {
          logger.warn("Invalid challenge response format: {}", challengeResponse);
          model.addAttribute("success", false);
          model.addAttribute("denied", false);
          model.addAttribute("failed", true);
          model.addAttribute(
              "message", "Invalid challenge response format. Please enter a numeric code.");
          return "phone/ezkey/auth_result";
        }
      }
      logger.info(
          "Responding to auth attempt {} for enrollment {}: {}",
          authAttemptId,
          enrollmentId,
          respondRequest);

      // Submit response
      AuthAttemptRespondResponseDto respondResponse =
          authApiService.respond(respondRequest).block();
      if (respondResponse != null) {
        logger.info("Auth response submitted successfully: {}", respondResponse);

        // Determine result based on the API response result field
        String result = respondResponse.getResult().getValue();
        if ("APPROVED".equals(result)) {
          // Authentication was successful
          model.addAttribute("success", true);
          model.addAttribute("denied", false);
          model.addAttribute("failed", false);
          model.addAttribute("message", respondResponse.getMessage());
        } else if ("DENIED".equals(result)) {
          // Authentication was denied by user
          model.addAttribute("success", false);
          model.addAttribute("denied", true);
          model.addAttribute("failed", false);
          model.addAttribute("message", respondResponse.getMessage());
        } else if ("FAILED".equals(result)) {
          // Technical error occurred
          model.addAttribute("success", false);
          model.addAttribute("denied", false);
          model.addAttribute("failed", true);
          model.addAttribute("message", respondResponse.getMessage());
        } else if ("EXPIRED".equals(result)) {
          // Authentication attempt expired
          model.addAttribute("success", false);
          model.addAttribute("denied", false);
          model.addAttribute("failed", false);
          model.addAttribute("expired", true);
          model.addAttribute("message", respondResponse.getMessage());
        } else {
          // Unknown result - treat as failed
          model.addAttribute("success", false);
          model.addAttribute("denied", false);
          model.addAttribute("failed", true);
          model.addAttribute("message", "Unknown authentication result: " + result);
        }
      } else {
        // Technical failure
        model.addAttribute("success", false);
        model.addAttribute("denied", false);
        model.addAttribute("failed", true);
        model.addAttribute("message", "Failed to submit authentication response");
      }
    } catch (Exception e) {
      logger.error(
          "Auth response failed for enrollment {} authAttempt {}", enrollmentId, authAttemptId, e);
      model.addAttribute("success", false);
      model.addAttribute("denied", false);
      model.addAttribute("failed", true);
      model.addAttribute("message", "Authentication response failed: " + e.getMessage());
    }
    return "phone/ezkey/auth_result";
  }
}
