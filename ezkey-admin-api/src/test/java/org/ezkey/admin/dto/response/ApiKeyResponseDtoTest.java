/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: ApiKeyResponseDtoTest
 * Description: Unit tests for ApiKeyResponseDto record.
 */

package org.ezkey.admin.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ApiKeyResponseDto}.
 *
 * <p>Tests verify proper behavior of the record including:
 *
 * <ul>
 *   <li>Record construction and accessor methods
 *   <li>Handling of nullable fields (description, expiresAt, lastUsedAt, etc.)
 *   <li>Equality and hashCode behavior
 *   <li>Active vs revoked state representation
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("ApiKeyResponseDto Tests")
class ApiKeyResponseDtoTest {

  private static final Integer TEST_API_KEY_ID = 42;
  private static final Integer TEST_INTEGRATION_ID = 123;
  private static final String TEST_INTEGRATION_KEY = "ezkey_ikey_a1b2c3d4e5f6g7h8i9j0";
  private static final String TEST_DESCRIPTION = "Production Server API Key";
  private static final Boolean TEST_ACTIVE = true;
  private static final OffsetDateTime TEST_CREATED_AT =
      OffsetDateTime.of(2025, 10, 17, 10, 30, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime TEST_EXPIRES_AT =
      OffsetDateTime.of(2025, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);
  private static final OffsetDateTime TEST_LAST_USED_AT =
      OffsetDateTime.of(2025, 10, 17, 15, 45, 30, 0, ZoneOffset.UTC);
  private static final String[] TEST_IP_WHITELIST = {"192.168.1.0/24", "10.0.0.100"};
  private static final OffsetDateTime TEST_REVOKED_AT = null;
  private static final String TEST_REVOKED_BY = null;

  @Test
  @DisplayName("Should create record with all fields for active key")
  void shouldCreateRecordWithAllFieldsForActiveKey() {
    // Arrange & Act
    ApiKeyResponseDto dto =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            TEST_DESCRIPTION,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_LAST_USED_AT,
            TEST_IP_WHITELIST,
            TEST_REVOKED_AT,
            TEST_REVOKED_BY);

    // Assert
    assertThat(dto.apiKeyId()).isEqualTo(TEST_API_KEY_ID);
    assertThat(dto.integrationId()).isEqualTo(TEST_INTEGRATION_ID);
    assertThat(dto.integrationKey()).isEqualTo(TEST_INTEGRATION_KEY);
    assertThat(dto.description()).isEqualTo(TEST_DESCRIPTION);
    assertThat(dto.active()).isTrue();
    assertThat(dto.createdAt()).isEqualTo(TEST_CREATED_AT);
    assertThat(dto.expiresAt()).isEqualTo(TEST_EXPIRES_AT);
    assertThat(dto.lastUsedAt()).isEqualTo(TEST_LAST_USED_AT);
    assertThat(dto.ipWhitelist()).containsExactly("192.168.1.0/24", "10.0.0.100");
    assertThat(dto.revokedAt()).isNull();
    assertThat(dto.revokedByUsername()).isNull();
  }

  @Test
  @DisplayName("Should create record for revoked key with revocation details")
  void shouldCreateRecordForRevokedKeyWithRevocationDetails() {
    // Arrange
    OffsetDateTime revokedAt = OffsetDateTime.of(2025, 10, 18, 9, 0, 0, 0, ZoneOffset.UTC);
    String revokedBy = "admin";

    // Act
    ApiKeyResponseDto dto =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            TEST_DESCRIPTION,
            false, // inactive
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_LAST_USED_AT,
            TEST_IP_WHITELIST,
            revokedAt,
            revokedBy);

