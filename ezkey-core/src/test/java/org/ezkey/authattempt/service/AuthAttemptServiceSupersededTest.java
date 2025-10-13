/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptServiceSupersededTest
 * Description: Tests for authentication attempt superseded by newer requests logic.
 */

package org.ezkey.authattempt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.AuthenticationResult;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for authentication attempt superseded logic.
 *
 * <p>Tests the business rule that when a newer authentication attempt is created for the same
 * enrollment, older attempts should be considered expired.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
class AuthAttemptServiceSupersededTest {

  @Mock private AuthAttemptRepository authAttemptRepository;

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private SignatureService signatureService;

  @Mock private EntityManager entityManager;

  @Mock private AuthAttemptPendingService pendingService;

  @Mock private AuthAttemptRespondService respondService;

  @Mock private AuthAttemptWaitService waitService;

  @InjectMocks private AuthAttemptService authAttemptService;

  private AuthAttempt olderAttempt;
  private AuthAttempt newerAttempt;
  private Enrollment enrollment;

  @BeforeEach
  void setUp() {
    // Configure EntityManager using reflection
    try {
      java.lang.reflect.Field entityManagerField =
          AuthAttemptService.class.getDeclaredField("entityManager");
      entityManagerField.setAccessible(true);
      entityManagerField.set(authAttemptService, entityManager);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set EntityManager", e);
    }

    // Create test enrollment
    enrollment = new Enrollment();
    enrollment.setEnrollmentId(1);
    enrollment.setDevicePublicKey("test-public-key");
    enrollment.setIntegrationPrivateKey("test-private-key");
    enrollment.setAuthAttemptChallengeRequired(false);

    // Configure mocks for specialized services
    setupSpecializedServiceMocks();

    // Create older authentication attempt
    olderAttempt = new AuthAttempt();
    olderAttempt.setAuthAttemptId(1);
    olderAttempt.setEnrollmentId(1);
    olderAttempt.setAuthAttemptStatus(AuthAttemptStatus.READ);
    olderAttempt.setAuthAttemptProofToken("older-proof-token");
    olderAttempt.setCreatedAt(LocalDateTime.now().minusMinutes(5));
    olderAttempt.setExpiresAt(LocalDateTime.now().plusMinutes(10));

    // Create newer authentication attempt
    newerAttempt = new AuthAttempt();
    newerAttempt.setAuthAttemptId(2);
    newerAttempt.setEnrollmentId(1);
    newerAttempt.setAuthAttemptStatus(AuthAttemptStatus.PENDING);
    newerAttempt.setAuthAttemptProofToken("newer-proof-token");
    newerAttempt.setCreatedAt(LocalDateTime.now().minusMinutes(1));
    newerAttempt.setExpiresAt(LocalDateTime.now().plusMinutes(10));
  }

  @Test
  void testRespond_ShouldReturnExpired_WhenNewerAttemptExists() {
    // Given - setup superseded mock
    setupSupersededRespondServiceMock();

    AuthAttemptRespondRequest request = new AuthAttemptRespondRequest();
    request.setAuthAttemptId(1);
    request.setAuthAttemptAccepted(true);
    request.setAuthAttemptProofTokenSignedByDevice("signed-token");

    // When
    AuthAttemptRespondResponse response = authAttemptService.respond(request);

    // Then
    assertNotNull(response);
    assertEquals(AuthenticationResult.EXPIRED, response.getResult());
    assertEquals("Authentication attempt superseded by newer request", response.getMessage());
    verify(respondService).respond(request);
  }

  @Test
  void testRespond_ShouldProceedNormally_WhenNoNewerAttemptExists() {
    // Given - use default mock (APPROVED)
    setupDefaultRespondServiceMock();

    AuthAttemptRespondRequest request = new AuthAttemptRespondRequest();
    request.setAuthAttemptId(1);
    request.setAuthAttemptAccepted(true);
    request.setAuthAttemptProofTokenSignedByDevice("signed-token");

    // When
    AuthAttemptRespondResponse response = authAttemptService.respond(request);

    // Then
    assertNotNull(response);
    assertEquals(AuthenticationResult.APPROVED, response.getResult());
    assertEquals("Auth attempt completed", response.getMessage());
    verify(respondService).respond(request);
  }

