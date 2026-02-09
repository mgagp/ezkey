/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminAuthService
 * Description: Service for passwordless administrator authentication.
 */

package org.ezkey.admin.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.ezkey.admin.config.AdminTokenRotationProperties;
import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for passwordless administrator authentication.
 *
 * <p>
 * This service handles passwordless authentication of administrators using
 * Ezkey's cryptographic
 * authentication system ("eating our own dogfood"). It eliminates passwords
 * entirely, providing
 * superior security through device-bound credentials.
 *
 * <p>
 * <b>Authentication Modes:</b>
 *
 * <ul>
 * <li><b>Single-call (no challenge):</b> Blocking wait for device approval
 * <li><b>Two-call (with challenge):</b> Return challenge code, wait separately
 * </ul>
 *
 * <p>
 * <b>Token Rotation:</b> When token rotation on login is enabled, this service
 * deactivates all
 * existing active tokens for an administrator when they log in, ensuring only
 * one active token
 * exists at any time.
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p>
 * <b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
@Transactional
public class AdminAuthService {

  private static final Logger logger = LoggerFactory.getLogger(AdminAuthService.class);

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
   * <p>
   * This is the only authentication method - passwords are not supported.
   * Delegates to {@link
   * #authenticatePasswordless(AdminLoginRequestDto)} internally.
   *
   * @param request the login request containing username and optional challenge
   *                flag
   * @return AdminLoginResponseDto with authentication result
   */
  public AdminLoginResponseDto authenticate(AdminLoginRequestDto request) {
    logger.info("Passwordless authentication attempt for user: {}", request.username());

    try {
      return authenticatePasswordless(request);
    } catch (AuthenticationException e) {
      logger.warn("Authentication failed for {}: {}", request.username(), e.getMessage());
      return buildErrorResponse(e.getMessage());
    }
  }

  /**
   * Authenticate administrator using passwordless Ezkey cryptographic
   * authentication.
   *
   * <p>
   * This method implements "eat your own dogfood" by using Ezkey's MFA system for
   * admin
   * authentication without passwords. The flow:
   *
   * <ol>
   * <li>Validate username and passwordless eligibility
   * <li>Create auth attempt internally
   * <li>Branch based on challenge requirement and nonBlocking flag:
   * <ul>
   * <li><b>Challenge required (any mode):</b> Return immediately with challenge
   * code
   * (non-blocking)
   * <li><b>No challenge + nonBlocking=true:</b> Return immediately with
   * authAttemptId for
   * client polling (enables countdown timer in TUI)
   * <li><b>No challenge + nonBlocking=false/null:</b> Block and wait for device
   * response
   * (backward compatible)
   * </ul>
   * <li>Issue bearer token on approval
   * </ol>
   *
   * <p>
   * <b>Security:</b> This provides superior security compared to passwords:
   *
   * <ul>
   * <li>No password to steal or guess
   * <li>Phishing resistant (cryptographic signatures)
   * <li>Device-bound credentials
   * <li>Biometric verification on device
   * </ul>
   *
   * @param request the login request with username, optional challenge flag, and
   *                optional
   *                nonBlocking flag
   * @return AdminLoginResponseDto with bearer token or challenge/pending info
   * @throws AuthenticationException if passwordless auth fails
   * @since 2025
   */
  private AdminLoginResponseDto authenticatePasswordless(AdminLoginRequestDto request) {
    logger.info("🔐 Passwordless authentication initiated for user: {}", request.username());

    // 1. Validate username and passwordless eligibility
    EzkeyAdmin admin = adminRepository
        .findByUsernameWithEnrollment(request.username())
        .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

    if (!admin.getActive()) {
      throw new AuthenticationException("Account is inactive");
    }

    // Check if admin's tenant is active (tenant deactivation blocks login)
    if (admin.getTenant() != null && !admin.getTenant().getActive()) {
      throw new AuthenticationException(
          "Your tenant has been deactivated. Contact your Ezkey administrator.");
    }

    // Passwordless is the ONLY mode - no flag to check
    // Just ensure enrollment exists and is bound
    if (admin.getMfaEnrollment() == null || admin.getMfaEnrollment().getDevicePublicKey() == null) {
      logger.warn("No bound enrollment for passwordless auth: {}", admin.getUsername());
      throw new AuthenticationException("No device enrolled for passwordless authentication");
    }

    // 2. Create auth attempt in separate transaction that commits immediately
    // Apply OR logic: challenge required if BD demands it OR client requests it
    // This ensures DB policy (admin.getChallengeRequired) always takes precedence
    Boolean challengeRequested = admin.getChallengeRequired() || Boolean.TRUE.equals(request.challengeRequested());

    AuthAttemptCreateRequest attemptReq = new AuthAttemptCreateRequest();
    attemptReq.setEnrollmentId(admin.getMfaEnrollment().getEnrollmentId());
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

      String message = "Challenge verification required. Enter code "
          + challengeCode
          + " on your device, then call /passwordless-wait.";

      return AdminLoginResponseDto.pendingPasswordless(
          attemptResponse.getAuthAttemptId(),
          challengeCode,
          admin.getUsername(),
          admin.getAdminType().name(),
          message,
          OffsetDateTime.now().plusMinutes(5));
    } else if (Boolean.TRUE.equals(request.nonBlocking())) {
      // NO CHALLENGE + NON-BLOCKING MODE: Return immediately with authAttemptId
      // Client will poll /passwordless-wait and display countdown based on expiresAt
      logger.info(
          "📋 Passwordless (no challenge, non-blocking): returning auth attempt info"
              + " for polling");

      String message = "Waiting for device response. Poll /passwordless-wait with authAttemptId"
          + " to complete authentication.";

      return AdminLoginResponseDto.pendingPasswordless(
          attemptResponse.getAuthAttemptId(),
          null, // No challenge code in non-challenge mode
          admin.getUsername(),
          admin.getAdminType().name(),
          message,
          OffsetDateTime.now().plusMinutes(5));
    } else {
      // NO CHALLENGE + BLOCKING MODE: Block and wait (original behavior, backward
      // compatible)
      logger.info("⏳ Passwordless (no challenge): waiting for device response...");

      // 5 min timeout, 2s polling interval
      AuthAttemptWaitRequest waitReq = new AuthAttemptWaitRequest(300, 2);
      AuthAttemptWaitResponse waitResp = authAttemptService.waitForResponse(attemptResponse.getAuthAttemptId(),
          waitReq);

      String status = waitResp.getStatus();

      if ("ACCEPTED".equals(status)) {
        rotateTokensIfEnabled(admin);
        AdminToken token = generateAndPersistToken(admin);
        updateLastLogin(admin);

        logger.info(
            "✅ Passwordless auth successful for admin: {} after {}s",
            admin.getUsername(),
            waitResp.getWaitDuration());

        return buildSuccessResponse(admin, token);
      } else if ("REJECTED".equals(status)) {
        logger.warn("❌ Passwordless auth rejected by device for admin: {}", admin.getUsername());
        throw new AuthenticationException("Authentication rejected by device");
      } else if ("INVALID".equals(status)) {
        logger.warn(
            "❌ Passwordless auth invalid (signature/challenge failed) for admin: {}",
            admin.getUsername());
        throw new AuthenticationException("Authentication failed - invalid signature or challenge");
      } else if ("EXPIRED".equals(status)) {
        logger.warn(
            "⏱️ Passwordless auth expired for admin: {} after {}s (superseded or expired)",
            admin.getUsername(),
            waitResp.getWaitDuration());
        throw new AuthenticationException("Authentication expired - please try again");
      } else if (Boolean.TRUE.equals(waitResp.getTimeoutReached())) {
        logger.warn(
            "⏱️ Passwordless auth timeout for admin: {} after {}s",
            admin.getUsername(),
            waitResp.getWaitDuration());
        throw new AuthenticationException("Authentication timeout - no device response");
      } else {
        logger.error("❌ Unexpected passwordless auth status: {}", status);
        throw new AuthenticationException("Authentication failed");
      }
    }
  }