    // Assert
    assertThat(dto.active()).isFalse();
    assertThat(dto.revokedAt()).isEqualTo(revokedAt);
    assertThat(dto.revokedByUsername()).isEqualTo(revokedBy);
  }

  @Test
  @DisplayName("Should create record with minimal fields (null optionals)")
  void shouldCreateRecordWithMinimalFields() {
    // Arrange & Act
    ApiKeyResponseDto dto =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            null, // no description
            TEST_ACTIVE,
            TEST_CREATED_AT,
            null, // no expiration
            null, // never used
            null, // no IP whitelist
            null, // not revoked
            null // no revoker
            );

    // Assert
    assertThat(dto.apiKeyId()).isEqualTo(TEST_API_KEY_ID);
    assertThat(dto.integrationId()).isEqualTo(TEST_INTEGRATION_ID);
    assertThat(dto.integrationKey()).isEqualTo(TEST_INTEGRATION_KEY);
    assertThat(dto.description()).isNull();
    assertThat(dto.active()).isTrue();
    assertThat(dto.createdAt()).isEqualTo(TEST_CREATED_AT);
    assertThat(dto.expiresAt()).isNull();
    assertThat(dto.lastUsedAt()).isNull();
    assertThat(dto.ipWhitelist()).isNull();
    assertThat(dto.revokedAt()).isNull();
    assertThat(dto.revokedByUsername()).isNull();
  }

  @Test
  @DisplayName("Should represent never-used key correctly")
  void shouldRepresentNeverUsedKeyCorrectly() {
    // Arrange & Act
    ApiKeyResponseDto dto =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            TEST_DESCRIPTION,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            null, // never used
            TEST_IP_WHITELIST,
            TEST_REVOKED_AT,
            TEST_REVOKED_BY);

    // Assert
    assertThat(dto.lastUsedAt()).isNull();
    assertThat(dto.active()).isTrue();
  }

  @Test
  @DisplayName("Should represent key without expiration correctly")
  void shouldRepresentKeyWithoutExpirationCorrectly() {
    // Arrange & Act
    ApiKeyResponseDto dto =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            TEST_DESCRIPTION,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            null, // no expiration
            TEST_LAST_USED_AT,
            TEST_IP_WHITELIST,
            TEST_REVOKED_AT,
            TEST_REVOKED_BY);

    // Assert
    assertThat(dto.expiresAt()).isNull();
    assertThat(dto.active()).isTrue();
  }

  @Test
  @DisplayName("Should represent key without IP restrictions correctly")
  void shouldRepresentKeyWithoutIpRestrictionsCorrectly() {
    // Arrange & Act
    ApiKeyResponseDto dto =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            TEST_DESCRIPTION,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_LAST_USED_AT,
            null, // no IP restrictions
            TEST_REVOKED_AT,
            TEST_REVOKED_BY);

    // Assert
    assertThat(dto.ipWhitelist()).isNull();
  }

  @Test
  @DisplayName("Should implement equals() correctly for records")
  void shouldImplementEqualsCorrectly() {
    // Arrange
    ApiKeyResponseDto dto1 =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            TEST_DESCRIPTION,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_LAST_USED_AT,
            TEST_IP_WHITELIST,
            TEST_REVOKED_AT,
            TEST_REVOKED_BY);

    ApiKeyResponseDto dto2 =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            TEST_DESCRIPTION,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_LAST_USED_AT,
            TEST_IP_WHITELIST,
            TEST_REVOKED_AT,
            TEST_REVOKED_BY);

    ApiKeyResponseDto dto3 =
        new ApiKeyResponseDto(
            999, // different ID
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            TEST_DESCRIPTION,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_LAST_USED_AT,
            TEST_IP_WHITELIST,
            TEST_REVOKED_AT,
            TEST_REVOKED_BY);

    // Assert
    assertThat(dto1).isEqualTo(dto2);
    assertThat(dto1).isNotEqualTo(dto3);
    assertThat(dto1).isNotEqualTo(null);
  }

  @Test
  @DisplayName("Should implement hashCode() correctly for records")
  void shouldImplementHashCodeCorrectly() {
    // Arrange
    ApiKeyResponseDto dto1 =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            TEST_DESCRIPTION,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_LAST_USED_AT,
            TEST_IP_WHITELIST,
            TEST_REVOKED_AT,
            TEST_REVOKED_BY);

    ApiKeyResponseDto dto2 =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            TEST_DESCRIPTION,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_LAST_USED_AT,
            TEST_IP_WHITELIST,
            TEST_REVOKED_AT,
            TEST_REVOKED_BY);

    // Assert
    assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
  }

  @Test
  @DisplayName("Should handle empty IP whitelist array")
  void shouldHandleEmptyIpWhitelistArray() {
    // Arrange
    String[] emptyWhitelist = new String[0];

    // Act
    ApiKeyResponseDto dto =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            TEST_DESCRIPTION,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_LAST_USED_AT,
            emptyWhitelist,
            TEST_REVOKED_AT,
            TEST_REVOKED_BY);

    // Assert
    assertThat(dto.ipWhitelist()).isNotNull().isEmpty();
  }

  @Test
  @DisplayName("Should preserve IP whitelist order")
  void shouldPreserveIpWhitelistOrder() {
    // Arrange
    String[] orderedWhitelist = {"10.0.0.1", "192.168.1.0/24", "172.16.0.0/16"};

    // Act
    ApiKeyResponseDto dto =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            TEST_DESCRIPTION,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_LAST_USED_AT,
            orderedWhitelist,
            TEST_REVOKED_AT,
            TEST_REVOKED_BY);

    // Assert
    assertThat(dto.ipWhitelist()).containsExactly("10.0.0.1", "192.168.1.0/24", "172.16.0.0/16");
  }

  @Test
  @DisplayName("Should represent complete revocation audit trail")
  void shouldRepresentCompleteRevocationAuditTrail() {
    // Arrange
    OffsetDateTime revokedAt = OffsetDateTime.of(2025, 10, 19, 14, 30, 0, 0, ZoneOffset.UTC);
    String revokedBy = "security_admin";

    // Act
    ApiKeyResponseDto dto =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            "Compromised key - emergency revocation",
            false,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_LAST_USED_AT,
            TEST_IP_WHITELIST,
            revokedAt,
            revokedBy);

    // Assert
    assertThat(dto.active()).isFalse();
    assertThat(dto.revokedAt()).isEqualTo(revokedAt);
    assertThat(dto.revokedByUsername()).isEqualTo(revokedBy);
    assertThat(dto.description()).contains("Compromised");
  }

  @Test
  @DisplayName("Should contain all integration context")
  void shouldContainAllIntegrationContext() {
    // Arrange & Act
    ApiKeyResponseDto dto =
        new ApiKeyResponseDto(
            TEST_API_KEY_ID,
            TEST_INTEGRATION_ID,
            TEST_INTEGRATION_KEY,
            TEST_DESCRIPTION,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_EXPIRES_AT,
            TEST_LAST_USED_AT,
            TEST_IP_WHITELIST,
            TEST_REVOKED_AT,
            TEST_REVOKED_BY);

    // Assert - verify integration context is complete
    assertThat(dto.integrationId()).isEqualTo(TEST_INTEGRATION_ID);
    assertThat(dto.integrationKey()).isEqualTo(TEST_INTEGRATION_KEY);
    assertThat(dto.integrationKey()).startsWith("ezkey_ikey_");
  }
}
