/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * Test Class: AuthAttemptWaitRequestDtoTest Description: Unit tests for AuthAttemptWaitRequestDto
 * validation and behavior.
 */

package org.ezkey.authattempt.dto;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.ezkey.exception.auth.AuthAttemptWaitValidationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AuthAttemptWaitRequestDto}.
 *
 * <p>Tests verify:
 *
 * <ul>
 *   <li>Default constructor initialization
 *   <li>Custom value construction
 *   <li>Bean validation constraints (@NotNull, @Min, @Max)
 *   <li>Custom validation logic (polling < timeout)
 *   <li>Record accessors
 *   <li>Equality and hash code behavior
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("AuthAttemptWaitRequestDto Tests")
class AuthAttemptWaitRequestDtoTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  @DisplayName("Should create record with default values")
  void shouldCreateWithDefaultValues() {
    // Act
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto();

    // Assert
    assertNotNull(dto);
    assertEquals(30, dto.timeout());
    assertEquals(2, dto.polling());
  }

  @Test
  @DisplayName("Should create record with custom values")
  void shouldCreateWithCustomValues() {
    // Act
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(60, 5);

    // Assert
    assertNotNull(dto);
    assertEquals(60, dto.timeout());
    assertEquals(5, dto.polling());
  }

  @Test
  @DisplayName("Should validate successfully with valid values")
  void shouldValidateSuccessfully() {
    // Arrange
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(30, 2);

    // Act
    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations = validator.validate(dto);

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("Should fail validation when timeout is null")
  void shouldFailWhenTimeoutIsNull() {
    // Arrange
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(null, 2);

    // Act
    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations = validator.validate(dto);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<AuthAttemptWaitRequestDto> violation = violations.iterator().next();
    assertEquals("Timeout cannot be null", violation.getMessage());
    assertEquals("timeout", violation.getPropertyPath().toString());
  }

  @Test
  @DisplayName("Should fail validation when polling is null")
  void shouldFailWhenPollingIsNull() {
    // Arrange
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(30, null);

    // Act
    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations = validator.validate(dto);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<AuthAttemptWaitRequestDto> violation = violations.iterator().next();
    assertEquals("Polling cannot be null", violation.getMessage());
    assertEquals("polling", violation.getPropertyPath().toString());
  }

  @Test
  @DisplayName("Should fail validation when timeout is less than minimum")
  void shouldFailWhenTimeoutIsTooSmall() {
    // Arrange - Use polling < timeout to pass custom validation
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(0, -1);

    // Act
    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations = validator.validate(dto);

    // Assert - Should have 2 violations: timeout too small and polling too small
    assertEquals(2, violations.size());
  }

  @Test
  @DisplayName("Should fail validation when timeout exceeds maximum")
  void shouldFailWhenTimeoutIsTooLarge() {
    // Arrange
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(301, 2);

    // Act
    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations = validator.validate(dto);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<AuthAttemptWaitRequestDto> violation = violations.iterator().next();
    assertEquals("Timeout cannot exceed 300 seconds", violation.getMessage());
  }

  @Test
  @DisplayName("Should fail validation when polling is less than minimum")
  void shouldFailWhenPollingIsTooSmall() {
    // Arrange
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(30, 0);

    // Act
    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations = validator.validate(dto);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<AuthAttemptWaitRequestDto> violation = violations.iterator().next();
    assertEquals("Polling interval must be at least 1 second", violation.getMessage());
  }

  @Test
  @DisplayName("Should fail validation when polling exceeds maximum")
  void shouldFailWhenPollingIsTooLarge() {
    // Arrange
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(100, 61);

    // Act
    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations = validator.validate(dto);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<AuthAttemptWaitRequestDto> violation = violations.iterator().next();
    assertEquals("Polling interval cannot exceed 60 seconds", violation.getMessage());
  }

  @Test
  @DisplayName("Should validate at minimum boundary values")
  void shouldValidateAtMinimumBoundary() {
    // Arrange - Minimum valid values where polling < timeout
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(2, 1);

    // Act
    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations = validator.validate(dto);

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("Should validate at maximum boundary values")
  void shouldValidateAtMaximumBoundary() {
    // Arrange
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(300, 60);

    // Act
    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations = validator.validate(dto);

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("Should throw exception when polling equals timeout")
  void shouldThrowExceptionWhenPollingEqualsTimeout() {
    // Act & Assert
    AuthAttemptWaitValidationException exception =
        assertThrows(
            AuthAttemptWaitValidationException.class, () -> new AuthAttemptWaitRequestDto(30, 30));

    assertEquals("Polling interval must be less than timeout duration", exception.getMessage());
  }

  @Test
  @DisplayName("Should throw exception when polling is greater than timeout")
  void shouldThrowExceptionWhenPollingGreaterThanTimeout() {
    // Act & Assert
    AuthAttemptWaitValidationException exception =
        assertThrows(
            AuthAttemptWaitValidationException.class, () -> new AuthAttemptWaitRequestDto(30, 31));

    assertEquals("Polling interval must be less than timeout duration", exception.getMessage());
  }

  @Test
  @DisplayName("Should allow polling just below timeout")
  void shouldAllowPollingJustBelowTimeout() {
    // Act
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(30, 29);

    // Assert
    assertNotNull(dto);
    assertEquals(30, dto.timeout());
    assertEquals(29, dto.polling());
  }

  @Test
  @DisplayName("Should handle multiple validation errors")
  void shouldHandleMultipleValidationErrors() {
    // Arrange - Use polling < timeout to pass custom validation, but both below minimum
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(0, -1);

    // Act
    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations = validator.validate(dto);

    // Assert - Should have 2 violations: both values below minimum
    assertEquals(2, violations.size());
  }

  @Test
  @DisplayName("Should support record equality")
  void shouldSupportRecordEquality() {
    // Arrange
    AuthAttemptWaitRequestDto dto1 = new AuthAttemptWaitRequestDto(30, 2);
    AuthAttemptWaitRequestDto dto2 = new AuthAttemptWaitRequestDto(30, 2);
    AuthAttemptWaitRequestDto dto3 = new AuthAttemptWaitRequestDto(60, 5);

    // Assert
    assertEquals(dto1, dto2);
    assertNotEquals(dto1, dto3);
    assertEquals(dto1.hashCode(), dto2.hashCode());
  }

  @Test
  @DisplayName("Should provide toString representation")
  void shouldProvideToStringRepresentation() {
    // Arrange
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(30, 2);

    // Act
    String toString = dto.toString();

    // Assert
    assertNotNull(toString);
    assertTrue(toString.contains("30"));
    assertTrue(toString.contains("2"));
  }

  @Test
  @DisplayName("Should work with realistic authentication scenario")
  void shouldWorkWithRealisticScenario() {
    // Arrange - Quick response scenario
    AuthAttemptWaitRequestDto quickResponse = new AuthAttemptWaitRequestDto(15, 1);

    // Assert
    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations =
        validator.validate(quickResponse);
    assertTrue(violations.isEmpty());
    assertEquals(15, quickResponse.timeout());
    assertEquals(1, quickResponse.polling());

    // Arrange - Standard scenario
    AuthAttemptWaitRequestDto standard = new AuthAttemptWaitRequestDto(30, 2);

    // Assert
    violations = validator.validate(standard);
    assertTrue(violations.isEmpty());

    // Arrange - Patient wait scenario
    AuthAttemptWaitRequestDto patient = new AuthAttemptWaitRequestDto(120, 5);

    // Assert
    violations = validator.validate(patient);
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("Should not allow construction with both null values")
  void shouldNotAllowBothNullValues() {
    // Act
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(null, null);

    // Assert - Custom validation doesn't throw, but bean validation should catch it
    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations = validator.validate(dto);
    assertEquals(2, violations.size());
  }

  @Test
  @DisplayName("Should handle edge case with null timeout and valid polling")
  void shouldHandleNullTimeoutWithValidPolling() {
    // Act
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(null, 2);

    // Assert
    assertNotNull(dto);
    assertNull(dto.timeout());
    assertEquals(2, dto.polling());

    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations = validator.validate(dto);
    assertEquals(1, violations.size());
  }

  @Test
  @DisplayName("Should handle edge case with valid timeout and null polling")
  void shouldHandleValidTimeoutWithNullPolling() {
    // Act
    AuthAttemptWaitRequestDto dto = new AuthAttemptWaitRequestDto(30, null);

    // Assert
    assertNotNull(dto);
    assertEquals(30, dto.timeout());
    assertNull(dto.polling());

    Set<ConstraintViolation<AuthAttemptWaitRequestDto>> violations = validator.validate(dto);
    assertEquals(1, violations.size());
  }
}
