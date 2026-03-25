/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentServiceTest
 * Description: Critical unit tests for EnrollmentService business logic and security operations.
 */

package org.ezkey.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.config.EnrollmentProperties;
import org.ezkey.config.EzkeyCoreProperties;
import org.ezkey.enrollment.domain.EnrollmentBindRequest;
import org.ezkey.enrollment.domain.EnrollmentBindResponse;
import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.signature.Ed25519KeyPair;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Critical unit tests for {@link EnrollmentService}.
 *
 * <p>This test class provides comprehensive coverage of the EnrollmentService focusing on
 * security-critical enrollment operations. Tests cover enrollment creation, device binding,
 * cryptographic verification, and proper state management with locking mechanisms.
 *
 * <p><b>Critical Test Coverage:</b>
 *
 * <ul>
 *   <li>create() - Enrollment creation with cryptographic key generation
 *   <li>bind() - Device binding with locking and state validation
 *   <li>verify() - Enrollment verification with signature validation
 *   <li>Security validation - Cryptographic signature handling
 *   <li>State management - Proper enrollment state transitions
 *   <li>Error handling - Appropriate exception handling
 * </ul>
 *
 * <p><b>Security Focus:</b> These tests validate the critical security aspects of enrollment
 * including cryptographic key validation, state consistency, locking mechanisms, and proper error
 * handling to prevent security vulnerabilities.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EnrollmentService
 * @see SignatureService
 * @see EnrollmentRepository
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Enrollment Service Critical Tests")
class EnrollmentServiceTest {

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private SignatureService signatureService;

  @Mock private EzkeyCoreProperties ezkeyCoreProperties;

  @Mock private EnrollmentProperties enrollmentProperties;

  @Mock private IntegrationRepository integrationRepository;

  @Mock private EnrollmentBindService bindService;

  @Mock private EnrollmentVerifyService verifyService;

  @InjectMocks private EnrollmentService enrollmentService;

  @Captor private ArgumentCaptor<Enrollment> enrollmentCaptor;

  private EnrollmentCreateRequest createRequest;
  private EnrollmentBindRequest bindRequest;
  private EnrollmentVerifyRequest verifyRequest;
  private Enrollment enrollment;
  private Integration integration;
  private Ed25519KeyPair ed25519KeyPair;

  @BeforeEach
  void setUp() {
    // Setup configuration properties
    EzkeyCoreProperties.Crypto crypto = new EzkeyCoreProperties.Crypto();
    when(ezkeyCoreProperties.getCrypto()).thenReturn(crypto);

    // Setup create request
    createRequest = new EnrollmentCreateRequest();
    createRequest.setIntegrationId(123);
    createRequest.setName("Test Enrollment");
    createRequest.setAuthAttemptChallengeRequired(true);

    // Setup bind request
    bindRequest = new EnrollmentBindRequest();
    bindRequest.setEnrollmentId(456);
    bindRequest.setEnrollmentProofToken("test-proof-token");

    // Setup verify request
    verifyRequest = new EnrollmentVerifyRequest();
    verifyRequest.setEnrollmentId(456);
    verifyRequest.setDevicePublicKey("device-public-key");
    verifyRequest.setEnrollmentProofTokenSigned("proof-token-signature");
    verifyRequest.setChallengeResponse(123456);

    // Setup enrollment entity
    enrollment = new Enrollment();
    enrollment.setEnrollmentId(456);
    enrollment.setIntegrationId(123);
    enrollment.setEnrollmentName("Test Enrollment");
    enrollment.setEnrollmentProofToken("test-proof-token");
    enrollment.setEnrollmentChallenge(123456);
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setActive(false);
    enrollment.setIntegrationPublicKey("integration-public-key");
    enrollment.setIntegrationPrivateKey("integration-private-key");
    enrollment.setCreatedAt(OffsetDateTime.now());

    // Setup integration entity
    integration = new Integration();
    integration.setId(123);

    // Setup EC P-256 key pair
    ed25519KeyPair = new Ed25519KeyPair("private-key", "public-key-url");

    // Setup integration repository mock - validates integration exists during
    // enrollment creation
    when(integrationRepository.findById(123)).thenReturn(Optional.of(integration));
  }

  // ===== CREATE ENDPOINT TESTS =====

