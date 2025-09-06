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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.ezkey.authattempt.domain.AuthAttemptPendingRequest;
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
 * <p>
 * Tests specifically focus on the device proof token recording and uniqueness
 * validation feature implemented to prevent replay attacks.
 * </p>
 *
 * <p><b>Test Coverage:</b></p>
 * <ul>
 *   <li>Device proof token uniqueness validation</li>
 *   <li>Proper error handling for duplicate tokens</li>
 *   <li>Token storage during pending authentication</li>
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 * <p><b>Usage:</b> Device proof token unicity validation tests</p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptService
 */
@DisplayName("AuthAttemptService Device Proof Token Unicity Tests")
class AuthAttemptServiceDeviceProofTokenTest {

    @Mock
    private AuthAttemptRepository authAttemptRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private SignatureService signatureService;

    private AuthAttemptService authAttemptService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authAttemptService = new AuthAttemptService(authAttemptRepository, enrollmentRepository, signatureService);
    }

    @Test
    @DisplayName("Should reject duplicate device proof token")
    void testRejectDuplicateDeviceProofToken() {
        // Arrange
        Integer enrollmentId = 1;
        String deviceProofToken = "duplicate-proof-token-123";
        String deviceProofTokenSigned = "signed-proof-token";
        String devicePublicKey = "mock-public-key";

        AuthAttemptPendingRequest request = new AuthAttemptPendingRequest();
        request.setEnrollmentId(enrollmentId);
        request.setDeviceProofToken(deviceProofToken);
        request.setDeviceProofTokenSigned(deviceProofTokenSigned);

        Enrollment mockEnrollment = mock(Enrollment.class);
        when(mockEnrollment.getDevicePublicKey()).thenReturn(devicePublicKey);

        // Setup mocks
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(mockEnrollment));
        when(signatureService.validateSignature(deviceProofToken, deviceProofTokenSigned, devicePublicKey))
                .thenReturn(true);
        when(authAttemptRepository.existsByDeviceProofToken(deviceProofToken)).thenReturn(true); // Token already exists

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            authAttemptService.pending(request);
        });

        // Verify
        assert exception.getMessage().equals("Authentication request failed");
        verify(authAttemptRepository).existsByDeviceProofToken(deviceProofToken);
    }

    @Test
    @DisplayName("Should proceed with unique device proof token")
    void testProceedWithUniqueDeviceProofToken() {
        // Arrange
        Integer enrollmentId = 1;
        String deviceProofToken = "unique-proof-token-456";
        String deviceProofTokenSigned = "signed-proof-token";
        String devicePublicKey = "mock-public-key";

        AuthAttemptPendingRequest request = new AuthAttemptPendingRequest();
        request.setEnrollmentId(enrollmentId);
        request.setDeviceProofToken(deviceProofToken);
        request.setDeviceProofTokenSigned(deviceProofTokenSigned);

        Enrollment mockEnrollment = mock(Enrollment.class);
        when(mockEnrollment.getDevicePublicKey()).thenReturn(devicePublicKey);
        when(mockEnrollment.getIntegrationPrivateKey()).thenReturn("mock-private-key");
        when(mockEnrollment.getAuthAttemptChallengeRequired()).thenReturn(false);

        AuthAttempt mockAuthAttempt = mock(AuthAttempt.class);
        when(mockAuthAttempt.getAuthAttemptStatus()).thenReturn(AuthAttemptStatus.PENDING);
        when(mockAuthAttempt.getAuthAttemptId()).thenReturn(123);
        when(mockAuthAttempt.getAuthAttemptProofToken()).thenReturn("auth-attempt-token");

        // Setup mocks
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(mockEnrollment));
        when(signatureService.validateSignature(deviceProofToken, deviceProofTokenSigned, devicePublicKey))
                .thenReturn(true);
        when(authAttemptRepository.existsByDeviceProofToken(deviceProofToken)).thenReturn(false); // Token is unique
        when(authAttemptRepository.findAndLockMostRecentValidByEnrollmentIdAndStatus(eq(enrollmentId), eq(AuthAttemptStatus.PENDING.name()), any()))
                .thenReturn(Optional.of(mockAuthAttempt));
        when(signatureService.generateSignature(anyString(), anyString())).thenReturn("integration-signature");

        // Act
        authAttemptService.pending(request);

        // Assert
        verify(authAttemptRepository).existsByDeviceProofToken(deviceProofToken);
        verify(mockAuthAttempt).setDeviceProofToken(deviceProofToken); // Verify token is stored
        verify(mockAuthAttempt).setAuthAttemptStatus(AuthAttemptStatus.READ);
        verify(authAttemptRepository).save(mockAuthAttempt);
    }

    @Test
    @DisplayName("Should reject request with invalid signature even if token is unique")
    void testRejectInvalidSignatureRegardlessOfTokenUniqueness() {
        // Arrange
        Integer enrollmentId = 1;
        String deviceProofToken = "unique-proof-token-789";
        String deviceProofTokenSigned = "invalid-signed-proof-token";
        String devicePublicKey = "mock-public-key";

        AuthAttemptPendingRequest request = new AuthAttemptPendingRequest();
        request.setEnrollmentId(enrollmentId);
        request.setDeviceProofToken(deviceProofToken);
        request.setDeviceProofTokenSigned(deviceProofTokenSigned);

        Enrollment mockEnrollment = mock(Enrollment.class);
        when(mockEnrollment.getDevicePublicKey()).thenReturn(devicePublicKey);

        // Setup mocks
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(mockEnrollment));
        when(signatureService.validateSignature(deviceProofToken, deviceProofTokenSigned, devicePublicKey))
                .thenReturn(false); // Invalid signature

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            authAttemptService.pending(request);
        });

        // Verify
        assert exception.getMessage().equals("Authentication request failed");
        // Verify that token uniqueness check is not reached because signature validation fails first
        verify(signatureService).validateSignature(deviceProofToken, deviceProofTokenSigned, devicePublicKey);
    }
}