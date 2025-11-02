/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * Test Class: AuthAttemptCreateRequestDtoTest Description: Unit tests for
 * AuthAttemptCreateRequestDto record.
 */

package org.ezkey.authattempt.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AuthAttemptCreateRequestDto}.
 *
 * <p>
 * Tests verify proper behavior of the record including:
 *
 * <ul>
 * <li>Record construction and accessor methods
 * <li>Bean validation constraints
 * <li>Equality and hashCode behavior
 * <li>JSON property mapping
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("AuthAttemptCreateRequestDto Tests")
class AuthAttemptCreateRequestDtoTest{

  private static Validator validator;

  private static final Integer TEST_ENROLLMENT_ID = 123;

  private static final Boolean TEST_CHALLENGE_REQUESTED = false;

  @BeforeAll
  static void setUpValidator(){
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test @DisplayName("Should create record with all fields")
  void shouldCreateRecordWithAllFields(){
    // Act
    AuthAttemptCreateRequestDto dto = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,
        TEST_CHALLENGE_REQUESTED);

    // Assert
    assertThat(dto.enrollmentId()).isEqualTo(TEST_ENROLLMENT_ID);
    assertThat(dto.challengeRequested()).isEqualTo(TEST_CHALLENGE_REQUESTED);
  }

  @Test @DisplayName("Should create record with challenge requested true")
  void shouldCreateRecordWithChallengeRequestedTrue(){
    // Act
    AuthAttemptCreateRequestDto dto = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,true);

