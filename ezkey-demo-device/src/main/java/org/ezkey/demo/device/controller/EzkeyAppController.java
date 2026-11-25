package org.ezkey.demo.device.controller;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
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
import org.springframework.web.reactive.function.client.WebClientRequestException;
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

  /** Suffix of ProblemDetail {@code type} for a temporary signing outage on respond (#844). */
  static final String INTEGRATION_SIGNING_UNAVAILABLE_TYPE_SUFFIX =
      "integration-signing-unavailable";

  /** Suffix of ProblemDetail {@code type} when pending no longer maps to an active enrollment. */
  static final String AUTH_ATTEMPT_BINDING_FAILED_TYPE_SUFFIX = "auth-attempt-binding-failed";

  // On-screen copy. Strings marked "mobile" are the mobile app's English strings
  // (ezkey_mobile/app/i18n/resources.ts): the evaluator must see the same outcome and tone (#943).
  // Never show HTTP codes, server `detail`, exception messages, URLs, or hosts.

  /** Mobile {@code pendingAuth.requestExpired}: typed 409 {@code auth-attempt-expired}. */
  static final String MSG_AUTH_EXPIRED = "This request has expired.";

  /** Mobile {@code pendingAuth.requestNoLongerActive}: any other 409 (cancel, decided, …). */
  static final String MSG_AUTH_NO_LONGER_ACTIVE =
      "This request is no longer active. No action is required.";

  /** Mobile {@code pendingAuth.resultNotConfirmed}: HTTP 200 but result signature missing/bad. */
  static final String MSG_RESULT_NOT_CONFIRMED =
      "The response was sent, but its confirmation could not be verified. Check the sign-in"
          + " where it was started.";

  /** Mobile {@code pendingAuth.uncertainDecision}: sent, then no usable answer (transport, 5xx). */
  static final String MSG_RESULT_UNCERTAIN =
      "The result of this request is uncertain. Check the sign-in where it was started. This is"
          + " not a failure — do not send another decision from this device.";

  /** Mobile {@code pendingAuth.integrationSigningUnavailable}: 503, retryable. */
  static final String MSG_SIGNING_UNAVAILABLE =
      "Authentication signing is temporarily unavailable. Try again later.";

  /** HTTP 429 on any Auth API call, retryable. */
  static final String MSG_RATE_LIMITED = "Too many attempts. Try again in a few seconds.";

  /** Mobile {@code pendingAuth.enterChallenge}: approve without a usable challenge. */
  static final String MSG_ENTER_CHALLENGE = "Enter the 2-digit code shown on the sign-in screen.";

  /** Mobile {@code pendingAuth.leaveDeclinesHint}, shown under Approve / Deny. */
  static final String MSG_LEAVE_DECLINES_HINT = "Leaving this screen declines the request.";

  /** Generic fallback (mobile {@code pendingAuth.requestFailed}, with a next step). */
  static final String MSG_REQUEST_FAILED = "Request failed. Try again.";

  /** No HTTP answer from the Auth API before anything was decided. */
  static final String MSG_AUTH_API_UNREACHABLE = "Could not reach the Auth API. Try again.";

  /** Pending refused with {@code auth-attempt-binding-failed} (mobile enrollment detail). */
  static final String MSG_ENROLLMENT_NO_LONGER_ACTIVE =
      "This enrollment is no longer active: the server no longer accepts it. Ask your"
          + " administrator for a new enrollment.";

  /** Bind answered with a key algorithm other than Ed25519 (mobile refuses it too). */
  static final String MSG_BIND_UNSUPPORTED_KEY =
      "This enrollment uses an unsupported integration key. Ask your administrator for a new"
          + " enrollment.";

  /** Bind failed without a usable HTTP answer. */
  static final String MSG_BIND_FAILED =
      "Bind failed. Check that the Auth API is reachable, then try again.";

  /** Verify failed; the local draft is discarded, as before. */
  static final String MSG_VERIFY_FAILED =
      "Verification failed. Please start over from the beginning.";

  /** Only Ed25519 integration keys are accepted at bind, like the mobile app. */
  static final String EXPECTED_INTEGRATION_KEY_ALGORITHM = "ed25519";

  /**
   * Terminal states of {@code auth_result}. Each sets one boolean model flag of the same name
   * (lower case) that the template reads.
   */
  enum ResultState {
    SUCCESS,
    DENIED,
    FAILED,
    EXPIRED,
    /** Neutral grey: 409 other than expired. Back only. */
    INACTIVE,
    /** Response sent; the integration's confirmation could not be verified. Back only. */
    UNCONFIRMED,
    /** Response sent; no usable answer came back. Back only. */
    UNCERTAIN
  }

  /** How an Auth API HTTP error on respond is shown. */
  enum RespondHttpOutcome {
    EXPIRED,
    NO_LONGER_ACTIVE,
    /** Retryable: back to the same request with a banner. */
    SIGNING_UNAVAILABLE,
    /** Retryable: back to the same request with a banner. */
    RATE_LIMITED,
    /** The request reached the server; its answer is unusable. */
    UNCERTAIN,
    FAILED
  }

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
      @RequestParam("enrollmentProofToken") String enrollmentProofToken,
      @RequestParam(value = "authApiBaseUrl", required = false) String authApiBaseUrl,
      Model model) {
    model.addAttribute("pageTitle", "Enrollment - Bind");
    BindResolution bindResolution;
    try {
      bindResolution =
          EnrollmentAuthApiUrlResolver.resolveForBind(
              authApiBaseUrl, configuredAuthApiBase, allowedAuthApiHosts);
    } catch (InvalidAuthApiUrlException e) {
      model.addAttribute("pageTitle", "New Enrollment");
      model.addAttribute("configuredAuthApiBase", configuredAuthApiBase);
      model.addAttribute("enrollmentProofToken", enrollmentProofToken);
      // Do not redisplay a rejected override URL (constant error only; no echo of attacker input)
      model.addAttribute("error", e.getMessage());
      return "phone/ezkey/new_enrollment";
    }
    logger.info(
        "Bind using Auth API {} (QR override submitted: {})",
        LogSanitizer.sanitizeForLog(bindResolution.apiBaseUrl()),
        authApiBaseUrl != null && !authApiBaseUrl.isBlank());
    try {
      EnrollmentBindResponseDto bindResponse =
          authApiService.bind(enrollmentProofToken, bindResolution.apiBaseUrl()).block();
      if (bindResponse != null && bindResponse.getEnrollmentId() != null) {
        Integer enrollmentId = bindResponse.getEnrollmentId();
        // Fail closed before generating or persisting anything, like the mobile wizard (#943)
        if (!EXPECTED_INTEGRATION_KEY_ALGORITHM.equalsIgnoreCase(
            bindResponse.getIntegrationKeyAlgorithm())) {
          logger.warn(
              "Bind refused for enrollment {}: integration key algorithm is not Ed25519",
              enrollmentId);
          model.addAttribute("error", MSG_BIND_UNSUPPORTED_KEY);
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
      logger.warn(
          "Bind API error: status={}, problemType={}",
          e.getStatusCode().value(),
          problemTypeFromError(e));
      model.addAttribute("error", toBindErrorMessage(e.getStatusCode().value()));
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Bind failed", e);
      model.addAttribute("error", MSG_BIND_FAILED);
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
   * Neutralizes control characters and bounds length for a ProblemDetail {@code type} log fragment.
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
   * Maps a bind API error status to an actionable message. Never echoes the response body or the
   * status code (#943).
   *
   * @param statusCode HTTP status from Auth API
   * @return on-screen message
   */
  static String toBindErrorMessage(int statusCode) {
    if (statusCode == 409) {
      return "This enrollment is already bound to a device. Create a new enrollment in the Admin UI"
          + " and scan its QR code.";
    }
    if (statusCode == 400) {
      return "This invitation cannot be used: it may be already used, mistyped, or expired. Create"
          + " a new enrollment in the Admin UI.";
    }
    if (statusCode == 429) {
      return MSG_RATE_LIMITED;
    }
    return MSG_BIND_FAILED;
  }

  /**
   * Classifies an Auth API HTTP error on respond, like the mobile app (#844, #895, #943).
   *
   * @param statusCode HTTP status
   * @param problemType sanitized ProblemDetail type, or {@code null}
   * @return how the outcome is shown
   */
  static RespondHttpOutcome classifyRespondHttpError(int statusCode, String problemType) {
    if (statusCode == 409) {
      return isAuthAttemptExpiredProblem(problemType)
          ? RespondHttpOutcome.EXPIRED
          : RespondHttpOutcome.NO_LONGER_ACTIVE;
    }
    if (statusCode == 503
        && problemType != null
        && problemType.endsWith(INTEGRATION_SIGNING_UNAVAILABLE_TYPE_SUFFIX)) {
      return RespondHttpOutcome.SIGNING_UNAVAILABLE;
    }
    if (statusCode == 429) {
      return RespondHttpOutcome.RATE_LIMITED;
    }
    if (statusCode >= 500) {
      return RespondHttpOutcome.UNCERTAIN;
    }
    return RespondHttpOutcome.FAILED;
  }

  /**
   * Sets the terminal result for {@code auth_result}: one boolean flag per {@link ResultState} plus
   * the message.
   *
   * @param model Thymeleaf model
   * @param state terminal state
   * @param message on-screen message (never a server detail or exception text)
   */
  static void applyResult(Model model, ResultState state, String message) {
    for (ResultState s : ResultState.values()) {
      model.addAttribute(s.name().toLowerCase(Locale.ROOT), s == state);
    }
    model.addAttribute("message", message);
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
      model.addAttribute("error", MSG_VERIFY_FAILED);
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
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Authentication check failed for enrollment {}", enrollmentId, e);
      model.addAttribute("error", toPendingErrorMessage(e));
      model.addAttribute("hasPendingAuth", false);
      return "phone/ezkey/auth";
    }
  }

  /**
   * Maps a pending (claim) failure to an on-screen message, never the exception text (#943).
   *
   * @param error failure from the pending call
   * @return on-screen message
   */
  static String toPendingErrorMessage(Exception error) {
    WebClientResponseException http =
        error instanceof WebClientResponseException direct
            ? direct
            : error.getCause() instanceof WebClientResponseException cause ? cause : null;
    if (http == null) {
      return isTransportFailure(error) ? MSG_AUTH_API_UNREACHABLE : MSG_REQUEST_FAILED;
    }
    if (http.getStatusCode().value() == 429) {
      return MSG_RATE_LIMITED;
    }
    String problemType = problemTypeFromError(http);
    if (problemType != null && problemType.endsWith(AUTH_ATTEMPT_BINDING_FAILED_TYPE_SUFFIX)) {
      return MSG_ENROLLMENT_NO_LONGER_ACTIVE;
    }
    return MSG_REQUEST_FAILED;
  }

  /**
   * Whether a failure means no HTTP answer came back (connection refused, reset, timeout).
   *
   * @param error failure from a blocking WebClient call
   * @return {@code true} for transport-level failures
   */
  static boolean isTransportFailure(Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (t instanceof WebClientRequestException || t instanceof TimeoutException) {
        return true;
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return false;
  }

  /**
   * Sends Approve / Deny, or the decline sent when the person leaves the request screen.
   *
   * <p>Outcomes follow the mobile pending-auth FSM where the evaluator sees them (#943): typed 409
   * expired vs no longer active (Back only); 503 signing unavailable and 429 return to the same
   * request with a banner and keep the session claim; a result whose signature cannot be verified
   * is "unconfirmed"; a request that was sent without a usable answer is "uncertain". The session
   * claim is cleared on every other outcome.
   *
   * <p>{@code leave=true} is the light leave-as-deny: the request screen's exit control sends a
   * Deny, then always returns to the enrollments list, whatever the outcome (mobile #834 Q3: never
   * trap the person; the integrating application shows the result).
   *
   * @param enrollmentId local enrollment id
   * @param authAttemptId claimed attempt id
   * @param approved {@code true} for Approve
   * @param challengeResponse 2-digit challenge (Approve only)
   * @param authAttemptProofToken proof token from the claimed pending
   * @param leave {@code true} when sent by the screen's exit control (forces Deny)
   * @param model Thymeleaf model
   * @param session HTTP session holding the claim
   * @return view name
   */
  @PostMapping("/enrollments/{enrollmentId}/auth/respond")
  public String respondToAuth(
      @PathVariable("enrollmentId") Integer enrollmentId,
      @RequestParam("authAttemptId") Integer authAttemptId,
      @RequestParam("approved") Boolean approved,
      @RequestParam(value = "challengeResponse", required = false) String challengeResponse,
      @RequestParam("authAttemptProofToken") String authAttemptProofToken,
      @RequestParam(value = "leave", defaultValue = "false") boolean leave,
      Model model,
      HttpSession session) {
    boolean accepted = Boolean.TRUE.equals(approved) && !leave;
    model.addAttribute("pageTitle", "Authentication Response");
    model.addAttribute("enrollmentId", enrollmentId);
    String view = "phone/ezkey/auth_result";
    boolean keepClaim = false;
    boolean sent = false;
    try {
      Optional<Record> recOpt = storeService.load(enrollmentId);
      if (recOpt.isEmpty()) {
        model.addAttribute("error", "Enrollment not found");
        view = "phone/ezkey/auth";
        return leave ? "redirect:/phone/ezkey" : view;
      }
      Record rec = recOpt.get();
      model.addAttribute("enrollmentName", rec.enrollmentName());
      model.addAttribute("integrationName", rec.integrationName());
      model.addAttribute("integrationDescription", rec.integrationDescription());
      model.addAttribute("integrationLogo", rec.integrationLogo());

      // Sign canonical payload (proofToken|accepted) so backend can verify accepted was not
      // tampered
      String respondPayload =
          AuthAttemptPayloadUtil.buildRespondPayload(authAttemptProofToken, accepted);
      String responseSignature =
          cryptoService.signStringToBase64(respondPayload, rec.devicePrivateKey());
      AuthAttemptRespondRequestDto respondRequest =
          new AuthAttemptRespondRequestDto()
              .authAttemptId(authAttemptId)
              .authAttemptAccepted(accepted)
              .authAttemptProofTokenSignedByDevice(responseSignature);

      // Challenge on Approve only; the server skips it on Deny. Never log its value (#753).
      if (accepted && challengeResponse != null && !challengeResponse.isBlank()) {
        try {
          respondRequest.authAttemptChallengeResponse(Integer.parseInt(challengeResponse.trim()));
        } catch (NumberFormatException e) {
          logger.warn(
              "Invalid challenge response format for auth attempt {} enrollment {}",
              authAttemptId,
              enrollmentId);
          keepClaim = reshowClaimedPending(model, session, enrollmentId, MSG_ENTER_CHALLENGE);
          if (keepClaim) {
            view = "phone/ezkey/auth_pending";
          } else {
            applyResult(model, ResultState.INACTIVE, MSG_AUTH_NO_LONGER_ACTIVE);
          }
          return view;
        }
      }
      logger.info(
          "Responding to auth attempt {} for enrollment {} (leave={})",
          authAttemptId,
          enrollmentId,
          leave);

      String authApiBase =
          EnrollmentAuthApiUrlResolver.resolveForStoredEnrollment(
              rec, configuredAuthApiBase, allowedAuthApiHosts);
      sent = true;
      AuthAttemptRespondResponseDto respondResponse =
          authApiService.respond(respondRequest, authApiBase).block();
      if (respondResponse == null) {
        applyResult(model, ResultState.UNCERTAIN, MSG_RESULT_UNCERTAIN);
      } else {
        applyVerifiedRespondResult(model, rec, authAttemptProofToken, respondResponse);
      }
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      WebClientResponseException http =
          e instanceof WebClientResponseException direct
              ? direct
              : e.getCause() instanceof WebClientResponseException cause ? cause : null;
      if (http != null) {
        // Never log the raw problem body (#754)
        String problemType = problemTypeFromError(http);
        logger.warn(
            "Auth response refused for enrollment {} authAttempt {} status={} type={}",
            enrollmentId,
            authAttemptId,
            http.getStatusCode().value(),
            problemType != null ? problemType : "-");
        RespondHttpOutcome outcome =
            classifyRespondHttpError(http.getStatusCode().value(), problemType);
        if (!leave
            && (outcome == RespondHttpOutcome.SIGNING_UNAVAILABLE
                || outcome == RespondHttpOutcome.RATE_LIMITED)) {
          String banner =
              outcome == RespondHttpOutcome.SIGNING_UNAVAILABLE
                  ? MSG_SIGNING_UNAVAILABLE
                  : MSG_RATE_LIMITED;
          keepClaim = reshowClaimedPending(model, session, enrollmentId, banner);
        }
        if (keepClaim) {
          view = "phone/ezkey/auth_pending";
        } else {
          applyRespondHttpOutcome(model, outcome);
        }
      } else {
        logger.error(
            "Auth response failed for enrollment {} authAttempt {}",
            enrollmentId,
            authAttemptId,
            e);
        if (sent) {
          applyResult(model, ResultState.UNCERTAIN, MSG_RESULT_UNCERTAIN);
        } else {
          applyResult(model, ResultState.FAILED, MSG_REQUEST_FAILED);
        }
      }
    } finally {
      if (!keepClaim) {
        PendingAuthClaimSession.clear(session, enrollmentId);
      }
    }
    return leave ? "redirect:/phone/ezkey" : view;
  }

  /**
   * Shows a respond HTTP 200: verifies the integration's result signature, then maps the result.
   *
   * @param model Thymeleaf model
   * @param rec local enrollment
   * @param authAttemptProofToken proof token from the claimed pending
   * @param respondResponse Auth API answer
   */
  private void applyVerifiedRespondResult(
      Model model,
      Record rec,
      String authAttemptProofToken,
      AuthAttemptRespondResponseDto respondResponse) {
    String result =
        respondResponse.getAuthAttemptResult() != null
            ? respondResponse.getAuthAttemptResult().getValue()
            : null;
    String resultPayload =
        AuthAttemptPayloadUtil.buildRespondResultPayload(
            authAttemptProofToken,
            respondResponse.getAuthAttemptId(),
            result,
            respondResponse.getAuthAttemptMessage());
    String respondSig = respondResponse.getAuthAttemptProofTokenResultSignedByIntegration();
    // Mobile #834 Q2/Q9: the decision was sent; without a valid signature, show neither approved
    // nor denied — "unconfirmed", Back only.
    if (respondSig == null
        || respondSig.isBlank()
        || !cryptoService.validateSignature(
            resultPayload, respondSig, rec.integrationPublicKey())) {
      applyResult(model, ResultState.UNCONFIRMED, MSG_RESULT_NOT_CONFIRMED);
      return;
    }
    String message = respondResponse.getAuthAttemptMessage();
    if ("APPROVED".equals(result)) {
      applyResult(model, ResultState.SUCCESS, message);
    } else if ("DENIED".equals(result)) {
      applyResult(model, ResultState.DENIED, message);
    } else if ("EXPIRED".equals(result)) {
      applyResult(model, ResultState.EXPIRED, message);
    } else {
      // FAILED (e.g. wrong challenge) or an unknown value: the signed message when present
      applyResult(
          model,
          ResultState.FAILED,
          "FAILED".equals(result) && message != null ? message : MSG_REQUEST_FAILED);
    }
  }

  /**
   * Shows a terminal respond HTTP error on {@code auth_result}.
   *
   * @param model Thymeleaf model
   * @param outcome classified outcome
   */
  static void applyRespondHttpOutcome(Model model, RespondHttpOutcome outcome) {
    switch (outcome) {
      case EXPIRED -> applyResult(model, ResultState.EXPIRED, MSG_AUTH_EXPIRED);
      case NO_LONGER_ACTIVE -> applyResult(model, ResultState.INACTIVE, MSG_AUTH_NO_LONGER_ACTIVE);
      case UNCERTAIN -> applyResult(model, ResultState.UNCERTAIN, MSG_RESULT_UNCERTAIN);
      case SIGNING_UNAVAILABLE -> applyResult(model, ResultState.FAILED, MSG_SIGNING_UNAVAILABLE);
      case RATE_LIMITED -> applyResult(model, ResultState.FAILED, MSG_RATE_LIMITED);
      default -> applyResult(model, ResultState.FAILED, MSG_REQUEST_FAILED);
    }
  }

  /**
   * Re-shows the claimed request with a banner, keeping the claim (retryable outcomes).
   *
   * @param model Thymeleaf model
   * @param session HTTP session
   * @param enrollmentId local enrollment id
   * @param banner neutral message shown above Approve / Deny
   * @return {@code true} when the claim was still open and the request is shown again
   */
  private static boolean reshowClaimedPending(
      Model model, HttpSession session, Integer enrollmentId, String banner) {
    Optional<ClaimedPendingAuth> claim =
        PendingAuthClaimSession.findOpen(
            session, enrollmentId, Instant.now(), PendingAuthClaimSession.DEFAULT_TTL);
    if (claim.isEmpty()) {
      return false;
    }
    applyClaimedPendingToModel(model, claim.get());
    model.addAttribute("retryMessage", banner);
    return true;
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
    model.addAttribute("leaveHint", MSG_LEAVE_DECLINES_HINT);
    model.addAttribute("hasPendingAuth", true);
  }
}