  /**
   * Wait for passwordless authentication completion with challenge verification.
   *
   * <p>
   * This method is used in the two-step passwordless flow when challenge
   * verification is
   * required. It validates the challengeCode to prevent enumeration attacks, then
   * waits for device
   * approval.
   *
   * <p>
   * <b>Security:</b> The challengeCode must match the auth attempt's challenge to
   * prevent
   * attackers from enumerating authAttemptId values and hijacking authentication
   * attempts.
   *
   * @param authAttemptId the auth attempt ID from the login response
   * @param challengeCode the challenge code from the login response (proof of
   *                      legitimacy)
   * @return AdminLoginResponseDto with bearer token if accepted
   * @throws IllegalArgumentException if auth attempt is invalid
   * @throws AuthenticationException  if authentication fails or challenge is
   *                                  incorrect
   */
  /**
   * Waits for passwordless authentication completion.
   *
   * <p>
   * Supports two flows:
   *
   * <ul>
   * <li><b>Challenge Flow:</b> When challengeCode is provided (non-null),
   * verifies the code
   * against stored challenge to prevent enumeration attacks, then waits for
   * device response.
   * <li><b>Non-Blocking Flow:</b> When challengeCode is null, skips challenge
   * verification
   * (already skipped in login response) and waits for device response using
   * polling.
   * </ul>
   *
   * <p>
   * This method handles both asynchronous authentication modes transparently
   * using the same
   * endpoint.
   *
   * @param authAttemptId the authentication attempt ID
   * @param challengeCode the challenge code (optional - null for non-blocking
   *                      flow, required for
   *                      challenge flow)
   * @return authentication response with token on success
   */
  public AdminLoginResponseDto waitForPasswordlessAuth(
      Integer authAttemptId, Integer challengeCode) {
    logger.info(
        "⏳ Waiting for passwordless auth completion (authAttemptId: {}, hasChallenge: {})",
        authAttemptId,
        challengeCode != null);

    // 1. Fetch auth attempt
    AuthAttempt authAttempt = authAttemptRepository
        .findById(authAttemptId)
        .orElseThrow(() -> new IllegalArgumentException("Auth attempt not found"));

    // 2. SECURITY: Verify challenge code only if provided (challenge flow)
    // If challengeCode is null, we're in non-blocking flow (challenge already
    // verified in login)
    if (challengeCode != null) {
      if (!challengeCode.equals(authAttempt.getAuthAttemptChallenge())) {
        logger.warn(
            "❌ Invalid challenge code for authAttemptId: {} (enumeration attack detected)",
            authAttemptId);
        throw new AuthenticationException("Invalid challenge code - authentication failed");
      }
      logger.debug("✅ Challenge code verified for authAttemptId: {}", authAttemptId);
    } else {
      logger.debug("ℹ️ No challenge code (non-blocking flow) for authAttemptId: {}", authAttemptId);
    }

    // 3. Get admin from enrollment
    EzkeyAdmin admin = adminRepository
        .findByMfaEnrollmentEnrollmentId(authAttempt.getEnrollmentId())
        .orElseThrow(
            () -> new IllegalArgumentException("Admin not found for this auth attempt"));

    // 4. Wait for device response
    AuthAttemptWaitRequest waitReq = new AuthAttemptWaitRequest(300, 2); // 5 min, 2s polling
    AuthAttemptWaitResponse waitResp = authAttemptService.waitForResponse(authAttemptId, waitReq);

    // 5. Process response
    String status = waitResp.getStatus();

    if ("ACCEPTED".equals(status)) {
      rotateTokensIfEnabled(admin);
      AdminToken token = generateAndPersistToken(admin);
      updateLastLogin(admin);

      String flowType = challengeCode != null ? "challenge" : "non-blocking";
      logger.info(
          "✅ Passwordless auth ({} flow) successful for admin: {} after {}s",
          flowType,
          admin.getUsername(),
          waitResp.getWaitDuration());

      return buildSuccessResponse(admin, token);
    } else if ("REJECTED".equals(status)) {
      logger.warn("❌ Passwordless auth rejected by device for admin: {}", admin.getUsername());
      throw new AuthenticationException("Authentication rejected by device");
    } else if ("INVALID".equals(status)) {
      logger.warn("❌ Passwordless auth invalid for admin: {}", admin.getUsername());
      throw new AuthenticationException(
          "Authentication failed - invalid signature or challenge code");
    } else if ("EXPIRED".equals(status)) {
      logger.warn(
          "⏱️ Passwordless auth expired for admin: {} after {}s (superseded or expired)",
          admin.getUsername(),
          waitResp.getWaitDuration());
      throw new AuthenticationException("Authentication expired - please try again");
    } else if (Boolean.TRUE.equals(waitResp.getTimeoutReached())) {
      logger.warn(
          "⏱️ Passwordless auth timeout for admin: {} after {}s",
          admin.getUsername(),
          waitResp.getWaitDuration());
      throw new AuthenticationException("Authentication timeout - no device response");
    } else {
      logger.error("❌ Unexpected passwordless auth status: {}", status);
      throw new AuthenticationException("Authentication failed");
    }
  }

