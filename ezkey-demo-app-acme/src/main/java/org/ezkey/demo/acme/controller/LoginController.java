/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: LoginController
 * Description: Handles login POST requests and coordinates EZKey authentication flow.
 */

package org.ezkey.demo.acme.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.ezkey.demo.acme.DemoAuthMessages;
import org.ezkey.demo.acme.config.EzkeyClientProvider;
import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.ezkey.demo.acme.security.DemoRateLimitService;
import org.ezkey.demo.acme.service.AccessCodeService;
import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.ezkey.demo.acme.web.LinkEntryMarker;
import org.ezkey.demo.acme.web.LogSanitizer;
import org.ezkey.demo.acme.web.SessionHelpers;
import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.WebUtils;

/**
 * Controller handling login POST requests and coordinating EZKey authentication flow.
 *
 * <p>This controller processes login form submissions, creates auth attempts via Integration API,
 * waits for device approval, and creates HTTP sessions upon successful authentication. Challenge
 * mode is always requested server-side.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Controller
public class LoginController {

  private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

  private static final String SDK_NOT_CONFIGURED_MSG =
      "Ezkey SDK is not configured. Set credentials via an access link, config file, or use the"
          + " 'Apply API Key' dialog in the About This Demo section.";

  /**
   * Session flag: ACCEPTED path already rotated the session id once. Concurrent or late polls must
   * not call {@link HttpServletRequest#changeSessionId()} again.
   */
  static final String AUTH_ACCEPTED_SESSION_ROTATED = "authAcceptedSessionRotated";

  /**
   * Server-side wait for {@code /wait} — must stay clearly below the SDK default HTTP read timeout
   * (30s) so the client does not abort a few ms before the Integration API responds.
   */
  static final int AUTH_STATUS_WAIT_SECONDS = 20;

  /** Server-side polling interval passed to {@code /wait}. */
  static final int AUTH_STATUS_POLL_SECONDS = 2;

  private final EzkeyClientProvider ezkeyClientProvider;
  private final DemoApiKeyConfigService demoApiKeyConfigService;
  private final DemoRateLimitService demoRateLimitService;
  private final AccessCodeService accessCodeService;

  /**
   * Creates the login controller.
   *
   * @param ezkeyClientProvider SDK client factory
   * @param demoApiKeyConfigService credential resolution
   * @param demoRateLimitService rate limiting
   * @param accessCodeService access-code slot labels
   */
  public LoginController(
      EzkeyClientProvider ezkeyClientProvider,
      DemoApiKeyConfigService demoApiKeyConfigService,
      DemoRateLimitService demoRateLimitService,
      AccessCodeService accessCodeService) {
    this.ezkeyClientProvider = ezkeyClientProvider;
    this.demoApiKeyConfigService = demoApiKeyConfigService;
    this.demoRateLimitService = demoRateLimitService;
    this.accessCodeService = accessCodeService;
  }

  /**
   * Handles POST /login form submission.
   *
   * <p>Always requests a challenge server-side ({@code challengeRequested=true}), ignoring any
   * client-supplied flag.
   *
   * @param username the username (used as userIdentifier for API lookup)
   * @param request the HTTP request
   * @param session the HTTP session
   * @param redirectAttributes for flash messages
   * @return redirect to challenge-wait on success, back to login on error
   */
  @PostMapping("/login")
  public String login(
      @RequestParam("username") String username,
      @RequestParam(value = LinkEntryMarker.PARAM, required = false) String entry,
      HttpServletRequest request,
      HttpSession session,
      RedirectAttributes redirectAttributes) {

    // Layout-only flag: never used to decide whether security steps run.
    final boolean layoutLinkMarker = LinkEntryMarker.isLink(entry);
    final boolean slotActive = demoApiKeyConfigService.getActiveSlotId(session) != null;

    // --- Security path (identical with or without entry=link) ---
    DemoRateLimitService.RateLimitDecision rateLimitDecision =
        demoRateLimitService.checkLogin(request);
    if (!rateLimitDecision.allowed()) {
      logger.warn(
          "Login rate limit exceeded for clientIp={} retryAfterSeconds={}",
          rateLimitDecision.clientId(),
          rateLimitDecision.retryAfterSeconds());
      redirectAttributes.addFlashAttribute("error", DemoAuthMessages.RATE_LIMIT_LOGIN);
      return loginRedirectWithOptionalMarker("error=ratelimited", slotActive, layoutLinkMarker);
    }

    String usernameForLog = LogSanitizer.sanitizeForLog(username);
    logger.info("Login attempt for username: {}", usernameForLog);

    // Clear any previous final status flag and pending attributes when starting a
    // new login attempt
    session.removeAttribute("authAttemptFinalStatus");
    session.removeAttribute("pendingAuthAttemptId");
    session.removeAttribute("pendingChallengeCode");
    session.removeAttribute("pendingUsername");
    session.removeAttribute("pendingDisplayName");
    session.removeAttribute("pendingEnrollmentId");
    session.removeAttribute("pendingTimeoutSeconds");
    session.removeAttribute("pendingExpiresAt");

    EzkeyClient client = ezkeyClientProvider.getClient(session);
    // Gate is credentials availability only — never the layout marker.
    if (client == null) {
      return rejectWhenClientMissing(redirectAttributes, slotActive, layoutLinkMarker);
    }

    try {
      // Challenge always on (server-enforced; client checkbox removed).
      var createResponse = client.createAuthAttemptByUserIdentifier(username.trim(), true);

      logger.info(
          "Auth attempt created: authAttemptId={}, userIdentifier={}",
          createResponse.authAttemptId(),
          usernameForLog);

      session.setAttribute("pendingAuthAttemptId", createResponse.authAttemptId());
      session.setAttribute("pendingChallengeCode", createResponse.authAttemptChallenge());
      session.setAttribute("pendingUsername", username);
      session.setAttribute("pendingDisplayName", username);
      session.setAttribute("pendingEnrollmentId", null);
      session.setAttribute("pendingTimeoutSeconds", createResponse.timeoutSeconds());
      session.setAttribute("pendingExpiresAt", createResponse.expiresAt());

      logger.info("Redirecting to wait page for authAttemptId={}", createResponse.authAttemptId());

      return "redirect:/challenge-wait";

    } catch (EzkeyException e) {
      logger.error(
          "EZKey authentication error for username={} exceptionClass={} httpStatus={}",
          usernameForLog,
          e.getClass().getSimpleName(),
          e.getStatusCode());
      redirectAttributes.addFlashAttribute("error", DemoAuthMessages.GENERIC_SIGN_IN_FAILED);
      return loginRedirectWithOptionalMarker("error=authfailed", slotActive, layoutLinkMarker);
    }
  }

  /**
   * Rejects login when the SDK client cannot be resolved.
   *
   * <p>Always rejects (no auth attempt). {@code layoutLinkMarker} only selects link-lost copy and
   * the {@code entry=link} redirect suffix — never whether rejection happens.
   *
   * @param redirectAttributes flash attributes
   * @param slotActive whether an access-code slot is in the session
   * @param layoutLinkMarker whether the request carried {@code entry=link}
   * @return redirect view name
   */
  private static String rejectWhenClientMissing(
      RedirectAttributes redirectAttributes, boolean slotActive, boolean layoutLinkMarker) {
    boolean linkLostCopy = !slotActive && layoutLinkMarker;
    if (linkLostCopy) {
      logger.warn("Login attempt rejected — access-link slot no longer in session");
      redirectAttributes.addFlashAttribute("error", DemoAuthMessages.SESSION_OR_SLOT_LOST);
    } else {
      logger.error("Login attempt rejected — Ezkey SDK not configured");
      redirectAttributes.addFlashAttribute("error", SDK_NOT_CONFIGURED_MSG);
    }
    String query = linkLostCopy ? "error=sessionexpired" : "error=authfailed";
    return loginRedirectWithOptionalMarker(query, slotActive, layoutLinkMarker);
  }

  /**
   * Login redirect with optional {@code entry=link} suffix for layout only.
   *
   * @param query query without leading {@code ?}
   * @param slotActive active access-code slot
   * @param layoutLinkMarker request carried the marker
   * @return Spring redirect string
   */
  private static String loginRedirectWithOptionalMarker(
      String query, boolean slotActive, boolean layoutLinkMarker) {
    return redirectLogin(query, slotActive || layoutLinkMarker);
  }

  /**
   * Handles GET /login.
   *
   * <p>Layout states: <strong>A</strong> active slot (tenant link), <strong>B</strong> link lost
   * ({@code entry=link} without slot), <strong>C</strong> self-service demo (unchanged chrome).
   *
   * @param error optional error parameter
   * @param logout optional logout parameter
   * @param entry optional {@link LinkEntryMarker} value
   * @param session the HTTP session (for active slot label)
   * @param model the Spring MVC model
   * @return login page template
   */
  @GetMapping("/login")
  public String loginPage(
      @RequestParam(value = "error", required = false) String error,
      @RequestParam(value = "logout", required = false) String logout,
      @RequestParam(value = LinkEntryMarker.PARAM, required = false) String entry,
      HttpSession session,
      Model model) {

    model.addAttribute("pageTitle", "Login - ACME Inc");

    String slotId = demoApiKeyConfigService.getActiveSlotId(session);
    boolean slotActive = slotId != null;
    boolean entryLink = LinkEntryMarker.isLink(entry);
    // A = slot; B = marker without slot; C = neither.
    boolean showSelfServiceChrome = !slotActive && !entryLink;
    boolean showTenantLinkInfoCard = slotActive;
    boolean showLoginForm = slotActive || !entryLink;

    model.addAttribute("showSelfServiceChrome", showSelfServiceChrome);
    model.addAttribute("showTenantLinkInfoCard", showTenantLinkInfoCard);
    model.addAttribute("showLoginForm", showLoginForm);
    model.addAttribute("entryLink", slotActive || entryLink);

    if (slotActive) {
      String label = accessCodeService.getLabel(slotId);
      if (label != null && !label.isBlank()) {
        model.addAttribute("slotLabel", label);
        model.addAttribute("loginHeading", "Sign in to " + label);
      }
    }

    boolean sessionOrSlotLostMessage = false;
    if (error != null) {
      model.addAttribute("hasError", true);
      switch (error) {
        case "rejected":
          model.addAttribute("error", "Authentication rejected by user. Please try again.");
          break;
        case "expired":
          model.addAttribute("error", "Authentication request expired. Please try again.");
          break;
        case "ratelimited":
          model.addAttribute("error", DemoAuthMessages.RATE_LIMIT_LOGIN);
          break;
        case "sessionexpired":
          model.addAttribute("error", DemoAuthMessages.SESSION_OR_SLOT_LOST);
          sessionOrSlotLostMessage = true;
          break;
        default:
          model.addAttribute("error", DemoAuthMessages.GENERIC_SIGN_IN_FAILED);
      }
    }
    if (logout != null) {
      model.addAttribute("logoutMessage", "You have been logged out.");
    }

    // State B recovery line — not when SESSION_OR_SLOT_LOST already says to reopen the link.
    if (!slotActive && entryLink && !sessionOrSlotLostMessage) {
      model.addAttribute("recoveryHint", DemoAuthMessages.LINK_RECOVERY_REOPEN);
    }

    return "login";
  }

  /**
   * Applies API key credentials at runtime for temporary evaluator console access.
   *
   * <p>Validates keys first, then invalidates the previous session (clears slot or prior paste),
   * opens a new session, and stores the pasted keys. The client must reload to pick up the new CSRF
   * token.
   *
   * @param body the API key credentials (integrationKey, secretKey)
   * @param request the HTTP request
   * @return JSON response indicating success or failure
   */
  @PostMapping("/api/apply-api-key")
  public ResponseEntity<ApplyApiKeyResponse> applyApiKey(
      @RequestBody ApplyApiKeyRequest body, HttpServletRequest request) {
    DemoRateLimitService.RateLimitDecision rateLimitDecision =
        demoRateLimitService.checkApplyApiKey(request);
    if (!rateLimitDecision.allowed()) {
      logger.warn(
          "Apply API key rate limit exceeded for clientIp={} retryAfterSeconds={}",
          rateLimitDecision.clientId(),
          rateLimitDecision.retryAfterSeconds());
      return ResponseEntity.status(429)
          .header("Retry-After", String.valueOf(rateLimitDecision.retryAfterSeconds()))
          .body(new ApplyApiKeyResponse(false, DemoAuthMessages.RATE_LIMIT_APPLY_API_KEY));
    }

    if (body == null || body.integrationKey() == null || body.secretKey() == null) {
      return ResponseEntity.badRequest()
          .body(new ApplyApiKeyResponse(false, "Integration key and secret key are required."));
    }
    if (body.integrationKey().isBlank() || body.secretKey().isBlank()) {
      return ResponseEntity.badRequest()
          .body(
              new ApplyApiKeyResponse(
                  false, "Both integration key and secret key must be non-blank."));
    }

    // Validate before invalidate so a failed paste does not drop the active slot / CSRF session.
    HttpSession session = SessionHelpers.invalidateAndCreate(request);

    boolean applied =
        demoApiKeyConfigService.applyApiKey(session, body.integrationKey(), body.secretKey());
    if (applied) {
      return ResponseEntity.ok(
          new ApplyApiKeyResponse(
              true,
              "API key applied. Reload the page, then you can test login attempts in this browser"
                  + " session."));
    }
    return ResponseEntity.badRequest()
        .body(
            new ApplyApiKeyResponse(
                false, "Both integration key and secret key must be non-blank."));
  }

  /**
   * Displays the challenge wait page with the challenge code.
   *
   * @param session the HTTP session
   * @param model the Spring MVC model
   * @return challenge-wait page template
   */
  @GetMapping("/challenge-wait")
  public String challengeWaitPage(HttpSession session, Model model) {
    Integer authAttemptId = (Integer) session.getAttribute("pendingAuthAttemptId");
    Integer challengeCode = (Integer) session.getAttribute("pendingChallengeCode");
    String username = (String) session.getAttribute("pendingUsername");
    Integer timeoutSeconds = (Integer) session.getAttribute("pendingTimeoutSeconds");
    String expiresAt = (String) session.getAttribute("pendingExpiresAt");
    boolean slotMode = demoApiKeyConfigService.getActiveSlotId(session) != null;

    if (authAttemptId == null || username == null) {
      // Do not guess entry=link after session loss — challenge-wait JS appends the marker
      // when the page was rendered in slot mode; a bare hit here falls to self-service C.
      logger.warn("Challenge wait page accessed without pending auth attempt");
      return "redirect:/login?error=sessionexpired";
    }

    String challengeCodeFormatted = null;
    if (challengeCode != null) {
      challengeCodeFormatted = "%02d".formatted(challengeCode);
    }

    model.addAttribute(
        "pageTitle",
        challengeCode != null ? "Enter Challenge Code - ACME Inc" : "Awaiting Approval - ACME Inc");
    model.addAttribute("challengeCode", challengeCodeFormatted);
    model.addAttribute("authAttemptId", authAttemptId);
    model.addAttribute("username", username);
    model.addAttribute("timeoutSeconds", timeoutSeconds);
    model.addAttribute("expiresAt", expiresAt);
    model.addAttribute("entryLink", slotMode);

    return "challenge-wait";
  }

  /**
   * Checks the status of a pending authentication attempt (for polling).
   *
   * <p>This endpoint is called by the challenge-wait page to check if the authentication attempt
   * has been approved.
   *
   * @param request the HTTP request (for session id change on ACCEPTED)
   * @param session the HTTP session
   * @return JSON response with status and redirect URL if approved
   */
  @GetMapping("/api/auth-status")
  public ResponseEntity<AuthStatusResponse> checkAuthStatus(
      HttpServletRequest request, HttpSession session) {
    boolean slotMode = demoApiKeyConfigService.getActiveSlotId(session) != null;

    // Already authenticated, or ACCEPTED already applied (session rotated once).
    if (session.getAttribute("user") instanceof AuthenticatedUser
        || Boolean.TRUE.equals(session.getAttribute(AUTH_ACCEPTED_SESSION_ROTATED))
        || "ACCEPTED".equals(session.getAttribute("authAttemptFinalStatus"))) {
      return ResponseEntity.ok(
          new AuthStatusResponse("accepted", "/dashboard", "Authentication successful"));
    }

    // Check if a final status was already returned (prevents race condition)
    // This handles the case where a poll arrives after we've already returned a
    // final status
    // and cleaned up session attributes, preventing "session expired" from being
    // returned
    String finalStatus = (String) session.getAttribute("authAttemptFinalStatus");
    if (finalStatus != null) {
      logger.info(
          "Final status already returned: {}, returning same status to prevent race condition",
          finalStatus);
      // A final status was already returned, return the same status to prevent race
      // condition
      if ("REJECTED".equals(finalStatus)) {
        return ResponseEntity.ok(
            new AuthStatusResponse("rejected", loginPath("error=rejected", false), "Rejected"));
      } else if ("EXPIRED".equals(finalStatus)) {
        return ResponseEntity.ok(
            new AuthStatusResponse("expired", loginPath("error=expired", false), "Expired"));
      } else if ("INVALID".equals(finalStatus)) {
        return ResponseEntity.ok(
            new AuthStatusResponse(
                "error",
                loginPath("error=authfailed", false),
                DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
      } else if ("UNKNOWN".equals(finalStatus) || "ERROR".equals(finalStatus)) {
        return ResponseEntity.ok(
            new AuthStatusResponse(
                "error",
                loginPath("error=authfailed", false),
                DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
      }
    }

    Integer authAttemptId = (Integer) session.getAttribute("pendingAuthAttemptId");
    String username = (String) session.getAttribute("pendingUsername");
    String displayName = (String) session.getAttribute("pendingDisplayName");
    Integer enrollmentId = (Integer) session.getAttribute("pendingEnrollmentId");

    if (authAttemptId == null || username == null) {
      return ResponseEntity.ok(
          new AuthStatusResponse(
              "expired",
              loginPath("error=sessionexpired", false),
              DemoAuthMessages.SESSION_OR_SLOT_LOST));
    }

    EzkeyClient client = ezkeyClientProvider.getClient(session);
    if (client == null) {
      if (slotMode) {
        return ResponseEntity.ok(
            new AuthStatusResponse(
                "expired",
                loginPath("error=sessionexpired", false),
                DemoAuthMessages.SESSION_OR_SLOT_LOST));
      }
      return ResponseEntity.ok(
          new AuthStatusResponse("error", "/login?error=authfailed", SDK_NOT_CONFIGURED_MSG));
    }

    String usernameForLog = LogSanitizer.sanitizeForLog(username);

    try {
      var waitResponse =
          client.waitForAuthAttempt(
              authAttemptId, AUTH_STATUS_WAIT_SECONDS, AUTH_STATUS_POLL_SECONDS);

      String status = waitResponse.status();
      boolean completed = waitResponse.completed();

      logger.info(
          "Received auth attempt status: status='{}', completed={}, authAttemptId={}, username={}",
          status,
          completed,
          authAttemptId,
          usernameForLog);

      String normalizedStatus = status != null ? status.trim().toUpperCase(Locale.ROOT) : null;

      if (normalizedStatus == null || normalizedStatus.isEmpty()) {
        logger.warn(
            "Received null or empty status for authAttemptId={}, username={}, completed={}",
            authAttemptId,
            usernameForLog,
            completed);
        if (completed) {
          session.removeAttribute("pendingAuthAttemptId");
          session.removeAttribute("pendingChallengeCode");
          session.removeAttribute("pendingUsername");
          session.removeAttribute("pendingDisplayName");
          session.removeAttribute("pendingEnrollmentId");
          session.removeAttribute("pendingTimeoutSeconds");
          session.removeAttribute("pendingExpiresAt");
          return ResponseEntity.ok(
              new AuthStatusResponse(
                  "error",
                  loginPath("error=authfailed", false),
                  DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
        } else {
          return pendingOrExpired(session);
        }
      }

      if ("ACCEPTED".equals(normalizedStatus)) {
        // Rotate session id exactly once under the session mutex so concurrent long-polls
        // that all see ACCEPTED cannot issue multiple changeSessionId() calls (fixation
        // protection kept; late polls stay on a live session).
        synchronized (WebUtils.getSessionMutex(session)) {
          if (session.getAttribute("user") instanceof AuthenticatedUser
              || Boolean.TRUE.equals(session.getAttribute(AUTH_ACCEPTED_SESSION_ROTATED))) {
            return ResponseEntity.ok(
                new AuthStatusResponse("accepted", "/dashboard", "Authentication successful"));
          }

          AuthenticatedUser authenticatedUser =
              new AuthenticatedUser(
                  username, displayName != null ? displayName : username, enrollmentId);

          // Rotate first: if changeSessionId throws, do not mark accepted/rotated so a
          // later poll can still perform fixation protection.
          request.changeSessionId();
          session.setAttribute("user", authenticatedUser);
          session.setAttribute(AUTH_ACCEPTED_SESSION_ROTATED, Boolean.TRUE);
          session.setAttribute("authAttemptFinalStatus", "ACCEPTED");

          session.removeAttribute("pendingAuthAttemptId");
          session.removeAttribute("pendingChallengeCode");
          session.removeAttribute("pendingUsername");
          session.removeAttribute("pendingDisplayName");
          session.removeAttribute("pendingEnrollmentId");
        }

        logger.info("Challenge authentication successful for username: {}", usernameForLog);
        return ResponseEntity.ok(
            new AuthStatusResponse("accepted", "/dashboard", "Authentication successful"));
      } else if ("REJECTED".equals(normalizedStatus)) {
        // Authentication rejected by user
        // Mark as final status to prevent race condition with subsequent polls
        // Don't clear session attributes immediately - let them be cleared on next
        // request
        // This prevents a race condition where a poll arrives after cleanup and returns
        // "expired"
        session.setAttribute("authAttemptFinalStatus", "REJECTED");

        logger.info("Challenge authentication rejected for username: {}", usernameForLog);
        return ResponseEntity.ok(
            new AuthStatusResponse("rejected", loginPath("error=rejected", false), "Rejected"));
      } else if ("EXPIRED".equals(normalizedStatus)) {
        // Authentication expired
        // Mark as final status to prevent race condition with subsequent polls
        session.setAttribute("authAttemptFinalStatus", "EXPIRED");

        logger.info("Challenge authentication expired for username: {}", usernameForLog);
        return ResponseEntity.ok(
            new AuthStatusResponse("expired", loginPath("error=expired", false), "Expired"));
      } else if ("INVALID".equals(normalizedStatus)) {
        // Authentication invalid (wrong signature, challenge, etc.)
        // Mark as final status to prevent race condition with subsequent polls
        session.setAttribute("authAttemptFinalStatus", "INVALID");

        logger.info("Challenge authentication invalid for username: {}", usernameForLog);
        return ResponseEntity.ok(
            new AuthStatusResponse(
                "error",
                loginPath("error=authfailed", false),
                DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
      } else if (completed) {
        // Completed but unknown status
        // Mark as final status to prevent race condition
        session.setAttribute("authAttemptFinalStatus", "UNKNOWN");

        logger.warn(
            "Challenge authentication completed with unknown status: '{}' (normalized: '{}') for"
                + " username: {}",
            status,
            normalizedStatus,
            usernameForLog);
        return ResponseEntity.ok(
            new AuthStatusResponse(
                "error",
                loginPath("error=authfailed", false),
                DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
      } else {
        // PENDING / READ / server wait timeoutReached — keep polling until attempt TTL.
        return pendingOrExpired(session);
      }
    } catch (EzkeyException e) {
      if (isTransientWaitFailure(e)) {
        logger.info(
            "Auth status wait soft-timeout for authAttemptId={} — keep polling until attempt TTL",
            authAttemptId);
        return pendingOrExpired(session);
      }
      logger.error(
          "Error checking auth status for authAttemptId={} exceptionClass={} httpStatus={}",
          authAttemptId,
          e.getClass().getSimpleName(),
          e.getStatusCode());
      // Mark as error to prevent race condition
      session.setAttribute("authAttemptFinalStatus", "ERROR");
      return ResponseEntity.ok(
          new AuthStatusResponse(
              "error",
              loginPath("error=authfailed", false),
              DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
    }
  }

  /**
   * Returns pending while the attempt TTL remains; otherwise expired.
   *
   * <p>Redirect URLs are marker-free; challenge-wait JS appends {@code entry=link} when the page
   * was rendered in slot mode.
   *
   * @param session current session (may hold {@code pendingExpiresAt})
   * @return pending or expired status response
   */
  private static ResponseEntity<AuthStatusResponse> pendingOrExpired(HttpSession session) {
    if (isAttemptExpired(session)) {
      session.setAttribute("authAttemptFinalStatus", "EXPIRED");
      return ResponseEntity.ok(
          new AuthStatusResponse("expired", "/login?error=expired", "Expired"));
    }
    return ResponseEntity.ok(
        new AuthStatusResponse("pending", null, "Waiting for device approval..."));
  }

  /**
   * Login redirect view name, optionally carrying {@code entry=link}.
   *
   * @param query query without leading {@code ?} (e.g. {@code error=expired})
   * @param withLinkMarker whether to append the tenant-link layout marker
   * @return Spring redirect string
   */
  private static String redirectLogin(String query, boolean withLinkMarker) {
    return "redirect:" + loginPath(query, withLinkMarker);
  }

  /**
   * Absolute login path with optional {@code entry=link} marker.
   *
   * @param query query without leading {@code ?} (may be blank)
   * @param withLinkMarker whether to append the tenant-link layout marker
   * @return path such as {@code /login?error=expired&entry=link}
   */
  static String loginPath(String query, boolean withLinkMarker) {
    String path = (query == null || query.isBlank()) ? "/login" : "/login?" + query;
    return withLinkMarker ? LinkEntryMarker.withMarker(path) : path;
  }

  /**
   * Whether {@code pendingExpiresAt} is in the past (auth-attempt TTL elapsed).
   *
   * @param session current session
   * @return true when the stored expiry is parseable and before now
   */
  private static boolean isAttemptExpired(HttpSession session) {
    Object expiresAt = session.getAttribute("pendingExpiresAt");
    if (!(expiresAt instanceof String expiresAtText) || expiresAtText.isBlank()) {
      return false;
    }
    try {
      return OffsetDateTime.parse(expiresAtText).isBefore(OffsetDateTime.now());
    } catch (DateTimeParseException ignored) {
      return false;
    }
  }

  /**
   * Read timeouts must not abort challenge-wait polling before the attempt TTL.
   *
   * <p>Classification is by exception type/cause only ({@link EzkeyException#isReadTimeout()}),
   * never by message text — HTTP 5xx bodies and wait URLs may contain the word {@code timeout}.
   *
   * @param exception SDK exception from wait
   * @return true when the failure is a transient client read timeout
   */
  static boolean isTransientWaitFailure(EzkeyException exception) {
    return exception != null && exception.isReadTimeout();
  }

  /**
   * Request DTO for apply API key operation.
   *
   * @param integrationKey the integration key (e.g. ezkey_ikey_xxx)
   * @param secretKey the secret key (e.g. ezkey_skey_xxx)
   */
  public record ApplyApiKeyRequest(String integrationKey, String secretKey) {}

  /**
   * Response DTO for apply API key operation.
   *
   * @param success whether the operation was successful
   * @param message status message
   */
  public record ApplyApiKeyResponse(boolean success, String message) {}

  /**
   * Response DTO for authentication status check.
   *
   * @param status current status (pending, accepted, rejected, expired, error)
   * @param redirectUrl URL to redirect to if status is final
   * @param message status message
   */
  public record AuthStatusResponse(String status, String redirectUrl, String message) {}
}
