/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentUniquenessTest
 * Description: Unit tests for enrollment uniqueness validation and constraint enforcement.
 */

package org.ezkey.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ezkey.config.EzkeyCoreProperties;
import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.signature.ECP256KeyPair;
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
 * Unit tests for enrollment uniqueness validation.
 *
 * <p>Tests validate that the system properly enforces uniqueness constraints for VERIFIED
 * enrollments while allowing multiple CREATED enrollments for retry scenarios.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Enrollment Uniqueness Validation Tests")
class EnrollmentUniquenessTest {

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private SignatureService signatureService;

  @Mock private EzkeyCoreProperties ezkeyCoreProperties;

  @Mock private IntegrationRepository integrationRepository;

  @Mock private EnrollmentBindService bindService;

  @Mock private EnrollmentVerifyService verifyService;

  @InjectMocks private EnrollmentService enrollmentService;

  private Integration testIntegration;
  private EnrollmentCreateRequest createRequest;
  private ECP256KeyPair testKeyPair;

  @BeforeEach
  void setUp() {
    // Setup test integration
    testIntegration = new Integration();
    testIntegration.setId(1);
    testIntegration.setIsSystemIntegration(false);

    // Setup test request
    createRequest = new EnrollmentCreateRequest();
    createRequest.setIntegrationId(1);
    createRequest.setName("Test Enrollment");

    // Setup test key pair
    testKeyPair = new ECP256KeyPair("base64PrivateKey", "base64PublicKey");

    // Mock integration repository
    when(integrationRepository.findById(1)).thenReturn(Optional.of(testIntegration));

    // Mock signature service
    when(signatureService.generateProofToken()).thenReturn("test-proof-token");
    when(signatureService.generateECP256KeyPair()).thenReturn(testKeyPair);
    when(signatureService.generateSecureChallenge(6)).thenReturn(123456);
  }

  @Test
  @DisplayName("create() - Should reject creation when active VERIFIED enrollment exists")
  void create_WhenActiveVerifiedExists_ShouldReject() {
    // Arrange
    Enrollment existingVerified = new Enrollment();
    existingVerified.setEnrollmentId(100);
    existingVerified.setIntegrationId(1);
    existingVerified.setEnrollmentName("Test Enrollment");
    existingVerified.setStatus(EnrollmentStatus.VERIFIED);
    existingVerified.setActive(true);

    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(
            eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED)))
        .thenReturn(List.of(existingVerified));

    // Act & Assert
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> enrollmentService.create(createRequest));

    assertTrue(
        exception.getMessage().contains("active verified enrollment"),
        "Error message should mention active verified enrollment");
    assertTrue(
        exception.getMessage().contains("recovery process"),
        "Error message should direct to recovery process");

    // Verify enrollment was never saved
    verify(enrollmentRepository, never()).save(any(Enrollment.class));
  }

  @Test
  @DisplayName("create() - Should allow creation when inactive VERIFIED enrollment exists")
  void create_WhenInactiveVerifiedExists_ShouldAllow() {
    // Arrange
    Enrollment existingInactiveVerified = new Enrollment();
    existingInactiveVerified.setEnrollmentId(100);
    existingInactiveVerified.setIntegrationId(1);
    existingInactiveVerified.setEnrollmentName("Test Enrollment");
    existingInactiveVerified.setStatus(EnrollmentStatus.VERIFIED);
    existingInactiveVerified.setActive(false);

    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(
            eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED)))
        .thenReturn(List.of(existingInactiveVerified));

    Enrollment newEnrollment = new Enrollment();
    newEnrollment.setEnrollmentId(200);
    newEnrollment.setIntegrationId(1);
    newEnrollment.setEnrollmentName("Test Enrollment");
    newEnrollment.setStatus(EnrollmentStatus.CREATED);
    newEnrollment.setActive(false);
    newEnrollment.setEnrollmentChallenge(123456);

    when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(newEnrollment);

    // Act
    EnrollmentCreateResponse response = enrollmentService.create(createRequest);

    // Assert
    assertNotNull(response);
    assertEquals(200, response.getEnrollmentId());
    verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
  }

  @Test
  @DisplayName("create() - Should allow creation when no VERIFIED enrollment exists")
  void create_WhenNoVerifiedExists_ShouldAllow() {
    // Arrange
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(
            eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED)))
        .thenReturn(new ArrayList<>());

    Enrollment newEnrollment = new Enrollment();
    newEnrollment.setEnrollmentId(200);
    newEnrollment.setIntegrationId(1);
    newEnrollment.setEnrollmentName("Test Enrollment");
    newEnrollment.setStatus(EnrollmentStatus.CREATED);
    newEnrollment.setActive(false);
    newEnrollment.setEnrollmentChallenge(123456);

    when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(newEnrollment);

    // Act
    EnrollmentCreateResponse response = enrollmentService.create(createRequest);

    // Assert
    assertNotNull(response);
    assertEquals(200, response.getEnrollmentId());
    verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
  }

  @Test
  @DisplayName("create() - Should allow multiple CREATED enrollments")
  void create_WhenCreatedExists_ShouldAllow() {
    // Arrange
    Enrollment existingCreated = new Enrollment();
    existingCreated.setEnrollmentId(100);
    existingCreated.setIntegrationId(1);
    existingCreated.setEnrollmentName("Test Enrollment");
    existingCreated.setStatus(EnrollmentStatus.CREATED);
    existingCreated.setActive(false);

    // No VERIFIED enrollment exists
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(
            eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED)))
        .thenReturn(new ArrayList<>());

    Enrollment newEnrollment = new Enrollment();
    newEnrollment.setEnrollmentId(200);
    newEnrollment.setIntegrationId(1);
    newEnrollment.setEnrollmentName("Test Enrollment");
    newEnrollment.setStatus(EnrollmentStatus.CREATED);
    newEnrollment.setActive(false);
    newEnrollment.setEnrollmentChallenge(123456);

    when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(newEnrollment);

    // Act
    EnrollmentCreateResponse response = enrollmentService.create(createRequest);

    // Assert
    assertNotNull(response);
    assertEquals(200, response.getEnrollmentId());
    verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
  }

  @Test
  @DisplayName("create() - Should preserve existing VERIFIED enrollment when rejecting")
  void create_WhenRejected_ShouldNotModifyExisting() {
    // Arrange
    Enrollment existingVerified = new Enrollment();
    existingVerified.setEnrollmentId(100);
    existingVerified.setIntegrationId(1);
    existingVerified.setEnrollmentName("Test Enrollment");
    existingVerified.setStatus(EnrollmentStatus.VERIFIED);
    existingVerified.setActive(true);

    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(
            eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED)))
        .thenReturn(List.of(existingVerified));

    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> enrollmentService.create(createRequest));

    // Verify existing enrollment was never modified
    verify(enrollmentRepository, never()).save(existingVerified);
    verify(enrollmentRepository, never()).save(any(Enrollment.class));
  }
}
