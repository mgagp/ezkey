/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminAuthService
 * Description: Service for passwordless administrator authentication.
 */

package org.ezkey.admin.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.ezkey.admin.config.AdminTokenRotationProperties;
import org.ezkey.admin.dto.AdminAuthAuditContext;
import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.exception.AdminAuthenticationException;
import org.ezkey.admin.exception.AdminAuthenticationExpiredException;
import org.ezkey.admin.exception.AdminAuthenticationRejectedException;
import org.ezkey.admin.exception.AdminAuthenticationTimeoutException;
import org.ezkey.admin.exception.AdminDeviceSignatureInvalidException;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.integration.domain.AdminTokenPurpose;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.security.SensitiveDataHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for passwordless administrator authentication.
 *
 * <p>This service handles passwordless authentication of administrators using Ezkey's cryptographic
 * authentication system ("eating our own dogfood"). It eliminates passwords entirely, providing
 * superior security through device-bound credentials.
 *
 * <p><b>Authentication Modes:</b>
 *
 * <ul>
 *   <li><b>Single-call (no challenge):</b> Blocking wait for device approval
 *   <li><b>Two-call (with challenge):</b> Return challenge code, wait separately
 * </ul>
 *
 * <p><b>Token Rotation:</b> When token rotation on login is enabled, this service deactivates all
 * existing active tokens for an administrator when they log in, ensuring only one active token
 * exists at any time.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
@Transactional
public class AdminAuthService {

  private static final Logger logger = LoggerFactory.getLogger(AdminAuthService.class);

  /** Generic client-facing message for all pre-authentication login failures (SEC-006). */
  private static final String GENERIC_LOGIN_FAILURE_MESSAGE = "Invalid username or password";

  private final EzkeyAdminRepository adminRepository;
  private final AdminTokenRepository tokenRepository;
  private final AdminTokenRotationProperties rotationProperties;
  private final AuthAttemptService authAttemptService;
  private final AuthAttemptRepository authAttemptRepository;
  private final AdminAuthAttemptTxHelper authAttemptTxHelper;

  public AdminAuthService(
      EzkeyAdminRepository adminRepository,
      AdminTokenRepository tokenRepository,
      AdminTokenRotationProperties rotationProperties,
      AuthAttemptService authAttemptService,
      AuthAttemptRepository authAttemptRepository,
      AdminAuthAttemptTxHelper authAttemptTxHelper) {
    this.adminRepository = adminRepository;
    this.tokenRepository = tokenRepository;
    this.rotationProperties = rotationProperties;
    this.authAttemptService = authAttemptService;
    this.authAttemptRepository = authAttemptRepository;
    this.authAttemptTxHelper = authAttemptTxHelper;
  }

  /**
   * Authenticate administrator using passwordless Ezkey authentication.
   *
   * <p>This is the only authentication method - passwords are not supported. Delegates to {@link
   * #authenticatePasswordless(AdminLoginRequestDto)} internally.
   *
   * <p>Exceptions thrown during authentication are handled by GlobalExceptionHandler and converted
   * to RFC 9457 ProblemDetail responses.
   *
   * @param request the login request containing username and optional challenge flag
   * @return AdminLoginResponseDto with authentication result (success only)
   * @throws AdminAuthenticationException if credentials are invalid or login is not permitted (HTTP
   *     401; generic message — see SEC-006)
   * @throws AdminAuthenticationExpiredException if auth attempt expired (HTTP 400)
   * @throws AdminAuthenticationRejectedException if device rejected (HTTP 400)
   * @throws AdminDeviceSignatureInvalidException if signature invalid (HTTP 400)
   * @throws AdminAuthenticationTimeoutException if no device response (HTTP 408)
   */
  public AdminLoginResponseDto authenticate(AdminLoginRequestDto request) {
    logger.info("Passwordless authentication attempt for user: {}", request.username());
    return authenticatePasswordless(request);
  }

