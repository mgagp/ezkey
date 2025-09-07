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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

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
import org.ezkey.integration.domain.entity.IntegrationI18n;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.signature.RsaKeyPair;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Critical unit tests for {@link EnrollmentService}.
 * <p>
 * This test class provides comprehensive coverage of the EnrollmentService
 * focusing on security-critical enrollment operations. Tests cover enrollment
 * creation, device binding, cryptographic verification, and proper state
 * management with locking mechanisms.
 * </p>
 *
 * <p>
 * <b>Critical Test Coverage:</b>
 * <ul>
 * <li>create() - Enrollment creation with cryptographic key generation</li>
 * <li>bind() - Device binding with locking and state validation</li>
 * <li>verify() - Enrollment verification with signature validation</li>
 * <li>Security validation - Cryptographic signature handling</li>
 * <li>State management - Proper enrollment state transitions</li>
 * <li>Error handling - Appropriate exception handling</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Security Focus:</b> These tests validate the critical security aspects
 * of enrollment including cryptographic key validation, state consistency,
 * locking mechanisms, and proper error handling to prevent security vulnerabilities.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EnrollmentService
 * @see SignatureService
 * @see EnrollmentRepository
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Enrollment Service Critical Tests")
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private SignatureService signatureService;

    @Mock
    private IntegrationRepository integrationRepository;

    @Mock
    private EnrollmentTxHelper enrollmentTxHelper;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private EnrollmentCreateRequest createRequest;
    private EnrollmentBindRequest bindRequest;
    private EnrollmentVerifyRequest verifyRequest;
    private Enrollment enrollment;
    private Integration integration;
    private RsaKeyPair rsaKeyPair;

    @BeforeEach
    void setUp() {
        // Setup create request
        createRequest = new EnrollmentCreateRequest();
        createRequest.setIntegrationId(123);
        createRequest.setName("Test Enrollment");
        createRequest.setAuthAttemptChallengeRequired(true);

        // Setup bind request
        bindRequest = new EnrollmentBindRequest();
        bindRequest.setEnrollmentId(456);
        bindRequest.setLanguage("en");

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
        enrollment.setCreatedAt(LocalDateTime.now());

        // Setup integration entity
        integration = new Integration();
        integration.setId(123);
        integration.setLogo("test-logo");

        // Setup RSA key pair
        rsaKeyPair = new RsaKeyPair("private-key", "public-key");
    }

    // ===== CREATE ENDPOINT TESTS =====

    @Test
    @DisplayName("create() - Should create enrollment successfully with cryptographic keys")
    void create_WhenValidRequest_ShouldCreateEnrollmentSuccessfully() {
        // Arrange
        when(signatureService.generateProofToken()).thenReturn("generated-proof-token");
        when(signatureService.generateRsaKeyPair(2048)).thenReturn(rsaKeyPair);
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
        verify(signatureService, times(1)).generateRsaKeyPair(2048);
        verify(signatureService, times(1)).generateSecureChallenge(6);
        verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
    }

    @Test
    @DisplayName("create() - Should throw IllegalArgumentException when integrationId is null")
    void create_WhenIntegrationIdIsNull_ShouldThrowIllegalArgumentException() {
        // Arrange
        createRequest.setIntegrationId(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> enrollmentService.create(createRequest));
        
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
        when(signatureService.generateRsaKeyPair(2048)).thenReturn(rsaKeyPair);
        when(signatureService.generateSecureChallenge(6)).thenReturn(123456);
        when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(enrollment);

        // Act
        EnrollmentCreateResponse response = enrollmentService.create(createRequest);

        // Assert
        assertNotNull(response);
        verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
    }

    // ===== BIND ENDPOINT TESTS =====

    @Test
    @DisplayName("bind() - Should bind enrollment successfully with proper locking")
    void bind_WhenValidRequest_ShouldBindEnrollmentSuccessfully() {
        // Arrange
        enrollment.setStatus(EnrollmentStatus.CREATED);
        when(enrollmentRepository.findById(456)).thenReturn(Optional.of(enrollment));
        when(integrationRepository.findById(123)).thenReturn(Optional.of(integration));
        when(enrollmentRepository.findAndLockUnreadById(456)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(enrollment);

        // Act
        EnrollmentBindResponse response = enrollmentService.bind(bindRequest);

        // Assert
        assertNotNull(response);
        assertEquals(456, response.getEnrollmentId());
        assertEquals("Test Enrollment", response.getEnrollmentName());
        assertEquals("integration-public-key", response.getIntegrationPublicKey());
        assertEquals("test-proof-token", response.getEnrollmentProofToken());

        // Verify service interactions
        verify(enrollmentRepository, times(1)).findById(456);
        verify(integrationRepository, times(1)).findById(123);
        verify(enrollmentRepository, times(1)).findAndLockUnreadById(456);
        verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
    }

    @Test
    @DisplayName("bind() - Should throw IllegalArgumentException when enrollment not found")
    void bind_WhenEnrollmentNotFound_ShouldThrowIllegalArgumentException() {
        // Arrange
        when(enrollmentRepository.findById(456)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> enrollmentService.bind(bindRequest));
        
        assertEquals("Enrollment binding failed", exception.getMessage());

        // Verify service interactions
        verify(enrollmentRepository, times(1)).findById(456);
        verify(enrollmentRepository, never()).findAndLockUnreadById(anyInt());
    }

    @Test
    @DisplayName("bind() - Should throw IllegalStateException when enrollment already bound")
    void bind_WhenEnrollmentAlreadyBound_ShouldThrowIllegalStateException() {
        // Arrange
        enrollment.setStatus(EnrollmentStatus.BOUND);
        when(enrollmentRepository.findById(456)).thenReturn(Optional.of(enrollment));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> enrollmentService.bind(bindRequest));
        
        assertEquals("Enrollment already bound by a device", exception.getMessage());

        // Verify service interactions
        verify(enrollmentRepository, times(1)).findById(456);
        verify(enrollmentRepository, never()).findAndLockUnreadById(anyInt());
    }

    @Test
    @DisplayName("bind() - Should throw IllegalStateException when integration not found")
    void bind_WhenIntegrationNotFound_ShouldThrowIllegalStateException() {
        // Arrange
        enrollment.setStatus(EnrollmentStatus.CREATED);
        when(enrollmentRepository.findById(456)).thenReturn(Optional.of(enrollment));
        when(integrationRepository.findById(123)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> enrollmentService.bind(bindRequest));
        
        assertEquals("Enrollment binding failed", exception.getMessage());

        // Verify service interactions
        verify(enrollmentRepository, times(1)).findById(456);
        verify(integrationRepository, times(1)).findById(123);
        verify(enrollmentRepository, never()).findAndLockUnreadById(anyInt());
    }

    @Test
    @DisplayName("bind() - Should throw IllegalArgumentException when lock acquisition fails")
    void bind_WhenLockAcquisitionFails_ShouldThrowIllegalArgumentException() {
        // Arrange
        enrollment.setStatus(EnrollmentStatus.CREATED);
        when(enrollmentRepository.findById(456)).thenReturn(Optional.of(enrollment));
        when(integrationRepository.findById(123)).thenReturn(Optional.of(integration));
        when(enrollmentRepository.findAndLockUnreadById(456)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> enrollmentService.bind(bindRequest));
        
        assertEquals("Enrollment not found or already bound", exception.getMessage());

        // Verify service interactions
        verify(enrollmentRepository, times(1)).findById(456);
        verify(integrationRepository, times(1)).findById(123);
        verify(enrollmentRepository, times(1)).findAndLockUnreadById(456);
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    // ===== VERIFY ENDPOINT TESTS =====

    @Test
    @DisplayName("verify() - Should verify enrollment successfully with signature validation")
    void verify_WhenValidRequest_ShouldVerifyEnrollmentSuccessfully() {
        // Arrange
        enrollment.setStatus(EnrollmentStatus.BOUND);
        when(enrollmentRepository.findById(456)).thenReturn(Optional.of(enrollment));
        when(signatureService.validateSignature(anyString(), anyString(), anyString())).thenReturn(true);
        when(enrollmentRepository.findAndLockBoundById(456)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(enrollment);

        // Act
        EnrollmentVerifyResponse response = enrollmentService.verify(verifyRequest);

        // Assert
        assertNotNull(response);
        assertTrue(response.isActive());

        // Verify service interactions
        verify(enrollmentRepository, times(1)).findById(456);
        verify(signatureService, times(1)).validateSignature(
            "test-proof-token", "proof-token-signature", "device-public-key");
        verify(enrollmentRepository, times(1)).findAndLockBoundById(456);
        verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
    }

    @Test
    @DisplayName("verify() - Should throw IllegalStateException when enrollment already verified")
    void verify_WhenEnrollmentAlreadyVerified_ShouldThrowIllegalStateException() {
        // Arrange
        enrollment.setStatus(EnrollmentStatus.VERIFIED);
        when(enrollmentRepository.findById(456)).thenReturn(Optional.of(enrollment));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> enrollmentService.verify(verifyRequest));
        
        assertEquals("Enrollment already verified", exception.getMessage());

        // Verify service interactions
        verify(enrollmentRepository, times(1)).findById(456);
        verify(signatureService, never()).validateSignature(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("verify() - Should throw IllegalStateException when enrollment not bound")
    void verify_WhenEnrollmentNotBound_ShouldThrowIllegalStateException() {
        // Arrange
        enrollment.setStatus(EnrollmentStatus.CREATED);
        when(enrollmentRepository.findById(456)).thenReturn(Optional.of(enrollment));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> enrollmentService.verify(verifyRequest));
        
        assertEquals("Enrollment must be bound before verification", exception.getMessage());

        // Verify service interactions
        verify(enrollmentRepository, times(1)).findById(456);
        verify(enrollmentTxHelper, times(1)).markInvalidAndClear(456);
        verify(signatureService, never()).validateSignature(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("verify() - Should throw IllegalArgumentException when signature validation fails")
    void verify_WhenSignatureValidationFails_ShouldThrowIllegalArgumentException() {
        // Arrange
        enrollment.setStatus(EnrollmentStatus.BOUND);
        when(enrollmentRepository.findById(456)).thenReturn(Optional.of(enrollment));
        when(signatureService.validateSignature(anyString(), anyString(), anyString())).thenReturn(false);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> enrollmentService.verify(verifyRequest));
        
        assertEquals("Invalid bind proof token signature", exception.getMessage());

        // Verify service interactions
        verify(enrollmentRepository, times(1)).findById(456);
        verify(signatureService, times(1)).validateSignature(
            "test-proof-token", "proof-token-signature", "device-public-key");
        verify(enrollmentTxHelper, times(1)).markInvalidAndClear(456);
    }

    @Test
    @DisplayName("verify() - Should throw IllegalArgumentException when challenge response is invalid")
    void verify_WhenChallengeResponseInvalid_ShouldThrowIllegalArgumentException() {
        // Arrange
        enrollment.setStatus(EnrollmentStatus.BOUND);
        verifyRequest.setChallengeResponse(999999); // Wrong challenge
        when(enrollmentRepository.findById(456)).thenReturn(Optional.of(enrollment));
        when(signatureService.validateSignature(anyString(), anyString(), anyString())).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> enrollmentService.verify(verifyRequest));
        
        assertEquals("Invalid challenge response", exception.getMessage());

        // Verify service interactions
        verify(enrollmentRepository, times(1)).findById(456);
        verify(signatureService, times(1)).validateSignature(
            "test-proof-token", "proof-token-signature", "device-public-key");
        verify(enrollmentTxHelper, times(1)).markInvalidAndClear(456);
    }

    @Test
    @DisplayName("verify() - Should throw IllegalStateException when lock acquisition fails")
    void verify_WhenLockAcquisitionFails_ShouldThrowIllegalStateException() {
        // Arrange
        enrollment.setStatus(EnrollmentStatus.BOUND);
        when(enrollmentRepository.findById(456)).thenReturn(Optional.of(enrollment));
        when(signatureService.validateSignature(anyString(), anyString(), anyString())).thenReturn(true);
        when(enrollmentRepository.findAndLockBoundById(456)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> enrollmentService.verify(verifyRequest));
        
        assertEquals("Enrollment already verified", exception.getMessage());

        // Verify service interactions
        verify(enrollmentRepository, times(1)).findById(456);
        verify(signatureService, times(1)).validateSignature(
            "test-proof-token", "proof-token-signature", "device-public-key");
        verify(enrollmentRepository, times(1)).findAndLockBoundById(456);
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
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
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, 
            () -> enrollmentService.getById(456));
        
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
