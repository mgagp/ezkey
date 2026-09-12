/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * Test Class: AuthAttemptDtoTest Description: Unit tests for AuthAttemptDto record.
 */

package org.ezkey.authattempt.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AuthAttemptDto}.
 *
 * <p>Tests verify proper behavior of the record including:
 *
 * <ul>
 *   <li>Record construction and accessor methods
 *   <li>Null handling for optional fields
 *   <li>Edge case values
 *   <li>Record equality and immutability (guaranteed by Java)
 * </ul>
 *
 * <p><b>Note:</b> Records automatically provide correct implementations of equals(), hashCode(),
 * toString(), and immutability guarantees. These behaviors are tested minimally as they are
 * guaranteed by the Java language specification.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("AuthAttemptDto Tests")
class AuthAttemptDtoTest {

  private static final Integer TEST_AUTH_ATTEMPT_ID = 456;

  private static final Integer TEST_ENROLLMENT_ID = 123;

  private static final AuthAttemptStatus TEST_STATUS = AuthAttemptStatus.PENDING;

  private static final Integer TEST_CHALLENGE = 789012;

  private static final String TEST_PROOF_TOKEN = "EZK-XYZ789-ABC123";

  private static final OffsetDateTime TEST_CREATED_AT =
      OffsetDateTime.of(2025, 1, 27, 10, 30, 0, 0, ZoneOffset.ofHours(1));

  private static final OffsetDateTime TEST_EXPIRES_AT =
      OffsetDateTime.of(2025, 1, 27, 10, 35, 0, 0, ZoneOffset.ofHours(1));