  /**
   * Authenticate administrator using passwordless Ezkey cryptographic authentication.
   *
   * <p>This method implements "eat your own dogfood" by using Ezkey's MFA system for admin
   * authentication without passwords. The flow:
   *
   * <ol>
   *   <li>Validate username and passwordless eligibility
   *   <li>Create auth attempt internally
   *   <li>Branch based on challenge requirement and nonBlocking flag:
   *       <ul>
   *         <li><b>Challenge required (any mode):</b> Return immediately with challenge code
   *             (non-blocking)
   *         <li><b>No challenge + nonBlocking=true:</b> Return immediately with authAttemptId for
   *             client polling (enables countdown timer in TUI)
   *         <li><b>No challenge + nonBlocking=false/null:</b> Block and wait for device response
   *             (backward compatible)
   *       </ul>
   *   <li>Issue bearer token on approval
   * </ol>
   *
   * <p><b>Security:</b> This provides superior security compared to passwords:
   *
   * <ul>
   *   <li>No password to steal or guess
   *   <li>Phishing resistant (cryptographic signatures)
   *   <li>Device-bound credentials
   *   <li>Optional local device confirmation in the enrolled app; not server-attested
   * </ul>
   *
   * @param request the login request with username, optional challenge flag, and optional
   *     nonBlocking flag
   * @return AdminLoginResponseDto with bearer token or challenge/pending info
   * @throws AdminAuthenticationException if credentials invalid or login not permitted (HTTP 401)
   * @throws AdminAuthenticationExpiredException if auth expired (HTTP 400)
   * @throws AdminAuthenticationRejectedException if device rejected (HTTP 400)
   * @throws AdminDeviceSignatureInvalidException if signature invalid (HTTP 400)
   * @throws AdminAuthenticationTimeoutException if timeout (HTTP 408)
   * @since 2025
   */
  private AdminLoginResponseDto authenticatePasswordless(AdminLoginRequestDto request) {
    logger.info("🔐 Passwordless authentication initiated for user: {}", request.username());

    // 1. Validate username and passwordless eligibility
    EzkeyAdmin admin =
        adminRepository
            .findByUsernameWithEnrollment(request.username())
            .orElseThrow(() -> new AdminAuthenticationException(GENERIC_LOGIN_FAILURE_MESSAGE));

    if (!admin.getActive()) {
      rejectPasswordlessLogin(admin.getUsername(), "account deactivated");
    }

    if (admin.getLifecycleStatus() == AdminLifecycleStatus.PENDING_ACTIVATION) {
      rejectPasswordlessLogin(admin.getUsername(), "account pending activation");
    }

    // Check if admin's tenant is active (tenant deactivation blocks login)
    if (admin.getTenant() != null && !admin.getTenant().getActive()) {
      rejectPasswordlessLogin(admin.getUsername(), "tenant deactivated");
    }

    // Passwordless is the ONLY mode - no flag to check
    // Just ensure enrollment exists and is bound
    if (admin.getEnrollment() == null || admin.getEnrollment().getDevicePublicKey() == null) {
      rejectPasswordlessLogin(admin.getUsername(), "no bound device enrollment");
    }

    // 2. Create auth attempt in separate transaction that commits immediately
    // Apply OR logic: challenge required if BD demands it OR client requests it
    // This ensures DB policy (admin.getChallengeRequired) always takes precedence
    Boolean challengeRequested =
        admin.getChallengeRequired() || Boolean.TRUE.equals(request.challengeRequested());

    AuthAttemptCreateRequest attemptReq = new AuthAttemptCreateRequest();
    attemptReq.setEnrollmentId(admin.getEnrollment().getEnrollmentId());
    attemptReq.setChallengeRequested(challengeRequested);

    AuthAttemptCreateResponse attemptResponse = authAttemptTxHelper.createAuthAttempt(attemptReq);

    logger.info(
        "🔐 Passwordless auth attempt created (ID: {}, challenge: {})",
        attemptResponse.getAuthAttemptId(),
        challengeRequested);

    // 3. Branch based on challenge requirement and nonBlocking flag
    // Challenge mode is always non-blocking (immediate return with challenge code)
    // Non-challenge mode can be:
    // - blocking (wait for response) or
    // - non-blocking (return with authAttemptId) if nonBlocking=true
    if (challengeRequested) {
      // CHALLENGE MODE: Return immediately with challenge info (non-blocking)
      Integer challengeCode = attemptResponse.getAuthAttemptChallenge();

      if (challengeCode == null) {
        logger.error(
            "❌ Challenge requested but not generated for authAttemptId: {}",
            attemptResponse.getAuthAttemptId());
        throw new IllegalStateException("Challenge code not generated");
      }

      logger.info(
          "📋 Passwordless with challenge: returning auth attempt info (challenge: {})",
          challengeCode);

      String message =
          "Challenge verification required. Enter code "
              + challengeCode
              + " on your device, then call /passwordless-wait.";

      return AdminLoginResponseDto.pendingPasswordless(
          attemptResponse.getAuthAttemptId(),
          challengeCode,
          admin.getUsername(),
          admin.getAdminType().name(),
          message,
          attemptResponse.getExpiresAt());
    } else if (Boolean.TRUE.equals(request.nonBlocking())) {
      // NO CHALLENGE + NON-BLOCKING MODE: Return immediately with authAttemptId
      // Client will poll /passwordless-wait and display countdown based on expiresAt
      logger.info(
          "📋 Passwordless (no challenge, non-blocking): returning auth attempt info"
              + " for polling");

      String message =
          "Waiting for device response. Poll /passwordless-wait with authAttemptId"
              + " to complete authentication.";

      return AdminLoginResponseDto.pendingPasswordless(
          attemptResponse.getAuthAttemptId(),
          null, // No challenge code in non-challenge mode
          admin.getUsername(),
          admin.getAdminType().name(),
          message,
          attemptResponse.getExpiresAt());
    } else {
      // NO CHALLENGE + BLOCKING MODE: Block and wait (original behavior, backward
      // compatible)
      logger.info("⏳ Passwordless (no challenge): waiting for device response...");

      AuthAttemptWaitRequest waitReq =
          AdminAuthAttemptWaitRequestFactory.forNewAttempt(attemptResponse.getTimeoutSeconds());
      AuthAttemptWaitResponse waitResp =
          authAttemptService.waitForResponse(attemptResponse.getAuthAttemptId(), waitReq);

      String status = waitResp.getStatus();

      if ("ACCEPTED".equals(status)) {
        rotateTokensIfEnabled(admin);
        TokenIssueResult result = generateAndPersistToken(admin);
        updateLastLogin(admin);

        logger.info(
            "✅ Passwordless auth successful for admin: {} after {}s",
            admin.getUsername(),
            waitResp.getWaitDuration());

        return buildSuccessResponse(admin, result.token(), result.plainToken());
      } else if ("REJECTED".equals(status)) {
        logger.warn("❌ Passwordless auth rejected by device for admin: {}", admin.getUsername());
        throw new AdminAuthenticationRejectedException(
            "Device rejected the authentication request");
      } else if ("INVALID".equals(status)) {
        logger.warn(
            "❌ Passwordless auth invalid (signature/challenge failed) for admin: {}",
            admin.getUsername());
        throw new AdminDeviceSignatureInvalidException("Device signature validation failed");
      } else if ("EXPIRED".equals(status)) {
        logger.warn(
            "⏱️ Passwordless auth expired for admin: {} after {}s (superseded or expired)",
            admin.getUsername(),
            waitResp.getWaitDuration());
        throw new AdminAuthenticationExpiredException(
            "Authentication attempt expired - please try again");
      } else if (Boolean.TRUE.equals(waitResp.getTimeoutReached())) {
        logger.warn(
            "⏱️ Passwordless auth timeout for admin: {} after {}s",
            admin.getUsername(),
            waitResp.getWaitDuration());
        throw new AdminAuthenticationTimeoutException("No device response within timeout period");
      } else {
        logger.error("❌ Unexpected passwordless auth status: {}", status);
        throw new AdminAuthenticationException("Authentication failed");
      }
    }
  }

