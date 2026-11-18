/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentBindServiceProofTokenTest
 * Description: Proof-token handle and read-once bind failures (ADR-0019).
 */

package org.ezkey.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.enrollment.domain.EnrollmentBindRequest;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.EnrollmentAlreadyBoundException;
import org.ezkey.exception.auth.EnrollmentBindingFailedException;
import org.ezkey.exception.auth.EnrollmentIntegrationNotFoundException;
import org.ezkey.exception.auth.EnrollmentNotAvailableAfterLockException;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.security.SensitiveDataHasher;
import org.ezkey.service.EntityEligibilityService;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Bind locates the enrollment from the proof-token hash alone and must not mark the row bound when
 * that proof fails before or after the lock.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentBindServiceProofTokenTest {

  private static final String PROOF_TOKEN = "proof-token";

  private static final String PROOF_TOKEN_HASH = SensitiveDataHasher.sha256Hex(PROOF_TOKEN);

  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private IntegrationRepository integrationRepository;
  @Mock private EzkeyAdminRepository ezkeyAdminRepository;
  @Mock private EnrollmentTxHelper enrollmentTxHelper;
  @Mock private SignatureService signatureService;

  private EnrollmentBindService enrollmentBindService;

  @BeforeEach
  void setUp() {
    enrollmentBindService =
        new EnrollmentBindService(
            enrollmentRepository,
            integrationRepository,
            ezkeyAdminRepository,
            new EntityEligibilityService(),
            enrollmentTxHelper,
            signatureService);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  @DisplayName("blank proof token fails closed without a repository lookup")
  void bindRejectsBlankProofTokenWithoutLookup(String proofToken) {
    EnrollmentBindRequest request = request(proofToken);

    EnrollmentBindingFailedException exception =
        assertThrows(
            EnrollmentBindingFailedException.class, () -> enrollmentBindService.bind(request));

    assertEquals("Enrollment binding failed", exception.getMessage());
    verify(enrollmentRepository, never()).findByEnrollmentProofTokenHash(any());
    verify(enrollmentRepository, never()).findAndLockUnreadById(any());
    verify(enrollmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("unknown proof token uses the same failure as a blank token and does not lock")
  void bindRejectsUnknownProofToken() {
    when(enrollmentRepository.findByEnrollmentProofTokenHash(PROOF_TOKEN_HASH))
        .thenReturn(Optional.empty());

    EnrollmentBindingFailedException exception =
        assertThrows(
            EnrollmentBindingFailedException.class,
            () -> enrollmentBindService.bind(request(PROOF_TOKEN)));

    assertEquals("Enrollment binding failed", exception.getMessage());
    verify(enrollmentRepository, never()).findAndLockUnreadById(any());
    verify(enrollmentRepository, never()).save(any());
    verify(ezkeyAdminRepository, never()).findByEnrollmentId(any());
  }

  @ParameterizedTest
  @EnumSource(value = EnrollmentStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "CREATED")
  @DisplayName("any status other than CREATED is already processed and is not locked")
  void bindRejectsProcessedEnrollmentBeforeLock(EnrollmentStatus status) {
    Enrollment enrollment = createdEnrollment(PROOF_TOKEN);
    enrollment.setStatus(status);
    when(enrollmentRepository.findByEnrollmentProofTokenHash(PROOF_TOKEN_HASH))
        .thenReturn(Optional.of(enrollment));

    EnrollmentAlreadyBoundException exception =
        assertThrows(
            EnrollmentAlreadyBoundException.class,
            () -> enrollmentBindService.bind(request(PROOF_TOKEN)));

    assertEquals("Enrollment already bound by a device", exception.getMessage());
    assertEquals(status, enrollment.getStatus());
    verify(enrollmentRepository, never()).findAndLockUnreadById(any());
    verify(enrollmentRepository, never()).save(any());
    verify(enrollmentTxHelper, never()).markExpiredAndEmitAudit(any(), any(), any(), any());
  }

  @Test
  @DisplayName("a missing integration is rejected before the row is locked or saved")
  void bindRejectsMissingIntegrationBeforeLock() {
    Enrollment enrollment = createdEnrollment(PROOF_TOKEN);
    when(enrollmentRepository.findByEnrollmentProofTokenHash(PROOF_TOKEN_HASH))
        .thenReturn(Optional.of(enrollment));
    when(ezkeyAdminRepository.findByEnrollmentId(101)).thenReturn(Optional.empty());
    when(integrationRepository.findById(7)).thenReturn(Optional.empty());

    EnrollmentIntegrationNotFoundException exception =
        assertThrows(
            EnrollmentIntegrationNotFoundException.class,
            () -> enrollmentBindService.bind(request(PROOF_TOKEN)));

    assertEquals("Integration not found for enrollment", exception.getMessage());
    assertEquals(EnrollmentStatus.CREATED, enrollment.getStatus());
    verify(enrollmentRepository, never()).findAndLockUnreadById(any());
    verify(enrollmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("a lost lock does not mark the enrollment bound")
  void bindDoesNotSaveWhenLockMisses() {
    Enrollment enrollment = createdEnrollment(PROOF_TOKEN);
    stubReadyForLock(enrollment);
    when(enrollmentRepository.findAndLockUnreadById(101)).thenReturn(Optional.empty());

    EnrollmentNotAvailableAfterLockException exception =
        assertThrows(
            EnrollmentNotAvailableAfterLockException.class,
            () -> enrollmentBindService.bind(request(PROOF_TOKEN)));

    assertEquals("Enrollment not found or already bound", exception.getMessage());
    assertEquals(EnrollmentStatus.CREATED, enrollment.getStatus());
    verify(enrollmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("a proof token that changed under the lock does not mark the enrollment bound")
  void bindDoesNotSaveWhenProofTokenChangesAfterLock() {
    Enrollment enrollment = createdEnrollment(PROOF_TOKEN);
    Enrollment locked = createdEnrollment("replaced-token");
    stubReadyForLock(enrollment);
    when(enrollmentRepository.findAndLockUnreadById(101)).thenReturn(Optional.of(locked));

    EnrollmentBindingFailedException exception =
        assertThrows(
            EnrollmentBindingFailedException.class,
            () -> enrollmentBindService.bind(request(PROOF_TOKEN)));

    assertEquals("Enrollment binding failed", exception.getMessage());
    assertEquals(EnrollmentStatus.CREATED, enrollment.getStatus());
    assertEquals(EnrollmentStatus.CREATED, locked.getStatus());
    verify(enrollmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("a status change under the lock does not mark the enrollment bound again")
  void bindDoesNotSaveWhenStatusChangesAfterLock() {
    Enrollment enrollment = createdEnrollment(PROOF_TOKEN);
    Enrollment locked = createdEnrollment(PROOF_TOKEN);
    locked.setStatus(EnrollmentStatus.BOUND);
    stubReadyForLock(enrollment);
    when(enrollmentRepository.findAndLockUnreadById(101)).thenReturn(Optional.of(locked));

    EnrollmentAlreadyBoundException exception =
        assertThrows(
            EnrollmentAlreadyBoundException.class,
            () -> enrollmentBindService.bind(request(PROOF_TOKEN)));

    assertEquals("Enrollment already bound by a device", exception.getMessage());
    assertEquals(EnrollmentStatus.CREATED, enrollment.getStatus());
    assertEquals(EnrollmentStatus.BOUND, locked.getStatus());
    verify(enrollmentRepository, never()).save(any());
  }

  private void stubReadyForLock(Enrollment enrollment) {
    when(enrollmentRepository.findByEnrollmentProofTokenHash(PROOF_TOKEN_HASH))
        .thenReturn(Optional.of(enrollment));
    when(ezkeyAdminRepository.findByEnrollmentId(101)).thenReturn(Optional.empty());
    Integration integration = new Integration();
    integration.setId(7);
    integration.setName("Acme");
    when(integrationRepository.findById(7)).thenReturn(Optional.of(integration));
  }

  private static EnrollmentBindRequest request(String proofToken) {
    EnrollmentBindRequest request = new EnrollmentBindRequest();
    request.setEnrollmentProofToken(proofToken);
    return request;
  }

  private static Enrollment createdEnrollment(String proofToken) {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(101);
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setIntegrationId(7);
    enrollment.setExpiresAt(OffsetDateTime.now().plusHours(1));
    enrollment.setEnrollmentProofToken(proofToken);
    return enrollment;
  }
}
