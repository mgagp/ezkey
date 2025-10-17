/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptServiceIntegrationTest
 * Description: Integration tests for authentication attempt superseded logic.
 */

package org.ezkey.authattempt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test class for authentication attempt superseded logic.
 *
 * <p>Tests the complete flow where a newer authentication attempt makes older attempts conceptually
 * expired.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthAttemptServiceIntegrationTest {

  @Autowired private AuthAttemptService authAttemptService;

  @Autowired private AuthAttemptRepository authAttemptRepository;

  @Autowired private EnrollmentRepository enrollmentRepository;

  @Autowired private SignatureService signatureService;

  private Enrollment testEnrollment;

  @BeforeEach
  void setUp() {
    // Create test enrollment
    testEnrollment = new Enrollment();
    testEnrollment.setIntegrationId(1);
    testEnrollment.setEnrollmentName("test-user");
    testEnrollment.setStatus(org.ezkey.enrollment.domain.EnrollmentStatus.CREATED);
    testEnrollment.setActive(false);
    // testEnrollment.setEnrollmentRead(true);
    // testEnrollment.setEnrollmentVerified(true);
    // testEnrollment.setEnrollmentValid(true);
    // testEnrollment.setEnrollmentActive(true);
    // Generate unique proof token to avoid constraint violations
    testEnrollment.setEnrollmentProofToken(
        "test-proof-token-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId());
    testEnrollment.setAuthAttemptChallengeRequired(false);
    testEnrollment.setIntegrationPrivateKey("test-private-key");
    testEnrollment.setIntegrationPublicKey("test-public-key");
    testEnrollment.setDevicePublicKey("test-device-public-key");
    testEnrollment.setCreatedAt(OffsetDateTime.now());

    testEnrollment = enrollmentRepository.save(testEnrollment);
  }

  @Test
  void testCompleteFlow_NewerAttemptSupersedesOlder() {
    // Step 1: Create first authentication attempt
    AuthAttemptCreateRequest createRequest1 = new AuthAttemptCreateRequest();
    createRequest1.setEnrollmentId(testEnrollment.getEnrollmentId());
    createRequest1.setChallengeRequested(false);

    AuthAttemptCreateResponse createResponse1 = authAttemptService.create(createRequest1);
    assertNotNull(createResponse1);
    Integer firstAttemptId = createResponse1.getAuthAttemptId();

    // Step 2: Create second authentication attempt (newer)
    AuthAttemptCreateRequest createRequest2 = new AuthAttemptCreateRequest();
    createRequest2.setEnrollmentId(testEnrollment.getEnrollmentId());
    createRequest2.setChallengeRequested(false);

    AuthAttemptCreateResponse createResponse2 = authAttemptService.create(createRequest2);
    assertNotNull(createResponse2);
    Integer secondAttemptId = createResponse2.getAuthAttemptId();

    // Verify that we have two attempts for this enrollment
    assertEquals(
        2, authAttemptRepository.findAllByEnrollmentId(testEnrollment.getEnrollmentId()).size());

    // Step 3: Try to respond to the first (older) attempt
    AuthAttemptRespondRequest respondRequest = new AuthAttemptRespondRequest();
    respondRequest.setAuthAttemptId(firstAttemptId);
    respondRequest.setAuthAttemptAccepted(true);
    respondRequest.setAuthAttemptProofTokenSignedByDevice("signed-token");

    AuthAttemptRespondResponse respondResponse = authAttemptService.respond(respondRequest);
    assertNotNull(respondResponse);
    // Since the attempt was never READ by device, respond() returns FAILED (not read)
    assertEquals(AuthenticationResult.FAILED, respondResponse.getResult());
    assertEquals("Auth attempt not read by device", respondResponse.getMessage());

    // Step 4: Try to wait for the first (older) attempt
    AuthAttemptWaitRequest waitRequest = new AuthAttemptWaitRequest();
    waitRequest.setTimeout(5);
    waitRequest.setPolling(1);

    AuthAttemptWaitResponse waitResponse =
        authAttemptService.waitForResponse(firstAttemptId, waitRequest);
    assertNotNull(waitResponse);
    // A newer attempt exists -> superseded returns EXPIRED in wait
    assertEquals("EXPIRED", waitResponse.getStatus());
    assertEquals(false, waitResponse.getCompleted());

    // Step 5: Verify that the second attempt is still valid
    Optional<AuthAttempt> secondAttempt = authAttemptRepository.findById(secondAttemptId);
    assertNotNull(secondAttempt.orElse(null));
    assertEquals(AuthAttemptStatus.PENDING, secondAttempt.get().getAuthAttemptStatus());
  }

  @Test
  void testNoSuperseding_WhenNoNewerAttempt() {
    // Create single authentication attempt
    AuthAttemptCreateRequest createRequest = new AuthAttemptCreateRequest();
    createRequest.setEnrollmentId(testEnrollment.getEnrollmentId());
    createRequest.setChallengeRequested(false);

    AuthAttemptCreateResponse createResponse = authAttemptService.create(createRequest);
    assertNotNull(createResponse);
    Integer attemptId = createResponse.getAuthAttemptId();

    // Try to wait for the attempt (should not be superseded)
    AuthAttemptWaitRequest waitRequest = new AuthAttemptWaitRequest();
    waitRequest.setTimeout(5);
    waitRequest.setPolling(1);

    AuthAttemptWaitResponse waitResponse =
        authAttemptService.waitForResponse(attemptId, waitRequest);
    assertNotNull(waitResponse);
    assertEquals("PENDING", waitResponse.getStatus());
    assertEquals(false, waitResponse.getCompleted());
  }
}
