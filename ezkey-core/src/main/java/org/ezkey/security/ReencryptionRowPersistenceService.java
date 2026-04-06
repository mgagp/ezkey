/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security;

import jakarta.persistence.EntityManager;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists one re-encrypted row per call in {@link Propagation#REQUIRES_NEW} so a failed flush does
 * not poison the outer batch transaction.
 */
@Service
public class ReencryptionRowPersistenceService {

  private final EnrollmentRepository enrollmentRepository;
  private final AuthAttemptRepository authAttemptRepository;
  private final EntityManager entityManager;
  private final ReencryptionRecordCipher recordCipher;

  public ReencryptionRowPersistenceService(
      EnrollmentRepository enrollmentRepository,
      AuthAttemptRepository authAttemptRepository,
      EntityManager entityManager,
      ReencryptionRecordCipher recordCipher) {
    this.enrollmentRepository = enrollmentRepository;
    this.authAttemptRepository = authAttemptRepository;
    this.entityManager = entityManager;
    this.recordCipher = recordCipher;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void persistEnrollmentReencryption(ReencryptionBatch batch, Integer enrollmentId) {
    Enrollment e =
        enrollmentRepository
            .findByIdForReencryptionUpdate(enrollmentId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Enrollment not found for re-encryption persistence: " + enrollmentId));
    ReencryptionRecordCipher.ReencryptResult result = recordCipher.reencryptRecord(batch, e);
    if (!result.reencrypted()) {
      return;
    }
    enrollmentRepository.save(e);
    entityManager.flush();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void persistAuthAttemptReencryption(ReencryptionBatch batch, Integer authAttemptId) {
    AuthAttempt a =
        authAttemptRepository
            .findByIdForReencryptionUpdate(authAttemptId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "AuthAttempt not found for re-encryption persistence: " + authAttemptId));
    ReencryptionRecordCipher.ReencryptResult result = recordCipher.reencryptRecord(batch, a);
    if (!result.reencrypted()) {
      return;
    }
    authAttemptRepository.save(a);
    entityManager.flush();
  }
}