  /**
   * Waits for passwordless authentication completion (two-step flow).
   *
   * <p>Supports challenge mode (validates {@code challengeCode} against the stored challenge to
   * prevent enumeration) and non-blocking mode ({@code challengeCode} null). Uses a wait window
   * aligned with the persisted attempt expiry (see {@link AdminAuthAttemptWaitRequestFactory}).
   *
   * @param authAttemptId the authentication attempt ID
   * @param challengeCode the challenge code from the login response when challenge mode; null for
   *     non-blocking flow
   * @return authentication response with token on success
   * @throws IllegalArgumentException if auth attempt is not found
   * @throws org.ezkey.admin.exception.AdminAuthenticationException if challenge code is missing for
   *     challenge-backed attempts or does not match persisted challenge
   */
  public AdminLoginResponseDto waitForPasswordlessAuth(
      Integer authAttemptId, Integer challengeCode) {
    logger.info(
        "⏳ Waiting for passwordless auth completion (authAttemptId: {}, hasChallenge: {})",
        authAttemptId,
        challengeCode != null);

    // 1. Fetch auth attempt
    AuthAttempt authAttempt =
        authAttemptRepository
            .findById(authAttemptId)
            .orElseThrow(() -> new IllegalArgumentException("Auth attempt not found"));

    // 2. SECURITY: Enforce challenge requirement from persisted auth attempt state.
    // If an attempt has a stored challenge, clients must provide a matching challengeCode.
    Integer persistedChallenge = authAttempt.getAuthAttemptChallenge();
    if (persistedChallenge != null) {
      if (challengeCode == null) {
        logger.warn(
            "❌ Missing challenge code for challenge-backed authAttemptId: {}"
                + " (enumeration attack detected)",
            authAttemptId);
        throw new AdminAuthenticationException("Invalid challenge code - authentication failed");
      }
      if (!challengeCode.equals(persistedChallenge)) {
        logger.warn(
            "❌ Invalid challenge code for authAttemptId: {} (enumeration attack detected)",
            authAttemptId);
        throw new AdminAuthenticationException("Invalid challenge code - authentication failed");
      }
      logger.debug("✅ Challenge code verified for authAttemptId: {}", authAttemptId);
    } else if (challengeCode != null) {
      logger.warn(
          "❌ Unexpected challenge code for non-challenge authAttemptId: {}"
              + " (enumeration attack detected)",
          authAttemptId);
      throw new AdminAuthenticationException("Invalid challenge code - authentication failed");
    } else {
      logger.debug("ℹ️ No challenge code (non-blocking flow) for authAttemptId: {}", authAttemptId);
    }

    // 3. Get admin from enrollment
    EzkeyAdmin admin =
        adminRepository
            .findByEnrollmentId(authAttempt.getEnrollmentId())
            .orElseThrow(
                () -> new IllegalArgumentException("Admin not found for this auth attempt"));

    // 4. Wait for device response (timeout aligned with persisted expiresAt, capped at 300s)
    AuthAttemptWaitRequest waitReq =
        AdminAuthAttemptWaitRequestFactory.forLoadedAttempt(authAttempt);
    AuthAttemptWaitResponse waitResp = authAttemptService.waitForResponse(authAttemptId, waitReq);

    // 5. Process response
    String status = waitResp.getStatus();

    if ("ACCEPTED".equals(status)) {
      rotateTokensIfEnabled(admin);
      TokenIssueResult result = generateAndPersistToken(admin);
      updateLastLogin(admin);

      String flowType = challengeCode != null ? "challenge" : "non-blocking";
      logger.info(
          "✅ Passwordless auth ({} flow) successful for admin: {} after {}s",
          flowType,
          admin.getUsername(),
          waitResp.getWaitDuration());

      return buildSuccessResponse(admin, result.token(), result.plainToken());
    } else if ("REJECTED".equals(status)) {
      logger.warn("❌ Passwordless auth rejected by device for admin: {}", admin.getUsername());
      throw new AdminAuthenticationRejectedException("Device rejected the authentication request");
    } else if ("INVALID".equals(status)) {
      logger.warn("❌ Passwordless auth invalid for admin: {}", admin.getUsername());
      throw new AdminDeviceSignatureInvalidException("Device signature validation failed");
    } else if ("EXPIRED".equals(status)) {
      logger.warn(
          "⏱️ Passwordless auth expired for admin: {} after {}s (superseded or expired)",
          admin.getUsername(),
          waitResp.getWaitDuration());
      throw new AdminAuthenticationExpiredException(
          "Authentication attempt expired - please try again");
    } else if (Boolean.TRUE.equals(waitResp.getTimeoutReached())) {
      logger.warn(
          "⏱️ Passwordless auth timeout for admin: {} after {}s",
          admin.getUsername(),
          waitResp.getWaitDuration());
      throw new AdminAuthenticationTimeoutException("No device response within timeout period");
    } else {
      logger.error("❌ Unexpected passwordless auth status: {}", status);
      throw new AdminAuthenticationException("Authentication failed");
    }
  }

