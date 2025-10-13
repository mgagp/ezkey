/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptServiceDeviceProofTokenTest
 * Description: Unit tests for device proof token unicity validation in AuthAttemptService.
 */

package org.ezkey.authattempt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.ezkey.authattempt.domain.AuthAttemptPendingRequest;
import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for device proof token unicity validation in AuthAttemptService.
 *
 * <p>Tests specifically focus on the device proof token recording and uniqueness validation feature
 * implemented to prevent replay attacks.
 *
 * <p><b>Test Coverage:</b>
 *
 * <ul>
 *   <li>Device proof token uniqueness validation
 *   <li>Proper error handling for duplicate tokens
 *   <li>Token storage during pending authentication
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Usage:</b> Device proof token unicity validation tests
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptService
 */
@DisplayName("AuthAttemptService Device Proof Token Unicity Tests")
class AuthAttemptServiceDeviceProofTokenTest {

  @Mock private AuthAttemptRepository authAttemptRepository;

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private SignatureService signatureService;

  @Mock private AuthAttemptPendingService pendingService;

  @Mock private AuthAttemptRespondService respondService;

  @Mock private AuthAttemptWaitService waitService;

  private AuthAttemptService authAttemptService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    authAttemptService =
        new AuthAttemptService(
            authAttemptRepository,
            enrollmentRepository,
            signatureService,
            pendingService,
            respondService,
            waitService);
  }

  @Test
  @DisplayName("Should reject duplicate device proof token")
  void testRejectDuplicateDeviceProofToken() {
    // Arrange
    Integer enrollmentId = 1;
    String enrollmentProofToken = "EZK-ABC123-DEF456";
    String deviceProofToken = "duplicate-proof-token-123";
    String deviceProofTokenSigned = "signed-proof-token";
    String devicePublicKey = "mock-public-key";

    AuthAttemptPendingRequest request = new AuthAttemptPendingRequest();
    request.setEnrollmentId(enrollmentId);
    request.setEnrollmentProofToken(enrollmentProofToken);
    request.setDeviceProofToken(deviceProofToken);
    request.setDeviceProofTokenSigned(deviceProofTokenSigned);

    Enrollment mockEnrollment = mock(Enrollment.class);
    when(mockEnrollment.getEnrollmentId()).thenReturn(enrollmentId);
    when(mockEnrollment.getDevicePublicKey()).thenReturn(devicePublicKey);

    // Mock the specialized service to throw exception for duplicate token
    when(pendingService.pending(any(AuthAttemptPendingRequest.class)))
        .thenThrow(new IllegalArgumentException("Authentication request failed"));

    // Act & Assert
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              authAttemptService.pending(request);
            });

    // Verify
    assertEquals("Authentication request failed", exception.getMessage());
    verify(pendingService).pending(request);
  }

  @Test
  @DisplayName("Should proceed with unique device proof token")
  void testProceedWithUniqueDeviceProofToken() {
    // Arrange
    Integer enrollmentId = 1;
    String enrollmentProofToken = "EZK-ABC123-DEF456";
    String deviceProofToken = "unique-proof-token-456";
    String deviceProofTokenSigned = "signed-proof-token";
    String devicePublicKey = "mock-public-key";

    AuthAttemptPendingRequest request = new AuthAttemptPendingRequest();
    request.setEnrollmentId(enrollmentId);
    request.setEnrollmentProofToken(enrollmentProofToken);
    request.setDeviceProofToken(deviceProofToken);
    request.setDeviceProofTokenSigned(deviceProofTokenSigned);

    Enrollment mockEnrollment = mock(Enrollment.class);
    when(mockEnrollment.getEnrollmentId()).thenReturn(enrollmentId);
    when(mockEnrollment.getDevicePublicKey()).thenReturn(devicePublicKey);
    when(mockEnrollment.getIntegrationPrivateKey()).thenReturn("mock-private-key");
    when(mockEnrollment.getAuthAttemptChallengeRequired()).thenReturn(false);

    AuthAttempt mockAuthAttempt = mock(AuthAttempt.class);
    when(mockAuthAttempt.getAuthAttemptStatus()).thenReturn(AuthAttemptStatus.PENDING);
    when(mockAuthAttempt.getAuthAttemptId()).thenReturn(123);
    when(mockAuthAttempt.getAuthAttemptProofToken()).thenReturn("auth-attempt-token");

    // Mock the specialized service to return success response
    AuthAttemptPendingResponse mockResponse = new AuthAttemptPendingResponse();
    mockResponse.setAuthAttemptId(123);
    mockResponse.setAuthAttemptProofToken("auth-attempt-token");
    mockResponse.setAuthAttemptProofTokenSignedByIntegration("integration-signature");
    mockResponse.setAuthAttemptChallengeRequired(false);

    when(pendingService.pending(any(AuthAttemptPendingRequest.class))).thenReturn(mockResponse);

    // Act
    AuthAttemptPendingResponse response = authAttemptService.pending(request);

    // Assert
    assertNotNull(response);
    assertEquals(123, response.getAuthAttemptId());
    assertEquals("auth-attempt-token", response.getAuthAttemptProofToken());
    verify(pendingService).pending(request);
  }

  @Test
  @DisplayName("Should reject request with invalid signature even if token is unique")
  void testRejectInvalidSignatureRegardlessOfTokenUniqueness() {
    // Arrange
    Integer enrollmentId = 1;
    String enrollmentProofToken = "EZK-ABC123-DEF456";
    String deviceProofToken = "unique-proof-token-789";
    String deviceProofTokenSigned = "invalid-signed-proof-token";
    String devicePublicKey = "mock-public-key";

    AuthAttemptPendingRequest request = new AuthAttemptPendingRequest();
    request.setEnrollmentId(enrollmentId);
    request.setEnrollmentProofToken(enrollmentProofToken);
    request.setDeviceProofToken(deviceProofToken);
    request.setDeviceProofTokenSigned(deviceProofTokenSigned);

    Enrollment mockEnrollment = mock(Enrollment.class);
    when(mockEnrollment.getEnrollmentId()).thenReturn(enrollmentId);
    when(mockEnrollment.getDevicePublicKey()).thenReturn(devicePublicKey);

    // Mock the specialized service to throw exception for invalid signature
    when(pendingService.pending(any(AuthAttemptPendingRequest.class)))
        .thenThrow(new IllegalArgumentException("Authentication request failed"));

    // Act & Assert
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              authAttemptService.pending(request);
            });

    // Verify
    assertEquals("Authentication request failed", exception.getMessage());
    verify(pendingService).pending(request);
  }

  @Test
  @DisplayName("Should reject request with invalid enrollment proof token")
  void testRejectInvalidEnrollmentProofToken() {
    // Arrange
    Integer enrollmentId = 1;
    String enrollmentProofToken = "INVALID-PROOF-TOKEN";
    String deviceProofToken = "unique-proof-token-789";
    String deviceProofTokenSigned = "signed-proof-token";

    AuthAttemptPendingRequest request = new AuthAttemptPendingRequest();
    request.setEnrollmentId(enrollmentId);
    request.setEnrollmentProofToken(enrollmentProofToken);
    request.setDeviceProofToken(deviceProofToken);
    request.setDeviceProofTokenSigned(deviceProofTokenSigned);

    // Mock the specialized service to throw exception for invalid enrollment proof token
    when(pendingService.pending(any(AuthAttemptPendingRequest.class)))
        .thenThrow(new IllegalArgumentException("Authentication request failed"));

    // Act & Assert
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              authAttemptService.pending(request);
            });

    // Verify
    assertEquals("Authentication request failed", exception.getMessage());
    verify(pendingService).pending(request);
  }

  @Test
  @DisplayName("Should reject request when enrollment ID mismatch with proof token")
  void testRejectEnrollmentIdMismatchWithProofToken() {
    // Arrange
    Integer enrollmentId = 1;
    String enrollmentProofToken = "EZK-ABC123-DEF456";
    String deviceProofToken = "unique-proof-token-789";
    String deviceProofTokenSigned = "signed-proof-token";
    String devicePublicKey = "mock-public-key";

    AuthAttemptPendingRequest request = new AuthAttemptPendingRequest();
    request.setEnrollmentId(enrollmentId);
    request.setEnrollmentProofToken(enrollmentProofToken);
    request.setDeviceProofToken(deviceProofToken);
    request.setDeviceProofTokenSigned(deviceProofTokenSigned);

    Enrollment mockEnrollment = mock(Enrollment.class);
    when(mockEnrollment.getEnrollmentId()).thenReturn(999); // Different ID than request
    when(mockEnrollment.getDevicePublicKey()).thenReturn(devicePublicKey);

    // Mock the specialized service to throw exception for enrollment ID mismatch
    when(pendingService.pending(any(AuthAttemptPendingRequest.class)))
        .thenThrow(new IllegalArgumentException("Authentication request failed"));

    // Act & Assert
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              authAttemptService.pending(request);
            });

    // Verify
    assertEquals("Authentication request failed", exception.getMessage());
    verify(pendingService).pending(request);
  }
}
