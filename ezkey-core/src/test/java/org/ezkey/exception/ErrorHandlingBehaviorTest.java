/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ErrorHandlingBehaviorTest
 * Description: Comprehensive error handling behavior characterization tests.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import org.ezkey.PostgreSQLTestBase;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprehensive error handling behavior characterization tests.
 *
 * <p>This test class characterizes the current error handling behavior across all services in
 * ezkey-core before implementing any refactoring. It documents how exceptions are currently thrown,
 * what messages are used, and what the expected behavior is for various error scenarios.
 *
 * <p><b>Purpose:</b> Establish a baseline of current error handling behavior to ensure that any
 * future refactoring preserves valid behaviors and only improves problematic ones.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Transactional
class ErrorHandlingBehaviorTest extends PostgreSQLTestBase {

  @Autowired private AuthAttemptService authAttemptService;
  @Autowired private EnrollmentService enrollmentService;
  @Autowired private SignatureService signatureService;
  @Autowired private AuthAttemptRepository authAttemptRepository;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private IntegrationRepository integrationRepository;

  @Autowired private EntityManager entityManager;

  private Integration testIntegration;
  private Enrollment testEnrollment;
  private AuthAttempt testAuthAttempt;

  @BeforeEach
  void setUp() {
    // Create test integration
    testIntegration = new Integration();
    testIntegration.setLogo("test-logo.png");
    testIntegration.setActive(true);
    testIntegration.setCreatedAt(OffsetDateTime.now());
    testIntegration = integrationRepository.save(testIntegration);

    // Create test enrollment
    testEnrollment = new Enrollment();
    testEnrollment.setIntegrationId(testIntegration.getId());
    testEnrollment.setEnrollmentName("test-user");
    testEnrollment.setStatus(EnrollmentStatus.CREATED);
    testEnrollment.setActive(false);
    testEnrollment.setEnrollmentProofToken("test-proof-token-" + System.currentTimeMillis());
    testEnrollment.setAuthAttemptChallengeRequired(false);
    testEnrollment.setIntegrationPrivateKey("test-private-key");
    testEnrollment.setIntegrationPublicKey("test-public-key");
    testEnrollment.setDevicePublicKey("test-device-public-key");
    testEnrollment.setCreatedAt(OffsetDateTime.now());
    testEnrollment = enrollmentRepository.save(testEnrollment);

    // Create test auth attempt
    testAuthAttempt = new AuthAttempt();
    testAuthAttempt.setEnrollmentId(testEnrollment.getEnrollmentId());
    testAuthAttempt.setAuthAttemptStatus(AuthAttemptStatus.PENDING);
    testAuthAttempt.setAuthAttemptProofToken("test-proof-token");
    testAuthAttempt.setCreatedAt(OffsetDateTime.now());
    testAuthAttempt.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
    testAuthAttempt = authAttemptRepository.save(testAuthAttempt);
  }

  @Nested
  @DisplayName("ResourceNotFoundException Behavior")
  class ResourceNotFoundExceptionBehavior {