  /**
   * Issue a bearer token after successful MFA validation.
   *
   * <p>Used by recovery flow after device re-enrollment. Rotates tokens if configured, generates a
   * new bearer token, updates last login.
   *
   * @param admin authenticated admin (MFA already satisfied)
   * @return login response with bearer token
   */
  public AdminLoginResponseDto authenticateAfterMfa(EzkeyAdmin admin) {
    rotateTokensIfEnabled(admin);
    TokenIssueResult result = generateAndPersistToken(admin);
    updateLastLogin(admin);
    return buildSuccessResponse(admin, result.token(), result.plainToken());
  }

  /**
   * Rotate tokens on login if enabled in configuration.
   *
   * <p>Deactivates all existing active tokens for this admin to enforce the "one active token per
   * admin" security policy.
   *
   * @param admin the administrator whose tokens should be rotated
   */
  private void rotateTokensIfEnabled(EzkeyAdmin admin) {
    if (!rotationProperties.isRotationOnLoginEnabled()) {
      return;
    }

    int deactivated = tokenRepository.deactivateAllTokensForAdmin(admin.getAdminId());

    if (deactivated > 0) {
      logger.info("Rotated {} old tokens for admin: {}", deactivated, admin.getUsername());
    }
  }

  /** Result of issuing a bearer token: entity (with hash stored) and plain token for the client. */
  private record TokenIssueResult(AdminToken token, String plainToken) {}

