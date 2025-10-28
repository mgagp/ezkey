/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: IntegrationResponseDtoTest
 * Description: Unit tests for IntegrationResponseDto record.
 */

package org.ezkey.integration.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
 *   <li>Nested i18n list handling
 *   <li>Null handling
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("IntegrationResponseDto Tests")
class IntegrationResponseDtoTest {

  private static final Integer TEST_ID = 1;
  private static final String TEST_LOGO = "https://example.com/logo.png";
  private static final Boolean TEST_ACTIVE = true;
  private static final OffsetDateTime TEST_CREATED_AT =
      OffsetDateTime.of(2025, 1, 15, 10, 30, 0, 0, ZoneOffset.ofHours(1));

  @Test
  @DisplayName("Should create record with all fields")
  void shouldCreateRecordWithAllFields() {
    // Arrange
    List<IntegrationI18nResponseDto> i18nList = createTestI18nList();

    // Act
    IntegrationResponseDto dto =
        new IntegrationResponseDto(TEST_ID, TEST_LOGO, TEST_ACTIVE, TEST_CREATED_AT, i18nList);

    // Assert
    assertThat(dto.id()).isEqualTo(TEST_ID);
    assertThat(dto.logo()).isEqualTo(TEST_LOGO);
    assertThat(dto.active()).isEqualTo(TEST_ACTIVE);
    assertThat(dto.createdAt()).isEqualTo(TEST_CREATED_AT);
    assertThat(dto.i18n()).hasSize(2);
  }

  @Test
  @DisplayName("Should create record with empty i18n list")
  void shouldCreateRecordWithEmptyI18nList() {
    // Act
    IntegrationResponseDto dto =
        new IntegrationResponseDto(
            TEST_ID, TEST_LOGO, TEST_ACTIVE, TEST_CREATED_AT, Collections.emptyList());

    // Assert
    assertThat(dto.id()).isEqualTo(TEST_ID);
    assertThat(dto.i18n()).isEmpty();
  }

  @Test
  @DisplayName("Should create record with null i18n list")
  void shouldCreateRecordWithNullI18nList() {
    // Act
    IntegrationResponseDto dto =
        new IntegrationResponseDto(TEST_ID, TEST_LOGO, TEST_ACTIVE, TEST_CREATED_AT, null);

    // Assert
    assertThat(dto.id()).isEqualTo(TEST_ID);
    assertThat(dto.i18n()).isNull();
  }

  @Test
  @DisplayName("Should create record with inactive status")
  void shouldCreateRecordWithInactiveStatus() {
    // Act
    IntegrationResponseDto dto =
        new IntegrationResponseDto(
            TEST_ID, TEST_LOGO, false, TEST_CREATED_AT, Collections.emptyList());

    // Assert
    assertThat(dto.active()).isFalse();
  }

  @Test
  @DisplayName("Should create record with null logo")
  void shouldCreateRecordWithNullLogo() {
    // Act
    IntegrationResponseDto dto =
        new IntegrationResponseDto(
            TEST_ID, null, TEST_ACTIVE, TEST_CREATED_AT, Collections.emptyList());

    // Assert
    assertThat(dto.logo()).isNull();
    assertThat(dto.id()).isEqualTo(TEST_ID);
  }

  @Test
  @DisplayName("Should have proper equality behavior")
  void shouldHaveProperEqualityBehavior() {
    // Arrange
    List<IntegrationI18nResponseDto> i18nList = createTestI18nList();
    IntegrationResponseDto dto1 =
        new IntegrationResponseDto(TEST_ID, TEST_LOGO, TEST_ACTIVE, TEST_CREATED_AT, i18nList);
    IntegrationResponseDto dto2 =
        new IntegrationResponseDto(TEST_ID, TEST_LOGO, TEST_ACTIVE, TEST_CREATED_AT, i18nList);
    IntegrationResponseDto dto3 =
        new IntegrationResponseDto(2, TEST_LOGO, TEST_ACTIVE, TEST_CREATED_AT, i18nList);

    // Assert
    assertThat(dto1).isEqualTo(dto2);
    assertThat(dto1).isNotEqualTo(dto3);
    assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
  }

  @Test
  @DisplayName("Should have meaningful toString representation")
  void shouldHaveMeaningfulToStringRepresentation() {
    // Arrange
    List<IntegrationI18nResponseDto> i18nList = createTestI18nList();
    IntegrationResponseDto dto =
        new IntegrationResponseDto(TEST_ID, TEST_LOGO, TEST_ACTIVE, TEST_CREATED_AT, i18nList);

    // Act
    String toString = dto.toString();

    // Assert
    assertThat(toString)
        .contains("IntegrationResponseDto")
        .contains(TEST_ID.toString())
        .contains(TEST_LOGO);
  }

