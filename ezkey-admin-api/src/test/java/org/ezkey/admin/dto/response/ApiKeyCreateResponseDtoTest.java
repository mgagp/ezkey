/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: ApiKeyCreateResponseDtoTest
 * Description: Unit tests for ApiKeyCreateResponseDto record.
 */

package org.ezkey.admin.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ApiKeyCreateResponseDto}.
 *
 * <p>Tests verify proper behavior of the record including:
 *
 * <ul>
 *   <li>Record construction and accessor methods
 *   <li>Security warning message generation
 *   <li>toString() masking of secret key
 *   <li>Equality and hashCode behavior
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("ApiKeyCreateResponseDto Tests")
class ApiKeyCreateResponseDtoTest {

  private static final Integer TEST_API_KEY_ID = 42;
  private static final String TEST_INTEGRATION_KEY = "ezkey_ikey_a1b2c3d4e5f6g7h8i9j0";
  private static final String TEST_SECRET_KEY =
      "ezkey_skey_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0";
  private static final String TEST_DESCRIPTION = "Production Server API Key";
  private static final OffsetDateTime TEST_CREATED_AT =
      OffsetDateTime.of(2025, 10, 17, 10, 30, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime TEST_EXPIRES_AT =
      OffsetDateTime.of(2025, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);
  private static final String[] TEST_IP_WHITELIST = {"192.168.1.0/24", "10.0.0.100"};
  private static final String TEST_WARNING =
      "IMPORTANT: Save the secret key now. It will not be shown again.";

  @Test
  @DisplayName("Should create record with all fields")
  void shouldCreateRecordWithAllFields() {
    // Arrange & Act
    ApiKeyCreateResponseDto dto =
        new ApiKeyCreateResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_KEY,
            TEST_SECRET_KEY,
            TEST_DESCRIPTION,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_IP_WHITELIST,
            TEST_WARNING);

    // Assert
    assertThat(dto.apiKeyId()).isEqualTo(TEST_API_KEY_ID);
    assertThat(dto.integrationKey()).isEqualTo(TEST_INTEGRATION_KEY);
    assertThat(dto.secretKey()).isEqualTo(TEST_SECRET_KEY);
    assertThat(dto.description()).isEqualTo(TEST_DESCRIPTION);
    assertThat(dto.createdAt()).isEqualTo(TEST_CREATED_AT);
    assertThat(dto.expiresAt()).isEqualTo(TEST_EXPIRES_AT);
    assertThat(dto.ipWhitelist()).containsExactly("192.168.1.0/24", "10.0.0.100");
    assertThat(dto.warning()).isEqualTo(TEST_WARNING);
  }

  @Test
  @DisplayName("Should create record with null optional fields")
  void shouldCreateRecordWithNullOptionalFields() {
    // Arrange & Act
    ApiKeyCreateResponseDto dto =
        new ApiKeyCreateResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_KEY,
            TEST_SECRET_KEY,
            null, // description
            TEST_CREATED_AT,
            null, // expiresAt
            null, // ipWhitelist
            TEST_WARNING);