  /**
   * Generate a new bearer token and persist only its SHA-256 hash to database.
   *
   * @param admin the administrator for whom to generate the token
   * @return the persisted token entity and the plain token to return to the client
   */
  private TokenIssueResult generateAndPersistToken(EzkeyAdmin admin) {
    String plainToken = generateBearerToken();
    String hash = SensitiveDataHasher.sha256Hex(plainToken);
    if (hash == null) {
      throw new IllegalStateException("Token hash could not be computed");
    }
    int hours = Math.max(1, rotationProperties.getExpirationHours());
    OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(hours);

    AdminToken token =
        new AdminToken(
            hash, admin, admin.getAdminType().name(), expiresAt, AdminTokenPurpose.SESSION);
    token.setTenant(admin.getTenant());
    token.setIntegration(admin.getIntegration());
    token.setCreatedAt(OffsetDateTime.now());
    token.setActive(true);

    tokenRepository.save(token);
    logger.debug("Token created for admin: {}", admin.getUsername());

    return new TokenIssueResult(token, plainToken);
  }

  /**
   * Generate a secure bearer token string.
   *
   * <p>Format: ezkey_[UUID without hyphens]
   *
   * @return the generated bearer token string
   */
  private String generateBearerToken() {
    return "ezkey_" + UUID.randomUUID().toString().replace("-", "");
  }

  /**
   * Update the admin's last login timestamp.
   *
   * @param admin the administrator whose last login should be updated
   */
  private void updateLastLogin(EzkeyAdmin admin) {
    admin.setLastLoginAt(OffsetDateTime.now());
    adminRepository.save(admin);
  }

  /**
   * Build success response DTO.
   *
   * @param admin the authenticated administrator
   * @param token the generated token entity
   * @param plainToken the plain bearer token to return to the client (not stored in DB)
   * @return success response DTO
   */
  private AdminLoginResponseDto buildSuccessResponse(
      EzkeyAdmin admin, AdminToken token, String plainToken) {
    logger.info("Authentication successful for: {}", admin.getUsername());

    Integer tenantId = admin.getTenant() != null ? admin.getTenant().getTenantId() : null;
    return new AdminLoginResponseDto(
        plainToken,
        admin.getAdminType().name(),
        admin.getUsername(),
        token.getExpiresAt(),
        admin.getAdminId(),
        tenantId);
  }

