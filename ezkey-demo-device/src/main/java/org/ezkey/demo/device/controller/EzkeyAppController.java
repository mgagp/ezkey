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
import org.ezkey.demo.device.service.ClaimExpiresAtResolver;
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
import org.ezkey.demo.device.service.PendingAuthPull;
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
 * Device bound; pending wire omits attempt expiry per #834) and is dropped at {@code min(expiresAt,
 * claimedAt + 10 min)} so a stale request cannot hide a newer pending (#895 PR-C).
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

  /** Suffix for missing integration signing key (#844). */
  static final String INTEGRATION_SIGNING_UNAVAILABLE_TYPE_SUFFIX =
      "integration-signing-unavailable";

  /** Julie locked copy — expired (#943). */
  static final String MSG_AUTH_EXPIRED = "This request expired. Ask for a new sign-in.";

  /** Julie locked copy — no longer active (#943); includes #916 signature refuse. */
  static final String MSG_AUTH_NO_LONGER_ACTIVE = "This request is no longer active.";

  /** Julie locked copy — HTTP 429 (#943). */
  static final String MSG_AUTH_RATE_LIMITED = "Too many attempts. Try again in a few seconds.";

  /** Julie locked copy — HTTP 503 signing unavailable (#943). */
  static final String MSG_AUTH_SIGNING_UNAVAILABLE =
      "Signing is temporarily unavailable. Try again.";

  /** Q9 unconfirmed — result sig fail or ambiguous transport/5xx after send (#943). */
  static final String MSG_AUTH_UNCONFIRMED =
      "Response sent, confirmation not verified; check the connection on the app side.";

  /** Generic non-leaking fallback (no status code, URL, host, or method). */
  static final String MSG_AUTH_GENERIC = "Something went wrong. Try again.";

  /** Phase-1 integration signing algorithm (mobile parity; fail-closed at bind) (#943). */
  static final String INTEGRATION_KEY_ALGORITHM_PHASE1 = "ed25519";

  /** Fail-closed bind when {@code integrationKeyAlgorithm} is not ed25519 (#943). */
  static final String MSG_UNSUPPORTED_INTEGRATION_KEY_ALGORITHM =
      "Unsupported integration key algorithm. This app requires ed25519.";

  /** Verify failure without leaking exception text (#943). */
  static final String MSG_VERIFY_FAILED_START_OVER =
      "Verification failed. Please start over from the beginning.";

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
        // Fail-closed: phase-1 requires ed25519 (mobile parity; one comparison) (#943).
        if (!INTEGRATION_KEY_ALGORITHM_PHASE1.equals(bindResponse.getIntegrationKeyAlgorithm())) {
          logger.warn(
              "Bind refused for enrollment {}: unsupported integrationKeyAlgorithm", enrollmentId);
          model.addAttribute("error", MSG_UNSUPPORTED_INTEGRATION_KEY_ALGORITHM);
          return "phone/ezkey/bind_enrollment";
        }

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
      String problemType = problemTypeFromError(e);
      logger.warn(
          "Bind API error for enrollment {}: status={}, problemType={}",
          enrollmentId,
          e.getStatusCode().value(),
          problemType != null ? problemType : "-");
      model.addAttribute(
          "error", toBindErrorMessage(e.getStatusCode().value(), problemType));
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Bind failed for enrollment {}", enrollmentId, e);
      model.addAttribute("error", MSG_AUTH_GENERIC);
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

  /**
   * Maps bind API HTTP status to a lab-actionable message — never status codes, body, URL, host, or
   * method (#943 / #754).
   *
   * @param statusCode HTTP status from Auth API
   * @param problemType sanitized ProblemDetail type, or {@code null}
   * @return user-facing message
   */
  static String toBindErrorMessage(int statusCode, String problemType) {
    if (statusCode == 409) {
      return "This enrollment is already bound to a device. Create a new enrollment in the Admin UI"
          + " and use its ID and proof token to bind.";
    }
    if (statusCode == 400) {
      return "Invalid request: use an enrollment that was just created (not yet bound), with the"
          + " exact proof token from the Admin UI. If the invitation has expired, create a"
          + " new enrollment.";
    }
    return safePreRespondHttpMessage(statusCode, problemType);
  }

  /**
   * User message for bind / verify / pending HTTP errors. Uses Julie 429/503 copy; never Q9
   * unconfirmed (that is respond-after-send only) and never codes/body/URL/host/method (#943).
   *
   * @param statusCode HTTP status
   * @param problemType sanitized type, or {@code null}
   * @return safe on-screen text
   */
  static String safePreRespondHttpMessage(int statusCode, String problemType) {
    AuthApiOutcome outcome = classify(statusCode, problemType);
    if (outcome == AuthApiOutcome.RATE_LIMITED
        || outcome == AuthApiOutcome.SIGNING_UNAVAILABLE) {
      return messageFor(outcome);
    }
    return MSG_AUTH_GENERIC;
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
        model.addAttribute("error", MSG_VERIFY_FAILED_START_OVER);
      }
    } catch (WebClientResponseException e) {
      storeService.delete(enrollmentId);
      String problemType = problemTypeFromError(e);
      logger.warn(
          "Verify API error for enrollment {}: status={}, problemType={}",
          enrollmentId,
          e.getStatusCode().value(),
          problemType != null ? problemType : "-");
      model.addAttribute("enrollmentId", enrollmentId);
      model.addAttribute(
          "error", safePreRespondHttpMessage(e.getStatusCode().value(), problemType));
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      // Verification failed - remove enrollment from store
      storeService.delete(enrollmentId);
      logger.error("Verify failed for enrollment {} - removed from store", enrollmentId, e);
      model.addAttribute("enrollmentId", enrollmentId);
      model.addAttribute("error", MSG_VERIFY_FAILED_START_OVER);
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

      PendingAuthPull pendingPull = authApiService.pending(pendingRequest, authApiBase).block();
      if (pendingPull != null && pendingPull.response() != null) {
        AuthAttemptPendingResponseDto pendingResponse = pendingPull.response();
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
        // Prefer server expiresAt when pending JSON includes it; else local TTL fallback (#895).
        Instant claimedAt = Instant.now();
        Instant claimExpiresAt =
            ClaimExpiresAtResolver.resolve(
                pendingPull.expiresAt(),
                claimedAt,
                demoDeviceProperties.getClaimedPendingAttemptTtl());
        ClaimedPendingAuth claim =
            new ClaimedPendingAuth(
                enrollmentId,
                pendingResponse.getAuthAttemptId(),
                pendingResponse.getAuthAttemptProofToken(),
                Boolean.TRUE.equals(pendingResponse.getAuthAttemptChallengeRequired()),
                pendingResponse.getContextTitle(),
                pendingResponse.getContextMessage(),
                claimedAt,
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
    } catch (WebClientResponseException e) {
      return handlePendingHttpError(enrollmentId, e, model);
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Authentication check failed for enrollment {}", enrollmentId, e);
      Throwable cause = e.getCause();
      if (cause instanceof WebClientResponseException wcre) {
        return handlePendingHttpError(enrollmentId, wcre, model);
      }
      model.addAttribute("error", MSG_AUTH_GENERIC);
      model.addAttribute("hasPendingAuth", false);
      return "phone/ezkey/auth";
    }
  }

  /**
   * Maps pending HTTP errors to a safe model error — never {@code detail}, URL, host, or method
   * (#943).
   *
   * @param enrollmentId enrollment id
   * @param error Auth API WebClient error
   * @param model Thymeleaf model
   * @return {@code phone/ezkey/auth}
   */
  private String handlePendingHttpError(
      Integer enrollmentId, WebClientResponseException error, Model model) {
    String problemType = problemTypeFromError(error);
    int status = error.getStatusCode().value();
    logger.warn(
        "Pending API error for enrollment {}: status={}, problemType={}",
        enrollmentId,
        status,
        problemType != null ? problemType : "-");
    model.addAttribute("error", safePreRespondHttpMessage(status, problemType));
    model.addAttribute("hasPendingAuth", false);
    return "phone/ezkey/auth";
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
    // Clear claim only after a terminal outcome (200 decision or non-retryable 409) — #943.
    model.addAttribute("pageTitle", "Authentication Response");
    model.addAttribute("enrollmentId", enrollmentId);
    try {
      Optional<Record> recOpt = storeService.load(enrollmentId);
      if (recOpt.isEmpty()) {
        model.addAttribute("error", "Enrollment not found");
        return "phone/ezkey/auth";
      }
      Record rec = recOpt.get();

      model.addAttribute("enrollmentName", rec.enrollmentName());
      model.addAttribute("integrationName", rec.integrationName());
      model.addAttribute("integrationDescription", rec.integrationDescription());
      model.addAttribute("integrationLogo", rec.integrationLogo());

      String respondPayload =
          AuthAttemptPayloadUtil.buildRespondPayload(authAttemptProofToken, approved);
      String responseSignature =
          cryptoService.signStringToBase64(respondPayload, rec.devicePrivateKey());

      AuthAttemptRespondRequestDto respondRequest =
          new AuthAttemptRespondRequestDto()
              .authAttemptId(authAttemptId)
              .authAttemptAccepted(approved)
              .authAttemptProofTokenSignedByDevice(responseSignature);

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
          applyFailedResult(
              model, "Invalid challenge response format. Please enter a numeric code.");
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
        if (respondSig == null || respondSig.isBlank()
            || !cryptoService.validateSignature(
                resultPayload, respondSig, rec.integrationPublicKey())) {
          PendingAuthClaimSession.clear(session, enrollmentId);
          applyOutcomeToResultModel(model, AuthApiOutcome.UNCONFIRMED);
          return "phone/ezkey/auth_result";
        }

        PendingAuthClaimSession.clear(session, enrollmentId);
        String result = respondResponse.getAuthAttemptResult().getValue();
        if ("APPROVED".equals(result)) {
          model.addAttribute("success", true);
          model.addAttribute("denied", false);
          model.addAttribute("failed", false);
          model.addAttribute("expired", false);
          model.addAttribute("inactive", false);
          model.addAttribute("unconfirmed", false);
          model.addAttribute("message", respondResponse.getAuthAttemptMessage());
        } else if ("DENIED".equals(result)) {
          model.addAttribute("success", false);
          model.addAttribute("denied", true);
          model.addAttribute("failed", false);
          model.addAttribute("expired", false);
          model.addAttribute("inactive", false);
          model.addAttribute("unconfirmed", false);
          model.addAttribute("message", respondResponse.getAuthAttemptMessage());
        } else if ("FAILED".equals(result)) {
          applyFailedResult(model, respondResponse.getAuthAttemptMessage());
        } else if ("EXPIRED".equals(result)) {
          applyOutcomeToResultModel(model, AuthApiOutcome.EXPIRED);
        } else {
          applyFailedResult(model, MSG_AUTH_GENERIC);
        }
        return "phone/ezkey/auth_result";
      }
      PendingAuthClaimSession.clear(session, enrollmentId);
      applyOutcomeToResultModel(model, AuthApiOutcome.UNCONFIRMED);
      return "phone/ezkey/auth_result";
    } catch (WebClientResponseException e) {
      return handleRespondHttpError(enrollmentId, authAttemptId, e, model, session);
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error(
          "Auth response failed for enrollment {} authAttempt {}", enrollmentId, authAttemptId, e);
      Throwable cause = e.getCause();
      if (cause instanceof WebClientResponseException wcre) {
        return handleRespondHttpError(enrollmentId, authAttemptId, wcre, model, session);
      }
      // Transport / timeout after send started — Q9 unconfirmed; clear claim (#943).
      PendingAuthClaimSession.clear(session, enrollmentId);
      applyOutcomeToResultModel(model, AuthApiOutcome.TRANSPORT);
      return "phone/ezkey/auth_result";
    }
  }

  /**
   * Maps a respond HTTP error to pending banner (429/503) or terminal result (409 / other).
   *
   * @param enrollmentId enrollment id
   * @param authAttemptId attempt id
   * @param error Auth API WebClient error
   * @param model Thymeleaf model
   * @param session HTTP session holding the claim
   * @return view name
   */
  private String handleRespondHttpError(
      Integer enrollmentId,
      Integer authAttemptId,
      WebClientResponseException error,
      Model model,
      HttpSession session) {
    String problemType = problemTypeFromError(error);
    int status = error.getStatusCode().value();
    logger.error(
        "Auth response failed for enrollment {} authAttempt {} status={} type={}",
        enrollmentId,
        authAttemptId,
        status,
        problemType != null ? problemType : "-");
    AuthApiOutcome outcome = classify(status, problemType);
    if (outcome == AuthApiOutcome.SIGNING_UNAVAILABLE || outcome == AuthApiOutcome.RATE_LIMITED) {
      return reShowPendingWithBanner(enrollmentId, session, model, outcome);
    }
    PendingAuthClaimSession.clear(session, enrollmentId);
    applyOutcomeToResultModel(model, outcome);
    return "phone/ezkey/auth_result";
  }

  /**
   * Re-shows Approve/Deny with a neutral banner; claim stays in session (#943).
   *
   * @param enrollmentId enrollment id
   * @param session HTTP session
   * @param model Thymeleaf model
   * @param outcome retryable outcome
   * @return {@code phone/ezkey/auth_pending} or idle auth if claim missing
   */
  private String reShowPendingWithBanner(
      Integer enrollmentId, HttpSession session, Model model, AuthApiOutcome outcome) {
    Optional<ClaimedPendingAuth> open =
        PendingAuthClaimSession.findOpen(
            session, enrollmentId, Instant.now(), PendingAuthClaimSession.DEFAULT_TTL);
    if (open.isEmpty()) {
      model.addAttribute("pageTitle", "Authentication");
      model.addAttribute("enrollmentId", enrollmentId);
      model.addAttribute("hasPendingAuth", false);
      model.addAttribute("error", messageFor(outcome));
      return "phone/ezkey/auth";
    }
    ClaimedPendingAuth claim = open.get();
    model.addAttribute("pageTitle", "Authentication Request");
    model.addAttribute("enrollmentId", enrollmentId);
    Optional<Record> recOpt = storeService.load(enrollmentId);
    if (recOpt.isPresent()) {
      Record rec = recOpt.get();
      model.addAttribute("enrollmentName", rec.enrollmentName());
      model.addAttribute("integrationName", rec.integrationName());
      model.addAttribute("integrationDescription", rec.integrationDescription());
      model.addAttribute("integrationLogo", rec.integrationLogo());
    }
    applyClaimedPendingToModel(model, claim);
    model.addAttribute("pendingBanner", messageFor(outcome));
    model.addAttribute("pendingBannerRetryable", true);
    return "phone/ezkey/auth_pending";
  }

  /**
   * Classifies Auth API HTTP status + optional ProblemDetail type into a Demo Device outcome.
   *
   * <p>Tolerant of missing or older {@code type}. Names aligned with mobile {@code
   * authApiProblem.ts}.
   *
   * @param statusCode HTTP status
   * @param problemType sanitized type URI, or {@code null}
   * @return outcome enum value
   */
  static AuthApiOutcome classify(int statusCode, String problemType) {
    if (statusCode == 409) {
      if (isAuthAttemptExpiredProblem(problemType)) {
        return AuthApiOutcome.EXPIRED;
      }
      return AuthApiOutcome.NO_LONGER_ACTIVE;
    }
    if (statusCode == 429) {
      return AuthApiOutcome.RATE_LIMITED;
    }
    if (statusCode == 503 || isIntegrationSigningUnavailableProblem(problemType)) {
      return AuthApiOutcome.SIGNING_UNAVAILABLE;
    }
    if (statusCode >= 500) {
      return AuthApiOutcome.UNCONFIRMED;
    }
    return AuthApiOutcome.HTTP_OTHER;
  }

  /**
   * User-facing message for an outcome — never includes status codes, URLs, hosts, or methods.
   *
   * @param outcome classified outcome
   * @return Julie / Q9 locked copy
   */
  static String messageFor(AuthApiOutcome outcome) {
    if (outcome == null) {
      return MSG_AUTH_GENERIC;
    }
    return switch (outcome) {
      case EXPIRED -> MSG_AUTH_EXPIRED;
      case NO_LONGER_ACTIVE -> MSG_AUTH_NO_LONGER_ACTIVE;
      case SIGNING_UNAVAILABLE -> MSG_AUTH_SIGNING_UNAVAILABLE;
      case RATE_LIMITED -> MSG_AUTH_RATE_LIMITED;
      case UNCONFIRMED, TRANSPORT -> MSG_AUTH_UNCONFIRMED;
      case HTTP_OTHER -> MSG_AUTH_GENERIC;
    };
  }

  /**
   * Applies a terminal outcome to the auth result model (Back only; no Approve/Deny).
   *
   * @param model Thymeleaf model
   * @param outcome terminal or unconfirmed outcome
   */
  static void applyOutcomeToResultModel(Model model, AuthApiOutcome outcome) {
    model.addAttribute("success", false);
    model.addAttribute("denied", false);
    model.addAttribute("failed", false);
    model.addAttribute("expired", false);
    model.addAttribute("inactive", false);
    model.addAttribute("unconfirmed", false);
    model.addAttribute("message", messageFor(outcome));
    if (outcome == AuthApiOutcome.EXPIRED) {
      model.addAttribute("expired", true);
      return;
    }
    if (outcome == AuthApiOutcome.NO_LONGER_ACTIVE) {
      model.addAttribute("inactive", true);
      return;
    }
    if (outcome == AuthApiOutcome.UNCONFIRMED || outcome == AuthApiOutcome.TRANSPORT) {
      model.addAttribute("unconfirmed", true);
      return;
    }
    model.addAttribute("failed", true);
  }

  /**
   * Maps respond HTTP conflicts to the auth result UI (legacy entry used by tests).
   *
   * @param model Thymeleaf model
   * @param statusCode HTTP status from Auth API
   * @param problemType sanitized ProblemDetail type, or {@code null}
   */
  static void applyRespondConflictToModel(Model model, int statusCode, String problemType) {
    applyOutcomeToResultModel(model, classify(statusCode, problemType));
  }

  private static void applyFailedResult(Model model, String message) {
    model.addAttribute("success", false);
    model.addAttribute("denied", false);
    model.addAttribute("failed", true);
    model.addAttribute("expired", false);
    model.addAttribute("inactive", false);
    model.addAttribute("unconfirmed", false);
    model.addAttribute(
        "message", message != null && !message.isBlank() ? message : MSG_AUTH_GENERIC);
  }

  /**
   * Whether a ProblemDetail {@code type} identifies integration signing unavailable (#844).
   *
   * @param problemType sanitized type URI (may be {@code null})
   * @return {@code true} when the type suffix matches
   */
  static boolean isIntegrationSigningUnavailableProblem(String problemType) {
    if (problemType == null || problemType.isBlank()) {
      return false;
    }
    return problemType.endsWith(INTEGRATION_SIGNING_UNAVAILABLE_TYPE_SUFFIX);
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