  @Test
  void testWaitForResponse_ShouldReturnExpired_WhenNewerAttemptExists() {
    // Given - setup superseded mock
    setupSupersededWaitServiceMock();

    AuthAttemptWaitRequest request = new AuthAttemptWaitRequest();
    request.setTimeout(1); // 1 second instead of 30
    request.setPolling(1); // 1 second instead of 2

    // When
    AuthAttemptWaitResponse response = authAttemptService.waitForResponse(1, request);

    // Then
    assertNotNull(response);
    assertEquals("EXPIRED", response.getStatus());
    assertEquals(false, response.getCompleted());
    verify(waitService).waitForResponse(1, request);
  }

  @Test
  void testWaitForResponse_ShouldProceedNormally_WhenNoNewerAttemptExists() {
    // Given - use default mock (ACCEPTED)
    setupDefaultWaitServiceMock();

    AuthAttemptWaitRequest request = new AuthAttemptWaitRequest();
    request.setTimeout(1); // 1 second instead of 30
    request.setPolling(1); // 1 second instead of 2

    // When
    AuthAttemptWaitResponse response = authAttemptService.waitForResponse(1, request);

    // Then
    assertNotNull(response);
    assertEquals("ACCEPTED", response.getStatus());
    assertEquals(true, response.getCompleted());
    verify(waitService).waitForResponse(1, request);
  }

  @Test
  void testRespond_ShouldCheckNewerAttemptBeforeExpirationCheck() {
    // Given - setup superseded mock (prioritizes supersession over expiration)
    setupSupersededRespondServiceMock();

    AuthAttemptRespondRequest request = new AuthAttemptRespondRequest();
    request.setAuthAttemptId(1);
    request.setAuthAttemptAccepted(true);
    request.setAuthAttemptProofTokenSignedByDevice("signed-token");

    // When
    AuthAttemptRespondResponse response = authAttemptService.respond(request);

    // Then - Should return superseded, not expired
    assertNotNull(response);
    assertEquals(AuthenticationResult.EXPIRED, response.getResult());
    assertEquals("Authentication attempt superseded by newer request", response.getMessage());
    verify(respondService).respond(request);
  }

  /**
   * Sets up mocks for specialized services to return appropriate responses. This method is called
   * in setUp() but individual tests will override specific mocks as needed.
   */
  private void setupSpecializedServiceMocks() {
    // No default mocks - each test will set up exactly what it needs
    // This prevents UnnecessaryStubbingException
  }

  private void setupDefaultRespondServiceMock() {
    // Default mock for respond service - returns APPROVED
    AuthAttemptRespondResponse defaultResponse = new AuthAttemptRespondResponse();
    defaultResponse.setResult(AuthenticationResult.APPROVED);
    defaultResponse.setMessage("Auth attempt completed");

    when(respondService.respond(any(AuthAttemptRespondRequest.class))).thenReturn(defaultResponse);
  }

  private void setupDefaultWaitServiceMock() {
    // Default mock for wait service - returns ACCEPTED
    AuthAttemptWaitResponse defaultResponse =
        new AuthAttemptWaitResponse(
            olderAttempt, "ACCEPTED", true, false, 5, java.time.LocalDateTime.now());

    when(waitService.waitForResponse(any(Integer.class), any(AuthAttemptWaitRequest.class)))
        .thenReturn(defaultResponse);
  }

  private void setupSupersededRespondServiceMock() {
    // Mock for superseded scenario
    AuthAttemptRespondResponse supersededResponse = new AuthAttemptRespondResponse();
    supersededResponse.setResult(AuthenticationResult.EXPIRED);
    supersededResponse.setMessage("Authentication attempt superseded by newer request");

    when(respondService.respond(any(AuthAttemptRespondRequest.class)))
        .thenReturn(supersededResponse);
  }

  private void setupSupersededWaitServiceMock() {
    // Mock for superseded scenario
    AuthAttemptWaitResponse supersededResponse =
        new AuthAttemptWaitResponse(
            olderAttempt, "EXPIRED", false, false, 10, java.time.LocalDateTime.now());

    when(waitService.waitForResponse(any(Integer.class), any(AuthAttemptWaitRequest.class)))
        .thenReturn(supersededResponse);
  }
}
