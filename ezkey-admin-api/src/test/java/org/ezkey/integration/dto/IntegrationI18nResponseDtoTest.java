/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: IntegrationI18nResponseDtoTest
 * Description: Unit tests for IntegrationI18nResponseDto record.
 */

package org.ezkey.integration.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IntegrationI18nResponseDto}.
 *
 * <p>Tests verify proper behavior of the record including:
 * <ul>
 *   <li>Record construction and accessor methods</li>
 *   <li>Equality and hashCode behavior</li>
 *   <li>toString representation</li>
 *   <li>Null handling</li>
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("IntegrationI18nResponseDto Tests")
class IntegrationI18nResponseDtoTest {

    private static final Integer TEST_ID = 1;
    private static final String TEST_LANGUAGE = "en";
    private static final String TEST_NAME = "ACME Corporation";
    private static final String TEST_DESCRIPTION = "Secure authentication system for ACME applications";

    @Test
    @DisplayName("Should create record with all fields")
    void shouldCreateRecordWithAllFields() {
        // Act
        IntegrationI18nResponseDto dto = 
            new IntegrationI18nResponseDto(TEST_ID, TEST_LANGUAGE, TEST_NAME, TEST_DESCRIPTION);

        // Assert
        assertThat(dto.id()).isEqualTo(TEST_ID);
        assertThat(dto.language()).isEqualTo(TEST_LANGUAGE);
        assertThat(dto.name()).isEqualTo(TEST_NAME);
        assertThat(dto.description()).isEqualTo(TEST_DESCRIPTION);
    }

    @Test
    @DisplayName("Should create record with null description")
    void shouldCreateRecordWithNullDescription() {
        // Act
        IntegrationI18nResponseDto dto = 
            new IntegrationI18nResponseDto(TEST_ID, TEST_LANGUAGE, TEST_NAME, null);

        // Assert
        assertThat(dto.id()).isEqualTo(TEST_ID);
        assertThat(dto.language()).isEqualTo(TEST_LANGUAGE);
        assertThat(dto.name()).isEqualTo(TEST_NAME);
        assertThat(dto.description()).isNull();
    }

    @Test
    @DisplayName("Should support different language codes")
    void shouldSupportDifferentLanguageCodes() {
        // Arrange
        String[] languages = {"en", "fr", "es", "de", "ja", "zh", "pt", "ru"};

        // Act & Assert
        for (String lang : languages) {
            IntegrationI18nResponseDto dto = 
                new IntegrationI18nResponseDto(TEST_ID, lang, TEST_NAME, TEST_DESCRIPTION);
            assertThat(dto.language()).isEqualTo(lang);
        }
    }

    @Test
    @DisplayName("Should have proper equality behavior")
    void shouldHaveProperEqualityBehavior() {
        // Arrange
        IntegrationI18nResponseDto dto1 = 
            new IntegrationI18nResponseDto(TEST_ID, TEST_LANGUAGE, TEST_NAME, TEST_DESCRIPTION);
        IntegrationI18nResponseDto dto2 = 
            new IntegrationI18nResponseDto(TEST_ID, TEST_LANGUAGE, TEST_NAME, TEST_DESCRIPTION);
        IntegrationI18nResponseDto dto3 = 
            new IntegrationI18nResponseDto(2, TEST_LANGUAGE, TEST_NAME, TEST_DESCRIPTION);

        // Assert
        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1).isNotEqualTo(dto3);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToStringRepresentation() {
        // Arrange
        IntegrationI18nResponseDto dto = 
            new IntegrationI18nResponseDto(TEST_ID, TEST_LANGUAGE, TEST_NAME, TEST_DESCRIPTION);

        // Act
        String toString = dto.toString();

        // Assert
        assertThat(toString)
            .contains("IntegrationI18nResponseDto")
            .contains(TEST_ID.toString())
            .contains(TEST_LANGUAGE)
            .contains(TEST_NAME)
            .contains(TEST_DESCRIPTION);
    }

    @Test
    @DisplayName("Should create record with French localization")
    void shouldCreateRecordWithFrenchLocalization() {
        // Arrange
        String frenchName = "Corporation ACME";
        String frenchDescription = "Système d'authentification sécurisé pour les applications ACME";

        // Act
        IntegrationI18nResponseDto dto = 
            new IntegrationI18nResponseDto(TEST_ID, "fr", frenchName, frenchDescription);

        // Assert
        assertThat(dto.id()).isEqualTo(TEST_ID);
        assertThat(dto.language()).isEqualTo("fr");
        assertThat(dto.name()).isEqualTo(frenchName);
        assertThat(dto.description()).isEqualTo(frenchDescription);
    }

    @Test
    @DisplayName("Should create record with different IDs")
    void shouldCreateRecordWithDifferentIds() {
        // Arrange
        Integer[] ids = {1, 42, 100, 999, 12345};

        // Act & Assert
        for (Integer id : ids) {
            IntegrationI18nResponseDto dto = 
                new IntegrationI18nResponseDto(id, TEST_LANGUAGE, TEST_NAME, TEST_DESCRIPTION);
            assertThat(dto.id()).isEqualTo(id);
        }
    }

    @Test
    @DisplayName("Should handle empty strings")
    void shouldHandleEmptyStrings() {
        // Act
        IntegrationI18nResponseDto dto = 
            new IntegrationI18nResponseDto(TEST_ID, "", "", "");

        // Assert
        assertThat(dto.id()).isEqualTo(TEST_ID);
        assertThat(dto.language()).isEmpty();
        assertThat(dto.name()).isEmpty();
        assertThat(dto.description()).isEmpty();
    }

    @Test
    @DisplayName("Should handle special characters in content")
    void shouldHandleSpecialCharactersInContent() {
        // Arrange
        String nameWithSpecialChars = "ACME Corp™ & Co. <Official>";
        String descWithSpecialChars = "Système d'authentification «sécurisé» — 100% fiable!";

        // Act
        IntegrationI18nResponseDto dto = 
            new IntegrationI18nResponseDto(TEST_ID, TEST_LANGUAGE, nameWithSpecialChars, descWithSpecialChars);

        // Assert
        assertThat(dto.name()).isEqualTo(nameWithSpecialChars);
        assertThat(dto.description()).isEqualTo(descWithSpecialChars);
    }

    @Test
    @DisplayName("Should handle null ID")
    void shouldHandleNullId() {
        // Act
        IntegrationI18nResponseDto dto = 
            new IntegrationI18nResponseDto(null, TEST_LANGUAGE, TEST_NAME, TEST_DESCRIPTION);

        // Assert
        assertThat(dto.id()).isNull();
        assertThat(dto.language()).isEqualTo(TEST_LANGUAGE);
    }

    @Test
    @DisplayName("Should support long descriptions")
    void shouldSupportLongDescriptions() {
        // Arrange
        String longDescription = "This is a very long description that contains a lot of text. ".repeat(10);

        // Act
        IntegrationI18nResponseDto dto = 
            new IntegrationI18nResponseDto(TEST_ID, TEST_LANGUAGE, TEST_NAME, longDescription);

        // Assert
        assertThat(dto.description()).isEqualTo(longDescription);
        assertThat(dto.description().length()).isGreaterThan(500);
    }
}