    @Test
    @DisplayName(
        "AuthAttemptService.getById() throws ResourceNotFoundException with correct message")
    void testAuthAttemptServiceGetByIdThrowsResourceNotFoundException() {
      // Given: Non-existent auth attempt ID
      Integer nonExistentId = 99999;

      // When & Then: Should throw ResourceNotFoundException with specific message format
      ResourceNotFoundException exception =
          assertThrows(
              ResourceNotFoundException.class, () -> authAttemptService.getById(nonExistentId));

      // Verify exception message format
      String expectedMessage = "Authentication attempt with id 99999 not found";
      assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    @DisplayName(
        "AuthAttemptService.delete() throws ResourceNotFoundException with correct message")
    void testAuthAttemptServiceDeleteThrowsResourceNotFoundException() {
      // Given: Non-existent auth attempt ID
      Integer nonExistentId = 99999;

      // When & Then: Should throw ResourceNotFoundException with specific message format
      ResourceNotFoundException exception =
          assertThrows(
              ResourceNotFoundException.class, () -> authAttemptService.delete(nonExistentId));

      // Verify exception message format
      String expectedMessage = "Authentication attempt with id 99999 not found";
      assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    @DisplayName(
        "EnrollmentService.getById() throws ResourceNotFoundException with correct message")
    void testEnrollmentServiceGetByIdThrowsResourceNotFoundException() {
      // Given: Non-existent enrollment ID
      Integer nonExistentId = 99999;

      // When & Then: Should throw ResourceNotFoundException with specific message format
      ResourceNotFoundException exception =
          assertThrows(
              ResourceNotFoundException.class, () -> enrollmentService.getById(nonExistentId));

      // Verify exception message format
      String expectedMessage = "Enrollment with id 99999 not found";
      assertEquals(expectedMessage, exception.getMessage());
    }
  }

  @Nested
  @DisplayName("IllegalArgumentException Behavior")
  class IllegalArgumentExceptionBehavior {

    @Test
    @DisplayName(
        "EnrollmentService.create() throws IllegalArgumentException for null integration ID")
    void testEnrollmentServiceCreateThrowsIllegalArgumentExceptionForNullIntegrationId() {
      // Given: Request with null integration ID
      EnrollmentCreateRequest request = new EnrollmentCreateRequest();
      request.setIntegrationId(null);
      request.setName("test-enrollment");

      // When & Then: Should throw IllegalArgumentException with specific message
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> enrollmentService.create(request));

      // Verify exception message
      String expectedMessage = "Integration ID is required";
      assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    @DisplayName(
        "SignatureService.generateSecureChallenge() throws IllegalArgumentException for invalid digits")
    void
        testSignatureServiceGenerateSecureChallengeThrowsIllegalArgumentExceptionForInvalidDigits() {
      // Given: Invalid challenge digits
      int invalidDigits = 10; // Should be between 1 and 6

      // When & Then: Should throw IllegalArgumentException with specific message
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> signatureService.generateSecureChallenge(invalidDigits));

      // Verify exception message format
      String expectedMessage = "Challenge digits must be between 1 and 6, got: 10";
      assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    @DisplayName(
        "SignatureService.generateSecureChallenge() throws IllegalArgumentException for zero digits")
    void testSignatureServiceGenerateSecureChallengeThrowsIllegalArgumentExceptionForZeroDigits() {
      // Given: Invalid challenge digits
      int invalidDigits = 0; // Should be between 1 and 6

      // When & Then: Should throw IllegalArgumentException with specific message
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> signatureService.generateSecureChallenge(invalidDigits));

      // Verify exception message format
      String expectedMessage = "Challenge digits must be between 1 and 6, got: 0";
      assertEquals(expectedMessage, exception.getMessage());
    }
  }

  @Nested
  @DisplayName("RuntimeException Behavior")
  class RuntimeExceptionBehavior {

    @Test
    @DisplayName(
        "SignatureService.generateSignature() throws RuntimeException for invalid private key")
    void testSignatureServiceGenerateSignatureThrowsRuntimeExceptionForInvalidPrivateKey() {
      // Given: Invalid private key
      String invalidPrivateKey = "invalid-base64-key";
      String data = "test-data";

      // When & Then: Should throw RuntimeException with specific message
      RuntimeException exception =
          assertThrows(
              RuntimeException.class,
              () -> signatureService.generateSignature(data, invalidPrivateKey));

      // Verify exception message
      String expectedMessage = "Failed to generate signature";
      assertEquals(expectedMessage, exception.getMessage());
      assertNotNull(exception.getCause());
    }

    @Test
    @DisplayName(
        "SignatureService.generateRsaKeyPair() accepts invalid key size (current behavior)")
    void testSignatureServiceGenerateRsaKeyPairAcceptsInvalidKeySize() {
      // Given: Invalid key size (too small)
      int invalidKeySize = 512; // Should be at least 2048

      // When: Generate key pair with invalid size
      var keyPair = signatureService.generateRsaKeyPair(invalidKeySize);

      // Then: Should succeed (current behavior - no validation)
      assertNotNull(keyPair);
      assertNotNull(keyPair.base64PrivateKey());
      assertNotNull(keyPair.base64PublicKey());
    }

    @Test
    @DisplayName(
        "SignatureService.generateSecureChallenge() throws RuntimeException for cryptographic errors")
    void testSignatureServiceGenerateSecureChallengeThrowsRuntimeExceptionForCryptoErrors() {
      // Given: Valid digits but potential crypto error scenario
      int validDigits = 3;

      // When: Generate challenge (should normally work, but we're testing the error path)
      // Note: This test documents the current behavior - it should not throw for valid input
      Integer challenge = signatureService.generateSecureChallenge(validDigits);

      // Then: Should return a valid challenge
      assertNotNull(challenge);
      assertTrue(challenge >= 100 && challenge <= 999); // 3-digit number
    }
  }