  /**
   * Issue a bearer token after successful MFA validation.
   *
   * <p>
   * Used by recovery flow after device re-enrollment. Rotates tokens if
   * configured, generates a
   * new bearer token, updates last login.
   *
   * @param admin authenticated admin (MFA already satisfied)
   * @return login response with bearer token
   */
  public AdminLoginResponseDto authenticateAfterMfa(EzkeyAdmin admin) {
    rotateTokensIfEnabled(admin);
    AdminToken token = generateAndPersistToken(admin);
    updateLastLogin(admin);
    return buildSuccessResponse(admin, token);
  }

  /**
   * Rotate tokens on login if enabled in configuration.
   *
   * <p>
   * Deactivates all existing active tokens for this admin to enforce the "one
   * active token per
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

  /**
   * Generate a new bearer token and persist it to database.
   *
   * @param admin the administrator for whom to generate the token
   * @return the persisted token entity
   */
  private AdminToken generateAndPersistToken(EzkeyAdmin admin) {
    String bearerToken = generateBearerToken();
    OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(24);

    AdminToken token = new AdminToken();
    token.setBearerToken(bearerToken);
    token.setAdmin(admin);
    token.setAdminType(admin.getAdminType().name());
    token.setTenant(admin.getTenant());
    token.setIntegration(admin.getIntegration());
    token.setExpiresAt(expiresAt);
    token.setCreatedAt(OffsetDateTime.now());
    token.setActive(true);

    tokenRepository.save(token);
    logger.debug("Token created for admin: {}", admin.getUsername());

    return token;
  }

