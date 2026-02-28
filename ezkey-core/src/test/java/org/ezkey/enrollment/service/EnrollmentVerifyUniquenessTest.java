/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentVerifyUniquenessTest
 * Description: Unit tests for enrollment verification uniqueness validation.
 */

package org.ezkey.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for enrollment verification uniqueness validation.
 *
 * <p>
 * Tests validate that verification is rejected when a VERIFIED enrollment
 * already exists with
 * the same integration and name.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Enrollment Verification Uniqueness Tests")
class EnrollmentVerifyUniquenessTest {

  @Mock
  private EnrollmentRepository enrollmentRepository;

  @Mock
  private SignatureService signatureService;

  @Mock
  private EnrollmentTxHelper enrollmentTxHelper;

  @InjectMocks
  private EnrollmentVerifyService enrollmentVerifyService;

  private Enrollment enrollment;
  private EnrollmentVerifyRequest verifyRequest;

  @BeforeEach
  void setUp() {
    // Setup enrollment being verified
    enrollment = new Enrollment();
    enrollment.setEnrollmentId(200);
    enrollment.setIntegrationId(1);
    enrollment.setEnrollmentName("Test Enrollment");
    enrollment.setStatus(EnrollmentStatus.BOUND);
    enrollment.setActive(false);
    enrollment.setEnrollmentProofToken("test-proof-token");
    enrollment.setEnrollmentChallenge(123456);
    enrollment.setIntegrationPublicKey("integration-public-key");
    enrollment.setIntegrationPrivateKey("integration-private-key");
    enrollment.setCreatedAt(OffsetDateTime.now());

    // Setup verify request
    verifyRequest = new EnrollmentVerifyRequest();
    verifyRequest.setEnrollmentId(200);
    verifyRequest.setDevicePublicKey("device-public-key");
    verifyRequest.setEnrollmentProofTokenSigned("proof-token-signature");
    verifyRequest.setChallengeResponse(123456);
  }