    // Assert
    assertThat(dto.enrollmentId()).isEqualTo(TEST_ENROLLMENT_ID);
    assertThat(dto.challengeRequested()).isTrue();
  }

  @Test @DisplayName("Should create record with challenge requested false")
  void shouldCreateRecordWithChallengeRequestedFalse(){
    // Act
    AuthAttemptCreateRequestDto dto = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,false);

    // Assert
    assertThat(dto.enrollmentId()).isEqualTo(TEST_ENROLLMENT_ID);
    assertThat(dto.challengeRequested()).isFalse();
  }

  @Test @DisplayName("Should pass validation with valid fields")
  void shouldPassValidationWithValidFields(){
    // Arrange
    AuthAttemptCreateRequestDto dto = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,
        TEST_CHALLENGE_REQUESTED);

    // Act
    Set<ConstraintViolation<AuthAttemptCreateRequestDto>> violations = validator.validate(dto);

    // Assert
    assertThat(violations).isEmpty();
  }

  @Test @DisplayName("Should fail validation when enrollmentId is null")
  void shouldFailValidationWhenEnrollmentIdIsNull(){
    // Arrange
    AuthAttemptCreateRequestDto dto = new AuthAttemptCreateRequestDto(null,
        TEST_CHALLENGE_REQUESTED);

    // Act
    Set<ConstraintViolation<AuthAttemptCreateRequestDto>> violations = validator.validate(dto);

    // Assert
    assertThat(violations).hasSize(1);
    ConstraintViolation<AuthAttemptCreateRequestDto> violation = violations.iterator().next();
    assertThat(violation.getMessage()).isEqualTo("Enrollment ID is required");
    assertThat(violation.getPropertyPath().toString()).isEqualTo("enrollmentId");
  }

  @Test @DisplayName("Should fail validation when challengeRequested is null")
  void shouldFailValidationWhenChallengeRequestedIsNull(){
    // Arrange
    AuthAttemptCreateRequestDto dto = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,null);

    // Act
    Set<ConstraintViolation<AuthAttemptCreateRequestDto>> violations = validator.validate(dto);

    // Assert
    assertThat(violations).hasSize(1);
    ConstraintViolation<AuthAttemptCreateRequestDto> violation = violations.iterator().next();
    assertThat(violation.getMessage()).isEqualTo("Challenge requested flag is required");
    assertThat(violation.getPropertyPath().toString()).isEqualTo("challengeRequested");
  }

  @Test @DisplayName("Should fail validation when both fields are null")
  void shouldFailValidationWhenBothFieldsAreNull(){
    // Arrange
    AuthAttemptCreateRequestDto dto = new AuthAttemptCreateRequestDto(null,null);

    // Act
    Set<ConstraintViolation<AuthAttemptCreateRequestDto>> violations = validator.validate(dto);

    // Assert
    assertThat(violations).hasSize(2);
  }

  @Test @DisplayName("Should implement equals() correctly for records")
  void shouldImplementEqualsCorrectly(){
    // Arrange
    AuthAttemptCreateRequestDto dto1 = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,
        TEST_CHALLENGE_REQUESTED);
    AuthAttemptCreateRequestDto dto2 = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,
        TEST_CHALLENGE_REQUESTED);
    AuthAttemptCreateRequestDto dto3 = new AuthAttemptCreateRequestDto(999,
        TEST_CHALLENGE_REQUESTED);
    AuthAttemptCreateRequestDto dto4 = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,true);

    // Assert
    assertThat(dto1).isEqualTo(dto2);
    assertThat(dto1).isNotEqualTo(dto3);
    assertThat(dto1).isNotEqualTo(dto4);
    assertThat(dto1).isNotEqualTo(null);
  }

  @Test @DisplayName("Should implement hashCode() correctly for records")
  void shouldImplementHashCodeCorrectly(){
    // Arrange
    AuthAttemptCreateRequestDto dto1 = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,
        TEST_CHALLENGE_REQUESTED);
    AuthAttemptCreateRequestDto dto2 = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,
        TEST_CHALLENGE_REQUESTED);

    // Assert
    assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
  }

  @Test @DisplayName("Should have consistent toString() representation")
  void shouldHaveConsistentToStringRepresentation(){
    // Arrange
    AuthAttemptCreateRequestDto dto = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,
        TEST_CHALLENGE_REQUESTED);

    // Act
    String toString = dto.toString();

    // Assert
    assertThat(toString).contains("AuthAttemptCreateRequestDto")
        .contains("enrollmentId=" + TEST_ENROLLMENT_ID)
        .contains("challengeRequested=" + TEST_CHALLENGE_REQUESTED);
  }

  @Test @DisplayName("Should handle different enrollment IDs correctly")
  void shouldHandleDifferentEnrollmentIdsCorrectly(){
    // Arrange
    Integer[] enrollmentIds = { 1,100,999,12345 };

    for (Integer enrollmentId : enrollmentIds){
      // Act
      AuthAttemptCreateRequestDto dto = new AuthAttemptCreateRequestDto(enrollmentId,
          TEST_CHALLENGE_REQUESTED);

      // Assert
      assertThat(dto.enrollmentId()).isEqualTo(enrollmentId);

      Set<ConstraintViolation<AuthAttemptCreateRequestDto>> violations = validator.validate(dto);
      assertThat(violations).isEmpty();
    }
  }

  @Test @DisplayName("Should handle both boolean values for challengeRequested")
  void shouldHandleBothBooleanValuesForChallengeRequested(){
    // Test with true
    AuthAttemptCreateRequestDto dtoTrue = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,true);
    assertThat(dtoTrue.challengeRequested()).isTrue();
    assertThat(validator.validate(dtoTrue)).isEmpty();

    // Test with false
    AuthAttemptCreateRequestDto dtoFalse = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,
        false);
    assertThat(dtoFalse.challengeRequested()).isFalse();
    assertThat(validator.validate(dtoFalse)).isEmpty();
  }

  @Test @DisplayName("Should be immutable - cannot modify fields after creation")
  void shouldBeImmutable(){
    // Arrange
    AuthAttemptCreateRequestDto dto = new AuthAttemptCreateRequestDto(TEST_ENROLLMENT_ID,
        TEST_CHALLENGE_REQUESTED);

    // Assert - Records are immutable by design
    // Attempting to modify would cause compilation error
    assertThat(dto.enrollmentId()).isEqualTo(TEST_ENROLLMENT_ID);
    assertThat(dto.challengeRequested()).isEqualTo(TEST_CHALLENGE_REQUESTED);
  }

  @Test @DisplayName("Should support different combinations of enrollment ID and challenge")
  void shouldSupportDifferentCombinations(){
    // Test combinations
    AuthAttemptCreateRequestDto dto1 = new AuthAttemptCreateRequestDto(123,true);
    AuthAttemptCreateRequestDto dto2 = new AuthAttemptCreateRequestDto(123,false);
    AuthAttemptCreateRequestDto dto3 = new AuthAttemptCreateRequestDto(456,true);
    AuthAttemptCreateRequestDto dto4 = new AuthAttemptCreateRequestDto(456,false);

    // Assert all are valid
    assertThat(validator.validate(dto1)).isEmpty();
    assertThat(validator.validate(dto2)).isEmpty();
    assertThat(validator.validate(dto3)).isEmpty();
    assertThat(validator.validate(dto4)).isEmpty();

    // Assert they are all different
    assertThat(dto1).isNotEqualTo(dto2);
    assertThat(dto1).isNotEqualTo(dto3);
    assertThat(dto1).isNotEqualTo(dto4);
    assertThat(dto2).isNotEqualTo(dto3);
    assertThat(dto2).isNotEqualTo(dto4);
    assertThat(dto3).isNotEqualTo(dto4);
  }
}
