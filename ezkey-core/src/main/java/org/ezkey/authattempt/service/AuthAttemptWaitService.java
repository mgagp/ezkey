/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthAttemptWaitService
 * Description: Specialized service for handling authentication wait operations.
 */

package org.ezkey.authattempt.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Specialized service for handling authentication wait operations and polling.
 *
 * <p>This service manages the synchronous polling mechanism for web applications awaiting
 * authentication responses from mobile devices. It implements efficient polling with configurable
 * timeouts and prevents long-running transactions.
 *
 * <p><b>Key Features:</b>
 *
 * <ul>
 *   <li><b>Configurable Timeouts:</b> Customizable timeout and polling intervals
 *   <li><b>Efficient Polling:</b> Short-lived transactions to prevent resource exhaustion
 *   <li><b>Status Calculation:</b> Intelligent status determination based on attempt state
 *   <li><b>Supersession Detection:</b> Handles newer authentication attempts appropriately
 *   <li><b>Expiration Handling:</b> Proper handling of expired authentication attempts
 * </ul>
 *
 * <p><b>Transaction Management:</b> This service uses NOT_SUPPORTED propagation to avoid holding
 * long-running transactions while maintaining data consistency through short-lived operations.
 *
 * <p><b>Performance Considerations:</b>
 *
 * <ul>
 *   <li><b>Short Transactions:</b> Each poll uses a brief transaction to prevent blocking
 *   <li><b>Efficient Queries:</b> Optimized database queries for status checking
 *   <li><b>Resource Management:</b> Proper cleanup and timeout handling
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptWaitRequest
 * @see AuthAttemptWaitResponse
 * @see AuthAttempt
 * @see AuthAttemptStatus
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AuthAttemptWaitService {

  private static final Logger logger = LoggerFactory.getLogger(AuthAttemptWaitService.class);

  private final AuthAttemptRepository authAttemptRepository;

  /**
   * Constructs the wait service with required dependencies.
   *
   * @param authAttemptRepository the JPA repository for authentication attempts
   */
  public AuthAttemptWaitService(AuthAttemptRepository authAttemptRepository) {
    this.authAttemptRepository = authAttemptRepository;
  }

  /**
   * Waits for authentication response completion with configurable timeout and polling.
   *
   * <p>This method implements efficient polling without holding long-running transactions. It
   * checks the authentication attempt status at regular intervals until completion, timeout, or
   * supersession occurs.
   *
   * @param authAttemptId the ID of the authentication attempt to wait for
   * @param request the wait request containing timeout and polling configuration
   * @return the wait response with final status and metadata
   * @throws ResourceNotFoundException if the authentication attempt is not found
   * @throws IllegalArgumentException if the wait request parameters are invalid
   */
  public AuthAttemptWaitResponse waitForResponse(
      Integer authAttemptId, AuthAttemptWaitRequest request) {
    logger.debug(
        "Starting wait operation for auth attempt {} with timeout={}s, polling={}s",
        authAttemptId,
        request.getTimeout(),
        request.getPolling());

    // Validate request parameters
    validateWaitRequest(request);

    long startTime = System.currentTimeMillis();
    long deadline = startTime + (request.getTimeout() * 1000L);
    int pollCount = 0;

    while (true) {
      // Short-lived fetch (no long transaction held between iterations)
      AuthAttempt authAttempt =
          authAttemptRepository
              .findById(authAttemptId)
              .orElseThrow(
                  () -> new ResourceNotFoundException("Authorization attempt", authAttemptId));

      // Check for supersession
      Optional<AuthAttempt> newerAttempt =
          authAttemptRepository.findNewerAttemptByEnrollmentId(
              authAttempt.getEnrollmentId(), authAttempt.getCreatedAt());
      if (newerAttempt.isPresent()) {
        int waitDuration = (int) ((System.currentTimeMillis() - startTime) / 1000);
        logger.info(
            "Auth attempt {} superseded by newer attempt {} for enrollment {} during wait operation",
            authAttemptId,
            newerAttempt.get().getAuthAttemptId(),
            authAttempt.getEnrollmentId());
        return buildWaitResponse(authAttempt, "EXPIRED", false, waitDuration);
      }

      // Check if completed
      if (isAttemptCompleted(authAttempt)) {
        int waitDuration = (int) ((System.currentTimeMillis() - startTime) / 1000);
        logger.info(
            "Auth attempt {} completed with status {} after {}s ({} polls)",
            authAttemptId,
            authAttempt.getAuthAttemptStatus(),
            waitDuration,
            pollCount);
        return buildWaitResponse(authAttempt, false, waitDuration);
      }

      // Check if expired
      if (isAttemptExpired(authAttempt)) {
        int waitDuration = (int) ((System.currentTimeMillis() - startTime) / 1000);
        logger.info(
            "Auth attempt {} expired during wait at {}", authAttemptId, authAttempt.getExpiresAt());
        return buildWaitResponse(authAttempt, "EXPIRED", false, waitDuration);
      }

      // Check timeout
      long nowMs = System.currentTimeMillis();
      if (nowMs >= deadline) {
        int waitDuration = (int) ((nowMs - startTime) / 1000);
        logger.info(
            "Auth attempt {} timed out after {}s ({} polls)",
            authAttemptId,
            waitDuration,
            pollCount);
        return buildWaitResponse(authAttempt, true, waitDuration);
      }

      // Sleep before next poll
      try {
        Thread.sleep(request.getPolling() * 1000L);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        logger.warn("Wait operation for auth attempt {} was interrupted", authAttemptId);
        throw new RuntimeException("Wait operation interrupted", ie);
      }

      pollCount++;
      if (pollCount % 10 == 0) {
        logger.debug("Auth attempt {} still pending after {} polls", authAttemptId, pollCount);
      }
    }
  }

  /**
   * Validates the wait request parameters.
   *
   * <p>Ensures that timeout and polling parameters are within acceptable ranges and that polling is
   * not greater than timeout.
   *
   * @param request the wait request to validate
   * @throws IllegalArgumentException if parameters are invalid
   */
  private void validateWaitRequest(AuthAttemptWaitRequest request) {
    if (request.getTimeout() == null || request.getTimeout() <= 0 || request.getTimeout() > 300) {
      throw new IllegalArgumentException("Timeout must be between 1 and 300 seconds");
    }
    if (request.getPolling() == null || request.getPolling() <= 0 || request.getPolling() > 60) {
      throw new IllegalArgumentException("Polling must be between 1 and 60 seconds");
    }
    if (request.getPolling() > request.getTimeout()) {
      throw new IllegalArgumentException("Polling interval cannot be greater than timeout");
    }
  }

  /**
   * Checks if the authentication attempt is in a completed state.
   *
   * @param authAttempt the authentication attempt to check
   * @return true if the attempt is completed (ACCEPTED, REJECTED, or INVALID)
   */
  private boolean isAttemptCompleted(AuthAttempt authAttempt) {
    return authAttempt.getAuthAttemptStatus() == AuthAttemptStatus.ACCEPTED
        || authAttempt.getAuthAttemptStatus() == AuthAttemptStatus.REJECTED
        || authAttempt.getAuthAttemptStatus() == AuthAttemptStatus.INVALID;
  }

  /**
   * Checks if the authentication attempt has expired.
   *
   * @param authAttempt the authentication attempt to check
   * @return true if the attempt has expired
   */
  private boolean isAttemptExpired(AuthAttempt authAttempt) {
    OffsetDateTime now = OffsetDateTime.now();
    return authAttempt.getExpiresAt() != null && now.isAfter(authAttempt.getExpiresAt());
  }

  /**
   * Builds the wait response with calculated status and metadata.
   *
   * <p>Creates a complete AuthAttemptWaitResponse with the authentication attempt data, calculated
   * status, and wait operation metadata.
   *
   * @param authAttempt the authentication attempt data
   * @param timeoutReached whether the wait operation timed out
   * @param waitDuration the actual duration waited in seconds
   * @return the complete AuthAttemptWaitResponse
   */
  private AuthAttemptWaitResponse buildWaitResponse(
      AuthAttempt authAttempt, boolean timeoutReached, int waitDuration) {
    String status = calculateStatus(authAttempt);
    boolean completed = isAttemptCompleted(authAttempt);

    return new AuthAttemptWaitResponse(
        authAttempt, status, completed, timeoutReached, waitDuration, OffsetDateTime.now());
  }

  /**
   * Builds the wait response with a specific status for special cases.
   *
   * <p>Creates a complete AuthAttemptWaitResponse with a specific status for cases where the normal
   * status calculation doesn't apply (e.g., superseded attempts).
   *
   * @param authAttempt the authentication attempt data
   * @param status the specific status to use
   * @param timeoutReached whether the wait operation timed out
   * @param waitDuration the actual duration waited in seconds
   * @return the complete AuthAttemptWaitResponse
   */
  private AuthAttemptWaitResponse buildWaitResponse(
      AuthAttempt authAttempt, String status, boolean timeoutReached, int waitDuration) {
    boolean completed = isAttemptCompleted(authAttempt);

    return new AuthAttemptWaitResponse(
        authAttempt, status, completed, timeoutReached, waitDuration, OffsetDateTime.now());
  }

  /**
   * Calculates the authentication status based on the rules defined in ENDPOINT.md.
   *
   * <p>This method implements the status calculation logic as specified in the project
   * documentation. The status is determined by checking the authentication attempt flags in a
   * specific order of priority.
   *
   * @param authAttempt the authentication attempt entity
   * @return the calculated status string (PENDING, READ, INVALID, REJECTED, ACCEPTED, EXPIRED)
   */
  private String calculateStatus(AuthAttempt authAttempt) {
    // Check if expired first (highest priority)
    if (isAttemptExpired(authAttempt)) {
      return AuthAttemptStatus.EXPIRED.name();
    }
    // Return the current status directly
    return authAttempt.getAuthAttemptStatus().name();
  }
}
