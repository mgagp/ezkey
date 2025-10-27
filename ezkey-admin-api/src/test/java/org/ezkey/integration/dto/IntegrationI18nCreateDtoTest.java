/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: IntegrationI18nCreateDtoTest
 * Description: Unit tests for IntegrationI18nCreateDto record.
 */

package org.ezkey.integration.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IntegrationI18nCreateDto}.
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
@DisplayName("IntegrationI18nCreateDto Tests")
class IntegrationI18nCreateDtoTest {

    private static final String TEST_LANGUAGE = "en";
    private static final String TEST_NAME = "ACME Corporation";
    private static final String TEST_DESCRIPTION = "Secure authentication system for ACME applications";

    @Test
    @DisplayName("Should create record with all fields")
    void shouldCreateRecordWithAllFields() {
        // Act
        IntegrationI18nCreateDto dto = 
            new IntegrationI18nCreateDto(TEST_LANGUAGE, TEST_NAME, TEST_DESCRIPTION);

        // Assert
        assertThat(dto.language()).isEqualTo(TEST_LANGUAGE);
        assertThat(dto.name()).isEqualTo(TEST_NAME);
        assertThat(dto.description()).isEqualTo(TEST_DESCRIPTION);
    }

    @Test
    @DisplayName("Should create record with null description")
    void shouldCreateRecordWithNullDescription() {
        // Act
        IntegrationI18nCreateDto dto = 
            new IntegrationI18nCreateDto(TEST_LANGUAGE, TEST_NAME, null);

        // Assert
        assertThat(dto.language()).isEqualTo(TEST_LANGUAGE);
        assertThat(dto.name()).isEqualTo(TEST_NAME);
        assertThat(dto.description()).isNull();
    }

    @Test
    @DisplayName("Should support different language codes")
    void shouldSupportDifferentLanguageCodes() {
        // Arrange
        String[] languages = {"en", "fr", "es", "de", "ja"};

        // Act & Assert
        for (String lang : languages) {
            IntegrationI18nCreateDto dto = 
                new IntegrationI18nCreateDto(lang, TEST_NAME, TEST_DESCRIPTION);
            assertThat(dto.language()).isEqualTo(lang);
        }
    }

    @Test
    @DisplayName("Should have proper equality behavior")
    void shouldHaveProperEqualityBehavior() {
        // Arrange
        IntegrationI18nCreateDto dto1 = 
            new IntegrationI18nCreateDto(TEST_LANGUAGE, TEST_NAME, TEST_DESCRIPTION);
        IntegrationI18nCreateDto dto2 = 
            new IntegrationI18nCreateDto(TEST_LANGUAGE, TEST_NAME, TEST_DESCRIPTION);
        IntegrationI18nCreateDto dto3 = 
            new IntegrationI18nCreateDto("fr", TEST_NAME, TEST_DESCRIPTION);

        // Assert
        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1).isNotEqualTo(dto3);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToStringRepresentation() {
        // Arrange
        IntegrationI18nCreateDto dto = 
            new IntegrationI18nCreateDto(TEST_LANGUAGE, TEST_NAME, TEST_DESCRIPTION);

        // Act
        String toString = dto.toString();

        // Assert
        assertThat(toString)
            .contains("IntegrationI18nCreateDto")
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
        IntegrationI18nCreateDto dto = 
            new IntegrationI18nCreateDto("fr", frenchName, frenchDescription);

        // Assert
        assertThat(dto.language()).isEqualTo("fr");
        assertThat(dto.name()).isEqualTo(frenchName);
        assertThat(dto.description()).isEqualTo(frenchDescription);
    }

    @Test
    @DisplayName("Should create record with Spanish localization")
    void shouldCreateRecordWithSpanishLocalization() {
        // Arrange
        String spanishName = "Corporación ACME";
        String spanishDescription = "Sistema de autenticación seguro para aplicaciones ACME";

        // Act
        IntegrationI18nCreateDto dto = 
            new IntegrationI18nCreateDto("es", spanishName, spanishDescription);

        // Assert
        assertThat(dto.language()).isEqualTo("es");
        assertThat(dto.name()).isEqualTo(spanishName);
        assertThat(dto.description()).isEqualTo(spanishDescription);
    }

    @Test
    @DisplayName("Should handle empty strings")
    void shouldHandleEmptyStrings() {
        // Act
        IntegrationI18nCreateDto dto = 
            new IntegrationI18nCreateDto("", "", "");

        // Assert
        assertThat(dto.language()).isEmpty();
        assertThat(dto.name()).isEmpty();
        assertThat(dto.description()).isEmpty();
    }

    @Test
    @DisplayName("Should handle special characters in content")
    void shouldHandleSpecialCharactersInContent() {
        // Arrange
        String nameWithSpecialChars = "ACME Corp™ & Co.";
        String descWithSpecialChars = "Système d'authentification <sécurisé>";

        // Act
        IntegrationI18nCreateDto dto = 
            new IntegrationI18nCreateDto(TEST_LANGUAGE, nameWithSpecialChars, descWithSpecialChars);

        // Assert
        assertThat(dto.name()).isEqualTo(nameWithSpecialChars);
        assertThat(dto.description()).isEqualTo(descWithSpecialChars);
    }
}