  /**
   * Generate a secure bearer token string.
   *
   * <p>
   * Format: ezkey_[UUID without hyphens]
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
   * @param token the generated token
   * @return success response DTO
   */
  private AdminLoginResponseDto buildSuccessResponse(EzkeyAdmin admin, AdminToken token) {
    logger.info("Authentication successful for: {}", admin.getUsername());

    return new AdminLoginResponseDto(
        token.getBearerToken(),
        admin.getAdminType().name(),
        admin.getUsername(),
        token.getExpiresAt());
  }

  /**
   * Build error response DTO.
   *
   * @param message the error message
   * @return error response DTO
   */
  private AdminLoginResponseDto buildErrorResponse(String message) {
    return new AdminLoginResponseDto("Authentication failed: " + message);
  }

  /**
   * Validate bearer token.
   *
   * <p>
   * This method validates a bearer token and returns the associated administrator
   * information if
   * the token is valid and not expired.
   *
   * @param bearerToken the bearer token to validate
   * @return EzkeyAdmin if token is valid, null otherwise
   */
  @Transactional(readOnly = true)
  public EzkeyAdmin validateToken(String bearerToken) {
    try {
      AdminToken token = tokenRepository.findByBearerTokenAndActiveTrue(bearerToken).orElse(null);

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

    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Logout administrator by invalidating token.
   *
   * <p>
   * This method invalidates the bearer token, effectively logging out the
   * administrator from the
   * system.
   *
   * @param bearerToken the bearer token to invalidate
   */
  public void logout(String bearerToken) {
    try {
      AdminToken token = tokenRepository.findByBearerTokenAndActiveTrue(bearerToken).orElse(null);

      if (token != null) {
        token.setActive(false);
        tokenRepository.save(token);
      }
    } catch (Exception e) {
      // Log error but don't throw exception
    }
  }
}