    // Assert
    assertThat(dto.apiKeyId()).isEqualTo(TEST_API_KEY_ID);
    assertThat(dto.integrationKey()).isEqualTo(TEST_INTEGRATION_KEY);
    assertThat(dto.secretKey()).isEqualTo(TEST_SECRET_KEY);
    assertThat(dto.description()).isNull();
    assertThat(dto.createdAt()).isEqualTo(TEST_CREATED_AT);
    assertThat(dto.expiresAt()).isNull();
    assertThat(dto.ipWhitelist()).isNull();
    assertThat(dto.warning()).isEqualTo(TEST_WARNING);
  }

  @Test
  @DisplayName("Should return consistent security warning message")
  void shouldReturnConsistentSecurityWarning() {
    // Arrange
    ApiKeyCreateResponseDto dto =
        new ApiKeyCreateResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_KEY,
            TEST_SECRET_KEY,
            TEST_DESCRIPTION,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_IP_WHITELIST,
            TEST_WARNING);

    // Act
    String warning1 = dto.warning();
    String warning2 = dto.warning();

    // Assert
    assertThat(warning1)
        .isNotNull()
        .isEqualTo("IMPORTANT: Save the secret key now. It will not be shown again.");
    assertThat(warning2).isEqualTo(warning1);
  }

  @Test
  @DisplayName("Should mask secret key in toString()")
  void shouldMaskSecretKeyInToString() {
    // Arrange
    ApiKeyCreateResponseDto dto =
        new ApiKeyCreateResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_KEY,
            TEST_SECRET_KEY,
            TEST_DESCRIPTION,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_IP_WHITELIST,
            TEST_WARNING);

    // Act
    String toString = dto.toString();

    // Assert
    assertThat(toString)
        .contains("ApiKeyCreateResponseDto")
        .contains("apiKeyId=" + TEST_API_KEY_ID)
        .contains("integrationKey='" + TEST_INTEGRATION_KEY + "'")
        .contains("secretKey='***MASKED***'")
        .contains("description='" + TEST_DESCRIPTION + "'")
        .doesNotContain(TEST_SECRET_KEY);
  }

  @Test
  @DisplayName("Should not expose secret key in toString() even when description is null")
  void shouldNotExposeSecretKeyInToStringWithNullDescription() {
    // Arrange
    ApiKeyCreateResponseDto dto =
        new ApiKeyCreateResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_KEY,
            TEST_SECRET_KEY,
            null,
            TEST_CREATED_AT,
            null,
            null,
            TEST_WARNING);

    // Act
    String toString = dto.toString();

    // Assert
    assertThat(toString).contains("***MASKED***").doesNotContain(TEST_SECRET_KEY);
  }

  @Test
  @DisplayName("Should implement equals() correctly for records")
  void shouldImplementEqualsCorrectly() {
    // Arrange
    ApiKeyCreateResponseDto dto1 =
        new ApiKeyCreateResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_KEY,
            TEST_SECRET_KEY,
            TEST_DESCRIPTION,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_IP_WHITELIST,
            TEST_WARNING);

    ApiKeyCreateResponseDto dto2 =
        new ApiKeyCreateResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_KEY,
            TEST_SECRET_KEY,
            TEST_DESCRIPTION,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_IP_WHITELIST,
            TEST_WARNING);

    ApiKeyCreateResponseDto dto3 =
        new ApiKeyCreateResponseDto(
            999, // different ID
            TEST_INTEGRATION_KEY,
            TEST_SECRET_KEY,
            TEST_DESCRIPTION,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_IP_WHITELIST,
            TEST_WARNING);

    // Assert
    assertThat(dto1).isEqualTo(dto2);
    assertThat(dto1).isNotEqualTo(dto3);
    assertThat(dto1).isNotEqualTo(null);
  }

  @Test
  @DisplayName("Should implement hashCode() correctly for records")
  void shouldImplementHashCodeCorrectly() {
    // Arrange
    ApiKeyCreateResponseDto dto1 =
        new ApiKeyCreateResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_KEY,
            TEST_SECRET_KEY,
            TEST_DESCRIPTION,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_IP_WHITELIST,
            TEST_WARNING);

    ApiKeyCreateResponseDto dto2 =
        new ApiKeyCreateResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_KEY,
            TEST_SECRET_KEY,
            TEST_DESCRIPTION,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_IP_WHITELIST,
            TEST_WARNING);

    // Assert
    assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
  }

  @Test
  @DisplayName("Should handle empty IP whitelist array")
  void shouldHandleEmptyIpWhitelistArray() {
    // Arrange
    String[] emptyWhitelist = new String[0];

    // Act
    ApiKeyCreateResponseDto dto =
        new ApiKeyCreateResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_KEY,
            TEST_SECRET_KEY,
            TEST_DESCRIPTION,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            emptyWhitelist,
            TEST_WARNING);

    // Assert
    assertThat(dto.ipWhitelist()).isNotNull().isEmpty();
  }

  @Test
  @DisplayName("Should preserve IP whitelist order")
  void shouldPreserveIpWhitelistOrder() {
    // Arrange
    String[] orderedWhitelist = {"10.0.0.1", "192.168.1.0/24", "172.16.0.0/16"};

    // Act
    ApiKeyCreateResponseDto dto =
        new ApiKeyCreateResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_KEY,
            TEST_SECRET_KEY,
            TEST_DESCRIPTION,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            orderedWhitelist,
            TEST_WARNING);

    // Assert
    assertThat(dto.ipWhitelist()).containsExactly("10.0.0.1", "192.168.1.0/24", "172.16.0.0/16");
  }
}
