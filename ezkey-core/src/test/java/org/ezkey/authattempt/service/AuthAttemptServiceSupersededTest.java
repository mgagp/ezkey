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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
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

import jakarta.persistence.EntityManager;

/**
 * Test class for authentication attempt superseded logic.
 * <p>
 * Tests the business rule that when a newer authentication attempt is created
 * for the same enrollment, older attempts should be considered expired.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
class AuthAttemptServiceSupersededTest {

    @Mock
    private AuthAttemptRepository authAttemptRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private SignatureService signatureService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AuthAttemptService authAttemptService;

    private AuthAttempt olderAttempt;
    private AuthAttempt newerAttempt;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        // Configure EntityManager using reflection
        try {
            java.lang.reflect.Field entityManagerField = AuthAttemptService.class.getDeclaredField("entityManager");
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

        // Create older authentication attempt
        olderAttempt = new AuthAttempt();
        olderAttempt.setAuthAttemptId(1);
        olderAttempt.setEnrollmentId(1);
        olderAttempt.setAuthAttemptRead(true);
        olderAttempt.setAuthAttemptResponded(false);
        olderAttempt.setAuthAttemptValid(false);
        olderAttempt.setAuthAttemptAccepted(false);
        olderAttempt.setAuthAttemptProofToken("older-proof-token");
        olderAttempt.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        olderAttempt.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        // Create newer authentication attempt
        newerAttempt = new AuthAttempt();
        newerAttempt.setAuthAttemptId(2);
        newerAttempt.setEnrollmentId(1);
        newerAttempt.setAuthAttemptRead(false);
        newerAttempt.setAuthAttemptResponded(false);
        newerAttempt.setAuthAttemptValid(false);
        newerAttempt.setAuthAttemptAccepted(false);
        newerAttempt.setAuthAttemptProofToken("newer-proof-token");
        newerAttempt.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        newerAttempt.setExpiresAt(LocalDateTime.now().plusMinutes(10));
    }

    @Test
    void testRespond_ShouldReturnExpired_WhenNewerAttemptExists() {
        // Given
        when(authAttemptRepository.findById(1)).thenReturn(Optional.of(olderAttempt));
        when(authAttemptRepository.findNewerAttemptByEnrollmentId(1, olderAttempt.getCreatedAt()))
            .thenReturn(Optional.of(newerAttempt));

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
    }

    @Test
    void testRespond_ShouldProceedNormally_WhenNoNewerAttemptExists() {
        // Given
        when(authAttemptRepository.findById(1)).thenReturn(Optional.of(olderAttempt));
        when(authAttemptRepository.findNewerAttemptByEnrollmentId(1, olderAttempt.getCreatedAt()))
            .thenReturn(Optional.empty());
        when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
        when(signatureService.validateSignature(anyString(), anyString(), anyString()))
            .thenReturn(true);

        AuthAttemptRespondRequest request = new AuthAttemptRespondRequest();
        request.setAuthAttemptId(1);
        request.setAuthAttemptAccepted(true);
        request.setAuthAttemptProofTokenSignedByDevice("signed-token");

        // When
        AuthAttemptRespondResponse response = authAttemptService.respond(request);

        // Then
        assertNotNull(response);
        assertEquals(AuthenticationResult.APPROVED, response.getResult());
    }

    @Test
    void testWaitForResponse_ShouldReturnExpired_WhenNewerAttemptExists() {
        // Given
        when(authAttemptRepository.findById(1)).thenReturn(Optional.of(olderAttempt));
        when(authAttemptRepository.findNewerAttemptByEnrollmentId(1, olderAttempt.getCreatedAt()))
            .thenReturn(Optional.of(newerAttempt));

        AuthAttemptWaitRequest request = new AuthAttemptWaitRequest();
        request.setTimeout(30);
        request.setPolling(2);

        // When
        AuthAttemptWaitResponse response = authAttemptService.waitForResponse(1, request);

        // Then
        assertNotNull(response);
        assertEquals("EXPIRED", response.getStatus());
        assertEquals(false, response.getCompleted());
    }

    @Test
    void testWaitForResponse_ShouldProceedNormally_WhenNoNewerAttemptExists() {
        // Given
        when(authAttemptRepository.findById(1)).thenReturn(Optional.of(olderAttempt));
        when(authAttemptRepository.findNewerAttemptByEnrollmentId(1, olderAttempt.getCreatedAt()))
            .thenReturn(Optional.empty());

        AuthAttemptWaitRequest request = new AuthAttemptWaitRequest();
        request.setTimeout(30);
        request.setPolling(2);

        // When
        AuthAttemptWaitResponse response = authAttemptService.waitForResponse(1, request);

        // Then
        assertNotNull(response);
        assertEquals("READ", response.getStatus());
        assertEquals(false, response.getCompleted());
    }

    @Test
    void testRespond_ShouldCheckNewerAttemptBeforeExpirationCheck() {
        // Given - Create an expired attempt but with a newer attempt
        olderAttempt.setExpiresAt(LocalDateTime.now().minusMinutes(1)); // Expired
        
        when(authAttemptRepository.findById(1)).thenReturn(Optional.of(olderAttempt));
        when(authAttemptRepository.findNewerAttemptByEnrollmentId(1, olderAttempt.getCreatedAt()))
            .thenReturn(Optional.of(newerAttempt));

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
    }
}