  @Test
  @DisplayName("Should create record with all fields")
  void shouldCreateRecordWithAllFields() {
    // Act
    AuthAttemptDto dto =
        new AuthAttemptDto(
            TEST_AUTH_ATTEMPT_ID,
            TEST_ENROLLMENT_ID,
            TEST_STATUS,
            TEST_CHALLENGE,
            TEST_PROOF_TOKEN,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    // Assert
    assertThat(dto.authAttemptId()).isEqualTo(TEST_AUTH_ATTEMPT_ID);
    assertThat(dto.enrollmentId()).isEqualTo(TEST_ENROLLMENT_ID);
    assertThat(dto.authAttemptStatus()).isEqualTo(TEST_STATUS);
    assertThat(dto.authAttemptChallenge()).isEqualTo(TEST_CHALLENGE);
    assertThat(dto.authAttemptProofToken()).isEqualTo(TEST_PROOF_TOKEN);
    assertThat(dto.createdAt()).isEqualTo(TEST_CREATED_AT);
    assertThat(dto.expiresAt()).isEqualTo(TEST_EXPIRES_AT);
  }

  @Test
  @DisplayName("Should handle null values for optional fields")
  void shouldHandleNullValues() {
    // Act
    AuthAttemptDto dto =
        new AuthAttemptDto(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    // Assert
    assertThat(dto.authAttemptId()).isNull();
    assertThat(dto.enrollmentId()).isNull();
    assertThat(dto.authAttemptStatus()).isNull();
    assertThat(dto.authAttemptChallenge()).isNull();
    assertThat(dto.authAttemptProofToken()).isNull();
    assertThat(dto.createdAt()).isNull();
    assertThat(dto.expiresAt()).isNull();
  }

  @Test
  @DisplayName("Should handle all authentication attempt statuses")
  void shouldHandleAllAuthAttemptStatuses() {
    // Test each status
    for (AuthAttemptStatus status : AuthAttemptStatus.values()) {
      AuthAttemptDto dto =
          new AuthAttemptDto(
              TEST_AUTH_ATTEMPT_ID,
              TEST_ENROLLMENT_ID,
              status,
              TEST_CHALLENGE,
              TEST_PROOF_TOKEN,
              TEST_CREATED_AT,
              TEST_EXPIRES_AT,
              null,
              null,
              null,
              null,
              null,
              null,
              null);

      assertThat(dto.authAttemptStatus()).isEqualTo(status);
    }
  }

  @Test
  @DisplayName("Should handle edge case values for integer fields")
  void shouldHandleEdgeCaseIntegerValues() {
    // Test with minimum values
    AuthAttemptDto dtoMin =
        new AuthAttemptDto(
            Integer.MIN_VALUE,
            Integer.MIN_VALUE,
            TEST_STATUS,
            Integer.MIN_VALUE,
            TEST_PROOF_TOKEN,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(dtoMin.authAttemptId()).isEqualTo(Integer.MIN_VALUE);
    assertThat(dtoMin.enrollmentId()).isEqualTo(Integer.MIN_VALUE);
    assertThat(dtoMin.authAttemptChallenge()).isEqualTo(Integer.MIN_VALUE);

    // Test with maximum values
    AuthAttemptDto dtoMax =
        new AuthAttemptDto(
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            TEST_STATUS,
            Integer.MAX_VALUE,
            TEST_PROOF_TOKEN,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(dtoMax.authAttemptId()).isEqualTo(Integer.MAX_VALUE);
    assertThat(dtoMax.enrollmentId()).isEqualTo(Integer.MAX_VALUE);
    assertThat(dtoMax.authAttemptChallenge()).isEqualTo(Integer.MAX_VALUE);

    // Test with zero
    AuthAttemptDto dtoZero =
        new AuthAttemptDto(
            0,
            0,
            TEST_STATUS,
            0,
            TEST_PROOF_TOKEN,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(dtoZero.authAttemptId()).isEqualTo(0);
    assertThat(dtoZero.enrollmentId()).isEqualTo(0);
    assertThat(dtoZero.authAttemptChallenge()).isEqualTo(0);
  }

  @Test
  @DisplayName("Should handle empty and special strings for proof token")
  void shouldHandleSpecialStrings() {
    // Empty string
    AuthAttemptDto dtoEmpty =
        new AuthAttemptDto(
            TEST_AUTH_ATTEMPT_ID,
            TEST_ENROLLMENT_ID,
            TEST_STATUS,
            TEST_CHALLENGE,
            "",
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(dtoEmpty.authAttemptProofToken()).isEmpty();

    // Very long string
    String longToken = "A".repeat(1000);
    AuthAttemptDto dtoLong =
        new AuthAttemptDto(
            TEST_AUTH_ATTEMPT_ID,
            TEST_ENROLLMENT_ID,
            TEST_STATUS,
            TEST_CHALLENGE,
            longToken,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(dtoLong.authAttemptProofToken()).hasSize(1000);
  }

  @Test
  @DisplayName("Should handle different timezones for timestamps")
  void shouldHandleDifferentTimezones() {
    // UTC timezone
    OffsetDateTime utcTime = OffsetDateTime.of(2025, 1, 27, 10, 30, 0, 0, ZoneOffset.UTC);
    AuthAttemptDto dtoUtc =
        new AuthAttemptDto(
            TEST_AUTH_ATTEMPT_ID,
            TEST_ENROLLMENT_ID,
            TEST_STATUS,
            TEST_CHALLENGE,
            TEST_PROOF_TOKEN,
            utcTime,
            utcTime,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(dtoUtc.createdAt()).isEqualTo(utcTime);
    assertThat(dtoUtc.expiresAt()).isEqualTo(utcTime);

    // Negative offset timezone
    OffsetDateTime negativeOffset =
        OffsetDateTime.of(2025, 1, 27, 10, 30, 0, 0, ZoneOffset.ofHours(-5));
    AuthAttemptDto dtoNegative =
        new AuthAttemptDto(
            TEST_AUTH_ATTEMPT_ID,
            TEST_ENROLLMENT_ID,
            TEST_STATUS,
            TEST_CHALLENGE,
            TEST_PROOF_TOKEN,
            negativeOffset,
            negativeOffset,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(dtoNegative.createdAt()).isEqualTo(negativeOffset);
  }

  @Test
  @DisplayName("Should verify basic record equality (guaranteed by Java)")
  void shouldVerifyBasicRecordEquality() {
    // Arrange
    AuthAttemptDto dto1 =
        new AuthAttemptDto(
            TEST_AUTH_ATTEMPT_ID,
            TEST_ENROLLMENT_ID,
            TEST_STATUS,
            TEST_CHALLENGE,
            TEST_PROOF_TOKEN,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    AuthAttemptDto dto2 =
        new AuthAttemptDto(
            TEST_AUTH_ATTEMPT_ID,
            TEST_ENROLLMENT_ID,
            TEST_STATUS,
            TEST_CHALLENGE,
            TEST_PROOF_TOKEN,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    AuthAttemptDto dto3 =
        new AuthAttemptDto(
            999,
            TEST_ENROLLMENT_ID,
            TEST_STATUS,
            TEST_CHALLENGE,
            TEST_PROOF_TOKEN,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    // Assert - Records provide correct equals/hashCode implementations
    assertThat(dto1).isEqualTo(dto2);
    assertThat(dto1).isNotEqualTo(dto3);
    assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
  }

  @Test
  @DisplayName("Should verify toString contains all field values")
  void shouldVerifyToString() {
    // Arrange
    AuthAttemptDto dto =
        new AuthAttemptDto(
            TEST_AUTH_ATTEMPT_ID,
            TEST_ENROLLMENT_ID,
            TEST_STATUS,
            TEST_CHALLENGE,
            TEST_PROOF_TOKEN,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    // Act
    String toString = dto.toString();

    // Assert - Record toString includes all field values
    assertThat(toString).contains("authAttemptId=" + TEST_AUTH_ATTEMPT_ID);
    assertThat(toString).contains("enrollmentId=" + TEST_ENROLLMENT_ID);
    assertThat(toString).contains("authAttemptStatus=" + TEST_STATUS);
    assertThat(toString).contains("authAttemptChallenge=" + TEST_CHALLENGE);
    assertThat(toString).contains("authAttemptProofToken=" + TEST_PROOF_TOKEN);
  }

  @Test
  @DisplayName("Should create record with different status values")
  void shouldCreateRecordWithDifferentStatuses() {
    // Test PENDING status
    AuthAttemptDto pendingDto =
        new AuthAttemptDto(
            TEST_AUTH_ATTEMPT_ID,
            TEST_ENROLLMENT_ID,
            AuthAttemptStatus.PENDING,
            TEST_CHALLENGE,
            TEST_PROOF_TOKEN,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(pendingDto.authAttemptStatus()).isEqualTo(AuthAttemptStatus.PENDING);

    // Test ACCEPTED status
    AuthAttemptDto acceptedDto =
        new AuthAttemptDto(
            TEST_AUTH_ATTEMPT_ID,
            TEST_ENROLLMENT_ID,
            AuthAttemptStatus.ACCEPTED,
            TEST_CHALLENGE,
            TEST_PROOF_TOKEN,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(acceptedDto.authAttemptStatus()).isEqualTo(AuthAttemptStatus.ACCEPTED);

    // Test REJECTED status
    AuthAttemptDto rejectedDto =
        new AuthAttemptDto(
            TEST_AUTH_ATTEMPT_ID,
            TEST_ENROLLMENT_ID,
            AuthAttemptStatus.REJECTED,
            TEST_CHALLENGE,
            TEST_PROOF_TOKEN,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(rejectedDto.authAttemptStatus()).isEqualTo(AuthAttemptStatus.REJECTED);

    // Test EXPIRED status
    AuthAttemptDto expiredDto =
        new AuthAttemptDto(
            TEST_AUTH_ATTEMPT_ID,
            TEST_ENROLLMENT_ID,
            AuthAttemptStatus.EXPIRED,
            TEST_CHALLENGE,
            TEST_PROOF_TOKEN,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(expiredDto.authAttemptStatus()).isEqualTo(AuthAttemptStatus.EXPIRED);
  }
}
