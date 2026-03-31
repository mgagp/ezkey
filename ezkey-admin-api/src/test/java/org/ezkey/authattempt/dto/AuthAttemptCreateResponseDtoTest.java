/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * Test Class: AuthAttemptCreateResponseDtoTest Description: Unit tests for
 * AuthAttemptCreateResponseDto record.
 */

package org.ezkey.authattempt.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AuthAttemptCreateResponseDto}.
 *
 * <p>Tests verify proper behavior of the record including:
 *
 * <ul>
 *   <li>Record construction and accessor methods
 *   <li>Null handling
 *   <li>Edge case values
 * </ul>
 *
 * <p><b>Note:</b> Records automatically provide correct implementations of equals(), hashCode(),
 * toString(), and immutability guarantees. These behaviors are tested minimally as they are
 * guaranteed by the Java language specification.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("AuthAttemptCreateResponseDto Tests")
class AuthAttemptCreateResponseDtoTest {

  private static final Integer TEST_AUTH_ATTEMPT_ID = 11;

  @Test
  @DisplayName("Should create record with auth attempt ID")
  void shouldCreateRecordWithAuthAttemptId() {
    // Arrange
    OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(120);

    // Act
    AuthAttemptCreateResponseDto dto =
        new AuthAttemptCreateResponseDto(TEST_AUTH_ATTEMPT_ID, null, 120, expiresAt, null, null);

    // Assert
    assertThat(dto.authAttemptId()).isEqualTo(TEST_AUTH_ATTEMPT_ID);
    assertThat(dto.authAttemptChallenge()).isNull();
    assertThat(dto.timeoutSeconds()).isEqualTo(120);
    assertThat(dto.expiresAt()).isEqualTo(expiresAt);
  }

  @Test
  @DisplayName("Should handle null auth attempt ID")
  void shouldHandleNullAuthAttemptId() {
    // Arrange
    OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(120);

    // Act
    AuthAttemptCreateResponseDto dto =
        new AuthAttemptCreateResponseDto(null, null, 120, expiresAt, null, null);

    // Assert
    assertThat(dto.authAttemptId()).isNull();
    assertThat(dto.authAttemptChallenge()).isNull();
    assertThat(dto.timeoutSeconds()).isEqualTo(120);
    assertThat(dto.expiresAt()).isEqualTo(expiresAt);
  }

  @Test
  @DisplayName("Should create record with challenge code")
  void shouldCreateRecordWithChallengeCode() {
    // Arrange
    OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(120);

    // Act
    AuthAttemptCreateResponseDto dto =
        new AuthAttemptCreateResponseDto(TEST_AUTH_ATTEMPT_ID, 42, 120, expiresAt, null, null);

    // Assert
    assertThat(dto.authAttemptId()).isEqualTo(TEST_AUTH_ATTEMPT_ID);
    assertThat(dto.authAttemptChallenge()).isEqualTo(42);
    assertThat(dto.timeoutSeconds()).isEqualTo(120);
    assertThat(dto.expiresAt()).isEqualTo(expiresAt);
  }

  @Test
  @DisplayName("Should handle edge case values")
  void shouldHandleEdgeCaseValues() {
    // Arrange
    OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(120);

    // Test with minimum value
    AuthAttemptCreateResponseDto dtoMin =
        new AuthAttemptCreateResponseDto(Integer.MIN_VALUE, null, 120, expiresAt, null, null);
    assertThat(dtoMin.authAttemptId()).isEqualTo(Integer.MIN_VALUE);

    // Test with maximum value
    AuthAttemptCreateResponseDto dtoMax =
        new AuthAttemptCreateResponseDto(Integer.MAX_VALUE, null, 120, expiresAt, null, null);
    assertThat(dtoMax.authAttemptId()).isEqualTo(Integer.MAX_VALUE);

    // Test with zero
    AuthAttemptCreateResponseDto dtoZero =
        new AuthAttemptCreateResponseDto(0, null, 120, expiresAt, null, null);
    assertThat(dtoZero.authAttemptId()).isEqualTo(0);

    // Test with negative value
    AuthAttemptCreateResponseDto dtoNeg =
        new AuthAttemptCreateResponseDto(-1, null, 120, expiresAt, null, null);
    assertThat(dtoNeg.authAttemptId()).isEqualTo(-1);

    // Test challenge code edge cases
    AuthAttemptCreateResponseDto dtoChallengeMin =
        new AuthAttemptCreateResponseDto(1, 0, 120, expiresAt, null, null);
    assertThat(dtoChallengeMin.authAttemptChallenge()).isEqualTo(0);

    AuthAttemptCreateResponseDto dtoChallengeMax =
        new AuthAttemptCreateResponseDto(1, 99, 120, expiresAt, null, null);
    assertThat(dtoChallengeMax.authAttemptChallenge()).isEqualTo(99);
  }

  @Test
  @DisplayName("Should verify basic record equality (guaranteed by Java)")
  void shouldVerifyBasicRecordEquality() {
    // Arrange
    OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(120);
    AuthAttemptCreateResponseDto dto1 =
        new AuthAttemptCreateResponseDto(TEST_AUTH_ATTEMPT_ID, null, 120, expiresAt, null, null);
    AuthAttemptCreateResponseDto dto2 =
        new AuthAttemptCreateResponseDto(TEST_AUTH_ATTEMPT_ID, null, 120, expiresAt, null, null);
    AuthAttemptCreateResponseDto dto3 =
        new AuthAttemptCreateResponseDto(999, null, 120, expiresAt, null, null);
    AuthAttemptCreateResponseDto dto4 =
        new AuthAttemptCreateResponseDto(TEST_AUTH_ATTEMPT_ID, 42, 120, expiresAt, null, null);

    // Assert - Records provide correct equals/hashCode implementations
    assertThat(dto1).isEqualTo(dto2);
    assertThat(dto1).isNotEqualTo(dto3);
    assertThat(dto1).isNotEqualTo(dto4); // Different challenge code
    assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
  }
}
