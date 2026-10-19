package org.ezkey.demo.device.controller;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.ezkey.demo.device.config.DemoDeviceProperties;
import org.ezkey.demo.device.service.AuthApiService;
import org.ezkey.demo.device.service.AuthApiUrlValidator;
import org.ezkey.demo.device.service.AuthAttemptPayloadUtil;
import org.ezkey.demo.device.service.ClaimedPendingAuth;
import org.ezkey.demo.device.service.DeviceCryptoService;
import org.ezkey.demo.device.service.DeviceCryptoService.ECP256DeviceKeyPair;
import org.ezkey.demo.device.service.EnrollmentAuthApiUrlResolver;
import org.ezkey.demo.device.service.EnrollmentAuthApiUrlResolver.BindResolution;
import org.ezkey.demo.device.service.EnrollmentStoreService;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;
import org.ezkey.demo.device.service.EnrollmentVerifyPayloadUtil;
import org.ezkey.demo.device.service.InvalidAuthApiUrlException;
import org.ezkey.demo.device.service.PendingAuthClaimSession;
import org.ezkey.demo.device.util.LogSanitizer;
import org.ezkey.demo.device.view.EnrollmentTenantGrouper;
import org.ezkey.demodevice.generated.dto.AuthAttemptPendingRequestDto;
import org.ezkey.demodevice.generated.dto.AuthAttemptPendingResponseDto;
import org.ezkey.demodevice.generated.dto.AuthAttemptRespondRequestDto;
import org.ezkey.demodevice.generated.dto.AuthAttemptRespondResponseDto;
import org.ezkey.demodevice.generated.dto.EnrollmentBindResponseDto;
import org.ezkey.demodevice.generated.dto.EnrollmentVerifyRequestDto;
import org.ezkey.demodevice.generated.dto.EnrollmentVerifyResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
 * <p>Pending auth is read-once on Auth API. After a successful claim, this controller keeps the
 * claim in the HTTP session so a second GET (double navigation, re-check, refresh) can re-show
 * Approve/Deny without calling pending again. The session claim carries {@code expiresAt} (Demo
 * Device bound; pending wire omits attempt expiry per #834) and is dropped at {@code
 * min(expiresAt, claimedAt + 10 min)} so a stale request cannot hide a newer pending (#895 PR-C).
 *
 * <p>The controller manages the mobile device's cryptographic state and communicates with the Ezkey
 * Auth API to complete the enrollment and authentication flows.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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

  private static final ObjectMapper PROBLEM_DETAIL_MAPPER = new ObjectMapper();

  /** Max chars of ProblemDetail {@code type} written to logs (log-injection bound). */
  private static final int PROBLEM_TYPE_LOG_MAX_LENGTH = 120;

  private final AuthApiService authApiService;

  private final DeviceCryptoService cryptoService;

  private final EnrollmentStoreService storeService;

  /** Auth API base URL configured for this demo-device (for QR payload vs runtime comparison). */
  private final String configuredAuthApiBase;

  /** Effective Auth API host allowlist for QR {@code authUrl} and bind overrides. */
  private final Set<String> allowedAuthApiHosts;

  /** Lab settings including claimed-pending session expiry bound (#895 PR-C). */
  private final DemoDeviceProperties demoDeviceProperties;

  /**
   * Suffix of ProblemDetail {@code type} for a late respond after attempt expiry (PR-A contract).
   */
  static final String AUTH_ATTEMPT_EXPIRED_TYPE_SUFFIX = "auth-attempt-expired";

  /**
   * Creates the controller.
   *
   * @param authApiService Auth API WebClient facade
   * @param cryptoService device key and signature helper
   * @param storeService local enrollment JSON store
   * @param configuredAuthApiBase {@code ezkey.auth.api.url}
   * @param demoDeviceProperties demo-device allowlist and lab settings
   */
  public EzkeyAppController(
      AuthApiService authApiService,
      DeviceCryptoService cryptoService,
      EnrollmentStoreService storeService,
      @Value("${ezkey.auth.api.url:http://localhost:8080}") String configuredAuthApiBase,
      DemoDeviceProperties demoDeviceProperties) {
    this.authApiService = authApiService;
    this.cryptoService = cryptoService;
    this.storeService = storeService;
    this.configuredAuthApiBase = configuredAuthApiBase;
    this.demoDeviceProperties =
        demoDeviceProperties != null ? demoDeviceProperties : new DemoDeviceProperties();
    this.allowedAuthApiHosts =
        AuthApiUrlValidator.effectiveAllowlist(
            configuredAuthApiBase, this.demoDeviceProperties.getAllowedAuthApiHosts());
  }

  @GetMapping
  public String appHome(Model model) {
    model.addAttribute("pageTitle", "Ezkey App");
    List<Record> enrollments = storeService.list();
    model.addAttribute("enrollments", enrollments);
    model.addAttribute("tenantGroups", EnrollmentTenantGrouper.group(enrollments));
    return "phone/ezkey/home";
  }

  @GetMapping("/enrollment/new")
  public String newEnrollment(Model model) {
    model.addAttribute("pageTitle", "New Enrollment");
    model.addAttribute("configuredAuthApiBase", configuredAuthApiBase);
    return "phone/ezkey/new_enrollment";
  }

  @PostMapping("/enrollment/bind")
  public String bindEnrollment(
      @RequestParam("enrollmentId") Integer enrollmentId,
      @RequestParam("enrollmentProofToken") String enrollmentProofToken,
      @RequestParam(value = "authApiBaseUrl", required = false) String authApiBaseUrl,
      Model model) {
    model.addAttribute("pageTitle", "Enrollment - Bind");
    model.addAttribute("enrollmentId", enrollmentId);
    BindResolution bindResolution;
    try {
      bindResolution =
          EnrollmentAuthApiUrlResolver.resolveForBind(
              authApiBaseUrl, configuredAuthApiBase, allowedAuthApiHosts);
    } catch (InvalidAuthApiUrlException e) {
      model.addAttribute("pageTitle", "New Enrollment");
      model.addAttribute("configuredAuthApiBase", configuredAuthApiBase);
      model.addAttribute("enrollmentId", enrollmentId);
      model.addAttribute("enrollmentProofToken", enrollmentProofToken);
      // Do not redisplay a rejected override URL (constant error only; no echo of attacker input)
      model.addAttribute("error", e.getMessage());
      return "phone/ezkey/new_enrollment";
    }
    logger.info(
        "Bind enrollment {} using Auth API {} (QR override submitted: {})",
        enrollmentId,
        LogSanitizer.sanitizeForLog(bindResolution.apiBaseUrl()),
        authApiBaseUrl != null && !authApiBaseUrl.isBlank());
    try {
      EnrollmentBindResponseDto bindResponse =
          authApiService
              .bind(enrollmentId, enrollmentProofToken, bindResolution.apiBaseUrl())
              .block();
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
                bindResolution.enrollmentUrlToPersist(),
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
                tenantDescription,
                "NONE");
        storeService.save(record);

        model.addAttribute("enrollmentId", enrollmentId);
        model.addAttribute("enrollmentName", enrollmentName);
        model.addAttribute("enrollmentProofToken", responseProofToken);
        model.addAttribute("integrationPublicKey", integrationPublicKey);
        model.addAttribute("integrationName", integrationName);
        model.addAttribute("integrationDescription", integrationDescription);
        model.addAttribute("integrationLogo", integrationLogo);
        model.addAttribute("tenantId", tenantId);
        model.addAttribute(
            "tenantName", EnrollmentTenantGrouper.normalizeTenantName(tenantName, tenantId));
        model.addAttribute("tenantDescription", tenantDescription);
        model.addAttribute("success", "Bind successful! Enter the challenge code to verify.");
      } else {
        model.addAttribute("error", "Bind failed: No response from server");
      }
    } catch (WebClientResponseException e) {
      // Log status (+ ProblemDetail type when parseable) only — never the raw error body (#754 S4)
      logger.warn(
          "Bind API error for enrollment {}: status={}, problemType={}",
          enrollmentId,
          e.getStatusCode().value(),
          problemTypeFromError(e));
      String userMessage =
          toBindErrorMessage(e.getStatusCode().value(), e.getResponseBodyAsString());
      model.addAttribute("error", userMessage);
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Bind failed for enrollment {}", enrollmentId, e);
      model.addAttribute("error", "Bind failed: " + e.getMessage());
    }
    return "phone/ezkey/bind_enrollment";
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    if (b != null && !b.isBlank()) {
      return b;
    }
    return null;
  }

  /**
   * Best-effort ProblemDetail {@code type} from an Auth API error response; never returns the body.
   *
   * <p>The value is CR/LF-sanitized and truncated before logging to prevent log injection.
   *
   * @param error WebClient error with optional problem+json body
   * @return sanitized problem type URI string, or {@code null} when absent / unparseable
   */
  static String problemTypeFromError(WebClientResponseException error) {
    if (error == null) {
      return null;
    }
    String body = error.getResponseBodyAsString();
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      JsonNode typeNode = PROBLEM_DETAIL_MAPPER.readTree(body).get("type");
      if (typeNode != null && typeNode.isValueNode() && !typeNode.isNull()) {
        String type = typeNode.asString();
        if (type == null || type.isBlank()) {
          return null;
        }
        return sanitizeProblemTypeForLog(type);
      }
    } catch (Exception ignored) { // CHECKSTYLE IGNORE IllegalCatch
      // Body may not be JSON ProblemDetail — status alone is enough for the log line
    }
    return null;
  }

  /**
   * Whether a ProblemDetail {@code type} identifies auth-attempt expiry (PR-A).
   *
   * <p>Tolerant of a missing or older contract: returns {@code false} when {@code type} is null or
   * does not end with {@link #AUTH_ATTEMPT_EXPIRED_TYPE_SUFFIX}.
   *
   * @param problemType sanitized type URI (may be {@code null})
   * @return {@code true} only when the typed expired contract is present
   */
  static boolean isAuthAttemptExpiredProblem(String problemType) {
    if (problemType == null || problemType.isBlank()) {
      return false;
    }
    return problemType.endsWith(AUTH_ATTEMPT_EXPIRED_TYPE_SUFFIX);
  }

  /**
   * Neutralizes CR/LF and bounds length for a ProblemDetail {@code type} log fragment.
   *
   * @param type raw type URI from the server
   * @return sanitized, truncated value safe for a single log line
   */
  static String sanitizeProblemTypeForLog(String type) {
    String cleaned = LogSanitizer.sanitizeForLog(type);
    if (cleaned == null) {
      return null;
    }
    if (cleaned.length() > PROBLEM_TYPE_LOG_MAX_LENGTH) {
      return cleaned.substring(0, PROBLEM_TYPE_LOG_MAX_LENGTH);
    }
    return cleaned;
  }

  /** Maps bind API error status and optional body to a clear, actionable message for the user. */
  private static String toBindErrorMessage(int statusCode, String responseBody) {
    if (statusCode == 409) {
      return "This enrollment is already bound to a device. Create a new enrollment in the Admin UI"
          + " and use its ID and proof token to bind.";
    }
    if (statusCode == 400) {
      return "Invalid request: use an enrollment that was just created (not yet bound), with the"
          + " exact proof token from the Admin UI. If the invitation has expired, create a"
          + " new enrollment.";
    }
    return "Bind failed: server returned "
        + statusCode
        + (responseBody != null && !responseBody.isBlank() ? " — " + responseBody : "");
  }

  @PostMapping("/enrollment/verify")
  public String verifyEnrollment(
      @RequestParam("enrollmentId") Integer enrollmentId,
      @RequestParam("challengeResponse") String challengeResponse,
      @RequestParam(value = "enrollmentProofToken", required = false)
          String enrollmentProofTokenParam,
      Model model) {
    model.addAttribute("pageTitle", "Enrollment - Verify");
    try {
      Optional<Record> recOpt = storeService.load(enrollmentId);
      if (recOpt.isEmpty()) {
        model.addAttribute("error", "Enrollment not found in local store");
        return "phone/ezkey/bind_enrollment";
      }
      Record rec = recOpt.get();

      String proofToken = firstNonBlank(rec.enrollmentProofToken(), enrollmentProofTokenParam);
      if (proofToken == null) {
        model.addAttribute(
            "error",
            "Enrollment proof token is missing from the demo device. Return to Bind and complete"
                + " bind again, or ensure enrollments are stored on a shared volume if you run"
                + " multiple demo-device replicas.");
        model.addAttribute("enrollmentId", enrollmentId);
        return "phone/ezkey/bind_enrollment";
      }

      final int challenge;
      try {
        challenge = Integer.parseInt(challengeResponse.trim());
      } catch (NumberFormatException e) {
        model.addAttribute(
            "error", "Invalid challenge code. Enter the numeric code from the console.");
        model.addAttribute("enrollmentId", enrollmentId);
        return "phone/ezkey/bind_enrollment";
      }

      // Canonical payload (must match Auth API / mobile): token|id|challenge|devicePublicKey
      String verifyPayload =
          EnrollmentVerifyPayloadUtil.buildVerifyDevicePayload(
              proofToken, enrollmentId, challenge, rec.devicePublicKey());
      String enrollmentProofTokenSigned =
          cryptoService.signStringToBase64(verifyPayload, rec.devicePrivateKey());

      // Create typed request DTO
      EnrollmentVerifyRequestDto requestDto =
          new EnrollmentVerifyRequestDto()
              .enrollmentId(enrollmentId)
              .challengeResponse(challenge)
              .devicePublicKey(rec.devicePublicKey())
              .enrollmentProofTokenSigned(enrollmentProofTokenSigned)
              .devicePrivateKeyStorageTier(
                  EnrollmentVerifyRequestDto.DevicePrivateKeyStorageTierEnum.NONE);

      // Do not log proof payloads, signatures, or full request bodies — they belong in API
      // responses only.
      logger.info("Verify request submitted for enrollment {}", enrollmentId);

      String authApiBase =
          EnrollmentAuthApiUrlResolver.resolveForStoredEnrollment(
              rec, configuredAuthApiBase, allowedAuthApiHosts);
      EnrollmentVerifyResponseDto verifyResponse =
          authApiService.verify(requestDto, authApiBase).block();
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
                  rec.tenantDescription(),
                  "NONE");
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
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
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
  public String enrollmentAuth(
      @PathVariable("enrollmentId") Integer enrollmentId, Model model, HttpSession session) {
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

      // Rehydrate after read-once claim (second GET / refresh) — do not call pending again
      Optional<ClaimedPendingAuth> openClaim =
          PendingAuthClaimSession.findOpen(
              session, enrollmentId, Instant.now(), PendingAuthClaimSession.DEFAULT_TTL);
      if (openClaim.isPresent()) {
        logger.info(
            "Rehydrating claimed pending auth attempt {} for enrollment {} from session",
            openClaim.get().authAttemptId(),
            enrollmentId);
        applyClaimedPendingToModel(model, openClaim.get());
        return "phone/ezkey/auth_pending";
      }

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

      // Log only identifiers — never proof tokens, signatures, or full DTO toString (#753)
      logger.info("Checking for pending auth attempts for enrollment {}", enrollmentId);

      String authApiBase =
          EnrollmentAuthApiUrlResolver.resolveForStoredEnrollment(
              rec, configuredAuthApiBase, allowedAuthApiHosts);

      AuthAttemptPendingResponseDto pendingResponse =
          authApiService.pending(pendingRequest, authApiBase).block();
      if (pendingResponse != null) {
        // There's a pending authentication attempt
        logger.info(
            "Found pending auth attempt {} for enrollment {}",
            pendingResponse.getAuthAttemptId(),
            enrollmentId);

        // Build canonical payload (proofToken|challengeRequired|contextTitle|contextMessage, NFC)
        String pendingPayload =
            AuthAttemptPayloadUtil.buildPendingPayload(
                pendingResponse.getAuthAttemptProofToken(),
                Boolean.TRUE.equals(pendingResponse.getAuthAttemptChallengeRequired()),
                pendingResponse.getContextTitle(),
                pendingResponse.getContextMessage());
        // Validate the integration signature over the full payload
        boolean signatureValid =
            cryptoService.validateSignature(
                pendingPayload,
                pendingResponse.getAuthAttemptProofTokenSignedByIntegration(),
                rec.integrationPublicKey());
        if (!signatureValid) {
          model.addAttribute("error", "Invalid integration signature");
          return "phone/ezkey/auth";
        }
        // Auth API pending omits attempt expiresAt (#834). Bound session rehydration to the
        // configured attempt TTL so a stale READ claim cannot hide a newer pending (#895 PR-C).
        Instant claimExpiresAt =
            Instant.now().plus(demoDeviceProperties.getClaimedPendingAttemptTtl());
        ClaimedPendingAuth claim =
            ClaimedPendingAuth.of(
                enrollmentId,
                pendingResponse.getAuthAttemptId(),
                pendingResponse.getAuthAttemptProofToken(),
                Boolean.TRUE.equals(pendingResponse.getAuthAttemptChallengeRequired()),
                pendingResponse.getContextTitle(),
                pendingResponse.getContextMessage(),
                claimExpiresAt);
        PendingAuthClaimSession.store(session, claim);
        applyClaimedPendingToModel(model, claim);

        return "phone/ezkey/auth_pending";
      } else {
        // No pending authentication attempts
        model.addAttribute("message", "No pending authentication requests");
        model.addAttribute("hasPendingAuth", false);
        return "phone/ezkey/auth";
      }
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Authentication check failed for enrollment {}", enrollmentId, e);

      // Check if it's an expired authentication attempt
      if (e.getMessage() != null && e.getMessage().contains("No pending authentication request")) {
        model.addAttribute(
            "message",
            "The authentication request has expired. Start a new login from the Admin UI.");
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
      Model model,
      HttpSession session) {
    // Leaving the pending UI — clear session claim whether respond succeeds or fails
    PendingAuthClaimSession.clear(session, enrollmentId);
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

      // Sign canonical payload (proofToken|accepted) so backend can verify accepted was not
      // tampered
      String respondPayload =
          AuthAttemptPayloadUtil.buildRespondPayload(authAttemptProofToken, approved);
      String responseSignature =
          cryptoService.signStringToBase64(respondPayload, rec.devicePrivateKey());

      // Create respond request with authAttemptId explicitly set
      AuthAttemptRespondRequestDto respondRequest =
          new AuthAttemptRespondRequestDto()
              .authAttemptId(authAttemptId)
              .authAttemptAccepted(approved)
              .authAttemptProofTokenSignedByDevice(responseSignature);

      // Add challenge response if provided — never log the challenge value (#753)
      if (challengeResponse != null && !challengeResponse.trim().isEmpty()) {
        try {
          Integer challengeResponseInt = Integer.parseInt(challengeResponse.trim());
          respondRequest.authAttemptChallengeResponse(challengeResponseInt);
          logger.info(
              "Challenge response included for auth attempt {} enrollment {}",
              authAttemptId,
              enrollmentId);
        } catch (NumberFormatException e) {
          logger.warn(
              "Invalid challenge response format for auth attempt {} enrollment {}",
              authAttemptId,
              enrollmentId);
          model.addAttribute("success", false);
          model.addAttribute("denied", false);
          model.addAttribute("failed", true);
          model.addAttribute(
              "message", "Invalid challenge response format. Please enter a numeric code.");
          return "phone/ezkey/auth_result";
        }
      }
      logger.info("Responding to auth attempt {} for enrollment {}", authAttemptId, enrollmentId);

      String authApiBase =
          EnrollmentAuthApiUrlResolver.resolveForStoredEnrollment(
              rec, configuredAuthApiBase, allowedAuthApiHosts);

      AuthAttemptRespondResponseDto respondResponse =
          authApiService.respond(respondRequest, authApiBase).block();
      if (respondResponse != null) {
        logger.info(
            "Auth response submitted successfully for auth attempt {} enrollment {}",
            respondResponse.getAuthAttemptId(),
            enrollmentId);

        String resultPayload =
            AuthAttemptPayloadUtil.buildRespondResultPayload(
                authAttemptProofToken,
                respondResponse.getAuthAttemptId(),
                respondResponse.getAuthAttemptResult() != null
                    ? respondResponse.getAuthAttemptResult().getValue()
                    : null,
                respondResponse.getAuthAttemptMessage());
        String respondSig = respondResponse.getAuthAttemptProofTokenResultSignedByIntegration();
        if (respondSig == null || respondSig.isBlank()) {
          model.addAttribute("success", false);
          model.addAttribute("denied", false);
          model.addAttribute("failed", true);
          model.addAttribute(
              "message",
              "Authentication response was not signed by the integration (missing signature).");
          return "phone/ezkey/auth_result";
        }
        boolean respondSignatureValid =
            cryptoService.validateSignature(resultPayload, respondSig, rec.integrationPublicKey());
        if (!respondSignatureValid) {
          model.addAttribute("success", false);
          model.addAttribute("denied", false);
          model.addAttribute("failed", true);
          model.addAttribute(
              "message", "Could not verify authentication result signature (possible tampering).");
          return "phone/ezkey/auth_result";
        }

        // Determine result based on the API response
        String result = respondResponse.getAuthAttemptResult().getValue();
        if ("APPROVED".equals(result)) {
          // Authentication was successful
          model.addAttribute("success", true);
          model.addAttribute("denied", false);
          model.addAttribute("failed", false);
          model.addAttribute("message", respondResponse.getAuthAttemptMessage());
        } else if ("DENIED".equals(result)) {
          // Authentication was denied by user
          model.addAttribute("success", false);
          model.addAttribute("denied", true);
          model.addAttribute("failed", false);
          model.addAttribute("message", respondResponse.getAuthAttemptMessage());
        } else if ("FAILED".equals(result)) {
          // Technical error occurred
          model.addAttribute("success", false);
          model.addAttribute("denied", false);
          model.addAttribute("failed", true);
          model.addAttribute("message", respondResponse.getAuthAttemptMessage());
        } else if ("EXPIRED".equals(result)) {
          // Authentication attempt expired
          model.addAttribute("success", false);
          model.addAttribute("denied", false);
          model.addAttribute("failed", false);
          model.addAttribute("expired", true);
          model.addAttribute("message", respondResponse.getAuthAttemptMessage());
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
    } catch (WebClientResponseException e) {
      // Never log the raw problem body (#754); map typed expiry when PR-A is present.
      String problemType = problemTypeFromError(e);
      logger.error(
          "Auth response failed for enrollment {} authAttempt {} status={} type={}",
          enrollmentId,
          authAttemptId,
          e.getStatusCode().value(),
          problemType != null ? problemType : "-");
      applyRespondConflictToModel(model, e.getStatusCode().value(), problemType);
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error(
          "Auth response failed for enrollment {} authAttempt {}", enrollmentId, authAttemptId, e);
      Throwable cause = e.getCause();
      if (cause instanceof WebClientResponseException wcre) {
        String problemType = problemTypeFromError(wcre);
        applyRespondConflictToModel(model, wcre.getStatusCode().value(), problemType);
      } else {
        model.addAttribute("success", false);
        model.addAttribute("denied", false);
        model.addAttribute("failed", true);
        model.addAttribute("expired", false);
        model.addAttribute("message", "Authentication response failed: " + e.getMessage());
      }
    }
    return "phone/ezkey/auth_result";
  }

  /**
   * Maps respond HTTP conflicts to the auth result UI.
   *
   * <p>When status is 409 and the ProblemDetail type ends with {@code auth-attempt-expired} (PR-A),
   * shows a clear expired state. Other 409s (including older {@code auth-attempt-state-conflict} or
   * a missing type) show a neutral “no longer active” message — tolerant of a server without PR-A.
   *
   * @param model Thymeleaf model
   * @param statusCode HTTP status from Auth API
   * @param problemType sanitized ProblemDetail type, or {@code null}
   */
  static void applyRespondConflictToModel(Model model, int statusCode, String problemType) {
    model.addAttribute("success", false);
    model.addAttribute("denied", false);
    if (statusCode == 409 && isAuthAttemptExpiredProblem(problemType)) {
      model.addAttribute("failed", false);
      model.addAttribute("expired", true);
      model.addAttribute(
          "message",
          "This authentication request has expired. Start a new login from the Admin UI.");
      return;
    }
    if (statusCode == 409) {
      model.addAttribute("failed", true);
      model.addAttribute("expired", false);
      model.addAttribute(
          "message",
          "This authentication request is no longer active. Start a new login from the Admin UI.");
      return;
    }
    model.addAttribute("failed", true);
    model.addAttribute("expired", false);
    model.addAttribute("message", "Authentication response failed: server returned " + statusCode);
  }

  /**
   * Copies a session-backed or freshly claimed pending into the Thymeleaf model for {@code
   * auth_pending}.
   *
   * @param model Spring MVC model
   * @param claim open claimed pending
   */
  private static void applyClaimedPendingToModel(Model model, ClaimedPendingAuth claim) {
    model.addAttribute("authAttemptId", claim.authAttemptId());
    model.addAttribute("authAttemptProofToken", claim.authAttemptProofToken());
    model.addAttribute("challengeRequired", claim.challengeRequired());
    model.addAttribute("contextTitle", claim.contextTitle());
    model.addAttribute("contextMessage", claim.contextMessage());
    model.addAttribute("hasPendingAuth", true);
  }
}