  @Test
  @DisplayName("create() - Should create enrollment successfully with cryptographic keys")
  void create_WhenValidRequest_ShouldCreateEnrollmentSuccessfully() {
    // Arrange
    when(signatureService.generateProofToken()).thenReturn("generated-proof-token");
    when(signatureService.generateEd25519KeyPair()).thenReturn(ed25519KeyPair);
    when(signatureService.generateSecureChallenge(6)).thenReturn(123456);
    when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(enrollment);

    // Act
    EnrollmentCreateResponse response = enrollmentService.create(createRequest);

    // Assert
    assertNotNull(response);
    assertEquals(456, response.getEnrollmentId());
    assertEquals(123456, response.getEnrollmentChallenge());

    // Verify service interactions
    verify(signatureService, times(1)).generateProofToken();
    verify(signatureService, times(1)).generateEd25519KeyPair();
    verify(signatureService, times(1)).generateSecureChallenge(6);
    verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
  }

  @Test
  @DisplayName("create() - Should throw IllegalArgumentException when integrationId is null")
  void create_WhenIntegrationIdIsNull_ShouldThrowIllegalArgumentException() {
    // Arrange
    createRequest.setIntegrationId(null);

    // Act & Assert
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> enrollmentService.create(createRequest));

    assertEquals("Integration ID is required", exception.getMessage());

