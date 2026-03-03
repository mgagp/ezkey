package org.ezkey.demo.device.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p>Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * <p>Controller: EzkeyAppController Description: Simulated Ezkey mobile app controller for
 * enrollment and authentication flows.
 */

/**
 * Simulated Ezkey mobile app controller.
 *
 * <p>This controller simulates a mobile device running the Ezkey app. It handles:
 *
 * <ul>
 *   <li>New enrollment initiation and binding
 *   <li>Enrollment verification with cryptographic signing
 *   <li>Authentication request polling and response submission
 *   <li>Challenge-response validation when required
 * </ul>
 *
 * <p>The controller manages the mobile device's cryptographic state and communicates with the Ezkey
 * Auth API to complete the enrollment and authentication flows.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthApiService
 * @see DeviceCryptoService
 * @see EnrollmentStoreService
 */
@Controller
@RequestMapping("/phone/ezkey")
public class EzkeyAppController {

  private static final Logger logger = LoggerFactory.getLogger(EzkeyAppController.class);

  private static final String UNKNOWN_TENANT_NAME = "Ezkey System";

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
    List<Record> enrollments = storeService.list();
    model.addAttribute("enrollments", enrollments);
    model.addAttribute("tenantGroups", groupEnrollmentsByTenant(enrollments));
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
      Model model) {
    model.addAttribute("pageTitle", "Enrollment - Bind");
    model.addAttribute("enrollmentId", enrollmentId);
    try {
      // Call the bind API with proof token
      EnrollmentBindResponseDto bindResponse =
          authApiService.bind(enrollmentId, enrollmentProofToken).block();
      if (bindResponse != null) {
        // Generate device keys
        ECP256DeviceKeyPair keyPair = cryptoService.generateDeviceKeyPair();
        String devicePublicKeyB64 = keyPair.base64PublicKey();
        String devicePrivateKeyB64 = keyPair.base64PrivateKey();

        String integrationPublicKey = bindResponse.getIntegrationPublicKey();
        String responseProofToken = bindResponse.getEnrollmentProofToken();

        // Get integration information from the response (logo removed from API)
        String integrationName = bindResponse.getIntegrationName();
        String integrationDescription = bindResponse.getIntegrationDescription();
        String integrationLogo = null;
        String enrollmentName = bindResponse.getEnrollmentName();
        Integer tenantId = bindResponse.getTenantId();
        String tenantName = bindResponse.getTenantName();
        String tenantDescription = bindResponse.getTenantDescription();

        logger.info(
            "Using integration info for enrollment {}: name={}, description={},"
                + " logo={}, enrollmentName={}, tenantId={}, tenantName={}",
            enrollmentId,
            integrationName,
            integrationDescription,
            integrationLogo,
            enrollmentName,
            tenantId,
            tenantName);

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
                integrationLogo,
                tenantId,
                tenantName,
                tenantDescription);
        storeService.save(record);

        model.addAttribute("enrollmentId", enrollmentId);
        model.addAttribute("enrollmentName", enrollmentName);
        model.addAttribute("enrollmentProofToken", responseProofToken);
        model.addAttribute("integrationPublicKey", integrationPublicKey);
        model.addAttribute("integrationName", integrationName);
        model.addAttribute("integrationDescription", integrationDescription);
        model.addAttribute("integrationLogo", integrationLogo);
        model.addAttribute("tenantId", tenantId);
        model.addAttribute("tenantName", tenantName);
        model.addAttribute("tenantDescription", tenantDescription);
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
                  rec.integrationLogo(),
                  rec.tenantId(),
                  rec.tenantName(),
                  rec.tenantDescription());
          storeService.save(updatedRecord);

          model.addAttribute("success", "Enrollment verified successfully!");
          model.addAttribute("enrollmentId", enrollmentId);
          return "phone/ezkey/verify_success";
        } else {
          // Verification failed - remove enrollment from store
          storeService.delete(enrollmentId);
          logger.info("Removed enrollment {} from store due to failed verification", enrollmentId);
          model.addAttribute("enrollmentId", enrollmentId);
          model.addAttribute(
              "error",
              "Verification failed: Enrollment is not active. Please start over from the"
                  + " beginning.");
        }
      } else {
        // Verification failed - remove enrollment from store
        storeService.delete(enrollmentId);
        logger.info(
            "Removed enrollment {} from store due to failed verification (no response)",
            enrollmentId);
        model.addAttribute("enrollmentId", enrollmentId);
        model.addAttribute(
            "error",
            "Verification failed: No response from server. Please start over from the beginning.");
      }
    } catch (Exception e) {
      // Verification failed - remove enrollment from store
      storeService.delete(enrollmentId);
      logger.error("Verify failed for enrollment {} - removed from store", enrollmentId, e);
      model.addAttribute("enrollmentId", enrollmentId);
      model.addAttribute(
          "error",
          "Verification failed: " + e.getMessage() + ". Please start over from the beginning.");
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
        model.addAttribute("contextTitle", pendingResponse.getContextTitle());
        model.addAttribute("contextMessage", pendingResponse.getContextMessage());
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

      // Sign the auth attempt proof token for the response (security: one-time use
      // token)
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

  private static List<TenantGroupViewModel> groupEnrollmentsByTenant(List<Record> enrollments) {
    Map<TenantKey, List<Record>> grouped = new HashMap<>();

    for (Record enrollment : enrollments) {
      Integer tenantId = enrollment.tenantId();
      String tenantName = normalizeTenantName(enrollment.tenantName());
      String tenantDescription = normalizeTenantDescription(enrollment.tenantDescription());

      TenantKey key = new TenantKey(tenantId, tenantName, tenantDescription);
      grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(enrollment);
    }

    Comparator<Record> enrollmentComparator =
        Comparator.comparing(
                Record::integrationName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(
                Record::enrollmentName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(Record::enrollmentId, Comparator.nullsLast(Integer::compareTo));

    for (List<Record> groupItems : grouped.values()) {
      groupItems.sort(enrollmentComparator);
    }

    Comparator<TenantKey> tenantComparator =
        Comparator.comparing(
                (TenantKey k) -> UNKNOWN_TENANT_NAME.equals(k.tenantName()) ? 1 : 0,
                Integer::compareTo)
            .thenComparing(
                TenantKey::tenantName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(TenantKey::tenantId, Comparator.nullsLast(Integer::compareTo));

    return grouped.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(tenantComparator))
        .map(
            e ->
                new TenantGroupViewModel(
                    e.getKey().tenantId(),
                    e.getKey().tenantName(),
                    e.getKey().tenantDescription(),
                    e.getValue()))
        .toList();
  }

  private static String normalizeTenantName(String tenantName) {
    if (tenantName == null || tenantName.isBlank()) {
      return UNKNOWN_TENANT_NAME;
    }
    return tenantName.trim();
  }

  private static String normalizeTenantDescription(String tenantDescription) {
    if (tenantDescription == null || tenantDescription.isBlank()) {
      return null;
    }
    return tenantDescription.trim();
  }

  /**
   * Internal record for grouping enrollments by tenant.
   *
   * <p>This record represents the key used to group enrollments by tenant information. It combines
   * the tenant ID, name, and description to create a unique key for grouping operations.
   *
   * @param tenantId the unique identifier of the tenant, or null if unknown
   * @param tenantName the display name of the tenant, or "Unknown tenant" if not available
   * @param tenantDescription optional description of the tenant
   * @since 2025
   */
  private record TenantKey(Integer tenantId, String tenantName, String tenantDescription) {}

  /**
   * View model for tenant enrollment groups.
   *
   * <p>This record represents a group of enrollments associated with a specific tenant. It is used
   * to structure the response for the home page, organizing enrollments hierarchically by tenant
   * and providing tenant metadata for display purposes.
   *
   * @param tenantId the unique identifier of the tenant, or null if unknown
   * @param tenantName the display name of the tenant for UI rendering
   * @param tenantDescription optional description of the tenant for UI rendering
   * @param enrollments the list of enrollment records belonging to this tenant group
   * @since 2025
   */
  public record TenantGroupViewModel(
      Integer tenantId, String tenantName, String tenantDescription, List<Record> enrollments) {}
}