  @Test
  @DisplayName("verify() - Should reject verification when VERIFIED enrollment exists")
  void verify_WhenVerifiedExists_ShouldReject() {
    // Arrange
    Enrollment existingVerified = new Enrollment();
    existingVerified.setEnrollmentId(100);
    existingVerified.setIntegrationId(1);
    existingVerified.setEnrollmentName("Test Enrollment");
    existingVerified.setStatus(EnrollmentStatus.VERIFIED);
    existingVerified.setActive(true);

    // Step 1: Mock validateEnrollmentState - findById returns BOUND enrollment
    when(enrollmentRepository.findById(200)).thenReturn(Optional.of(enrollment));

    // Step 2: Mock validateSignature - signature validation passes
    when(signatureService.validateSignature(
        eq("test-proof-token"), eq("proof-token-signature"), eq("device-public-key")))
        .thenReturn(true);

    // Step 3: Mock validateDeviceKeyUniqueness - device key is unique
    when(enrollmentRepository.existsByDevicePublicKeyHash(any(String.class))).thenReturn(false);

    // Step 5: Mock acquireLockAndValidate - findAndLockBoundById returns BOUND
    // enrollment
    when(enrollmentRepository.findAndLockBoundById(200)).thenReturn(Optional.of(enrollment));

    // Step 6: Mock validateUniqueness - existing VERIFIED enrollment found
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
        eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED), eq(200)))
        .thenReturn(List.of(existingVerified));

    // Act & Assert
    // The exception will be thrown during validateUniqueness (Step 6)
    IllegalStateException exception = assertThrows(
        IllegalStateException.class, () -> enrollmentVerifyService.verify(verifyRequest));

    assertTrue(
        exception.getMessage().contains("verified enrollment with the same name"),
        "Error message should mention verified enrollment exists");
    assertTrue(
        exception.getMessage().contains("recovery process"),
        "Error message should direct to recovery process");

    // Verify enrollment was never marked as verified
    verify(enrollmentRepository, never()).save(any(Enrollment.class));
  }

  @Test
  @DisplayName("verify() - Should preserve existing VERIFIED enrollment when rejecting")
  void verify_WhenRejected_ShouldNotModifyExisting() {
    // Arrange
    Enrollment existingVerified = new Enrollment();
    existingVerified.setEnrollmentId(100);
    existingVerified.setIntegrationId(1);
    existingVerified.setEnrollmentName("Test Enrollment");
    existingVerified.setStatus(EnrollmentStatus.VERIFIED);
    existingVerified.setActive(true);

    // Step 1: Mock validateEnrollmentState
    when(enrollmentRepository.findById(200)).thenReturn(Optional.of(enrollment));

    // Step 2: Mock validateSignature
    when(signatureService.validateSignature(
        eq("test-proof-token"), eq("proof-token-signature"), eq("device-public-key")))
        .thenReturn(true);

    // Step 3: Mock validateDeviceKeyUniqueness
    when(enrollmentRepository.existsByDevicePublicKeyHash(any(String.class))).thenReturn(false);

    // Step 5: Mock acquireLockAndValidate
    when(enrollmentRepository.findAndLockBoundById(200)).thenReturn(Optional.of(enrollment));

    // Step 6: Mock validateUniqueness
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
        eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED), eq(200)))
        .thenReturn(List.of(existingVerified));

    // Act & Assert
    assertThrows(IllegalStateException.class, () -> enrollmentVerifyService.verify(verifyRequest));

    // Verify existing enrollment was never modified
    verify(enrollmentRepository, never()).save(existingVerified);
    assertEquals(EnrollmentStatus.VERIFIED, existingVerified.getStatus());
    assertTrue(existingVerified.getActive());
  }

  @Test
  @DisplayName("verify() - Should allow verification when no VERIFIED enrollment exists")
  void verify_WhenNoVerifiedExists_ShouldAllow() {
    // Arrange
    // Step 1: Mock validateEnrollmentState
    when(enrollmentRepository.findById(200)).thenReturn(Optional.of(enrollment));

    // Step 2: Mock validateSignature
    when(signatureService.validateSignature(
        eq("test-proof-token"), eq("proof-token-signature"), eq("device-public-key")))
        .thenReturn(true);

    // Step 3: Mock validateDeviceKeyUniqueness - device key is unique
    when(enrollmentRepository.existsByDevicePublicKeyHash(any(String.class))).thenReturn(false);

    // Step 5: Mock acquireLockAndValidate
    when(enrollmentRepository.findAndLockBoundById(200)).thenReturn(Optional.of(enrollment));

    // Step 6: Mock validateUniqueness - no existing VERIFIED enrollment
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
        eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED), eq(200)))
        .thenReturn(List.of());

    // Mock markAsVerified - enrollment will be saved
    when(enrollmentRepository.saveAndFlush(any(Enrollment.class)))
        .thenAnswer(
            invocation -> {
              Enrollment saved = invocation.getArgument(0);
              saved.setStatus(EnrollmentStatus.VERIFIED);
              saved.setActive(true);
              return saved;
            });

    // Act
    var response = enrollmentVerifyService.verify(verifyRequest);

    // Assert
    assertTrue(response.isActive());
    verify(enrollmentRepository, times(1)).saveAndFlush(any(Enrollment.class));
  }

  @Test
  @DisplayName("verify() - Should reject verification when inactive VERIFIED enrollment exists")
  void verify_WhenInactiveVerifiedExists_ShouldReject() {
    // Arrange
    Enrollment existingInactiveVerified = new Enrollment();
    existingInactiveVerified.setEnrollmentId(100);
    existingInactiveVerified.setIntegrationId(1);
    existingInactiveVerified.setEnrollmentName("Test Enrollment");
    existingInactiveVerified.setStatus(EnrollmentStatus.VERIFIED);
    existingInactiveVerified.setActive(false); // Inactive but still VERIFIED

    // Step 1: Mock validateEnrollmentState
    when(enrollmentRepository.findById(200)).thenReturn(Optional.of(enrollment));

    // Step 2: Mock validateSignature
    when(signatureService.validateSignature(
        eq("test-proof-token"), eq("proof-token-signature"), eq("device-public-key")))
        .thenReturn(true);

    // Step 3: Mock validateDeviceKeyUniqueness
    when(enrollmentRepository.existsByDevicePublicKeyHash(any(String.class))).thenReturn(false);

    // Step 5: Mock acquireLockAndValidate
    when(enrollmentRepository.findAndLockBoundById(200)).thenReturn(Optional.of(enrollment));

    // Step 6: Mock validateUniqueness - inactive VERIFIED enrollment found
    // CRITICAL: Inactive VERIFIED enrollments also prevent verification (active
    // flag doesn't affect
    // uniqueness)
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
        eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED), eq(200)))
        .thenReturn(List.of(existingInactiveVerified));

    // Act & Assert
    IllegalStateException exception = assertThrows(
        IllegalStateException.class, () -> enrollmentVerifyService.verify(verifyRequest));

    assertTrue(
        exception.getMessage().contains("verified enrollment with the same name"),
        "Error message should mention verified enrollment exists");
    assertTrue(
        exception.getMessage().contains("recovery process"),
        "Error message should direct to recovery process");

    // Verify enrollment was never marked as verified
    verify(enrollmentRepository, never()).save(any(Enrollment.class));
  }

  @Test
  @DisplayName("verify() - Error message should contain recovery process endpoints")
  void verify_WhenRejected_ErrorMessageShouldContainRecoveryEndpoints() {
    // Arrange
    Enrollment existingVerified = new Enrollment();
    existingVerified.setEnrollmentId(100);
    existingVerified.setIntegrationId(1);
    existingVerified.setEnrollmentName("Test Enrollment");
    existingVerified.setStatus(EnrollmentStatus.VERIFIED);
    existingVerified.setActive(true);

    when(enrollmentRepository.findById(200)).thenReturn(Optional.of(enrollment));
    when(signatureService.validateSignature(
        eq("test-proof-token"), eq("proof-token-signature"), eq("device-public-key")))
        .thenReturn(true);
    when(enrollmentRepository.existsByDevicePublicKeyHash(any(String.class))).thenReturn(false);
    when(enrollmentRepository.findAndLockBoundById(200)).thenReturn(Optional.of(enrollment));
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
        eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED), eq(200)))
        .thenReturn(List.of(existingVerified));

    // Act
    IllegalStateException exception = assertThrows(
        IllegalStateException.class, () -> enrollmentVerifyService.verify(verifyRequest));

    // Assert
    String errorMessage = exception.getMessage();
    assertTrue(
        errorMessage.contains("/api/v1/admin/auth/recover")
            || errorMessage.contains("/auth/recover"),
        "Error message should contain recovery endpoint");
    assertTrue(
        errorMessage.contains("/api/v1/admin/enrollments/reset")
            || errorMessage.contains("/enrollments/reset"),
        "Error message should contain reset endpoint");
  }
}
