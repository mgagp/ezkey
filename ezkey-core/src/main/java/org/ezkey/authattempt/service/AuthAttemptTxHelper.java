/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthAttemptTxHelper
 * Description: Transaction helper for auth attempt status updates requiring independent commits.
 */

package org.ezkey.authattempt.service;

import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction helper service for auth attempt status management.
 *
 * <p>This service provides methods that require independent transaction management, particularly
 * for operations that must commit regardless of the calling method's transaction outcome (e.g.,
 * marking attempt as INVALID even when validation fails).
 *
 * <p><b>Pattern:</b> Similar to {@link org.ezkey.enrollment.service.EnrollmentTxHelper}, this
 * service uses REQUIRES_NEW propagation to ensure status updates persist even when the caller's
 * transaction rolls back.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class AuthAttemptTxHelper {

  private static final Logger logger = LoggerFactory.getLogger(AuthAttemptTxHelper.class);

  private final AuthAttemptRepository authAttemptRepository;

  public AuthAttemptTxHelper(AuthAttemptRepository authAttemptRepository) {
    this.authAttemptRepository = authAttemptRepository;
  }

  /**
   * Mark auth attempt as INVALID in an independent transaction.
   *
   * <p>This method commits immediately regardless of the calling method's transaction state. This
   * ensures auth attempts are marked as INVALID even when validation throws an exception (e.g.,
   * challenge mismatch, signature validation failure).
   *
   * <p><b>Security:</b> Critical for audit trail - we need to record INVALID attempts even when the
   * response processing fails. This helps detect brute force attacks and provides accurate security
   * metrics.
   *
   * @param authAttemptId the auth attempt ID to mark as invalid
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markAsInvalid(Integer authAttemptId) {
    AuthAttempt authAttempt = authAttemptRepository.findById(authAttemptId).orElse(null);
    if (authAttempt != null) {
      authAttempt.setAuthAttemptStatus(AuthAttemptStatus.INVALID);
      authAttemptRepository.save(authAttempt);
      logger.warn("🔒 Auth attempt {} marked as INVALID (committed independently)", authAttemptId);
    }
    // Transaction commits here when method returns
  }

  /**
   * Mark auth attempt as REJECTED in an independent transaction.
   *
   * <p>Similar to markAsInvalid, ensures the rejection is persisted even if subsequent processing
   * fails.
   *
   * @param authAttemptId the auth attempt ID to mark as rejected
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markAsRejected(Integer authAttemptId) {
    AuthAttempt authAttempt = authAttemptRepository.findById(authAttemptId).orElse(null);
    if (authAttempt != null) {
      authAttempt.setAuthAttemptStatus(AuthAttemptStatus.REJECTED);
      authAttemptRepository.save(authAttempt);
      logger.debug(
          "🔒 Auth attempt {} marked as REJECTED (committed independently)", authAttemptId);
    }
    // Transaction commits here when method returns
  }
}
