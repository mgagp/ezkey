/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptServiceChallengeTest
 * Description: Tests for configurable challenge digits functionality in AuthAttemptService.
 */

package org.ezkey.authattempt.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthAttemptService Challenge Configuration Tests")
class AuthAttemptServiceChallengeTest {

    @Mock
    private AuthAttemptRepository authAttemptRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private SignatureService signatureService;

    private AuthAttemptService authAttemptService;

    @BeforeEach
    void setUp() {
        authAttemptService = new AuthAttemptService(authAttemptRepository, enrollmentRepository, signatureService);
    }

    @Test
    @DisplayName("Should generate 2-digit challenge by default")
    void testDefaultTwoDigitChallenge() {
        // Arrange
        ReflectionTestUtils.setField(authAttemptService, "challengeDigits", 2);
        setupBasicMocks(true);
        when(signatureService.generateSecureChallenge(2)).thenReturn(42);

        // Act
        AuthAttemptCreateResponse response = authAttemptService.create(createChallengeRequest());

        // Assert
        verify(authAttemptRepository).save(any(AuthAttempt.class));
        // The challenge should be between 10 and 99 (2 digits)
        // We'll verify this by capturing the saved AuthAttempt
        verify(authAttemptRepository).save(argThat(authAttempt -> {
            Integer challenge = authAttempt.getAuthAttemptChallenge();
            return challenge != null && challenge.equals(42); // Mocked value for 2 digits
        }));
    }

    @Test
    @DisplayName("Should generate 1-digit challenge when configured")
    void testOneDigitChallenge() {
        // Arrange
        ReflectionTestUtils.setField(authAttemptService, "challengeDigits", 1);
        setupBasicMocks(true);
        when(signatureService.generateSecureChallenge(1)).thenReturn(5);

        // Act
        AuthAttemptCreateResponse response = authAttemptService.create(createChallengeRequest());

        // Assert
        verify(authAttemptRepository).save(argThat(authAttempt -> {
            Integer challenge = authAttempt.getAuthAttemptChallenge();
            return challenge != null && challenge.equals(5); // Mocked value for 1 digit
        }));
    }

    @Test
    @DisplayName("Should generate 6-digit challenge when configured")
    void testSixDigitChallenge() {
        // Arrange
        ReflectionTestUtils.setField(authAttemptService, "challengeDigits", 6);
        setupBasicMocks(true);
        when(signatureService.generateSecureChallenge(6)).thenReturn(123456);

        // Act
        AuthAttemptCreateResponse response = authAttemptService.create(createChallengeRequest());

        // Assert
        verify(authAttemptRepository).save(argThat(authAttempt -> {
            Integer challenge = authAttempt.getAuthAttemptChallenge();
            return challenge != null && challenge.equals(123456); // Mocked value for 6 digits
        }));
    }

    @Test
    @DisplayName("Should truncate to 6 digits when configured with higher value")
    void testTruncationToSixDigits() {
        // Arrange
        ReflectionTestUtils.setField(authAttemptService, "challengeDigits", 8);
        setupBasicMocks(true);
        when(signatureService.generateSecureChallenge(6)).thenReturn(123456); // Should be truncated to 6

        // Act
        AuthAttemptCreateResponse response = authAttemptService.create(createChallengeRequest());

        // Assert - should generate 6-digit challenge despite being configured for 8
        verify(authAttemptRepository).save(argThat(authAttempt -> {
            Integer challenge = authAttempt.getAuthAttemptChallenge();
            return challenge != null && challenge.equals(123456); // Mocked value for 6 digits (truncated from 8)
        }));
    }

    @Test
    @DisplayName("Should not generate challenge when not required")
    void testNoChallengeGeneration() {
        // Arrange
        ReflectionTestUtils.setField(authAttemptService, "challengeDigits", 2);
        setupBasicMocks(false);

        // Act
        AuthAttemptCreateResponse response = authAttemptService.create(createNoChallengeRequest());

        // Assert
        verify(authAttemptRepository).save(argThat(authAttempt -> 
            authAttempt.getAuthAttemptChallenge() == null
        ));
    }

    private void setupBasicMocks(boolean challengeRequired) {
        Enrollment enrollment = new Enrollment();
        enrollment.setEnrollmentId(1);
        enrollment.setAuthAttemptChallengeRequired(challengeRequired);
        
        when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
        when(signatureService.generateProofToken()).thenReturn("test-proof-token");
        
        AuthAttempt savedAuthAttempt = new AuthAttempt();
        savedAuthAttempt.setAuthAttemptId(1);
        when(authAttemptRepository.save(any(AuthAttempt.class))).thenReturn(savedAuthAttempt);
    }

    private AuthAttemptCreateRequest createChallengeRequest() {
        AuthAttemptCreateRequest request = new AuthAttemptCreateRequest();
        request.setEnrollmentId(1);
        request.setChallengeRequested(false); // Rely on enrollment setting
        return request;
    }

    private AuthAttemptCreateRequest createNoChallengeRequest() {
        AuthAttemptCreateRequest request = new AuthAttemptCreateRequest();
        request.setEnrollmentId(1);
        request.setChallengeRequested(false);
        return request;
    }
}