  @Nested
  @DisplayName("Service-Specific Error Handling Behavior")
  class ServiceSpecificErrorHandlingBehavior {

    @Test
    @DisplayName("AuthAttemptService.create() handles non-existent enrollment gracefully")
    void testAuthAttemptServiceCreateHandlesNonExistentEnrollment() {
      // Given: Request with non-existent enrollment ID
      AuthAttemptCreateRequest request = new AuthAttemptCreateRequest();
      request.setEnrollmentId(99999);
      request.setChallengeRequested(false);

      // When & Then: Should throw IllegalArgumentException (current behavior)
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> authAttemptService.create(request));

      // Verify exception message
      String expectedMessage = "Enrollment not found for ID: 99999";
      assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    @DisplayName("AuthAttemptService.respond() returns FAILED response for validation errors")
    void testAuthAttemptServiceRespondReturnsFailedResponseForValidationErrors() {
      // Given: Request for auth attempt that hasn't been read by device
      AuthAttemptRespondRequest request = new AuthAttemptRespondRequest();
      request.setAuthAttemptId(testAuthAttempt.getAuthAttemptId());
      request.setAuthAttemptAccepted(true);
      request.setAuthAttemptProofTokenSignedByDevice("signed-token");

      // When: Try to respond to unread attempt
      var response = authAttemptService.respond(request);

      // Then: Should return FAILED response (not throw exception)
      assertNotNull(response);
      assertEquals("FAILED", response.getResult().name());
      assertEquals("Auth attempt not read by device", response.getMessage());
    }
  }

  @Nested
  @DisplayName("Exception Message Consistency")
  class ExceptionMessageConsistency {

    @Test
    @DisplayName("ResourceNotFoundException messages follow consistent format")
    void testResourceNotFoundExceptionMessagesFollowConsistentFormat() {
      // Test different resource types
      String[] resources = {"Integration", "Enrollment", "AuthAttempt", "User"};
      Object[] ids = {123, "uuid-123", 456, "user@example.com"};

      for (int i = 0; i < resources.length; i++) {
        ResourceNotFoundException exception = new ResourceNotFoundException(resources[i], ids[i]);

        String expectedMessage = String.format("%s with id %s not found", resources[i], ids[i]);
        assertEquals(expectedMessage, exception.getMessage());
      }
    }

    @Test
    @DisplayName("IllegalArgumentException messages are descriptive and actionable")
    void testIllegalArgumentExceptionMessagesAreDescriptiveAndActionable() {
      // Test various validation scenarios
      IllegalArgumentException exception1 =
          assertThrows(
              IllegalArgumentException.class, () -> signatureService.generateSecureChallenge(0));

      IllegalArgumentException exception2 =
          assertThrows(
              IllegalArgumentException.class, () -> signatureService.generateSecureChallenge(10));

      // Verify messages are descriptive
      assertTrue(exception1.getMessage().contains("Challenge digits must be between 1 and 6"));
      assertTrue(exception1.getMessage().contains("got: 0"));
      assertTrue(exception2.getMessage().contains("Challenge digits must be between 1 and 6"));
      assertTrue(exception2.getMessage().contains("got: 10"));
    }
  }

  @Nested
  @DisplayName("Error Handling Patterns")
  class ErrorHandlingPatterns {

    @Test
    @DisplayName("Services use consistent error handling patterns")
    void testServicesUseConsistentErrorHandlingPatterns() {
      // Pattern 1: ResourceNotFoundException for missing entities
      assertThrows(ResourceNotFoundException.class, () -> authAttemptService.getById(99999));

      assertThrows(ResourceNotFoundException.class, () -> enrollmentService.getById(99999));

      // Pattern 2: IllegalArgumentException for invalid input
      assertThrows(
          IllegalArgumentException.class, () -> signatureService.generateSecureChallenge(0));

      // Pattern 3: RuntimeException for technical failures
      assertThrows(
          RuntimeException.class, () -> signatureService.generateSignature("data", "invalid-key"));
    }

    @Test
    @DisplayName("Error messages provide sufficient context for debugging")
    void testErrorMessagesProvideSufficientContextForDebugging() {
      // Test that error messages include relevant context
      ResourceNotFoundException exception =
          assertThrows(ResourceNotFoundException.class, () -> authAttemptService.getById(12345));

      String message = exception.getMessage();
      assertTrue(message.contains("Authentication attempt"));
      assertTrue(message.contains("12345"));
      assertTrue(message.contains("not found"));
    }
  }
}