  /**
   * Resolves username, admin id, and tenant id for audit logging for an auth attempt, when the
   * attempt exists and is linked to an administrator enrollment.
   *
   * @param authAttemptId the authentication attempt id
   * @return context if resolvable; empty if the attempt or admin link is missing
   */
  @Transactional(readOnly = true)
  public Optional<AdminAuthAuditContext> findAuditContextForAuthAttempt(Integer authAttemptId) {
    if (authAttemptId == null) {
      return Optional.empty();
    }
    return authAttemptRepository
        .findById(authAttemptId)
        .flatMap(
            attempt ->
                adminRepository
                    .findByEnrollmentId(attempt.getEnrollmentId())
                    .map(
                        admin ->
                            new AdminAuthAuditContext(
                                admin.getUsername(),
                                admin.getAdminId(),
                                admin.getTenant() != null
                                    ? admin.getTenant().getTenantId()
                                    : null)));
  }

  /**
   * Validate bearer token.
   *
   * <p>This method validates a bearer token and returns the associated administrator information if
   * the token is valid and not expired.
   *
   * @param bearerToken the bearer token to validate
   * @return EzkeyAdmin if token is valid, null otherwise
   */
  @Transactional(readOnly = true)
  public EzkeyAdmin validateToken(String bearerToken) {
    try {
      String hash = SensitiveDataHasher.sha256Hex(bearerToken);
      if (hash == null) {
        return null;
      }
      AdminToken token = tokenRepository.findByBearerTokenHashAndActiveTrue(hash).orElse(null);

      if (token == null) {
        return null;
      }

      // Check if token is expired
      if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
        return null;
      }

      // Update last used timestamp
      token.setLastUsedAt(OffsetDateTime.now());
      tokenRepository.save(token);

      return token.getAdmin();

    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      return null;
    }
  }

  /**
   * Returns the admin ID associated with an active bearer token for audit purposes.
   *
   * <p>Read-only lookup; does not update last-used or invalidate the token. Use before calling
   * {@link #logout(String)} when audit logging the logout event with adminId.
   *
   * @param bearerToken the bearer token
   * @return the admin ID, or null if token not found or inactive
   */
  public Integer getAdminIdForToken(String bearerToken) {
    if (bearerToken == null || bearerToken.isBlank()) {
      return null;
    }
    String hash = SensitiveDataHasher.sha256Hex(bearerToken);
    if (hash == null) {
      return null;
    }
    return tokenRepository
        .findByBearerTokenHashAndActiveTrue(hash)
        .map(AdminToken::getAdmin)
        .map(EzkeyAdmin::getAdminId)
        .orElse(null);
  }

  /**
   * Logout administrator by invalidating token.
   *
   * <p>This method invalidates the bearer token, effectively logging out the administrator from the
   * system.
   *
   * @param bearerToken the bearer token to invalidate
   */
  public void logout(String bearerToken) {
    try {
      String hash = SensitiveDataHasher.sha256Hex(bearerToken);
      if (hash == null) {
        return;
      }
      AdminToken token = tokenRepository.findByBearerTokenHashAndActiveTrue(hash).orElse(null);

      if (token != null) {
        token.setActive(false);
        tokenRepository.save(token);
      }
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      // Best-effort logout: never fail the API surface; token TTL remains the backstop.
      logger.warn("Logout token invalidation failed", e);
    }
  }

  /**
   * Rejects passwordless login with a generic client message while logging the specific reason
   * internally (SEC-006 anti-enumeration).
   *
   * @param username the username presented at login
   * @param internalReason operator-safe internal detail for WARN logs and audit follow-up
   */
  private void rejectPasswordlessLogin(String username, String internalReason) {
    logger.warn("Passwordless login rejected for user {}: {}", username, internalReason);
    throw new AdminAuthenticationException(GENERIC_LOGIN_FAILURE_MESSAGE);
  }
}
