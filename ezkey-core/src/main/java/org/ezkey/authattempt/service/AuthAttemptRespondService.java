/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthAttemptRespondService
 * Description: Specialized service for processing authentication responses.
 */

package org.ezkey.authattempt.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.AuthenticationResult;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.AuthAttemptExpiredException;
import org.ezkey.exception.auth.AuthAttemptRequestFailedException;
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.ezkey.exception.auth.IntegrationSigningUnavailableException;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Specialized service for processing authentication responses from mobile devices.
 *
 * <p>This service handles the completion of authentication attempts when mobile devices submit
 * their responses (approve/deny) with cryptographic proof. It validates device signatures, checks
 * challenge responses if required, and updates authentication attempt status accordingly.
 *
 * <p><b>Security Validations:</b>
 *
 * <ul>
 *   <li><b>Signature Verification:</b> Validates device signature using enrollment public key
 *   <li><b>Challenge Validation:</b> Verifies challenge response if required by enrollment
 *   <li><b>Status Validation:</b> Ensures authentication attempt is in READ status
 *   <li><b>Proof Token Validation:</b> Verifies authentication attempt proof token signature
 *   <li><b>Supersession Check:</b> Ensures no newer authentication attempt exists
 *   <li><b>Expiration Check:</b> Validates authentication attempt has not expired
 * </ul>
 *
 * <p><b>Signed outcomes (#844):</b> Only decisions and final statuses leave this service as HTTP
 * 200 bodies, and those bodies are always integration-signed. Not-found, PENDING, and terminal
 * statuses all map to HTTP <b>409</b> {@code auth-attempt-state-conflict} for clients (distinction
 * stays in server logs only — Marc m1). Missing integration private key → HTTP <b>503</b>. Never an
 * unsigned 200.
 *
 * <p><b>Wall-clock expiry (#895, Christophe):</b> {@code auth-attempt-expired} is raised only
 * <em>after</em> enrollment load and device-signature (ownership) verification succeed. An unknown
 * attempt, a still-{@code PENDING} or terminal attempt, or a non-owner device (invalid signature on
 * an already-expired attempt) keep the generic {@code auth-attempt-state-conflict} surface so
 * sequential ids do not reveal expiry before the caller is verified.
 *
 * <p><b>Transaction Management:</b> This service uses Spring's declarative transaction management
 * to ensure data consistency during the authentication response processing.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptRespondRequest
 * @see AuthAttemptRespondResponse
 * @see AuthAttempt
 * @see Enrollment
 * @see SignatureService
 * @see AuthenticationResult
 */
@Service
@Transactional
public class AuthAttemptRespondService {

  private static final Logger logger = LoggerFactory.getLogger(AuthAttemptRespondService.class);

  /**
   * Client-visible message for FAILED results. Must not echo internal exception messages (IDs,
   * signature hints).
   */
  private static final String RESPOND_FAILURE_CLIENT_MESSAGE =
      "The response could not be processed.";

  private final AuthAttemptRepository authAttemptRepository;

  private final EnrollmentRepository enrollmentRepository;

  private final SignatureService signatureService;

  private final AuthAttemptTxHelper authAttemptTxHelper;

  /**
   * Constructs the respond service with required dependencies.
   *
   * @param authAttemptRepository the JPA repository for authentication attempts
   * @param enrollmentRepository the JPA repository for enrollments
   * @param signatureService the signature service for cryptographic operations
   * @param authAttemptTxHelper the transaction helper for independent status commits
   */
  public AuthAttemptRespondService(
      AuthAttemptRepository authAttemptRepository,
      EnrollmentRepository enrollmentRepository,
      SignatureService signatureService,
      AuthAttemptTxHelper authAttemptTxHelper) {
    this.authAttemptRepository = authAttemptRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.authAttemptTxHelper = authAttemptTxHelper;
    this.signatureService = signatureService;
  }

  /**
   * Processes an authentication response from a mobile device.
   *
   * <p>This method handles the completion of an authentication attempt when a mobile device submits
   * its response (approve/deny) with cryptographic proof. It validates the device signature, checks
   * challenge responses if required, and updates the authentication attempt status accordingly.
   *
   * @param request the authentication response request from mobile device
   * @return the authentication response result with status and message
   */
  public AuthAttemptRespondResponse respond(AuthAttemptRespondRequest request) {
    AuthAttempt authAttempt = null;
    try {
      // Step 1: Validate and get the authentication attempt (no wall-clock expiry yet — #895)
      authAttempt = validateAndGetAttempt(request);

      // Step 2: Validate enrollment
      Enrollment enrollment = validateEnrollment(authAttempt);

      // Step 3: Validate device signature (ownership) before typed expiry (#895 Christophe)
      validateDeviceSignature(request, authAttempt, enrollment);

      // Step 4: Validate challenge if required
      validateChallenge(request, authAttempt, enrollment);

      // Step 5: Wall-clock expiry only after ownership proof (#895)
      ensureNotExpired(authAttempt);

      // Step 6: Update attempt status
      updateAttemptStatus(authAttempt, enrollment, request);

      // Step 7: Build and return response (integration-signed result payload)
      return buildResponse(request, authAttempt, enrollment);
    } catch (AuthAttemptRequestFailedException ignored) {
      // Legitimate domain FAILED only (invalid device signature, challenge mismatch, missing
      // device key after the attempt was loaded). State conflict (incl. not-found / PENDING /
      // terminal) and missing integration signing key propagate as problem+json — never unsigned
      // 200 (#844).
      return buildFailedResponse(request, authAttempt);
    }
  }

  /**
   * Validates and retrieves the authentication attempt.
   *
   * <p>This method ensures the authentication attempt exists and is in the correct state for
   * processing a response.
   *
   * @param request the authentication response request
   * @return the validated authentication attempt
   */
  private AuthAttempt validateAndGetAttempt(AuthAttemptRespondRequest request) {
    Optional<AuthAttempt> authAttemptOpt =
        authAttemptRepository.findById(request.getAuthAttemptId());
    if (authAttemptOpt.isEmpty()) {
      // Client sees unified 409; log keeps not-found distinct (#844 Marc m1).
      logger.warn(
          "Respond refused: auth attempt {} not found (client → 409 state-conflict)",
          request.getAuthAttemptId());
      throw new AuthAttemptStateConflictException(
          "Cannot respond to authentication attempt: not found");
    }
    AuthAttempt authAttempt = authAttemptOpt.get();

    AuthAttemptStatus status = authAttempt.getAuthAttemptStatus();
    if (status == AuthAttemptStatus.PENDING) {
      // Protocol violation — client still gets unified 409; log keeps PENDING distinct (#844 m1).
      logger.warn(
          "Respond refused: auth attempt {} still PENDING (respond before claim;"
              + " client → 409 state-conflict)",
          authAttempt.getAuthAttemptId());
      throw new AuthAttemptStateConflictException(
          "Cannot respond to authentication attempt: status is PENDING");
    }
    if (status != AuthAttemptStatus.READ) {
      // Terminal: ACCEPTED, REJECTED, EXPIRED, INVALID → 409 (#844).
      logger.warn(
          "Respond refused: auth attempt {} status {} (client → 409 state-conflict)",
          authAttempt.getAuthAttemptId(),
          status);
      throw new AuthAttemptStateConflictException(
          "Cannot respond to authentication attempt: status is " + status);
    }
    // Check if superseded by a newer authentication attempt for the same enrollment
    Optional<AuthAttempt> newerAttempt =
        authAttemptRepository.findNewerAttemptByEnrollmentId(
            authAttempt.getEnrollmentId(), authAttempt.getCreatedAt());
    if (newerAttempt.isPresent()) {
      logger.info(
          "Auth attempt {} superseded by newer attempt {} for enrollment {}",
          authAttempt.getAuthAttemptId(),
          newerAttempt.get().getAuthAttemptId(),
          authAttempt.getEnrollmentId());
      throw new AuthAttemptStateConflictException(
          "Authentication attempt superseded by newer request");
    }
    // Wall-clock expiry is checked only after device-signature ownership (#895 Christophe).
    return authAttempt;
  }

  /**
   * Returns whether the attempt's wall-clock {@code expiresAt} has been reached (fail-closed: refuse
   * at {@code expiresAt}, not only after).
   *
   * @param authAttempt the attempt to inspect
   * @return {@code true} when {@code expiresAt} is set and not strictly after now
   */
  private static boolean isWallClockExpired(AuthAttempt authAttempt) {
    OffsetDateTime expiresAt = authAttempt.getExpiresAt();
    return expiresAt != null && !expiresAt.isAfter(OffsetDateTime.now());
  }

  /**
   * Raises typed expiry only after enrollment and device-signature checks have succeeded (#895).
   *
   * @param authAttempt the attempt under respond
   * @throws AuthAttemptExpiredException when {@code expiresAt} has been reached
   */
  private void ensureNotExpired(AuthAttempt authAttempt) {
    if (isWallClockExpired(authAttempt)) {
      throw new AuthAttemptExpiredException("Authentication attempt expired");
    }
  }

  /**
   * Validates the enrollment for the authentication attempt.
   *
   * <p>This method ensures the enrollment exists and contains the necessary cryptographic keys for
   * validation.
   *
   * @param authAttempt the authentication attempt
   * @return the validated enrollment
   */
  private Enrollment validateEnrollment(AuthAttempt authAttempt) {
    Optional<Enrollment> enrollmentOpt =
        enrollmentRepository.findById(authAttempt.getEnrollmentId());
    if (enrollmentOpt.isEmpty()) {
      logger.warn(
          "Respond refused: enrollment {} not found for auth attempt {} (client → 409"
              + " state-conflict)",
          authAttempt.getEnrollmentId(),
          authAttempt.getAuthAttemptId());
      throw new AuthAttemptStateConflictException(
          "Cannot respond to authentication attempt: enrollment not found");
    }
    Enrollment enrollment = enrollmentOpt.get();

    // Validate device public key
    String devicePublicKey = enrollment.getDevicePublicKey();
    if (devicePublicKey == null) {
      // Use TxHelper to commit INVALID status before throwing exception
      authAttemptTxHelper.markAsInvalid(authAttempt.getAuthAttemptId());
      throw new AuthAttemptRequestFailedException("Device public key not found");
    }
    return enrollment;
  }

  /**
   * Validates the device signature for the authentication attempt.
   *
   * <p>The device signs the canonical payload {@code proofToken|accepted} (UTF-8) so that
   * authAttemptAccepted cannot be flipped by a MITM. See {@link AuthAttemptSignaturePayload} and
   * docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md.
   *
   * @param request the authentication response request
   * @param authAttempt the authentication attempt
   * @param enrollment the enrollment containing device public key
   */
  private void validateDeviceSignature(
      AuthAttemptRespondRequest request, AuthAttempt authAttempt, Enrollment enrollment) {
    boolean accepted = Boolean.TRUE.equals(request.getAuthAttemptAccepted());
    String payload =
        AuthAttemptSignaturePayload.buildRespondPayload(
            authAttempt.getAuthAttemptProofToken(), accepted);
    boolean isValid =
        signatureService.validateSignature(
            payload,
            request.getAuthAttemptProofTokenSignedByDevice(),
            enrollment.getDevicePublicKey());
    if (!isValid) {
      // Non-owner probe on an already-expired attempt: keep generic 409 — do not reveal typed
      // expiry and do not flip the row to INVALID (#895 Christophe).
      if (isWallClockExpired(authAttempt)) {
        logger.warn(
            "Respond refused: invalid device signature on expired auth attempt {}"
                + " (client → 409 state-conflict)",
            authAttempt.getAuthAttemptId());
        throw new AuthAttemptStateConflictException(
            "Cannot respond to authentication attempt: not authorized");
      }
      authAttemptTxHelper.markAsInvalid(authAttempt.getAuthAttemptId());
      throw new AuthAttemptRequestFailedException("Invalid signature for auth attempt code");
    }
  }

  /**
   * Validates the challenge response if required by the enrollment or auth attempt.
   *
   * <p>This method ensures the challenge response matches the expected value if challenge
   * validation is required. Challenge can be required either at enrollment level (permanent) or at
   * auth attempt level (per-request).
   *
   * <p><b>Security:</b> If an auth attempt has a challenge code (authAttemptChallenge != null), the
   * challenge MUST be validated regardless of enrollment settings. This prevents bypassing
   * challenge verification when it was explicitly requested.
   *
   * <p><b>Reject Handling:</b> Challenge validation is skipped if the user rejected the
   * authentication request ({@code authAttemptAccepted = false}). Since the device signature is
   * already validated, we can trust that a reject is legitimate and there's no security risk in
   * skipping challenge validation for rejects. This ensures rejects are properly marked as REJECTED
   * rather than INVALID.
   *
   * @param request the authentication response request
   * @param authAttempt the authentication attempt
   * @param enrollment the enrollment containing challenge requirements
   */
  private void validateChallenge(
      AuthAttemptRespondRequest request, AuthAttempt authAttempt, Enrollment enrollment) {
    // Skip challenge validation if user rejected - no need to validate challenge for rejects
    // This ensures rejects are properly marked as REJECTED rather than INVALID
    if (Boolean.FALSE.equals(request.getAuthAttemptAccepted())) {
      logger.debug(
          "Skipping challenge validation for rejected auth attempt: {}",
          authAttempt.getAuthAttemptId());
      return;
    }

    // Validate challenge if this auth attempt has a challenge code
    // Challenge can be required at enrollment level OR per-request (authAttemptChallenge != null)
    boolean challengeRequired =
        Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())
            || authAttempt.getAuthAttemptChallenge() != null;
    if (challengeRequired) {
      if (request.getAuthAttemptChallengeResponse() == null
          || !request
              .getAuthAttemptChallengeResponse()
              .equals(authAttempt.getAuthAttemptChallenge())) {
        // Use TxHelper to commit INVALID status before throwing exception
        // This prevents rollback and ensures audit trail is preserved
        authAttemptTxHelper.markAsInvalid(authAttempt.getAuthAttemptId());

        logger.warn(
            "❌ Challenge validation failed for authAttemptId: {}", authAttempt.getAuthAttemptId());
        throw new AuthAttemptRequestFailedException("Challenge value mismatch");
      }
      logger.debug(
          "✅ Challenge validated successfully for authAttemptId: {}",
          authAttempt.getAuthAttemptId());
    }
  }

  /**
   * Updates the authentication attempt status based on the user's decision.
   *
   * <p>Uses a conditional {@code UPDATE … WHERE status = READ AND expiresAt > now} so a concurrent
   * cancel that already committed {@code EXPIRED}, or a wall-clock expiry, cannot be overwritten by
   * approve/deny (#833 m1, #895).
   *
   * @param authAttempt the authentication attempt to update
   * @param enrollment the enrollment (last-used timestamp on accept)
   * @param request the authentication response request
   * @throws AuthAttemptExpiredException if the attempt expired between the pre-check and UPDATE
   * @throws AuthAttemptStateConflictException if the attempt is no longer READ (cancel won)
   */
  private void updateAttemptStatus(
      AuthAttempt authAttempt, Enrollment enrollment, AuthAttemptRespondRequest request) {
    AuthAttemptStatus newStatus =
        Boolean.TRUE.equals(request.getAuthAttemptAccepted())
            ? AuthAttemptStatus.ACCEPTED
            : AuthAttemptStatus.REJECTED;
    OffsetDateTime now = OffsetDateTime.now();
    int updated =
        authAttemptRepository.updateStatusIfCurrent(
            authAttempt.getAuthAttemptId(), AuthAttemptStatus.READ, newStatus, now);
    if (updated != 1) {
      AuthAttempt latest =
          authAttemptRepository
              .findById(authAttempt.getAuthAttemptId())
              .orElseThrow(
                  () ->
                      new AuthAttemptStateConflictException(
                          "Cannot respond to authentication attempt: not found after update"));
      OffsetDateTime recheckNow = OffsetDateTime.now();
      if (latest.getExpiresAt() != null && !latest.getExpiresAt().isAfter(recheckNow)) {
        throw new AuthAttemptExpiredException(
            "Authentication attempt expired (conditional update refused)");
      }
      throw new AuthAttemptStateConflictException(
          "Cannot respond to authentication attempt: status is no longer READ"
              + " (cancelled or already finalized)");
    }
    authAttempt.setAuthAttemptStatus(newStatus);
    if (newStatus == AuthAttemptStatus.ACCEPTED) {
      enrollment.setLastUsedAt(OffsetDateTime.now());
      enrollmentRepository.save(enrollment);
    }
  }

  /**
   * Builds the authentication response with the appropriate result and integration signature over
   * the canonical Respond result payload.
   *
   * @param request the authentication response request
   * @param authAttempt the updated attempt
   * @param enrollment enrollment (integration private key for signing)
   * @return the authentication response with result, message, and signature
   */
  private AuthAttemptRespondResponse buildResponse(
      AuthAttemptRespondRequest request, AuthAttempt authAttempt, Enrollment enrollment) {
    AuthAttemptRespondResponse response = new AuthAttemptRespondResponse();

    if (Boolean.TRUE.equals(request.getAuthAttemptAccepted())) {
      response.setResult(AuthenticationResult.APPROVED);
    } else {
      response.setResult(AuthenticationResult.DENIED);
    }
    response.setMessage("Auth attempt completed");
    response.setAuthAttemptId(authAttempt.getAuthAttemptId());
    response.setCreatedAt(authAttempt.getCreatedAt());
    attachRespondResultSignature(response, enrollment, authAttempt.getAuthAttemptProofToken());
    return response;
  }

  /**
   * Builds a FAILED result for validation errors and signs with the integration key. A missing or
   * blank per-enrollment key throws {@link IntegrationSigningUnavailableException} → HTTP 503 (no
   * unsigned 200); there is no JVM startup check for integration keys (#844).
   *
   * <p>Note: callers that already committed {@code INVALID} via {@link
   * AuthAttemptTxHelper#markAsInvalid} (REQUIRES_NEW) keep that status if signing then fails — a
   * client retry sees terminal → 409, not a second 503.
   */
  private AuthAttemptRespondResponse buildFailedResponse(
      AuthAttemptRespondRequest request, AuthAttempt authAttempt) {
    AuthAttemptRespondResponse response =
        new AuthAttemptRespondResponse(AuthenticationResult.FAILED, RESPOND_FAILURE_CLIENT_MESSAGE);
    if (authAttempt == null) {
      // Should not happen: not-found / state gates no longer throw RequestFailed into this catch.
      throw new IllegalStateException(
          "buildFailedResponse requires a loaded auth attempt; refuse unsigned FAILED (#844)");
    }
    response.setAuthAttemptId(authAttempt.getAuthAttemptId());
    response.setCreatedAt(authAttempt.getCreatedAt());
    String proofToken =
        authAttempt.getAuthAttemptProofToken() != null
            ? authAttempt.getAuthAttemptProofToken()
            : "";
    Optional<Enrollment> enrollmentOpt =
        enrollmentRepository.findById(authAttempt.getEnrollmentId());
    if (enrollmentOpt.isEmpty()) {
      logger.warn(
          "Respond FAILED path: enrollment {} missing for auth attempt {} (client → 409"
              + " state-conflict)",
          authAttempt.getEnrollmentId(),
          authAttempt.getAuthAttemptId());
      throw new AuthAttemptStateConflictException(
          "Cannot respond to authentication attempt: enrollment not found");
    }
    attachRespondResultSignature(response, enrollmentOpt.get(), proofToken);
    return response;
  }

  /**
   * Signs {@code proofToken|authAttemptId|result|message} with the enrollment's integration private
   * key. When the key is missing or blank, throws {@link IntegrationSigningUnavailableException}
   * (HTTP 503) instead of returning an unsigned body (#844). Integration keys are per enrollment —
   * there is no application-startup gate; the outer {@code @Transactional} respond method rolls
   * back (so a status flip to ACCEPTED/REJECTED in the same transaction is undone).
   *
   * @param response respond body being built
   * @param enrollment enrollment holding the integration private key
   * @param proofToken attempt proof token (may be empty string for FAILED edge paths)
   * @throws IntegrationSigningUnavailableException when the key cannot sign
   */
  private void attachRespondResultSignature(
      AuthAttemptRespondResponse response, Enrollment enrollment, String proofToken) {
    if (enrollment == null) {
      throw new IntegrationSigningUnavailableException(
          "Enrollment required to sign respond result");
    }
    String privateKey = enrollment.getIntegrationPrivateKey();
    if (privateKey == null || privateKey.isBlank()) {
      throw new IntegrationSigningUnavailableException(
          "Integration private key missing or blank on enrollment");
    }
    String payload =
        AuthAttemptSignaturePayload.buildRespondResultPayload(
            proofToken, response.getAuthAttemptId(), response.getResult(), response.getMessage());
    String signature = signatureService.signIntegrationPayload(payload, privateKey);
    response.setAuthAttemptProofTokenResultSignedByIntegration(signature);
  }
}
