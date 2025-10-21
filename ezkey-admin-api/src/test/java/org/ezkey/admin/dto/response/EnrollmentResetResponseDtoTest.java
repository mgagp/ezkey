/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: EnrollmentResetResponseDtoTest
 * Description: Unit tests for EnrollmentResetResponseDto record.
 */

package org.ezkey.admin.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EnrollmentResetResponseDto}.
 *
 * <p>Tests verify proper behavior of the record including:
 * <ul>
 *   <li>Factory methods for success and error responses</li>
 *   <li>Field validation and accessor methods</li>
 *   <li>Security masking in toString()</li>
 *   <li>Equality and hashCode behavior</li>
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("EnrollmentResetResponseDto Tests")
class EnrollmentResetResponseDtoTest {

  private static final Integer TEST_ENROLLMENT_ID = 123;
  private static final String TEST_PROOF_TOKEN = "ezkey_proof_a1b2c3d4e5f6g7h8i9j0";
  private static final Integer TEST_CHALLENGE = 123456;
  private static final Integer TEST_INTEGRATION_ID = 456;
  private static final String TEST_ERROR_MESSAGE = "Reset failed: Invalid recovery token";

  @Test
  @DisplayName("Factory method success() should create successful response with all fields")
  void factoryMethodSuccessShouldCreateSuccessfulResponse() {
    // Act
    EnrollmentResetResponseDto dto = EnrollmentResetResponseDto.success(
        TEST_ENROLLMENT_ID,
        TEST_PROOF_TOKEN,
        TEST_CHALLENGE,
        TEST_INTEGRATION_ID
    );

    // Assert
    assertThat(dto.success()).isTrue();
    assertThat(dto.enrollmentId()).isEqualTo(TEST_ENROLLMENT_ID);
    assertThat(dto.enrollmentProofToken()).isEqualTo(TEST_PROOF_TOKEN);
    assertThat(dto.enrollmentChallenge()).isEqualTo(TEST_CHALLENGE);
    assertThat(dto.integrationId()).isEqualTo(TEST_INTEGRATION_ID);
    assertThat(dto.message())
        .isEqualTo("Enrollment reset successfully. Old device unbound. Use these credentials to bind new device.");
  }

  @Test
  @DisplayName("Factory method error() should create error response with null credentials")
  void factoryMethodErrorShouldCreateErrorResponse() {
    // Act
    EnrollmentResetResponseDto dto = EnrollmentResetResponseDto.error(TEST_ERROR_MESSAGE);

    // Assert
    assertThat(dto.success()).isFalse();
    assertThat(dto.enrollmentId()).isNull();
    assertThat(dto.enrollmentProofToken()).isNull();
    assertThat(dto.enrollmentChallenge()).isNull();
    assertThat(dto.integrationId()).isNull();
    assertThat(dto.message()).isEqualTo(TEST_ERROR_MESSAGE);
  }

  @Test
  @DisplayName("Should create record with all fields using constructor")
  void shouldCreateRecordWithAllFields() {
    // Act
    EnrollmentResetResponseDto dto = new EnrollmentResetResponseDto(
        true,
        TEST_ENROLLMENT_ID,
        TEST_PROOF_TOKEN,
        TEST_CHALLENGE,
        TEST_INTEGRATION_ID,
        "Custom message"
    );

    // Assert
    assertThat(dto.success()).isTrue();
    assertThat(dto.enrollmentId()).isEqualTo(TEST_ENROLLMENT_ID);
    assertThat(dto.enrollmentProofToken()).isEqualTo(TEST_PROOF_TOKEN);
    assertThat(dto.enrollmentChallenge()).isEqualTo(TEST_CHALLENGE);
    assertThat(dto.integrationId()).isEqualTo(TEST_INTEGRATION_ID);
    assertThat(dto.message()).isEqualTo("Custom message");
  }

  @Test
  @DisplayName("Should mask proof token in toString()")
  void shouldMaskProofTokenInToString() {
    // Arrange
    EnrollmentResetResponseDto dto = EnrollmentResetResponseDto.success(
        TEST_ENROLLMENT_ID,
        TEST_PROOF_TOKEN,
        TEST_CHALLENGE,
        TEST_INTEGRATION_ID
    );

    // Act
    String toString = dto.toString();

    // Assert
    assertThat(toString)
        .contains("EnrollmentResetResponseDto")
        .contains("success=true")
        .contains("enrollmentId=" + TEST_ENROLLMENT_ID)
        .contains("enrollmentProofToken='[PROTECTED]'")
        .contains("enrollmentChallenge=[PROTECTED]")
        .contains("integrationId=" + TEST_INTEGRATION_ID)
        .doesNotContain(TEST_PROOF_TOKEN)
        .doesNotContain(TEST_CHALLENGE.toString());
  }

  @Test
  @DisplayName("Should show null for null credentials in toString()")
  void shouldShowNullForNullCredentialsInToString() {
    // Arrange
    EnrollmentResetResponseDto dto = EnrollmentResetResponseDto.error(TEST_ERROR_MESSAGE);

    // Act
    String toString = dto.toString();

    // Assert
    assertThat(toString)
        .contains("enrollmentProofToken='null'")
        .contains("enrollmentChallenge=null")
        .contains("message='" + TEST_ERROR_MESSAGE + "'");
  }

