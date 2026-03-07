/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * Test Class: IntegrationResponseDtoTest Description: Unit tests for IntegrationResponseDto record.
 */

package org.ezkey.integration.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IntegrationResponseDto}.
 *
 * <p>Tests verify proper behavior of the record including:
 *
 * <ul>
 *   <li>Record construction and accessor methods
 *   <li>Equality and hashCode behavior
 *   <li>toString representation
 *   <li>Name and description handling
 *   <li>Null handling
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("IntegrationResponseDto Tests")
class IntegrationResponseDtoTest {

  private static final Integer TEST_ID = 1;

  private static final String TEST_CODE = "test-integration";

  private static final Integer TEST_TENANT_ID = 2;

  private static final Boolean TEST_ACTIVE = true;

  private static final OffsetDateTime TEST_CREATED_AT =
      OffsetDateTime.of(2025, 1, 15, 10, 30, 0, 0, ZoneOffset.ofHours(1));

  private static final String TEST_NAME = "Test Integration";

  private static final String TEST_DESCRIPTION = "Test description";

  @Test
  @DisplayName("Should create record with all fields")
  void shouldCreateRecordWithAllFields() {
    IntegrationResponseDto dto =
        new IntegrationResponseDto(
            TEST_ID,
            TEST_CODE,
            TEST_TENANT_ID,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_NAME,
            TEST_DESCRIPTION,
            null);

    assertThat(dto.id()).isEqualTo(TEST_ID);
    assertThat(dto.code()).isEqualTo(TEST_CODE);
    assertThat(dto.tenantId()).isEqualTo(TEST_TENANT_ID);
    assertThat(dto.active()).isEqualTo(TEST_ACTIVE);
    assertThat(dto.createdAt()).isEqualTo(TEST_CREATED_AT);
    assertThat(dto.name()).isEqualTo(TEST_NAME);
    assertThat(dto.description()).isEqualTo(TEST_DESCRIPTION);
    assertThat(dto.isSystemIntegration()).isNull();
  }

  @Test
  @DisplayName("Should create record with null name and description")
  void shouldCreateRecordWithNullNameAndDescription() {
    IntegrationResponseDto dto =
        new IntegrationResponseDto(
            TEST_ID, TEST_CODE, TEST_TENANT_ID, TEST_ACTIVE, TEST_CREATED_AT, null, null, null);

    assertThat(dto.id()).isEqualTo(TEST_ID);
    assertThat(dto.name()).isNull();
    assertThat(dto.description()).isNull();
  }

  @Test
  @DisplayName("Should create record with null description")
  void shouldCreateRecordWithNullDescription() {
    IntegrationResponseDto dto =
        new IntegrationResponseDto(
            TEST_ID,
            TEST_CODE,
            TEST_TENANT_ID,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_NAME,
            null,
            null);

    assertThat(dto.name()).isEqualTo(TEST_NAME);
    assertThat(dto.description()).isNull();
  }

  @Test
  @DisplayName("Should create record with inactive status")
  void shouldCreateRecordWithInactiveStatus() {
    IntegrationResponseDto dto =
        new IntegrationResponseDto(
            TEST_ID,
            TEST_CODE,
            TEST_TENANT_ID,
            false,
            TEST_CREATED_AT,
            TEST_NAME,
            TEST_DESCRIPTION,
            null);

    assertThat(dto.active()).isFalse();
  }

  @Test
  @DisplayName("Should have proper equality behavior")
  void shouldHaveProperEqualityBehavior() {
    IntegrationResponseDto dto1 =
        new IntegrationResponseDto(
            TEST_ID,
            TEST_CODE,
            TEST_TENANT_ID,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_NAME,
            TEST_DESCRIPTION,
            null);
    IntegrationResponseDto dto2 =
        new IntegrationResponseDto(
            TEST_ID,
            TEST_CODE,
            TEST_TENANT_ID,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_NAME,
            TEST_DESCRIPTION,
            null);
    IntegrationResponseDto dto3 =
        new IntegrationResponseDto(
            2,
            TEST_CODE,
            TEST_TENANT_ID,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_NAME,
            TEST_DESCRIPTION,
            null);

    assertThat(dto1).isEqualTo(dto2);
    assertThat(dto1).isNotEqualTo(dto3);
    assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
  }

  @Test
  @DisplayName("Should have meaningful toString representation")
  void shouldHaveMeaningfulToStringRepresentation() {
    IntegrationResponseDto dto =
        new IntegrationResponseDto(
            TEST_ID,
            TEST_CODE,
            TEST_TENANT_ID,
            TEST_ACTIVE,
            TEST_CREATED_AT,
            TEST_NAME,
            TEST_DESCRIPTION,
            null);

    String toString = dto.toString();

    assertThat(toString).contains("IntegrationResponseDto").contains(TEST_ID.toString());
  }

  @Test
  @DisplayName("Should preserve timezone information")
  void shouldPreserveTimezoneInformation() {
    OffsetDateTime utcTime = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime estTime = OffsetDateTime.now(ZoneOffset.ofHours(-5));
    OffsetDateTime jstTime = OffsetDateTime.now(ZoneOffset.ofHours(9));

    IntegrationResponseDto dtoUtc =
        new IntegrationResponseDto(
            1, TEST_CODE, TEST_TENANT_ID, TEST_ACTIVE, utcTime, TEST_NAME, TEST_DESCRIPTION, null);
    IntegrationResponseDto dtoEst =
        new IntegrationResponseDto(
            2, TEST_CODE, TEST_TENANT_ID, TEST_ACTIVE, estTime, TEST_NAME, TEST_DESCRIPTION, null);
    IntegrationResponseDto dtoJst =
        new IntegrationResponseDto(
            3, TEST_CODE, TEST_TENANT_ID, TEST_ACTIVE, jstTime, TEST_NAME, TEST_DESCRIPTION, null);

    assertThat(dtoUtc.createdAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(dtoEst.createdAt().getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
    assertThat(dtoJst.createdAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
  }

  @Test
  @DisplayName("Should create record with different IDs")
  void shouldCreateRecordWithDifferentIds() {
    Integer[] ids = {1, 42, 100, 999, 12345};

    for (Integer id : ids) {
      IntegrationResponseDto dto =
          new IntegrationResponseDto(
              id,
              TEST_CODE,
              TEST_TENANT_ID,
              TEST_ACTIVE,
              TEST_CREATED_AT,
              TEST_NAME,
              TEST_DESCRIPTION,
              null);
      assertThat(dto.id()).isEqualTo(id);
    }
  }

  @Test
  @DisplayName("Should handle null createdAt")
  void shouldHandleNullCreatedAt() {
    IntegrationResponseDto dto =
        new IntegrationResponseDto(
            TEST_ID,
            TEST_CODE,
            TEST_TENANT_ID,
            TEST_ACTIVE,
            null,
            TEST_NAME,
            TEST_DESCRIPTION,
            null);

    assertThat(dto.createdAt()).isNull();
  }

  @Test
  @DisplayName("Should handle null active status")
  void shouldHandleNullActiveStatus() {
    IntegrationResponseDto dto =
        new IntegrationResponseDto(
            TEST_ID,
            TEST_CODE,
            TEST_TENANT_ID,
            null,
            TEST_CREATED_AT,
            TEST_NAME,
            TEST_DESCRIPTION,
            null);

    assertThat(dto.active()).isNull();
  }
}