  @Test
  @DisplayName("Should handle multiple i18n entries")
  void shouldHandleMultipleI18nEntries() {
    // Arrange
    List<IntegrationI18nResponseDto> i18nList =
        Arrays.asList(
            new IntegrationI18nResponseDto(1, "en", "ACME Corp", "English description"),
            new IntegrationI18nResponseDto(2, "fr", "Corp ACME", "Description française"),
            new IntegrationI18nResponseDto(3, "es", "Corp ACME", "Descripción española"),
            new IntegrationI18nResponseDto(4, "de", "ACME Unternehmen", "Deutsche Beschreibung"));

    // Act
    IntegrationResponseDto dto =
        new IntegrationResponseDto(TEST_ID, TEST_LOGO, TEST_ACTIVE, TEST_CREATED_AT, i18nList);

    // Assert
    assertThat(dto.i18n()).hasSize(4);
    assertThat(dto.i18n().get(0).language()).isEqualTo("en");
    assertThat(dto.i18n().get(1).language()).isEqualTo("fr");
    assertThat(dto.i18n().get(2).language()).isEqualTo("es");
    assertThat(dto.i18n().get(3).language()).isEqualTo("de");
  }

  @Test
  @DisplayName("Should preserve timezone information")
  void shouldPreserveTimezoneInformation() {
    // Arrange
    OffsetDateTime utcTime = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime estTime = OffsetDateTime.now(ZoneOffset.ofHours(-5));
    OffsetDateTime jstTime = OffsetDateTime.now(ZoneOffset.ofHours(9));

    // Act
    IntegrationResponseDto dtoUtc =
        new IntegrationResponseDto(1, TEST_LOGO, TEST_ACTIVE, utcTime, Collections.emptyList());
    IntegrationResponseDto dtoEst =
        new IntegrationResponseDto(2, TEST_LOGO, TEST_ACTIVE, estTime, Collections.emptyList());
    IntegrationResponseDto dtoJst =
        new IntegrationResponseDto(3, TEST_LOGO, TEST_ACTIVE, jstTime, Collections.emptyList());

    // Assert
    assertThat(dtoUtc.createdAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(dtoEst.createdAt().getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
    assertThat(dtoJst.createdAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
  }

  @Test
  @DisplayName("Should handle different logo URL formats")
  void shouldHandleDifferentLogoUrlFormats() {
    // Arrange
    String[] logoUrls = {
      "https://example.com/logo.png",
      "http://example.com/logo.jpg",
      "/static/images/logo.svg",
      "data:image/png;base64,iVBORw0KG...",
      "logo.png"
    };

    // Act & Assert
    for (int i = 0; i < logoUrls.length; i++) {
      IntegrationResponseDto dto =
          new IntegrationResponseDto(
              i + 1, logoUrls[i], TEST_ACTIVE, TEST_CREATED_AT, Collections.emptyList());
      assertThat(dto.logo()).isEqualTo(logoUrls[i]);
    }
  }

  @Test
  @DisplayName("Should support immutable i18n list")
  void shouldSupportImmutableI18nList() {
    // Arrange
    List<IntegrationI18nResponseDto> i18nList = createTestI18nList();
    IntegrationResponseDto dto =
        new IntegrationResponseDto(TEST_ID, TEST_LOGO, TEST_ACTIVE, TEST_CREATED_AT, i18nList);

    // Act
    List<IntegrationI18nResponseDto> retrievedList = dto.i18n();

    // Assert
    assertThat(retrievedList).hasSize(2);
    assertThat(retrievedList).isEqualTo(i18nList);
  }

  @Test
  @DisplayName("Should create record with different IDs")
  void shouldCreateRecordWithDifferentIds() {
    // Arrange
    Integer[] ids = {1, 42, 100, 999, 12345};

    // Act & Assert
    for (Integer id : ids) {
      IntegrationResponseDto dto =
          new IntegrationResponseDto(
              id, TEST_LOGO, TEST_ACTIVE, TEST_CREATED_AT, Collections.emptyList());
      assertThat(dto.id()).isEqualTo(id);
    }
  }

  @Test
  @DisplayName("Should handle null createdAt")
  void shouldHandleNullCreatedAt() {
    // Act
    IntegrationResponseDto dto =
        new IntegrationResponseDto(TEST_ID, TEST_LOGO, TEST_ACTIVE, null, Collections.emptyList());

    // Assert
    assertThat(dto.createdAt()).isNull();
  }

  @Test
  @DisplayName("Should handle null active status")
  void shouldHandleNullActiveStatus() {
    // Act
    IntegrationResponseDto dto =
        new IntegrationResponseDto(
            TEST_ID, TEST_LOGO, null, TEST_CREATED_AT, Collections.emptyList());

    // Assert
    assertThat(dto.active()).isNull();
  }

  // Helper method to create test i18n list
  private List<IntegrationI18nResponseDto> createTestI18nList() {
    return Arrays.asList(
        new IntegrationI18nResponseDto(1, "en", "ACME Corporation", "Secure authentication system"),
        new IntegrationI18nResponseDto(
            2, "fr", "Corporation ACME", "Système d'authentification sécurisé"));
  }
}