  @Test
  @DisplayName("Should implement equals() correctly for records")
  void shouldImplementEqualsCorrectly() {
    // Arrange
    EnrollmentResetResponseDto dto1 = EnrollmentResetResponseDto.success(
        TEST_ENROLLMENT_ID,
        TEST_PROOF_TOKEN,
        TEST_CHALLENGE,
        TEST_INTEGRATION_ID
    );

    EnrollmentResetResponseDto dto2 = EnrollmentResetResponseDto.success(
        TEST_ENROLLMENT_ID,
        TEST_PROOF_TOKEN,
        TEST_CHALLENGE,
        TEST_INTEGRATION_ID
    );

    EnrollmentResetResponseDto dto3 = EnrollmentResetResponseDto.error("Different message");

    // Assert
    assertThat(dto1).isEqualTo(dto2);
    assertThat(dto1).isNotEqualTo(dto3);
    assertThat(dto1).isNotEqualTo(null);
  }

  @Test
  @DisplayName("Should implement hashCode() correctly for records")
  void shouldImplementHashCodeCorrectly() {
    // Arrange
    EnrollmentResetResponseDto dto1 = EnrollmentResetResponseDto.success(
        TEST_ENROLLMENT_ID,
        TEST_PROOF_TOKEN,
        TEST_CHALLENGE,
        TEST_INTEGRATION_ID
    );

    EnrollmentResetResponseDto dto2 = EnrollmentResetResponseDto.success(
        TEST_ENROLLMENT_ID,
        TEST_PROOF_TOKEN,
        TEST_CHALLENGE,
        TEST_INTEGRATION_ID
    );

    // Assert
    assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
  }

  @Test
  @DisplayName("Success response should have consistent message")
  void successResponseShouldHaveConsistentMessage() {
    // Act
    EnrollmentResetResponseDto dto1 = EnrollmentResetResponseDto.success(
        TEST_ENROLLMENT_ID,
        TEST_PROOF_TOKEN,
        TEST_CHALLENGE,
        TEST_INTEGRATION_ID
    );

    EnrollmentResetResponseDto dto2 = EnrollmentResetResponseDto.success(
        999,
        "different_token",
        999999,
        789
    );

    // Assert
    assertThat(dto1.message()).isEqualTo(dto2.message());
    assertThat(dto1.message())
        .isEqualTo("Enrollment reset successfully. Old device unbound. Use these credentials to bind new device.");
  }

  @Test
  @DisplayName("Error response should preserve custom error message")
  void errorResponseShouldPreserveCustomErrorMessage() {
    // Arrange
    String customError1 = "Invalid recovery token";
    String customError2 = "Enrollment not found";

    // Act
    EnrollmentResetResponseDto dto1 = EnrollmentResetResponseDto.error(customError1);
    EnrollmentResetResponseDto dto2 = EnrollmentResetResponseDto.error(customError2);

    // Assert
    assertThat(dto1.message()).isEqualTo(customError1);
    assertThat(dto2.message()).isEqualTo(customError2);
    assertThat(dto1.message()).isNotEqualTo(dto2.message());
  }

  @Test
  @DisplayName("Success and error responses should be distinguishable by success flag")
  void successAndErrorResponsesShouldBeDistinguishableBySuccessFlag() {
    // Arrange
    EnrollmentResetResponseDto successDto = EnrollmentResetResponseDto.success(
        TEST_ENROLLMENT_ID,
        TEST_PROOF_TOKEN,
        TEST_CHALLENGE,
        TEST_INTEGRATION_ID
    );

    EnrollmentResetResponseDto errorDto = EnrollmentResetResponseDto.error(TEST_ERROR_MESSAGE);

    // Assert
    assertThat(successDto.success()).isTrue();
    assertThat(errorDto.success()).isFalse();
  }

  @Test
  @DisplayName("Error response should have all credential fields as null")
  void errorResponseShouldHaveAllCredentialFieldsAsNull() {
    // Act
    EnrollmentResetResponseDto dto = EnrollmentResetResponseDto.error("Some error");

    // Assert
    assertThat(dto.enrollmentId()).isNull();
    assertThat(dto.enrollmentProofToken()).isNull();
    assertThat(dto.enrollmentChallenge()).isNull();
    assertThat(dto.integrationId()).isNull();
  }

  @Test
  @DisplayName("Success response should have all credential fields populated")
  void successResponseShouldHaveAllCredentialFieldsPopulated() {
    // Act
    EnrollmentResetResponseDto dto = EnrollmentResetResponseDto.success(
        TEST_ENROLLMENT_ID,
        TEST_PROOF_TOKEN,
        TEST_CHALLENGE,
        TEST_INTEGRATION_ID
    );

    // Assert
    assertThat(dto.enrollmentId()).isNotNull();
    assertThat(dto.enrollmentProofToken()).isNotNull();
    assertThat(dto.enrollmentChallenge()).isNotNull();
    assertThat(dto.integrationId()).isNotNull();
  }
}