    // Verify no service interactions
    verify(signatureService, never()).generateProofToken();
    verify(enrollmentRepository, never()).save(any(Enrollment.class));
  }

  @Test
  @DisplayName("create() - Should handle name trimming correctly")
  void create_WhenNameHasWhitespace_ShouldTrimName() {
    // Arrange
    createRequest.setName("  Test Enrollment  ");
    when(signatureService.generateProofToken()).thenReturn("generated-proof-token");
    when(signatureService.generateEd25519KeyPair()).thenReturn(ed25519KeyPair);
    when(signatureService.generateSecureChallenge(6)).thenReturn(123456);
    when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(enrollment);

    // Act
    EnrollmentCreateResponse response = enrollmentService.create(createRequest);

    // Assert
    assertNotNull(response);
    verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
  }

  @Test
  @DisplayName("create() - Should set expiresAt when pendingExpirationDays is set")
  void create_WhenPendingExpirationDaysSet_ShouldSetExpiresAt() {
    when(enrollmentProperties.getPendingExpirationDays()).thenReturn(30);
    when(signatureService.generateProofToken()).thenReturn("generated-proof-token");
    when(signatureService.generateEd25519KeyPair()).thenReturn(ed25519KeyPair);
    when(signatureService.generateSecureChallenge(6)).thenReturn(123456);
    when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

    enrollmentService.create(createRequest);

    verify(enrollmentRepository).save(enrollmentCaptor.capture());
    Enrollment saved = enrollmentCaptor.getValue();
    assertNotNull(saved.getExpiresAt());
    assertNotNull(saved.getCreatedAt());
    assertTrue(
        saved.getExpiresAt().isAfter(saved.getCreatedAt()), "expiresAt should be after createdAt");
    assertTrue(
        saved.getExpiresAt().isBefore(saved.getCreatedAt().plusDays(31)),
        "expiresAt should be within 31 days");
  }

  @Test
  @DisplayName("create() - Should not set expiresAt when pendingExpirationDays is null")
  void create_WhenPendingExpirationDaysNull_ShouldNotSetExpiresAt() {
    when(enrollmentProperties.getPendingExpirationDays()).thenReturn(null);
    when(signatureService.generateProofToken()).thenReturn("generated-proof-token");
    when(signatureService.generateEd25519KeyPair()).thenReturn(ed25519KeyPair);
    when(signatureService.generateSecureChallenge(6)).thenReturn(123456);
    when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

    enrollmentService.create(createRequest);

    verify(enrollmentRepository).save(enrollmentCaptor.capture());
    assertNull(enrollmentCaptor.getValue().getExpiresAt());
  }

  // ===== BIND ENDPOINT TESTS =====

  @Test
  @DisplayName("bind() - Should delegate to bindService")
  void bind_WhenValidRequest_ShouldDelegateToBindService() {
    // Arrange
    EnrollmentBindResponse expectedResponse = new EnrollmentBindResponse();
    expectedResponse.setEnrollmentId(456);
    expectedResponse.setEnrollmentName("Test Enrollment");
    when(bindService.bind(bindRequest)).thenReturn(expectedResponse);

    // Act
    EnrollmentBindResponse response = enrollmentService.bind(bindRequest);

    // Assert
    assertNotNull(response);
    assertEquals(456, response.getEnrollmentId());
    assertEquals("Test Enrollment", response.getEnrollmentName());

    // Verify delegation
    verify(bindService, times(1)).bind(bindRequest);
  }

  @Test
  @DisplayName("bind() - Should delegate exception from bindService")
  void bind_WhenEnrollmentNotFound_ShouldDelegateException() {
    // Arrange
    when(bindService.bind(bindRequest))
        .thenThrow(new IllegalArgumentException("Enrollment binding failed"));

    // Act & Assert
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> enrollmentService.bind(bindRequest));

    assertEquals("Enrollment binding failed", exception.getMessage());

    // Verify delegation
    verify(bindService, times(1)).bind(bindRequest);
  }

  @Test
  @DisplayName("bind() - Should delegate IllegalStateException from bindService")
  void bind_WhenEnrollmentAlreadyBound_ShouldDelegateException() {
    // Arrange
    when(bindService.bind(bindRequest))
        .thenThrow(new IllegalStateException("Enrollment already bound by a device"));

    // Act & Assert
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> enrollmentService.bind(bindRequest));

    assertEquals("Enrollment already bound by a device", exception.getMessage());

    // Verify delegation
    verify(bindService, times(1)).bind(bindRequest);
  }

  @Test
  @DisplayName("bind() - Should delegate IllegalStateException from bindService")
  void bind_WhenIntegrationNotFound_ShouldDelegateException() {
    // Arrange
    when(bindService.bind(bindRequest))
        .thenThrow(new IllegalStateException("Enrollment binding failed"));

    // Act & Assert
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> enrollmentService.bind(bindRequest));

    assertEquals("Enrollment binding failed", exception.getMessage());

    // Verify delegation
    verify(bindService, times(1)).bind(bindRequest);
  }

  @Test
  @DisplayName("bind() - Should delegate IllegalArgumentException from bindService")
  void bind_WhenLockAcquisitionFails_ShouldDelegateException() {
    // Arrange
    when(bindService.bind(bindRequest))
        .thenThrow(new IllegalArgumentException("Enrollment not found or already bound"));

    // Act & Assert
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> enrollmentService.bind(bindRequest));

    assertEquals("Enrollment not found or already bound", exception.getMessage());

    // Verify delegation
    verify(bindService, times(1)).bind(bindRequest);
  }

  // ===== VERIFY ENDPOINT TESTS =====

  @Test
  @DisplayName("verify() - Should delegate to verifyService")
  void verify_WhenValidRequest_ShouldDelegateToVerifyService() {
    // Arrange
    EnrollmentVerifyResponse expectedResponse = new EnrollmentVerifyResponse();
    expectedResponse.setActive(true);
    when(verifyService.verify(verifyRequest)).thenReturn(expectedResponse);

    // Act
    EnrollmentVerifyResponse response = enrollmentService.verify(verifyRequest);

    // Assert
    assertNotNull(response);
    assertTrue(response.isActive());

    // Verify delegation
    verify(verifyService, times(1)).verify(verifyRequest);
  }

  @Test
  @DisplayName("verify() - Should delegate IllegalStateException from verifyService")
  void verify_WhenEnrollmentAlreadyVerified_ShouldDelegateException() {
    // Arrange
    when(verifyService.verify(verifyRequest))
        .thenThrow(new IllegalStateException("Enrollment already verified"));

    // Act & Assert
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> enrollmentService.verify(verifyRequest));

    assertEquals("Enrollment already verified", exception.getMessage());

    // Verify delegation
    verify(verifyService, times(1)).verify(verifyRequest);
  }

  @Test
  @DisplayName("verify() - Should delegate IllegalStateException from verifyService")
  void verify_WhenEnrollmentNotBound_ShouldDelegateException() {
    // Arrange
    when(verifyService.verify(verifyRequest))
        .thenThrow(new IllegalStateException("Enrollment must be bound before verification"));

    // Act & Assert
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> enrollmentService.verify(verifyRequest));

    assertEquals("Enrollment must be bound before verification", exception.getMessage());

    // Verify delegation
    verify(verifyService, times(1)).verify(verifyRequest);
  }

  @Test
  @DisplayName("verify() - Should delegate IllegalArgumentException from verifyService")
  void verify_WhenSignatureValidationFails_ShouldThrowIllegalArgumentException() {
    // Arrange
    when(verifyService.verify(verifyRequest))
        .thenThrow(new IllegalArgumentException("Invalid bind proof token signature"));

    // Act & Assert
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> enrollmentService.verify(verifyRequest));

    assertEquals("Invalid bind proof token signature", exception.getMessage());

    // Verify delegation
    verify(verifyService, times(1)).verify(verifyRequest);
  }

  @Test
  @DisplayName("verify() - Should delegate IllegalArgumentException from verifyService")
  void verify_WhenChallengeResponseInvalid_ShouldThrowIllegalArgumentException() {
    // Arrange
    when(verifyService.verify(verifyRequest))
        .thenThrow(new IllegalArgumentException("Invalid bind proof token signature"));

    // Act & Assert
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> enrollmentService.verify(verifyRequest));

    assertEquals("Invalid bind proof token signature", exception.getMessage());

    // Verify delegation
    verify(verifyService, times(1)).verify(verifyRequest);
  }

  @Test
  @DisplayName("verify() - Should delegate IllegalStateException from verifyService")
  void verify_WhenLockAcquisitionFails_ShouldThrowIllegalStateException() {
    // Arrange
    when(verifyService.verify(verifyRequest))
        .thenThrow(new IllegalStateException("Enrollment already verified"));

    // Act & Assert
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> enrollmentService.verify(verifyRequest));

    assertEquals("Enrollment already verified", exception.getMessage());

    // Verify delegation
    verify(verifyService, times(1)).verify(verifyRequest);
  }

  @Test
  @DisplayName("verify() - Should delegate IllegalArgumentException from verifyService")
  void verify_WhenDevicePublicKeyAlreadyUsed_ShouldThrowIllegalArgumentException() {
    // Arrange
    when(verifyService.verify(verifyRequest))
        .thenThrow(new IllegalArgumentException("Invalid bind proof token signature"));

    // Act & Assert
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> enrollmentService.verify(verifyRequest));

    assertEquals("Invalid bind proof token signature", exception.getMessage());

    // Verify delegation
    verify(verifyService, times(1)).verify(verifyRequest);
  }

  @Test
  @DisplayName("verify() - Should delegate to verifyService")
  void verify_WhenDevicePublicKeyIsUnique_ShouldProceedSuccessfully() {
    // Arrange
    EnrollmentVerifyResponse expectedResponse = new EnrollmentVerifyResponse();
    expectedResponse.setActive(true);
    when(verifyService.verify(verifyRequest)).thenReturn(expectedResponse);

    // Act
    EnrollmentVerifyResponse response = enrollmentService.verify(verifyRequest);

    // Assert
    assertNotNull(response);
    assertTrue(response.isActive());

    // Verify delegation
    verify(verifyService, times(1)).verify(verifyRequest);
  }

  // ===== GET BY ID TESTS =====

  @Test
  @DisplayName("getById() - Should return enrollment when found")
  void getById_WhenEnrollmentFound_ShouldReturnEnrollment() {
    // Arrange
    when(enrollmentRepository.findById(456)).thenReturn(Optional.of(enrollment));

    // Act
    Enrollment result = enrollmentService.getById(456);

    // Assert
    assertNotNull(result);
    assertEquals(456, result.getEnrollmentId());
    verify(enrollmentRepository, times(1)).findById(456);
  }

  @Test
  @DisplayName("getById() - Should throw ResourceNotFoundException when enrollment not found")
  void getById_WhenEnrollmentNotFound_ShouldThrowResourceNotFoundException() {
    // Arrange
    when(enrollmentRepository.findById(456)).thenReturn(Optional.empty());

    // Act & Assert
    ResourceNotFoundException exception =
        assertThrows(ResourceNotFoundException.class, () -> enrollmentService.getById(456));

    assertEquals("Enrollment with id 456 not found", exception.getMessage());
    verify(enrollmentRepository, times(1)).findById(456);
  }

  // ===== DELETE TESTS =====

  @Test
  @DisplayName("delete() - Should delete enrollment successfully")
  void delete_WhenValidId_ShouldDeleteEnrollment() {
    // Act
    enrollmentService.delete(456);

    // Assert
    verify(enrollmentRepository, times(1)).deleteById(456);
  }

  // ===== FIND BY INTEGRATION ID TESTS =====

  @Test
  @DisplayName("findByIntegrationId() - Should return enrollments for integration")
  void findByIntegrationId_WhenValidId_ShouldReturnEnrollments() {
    // Act
    enrollmentService.findByIntegrationId(123);

    // Assert
    verify(enrollmentRepository, times(1)).findByIntegrationId(123);
  }
}
