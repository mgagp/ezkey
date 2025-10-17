/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrationServiceMapperTest
 * Description: Critical unit tests for IntegrationServiceMapper MapStruct transformations.
 */

package org.ezkey.integration.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.domain.IntegrationI18nCreate;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.IntegrationI18n;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Critical unit tests for {@link IntegrationServiceMapper}.
 *
 * <p>This test class provides comprehensive coverage of the IntegrationServiceMapper MapStruct
 * interface focusing on entity-to-DTO transformations and complex mapping scenarios including
 * nested I18n objects.
 *
 * <p><b>Critical Test Coverage:</b>
 *
 * <ul>
 *   <li>map() - Mapping IntegrationI18nCreate to IntegrationI18n entity
 *   <li>toEntity() - Mapping IntegrationCreateRequest to Integration entity
 *   <li>toCreateResponse() - Mapping Integration entity to IntegrationCreateResponse DTO
 *   <li>Field mapping validation - Correct field transformations and ignored fields
 *   <li>Null handling - Proper null value handling
 *   <li>Type conversion - Correct type transformations
 *   <li>Nested object mapping - I18n object mapping within integration
 * </ul>
 *
 * <p><b>Security Focus:</b> These tests validate that sensitive data is properly mapped and that
 * field transformations maintain data integrity and security.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see IntegrationServiceMapper
 * @see Integration
 * @see IntegrationCreateRequest
 * @see IntegrationCreateResponse
 * @see IntegrationI18nCreate
 * @see IntegrationI18n
 */
@DisplayName("Integration Service Mapper Critical Tests")
class IntegrationServiceMapperTest {

  private IntegrationServiceMapper integrationServiceMapper;

  private IntegrationCreateRequest integrationCreateRequest;
  private Integration integration;
  private IntegrationI18nCreate integrationI18nCreate;

  @BeforeEach
  void setUp() {
    // Create mapper instance directly (MapStruct generates implementation)
    integrationServiceMapper =
        org.mapstruct.factory.Mappers.getMapper(IntegrationServiceMapper.class);

    // Setup IntegrationCreateRequest
    integrationCreateRequest = new IntegrationCreateRequest();
    integrationCreateRequest.setLogo("test-logo.png");
    integrationCreateRequest.setI18n(List.of());

    // Setup IntegrationI18nCreate
    integrationI18nCreate = new IntegrationI18nCreate();
    integrationI18nCreate.setLanguage("en");
    integrationI18nCreate.setName("English Name");
    integrationI18nCreate.setDescription("English Description");

    // Setup Integration entity
    integration = new Integration();
    integration.setId(456);
    integration.setLogo("test-logo.png");
    integration.setActive(true);
    integration.setCreatedAt(OffsetDateTime.now().minusMinutes(10));
  }

  // ===== MAP I18N TESTS =====

  @Test
  @DisplayName("map() - Should map IntegrationI18nCreate to IntegrationI18n correctly")
  void map_WhenValidI18nCreate_ShouldMapCorrectly() {
    // Act
    IntegrationI18n result = integrationServiceMapper.map(integrationI18nCreate);

    // Assert
    assertNotNull(result);
    assertEquals(integrationI18nCreate.getLanguage(), result.getLanguage());
    assertEquals(integrationI18nCreate.getName(), result.getName());
    assertEquals(integrationI18nCreate.getDescription(), result.getDescription());

    // Verify ignored fields are null
    assertNull(result.getId());
    assertNull(result.getIntegration());
  }

  @Test
  @DisplayName("map() - Should handle null IntegrationI18nCreate")
  void map_WhenNullI18nCreate_ShouldReturnNull() {
    // Act
    IntegrationI18n result = integrationServiceMapper.map(null);

    // Assert
    assertNull(result);
  }

  @Test
  @DisplayName("map() - Should map with null values")
  void map_WhenI18nCreateWithNullValues_ShouldMapCorrectly() {
    // Arrange
    integrationI18nCreate.setLanguage(null);
    integrationI18nCreate.setName(null);
    integrationI18nCreate.setDescription(null);

    // Act
    IntegrationI18n result = integrationServiceMapper.map(integrationI18nCreate);

    // Assert
    assertNotNull(result);
    assertNull(result.getLanguage());
    assertNull(result.getName());
    assertNull(result.getDescription());
    assertNull(result.getId());
    assertNull(result.getIntegration());
  }

  // ===== TO ENTITY TESTS =====

  @Test
  @DisplayName("toEntity() - Should map IntegrationCreateRequest to Integration correctly")
  void toEntity_WhenValidRequest_ShouldMapCorrectly() {
    // Act
    Integration result = integrationServiceMapper.toEntity(integrationCreateRequest);

    // Assert
    assertNotNull(result);
    assertEquals(integrationCreateRequest.getLogo(), result.getLogo());
    assertEquals(integrationCreateRequest.getI18n().size(), result.getI18n().size());

    // Verify ignored fields are null
    assertNull(result.getId());
    assertNull(result.getCreatedAt());
    assertNull(result.getActive());
  }

  @Test
  @DisplayName("toEntity() - Should handle null IntegrationCreateRequest")
  void toEntity_WhenNullRequest_ShouldReturnNull() {
    // Act
    Integration result = integrationServiceMapper.toEntity(null);

    // Assert
    assertNull(result);
  }

  @Test
  @DisplayName("toEntity() - Should map with null values")
  void toEntity_WhenRequestWithNullValues_ShouldMapCorrectly() {
    // Arrange
    integrationCreateRequest.setLogo(null);
    integrationCreateRequest.setI18n(null);

    // Act
    Integration result = integrationServiceMapper.toEntity(integrationCreateRequest);

    // Assert
    assertNotNull(result);
    assertNull(result.getLogo());
    assertNull(result.getI18n());
    assertNull(result.getId());
    assertNull(result.getCreatedAt());
    assertNull(result.getActive());
  }

  // ===== TO CREATE RESPONSE TESTS =====

  @Test
  @DisplayName("toCreateResponse() - Should map Integration to IntegrationCreateResponse correctly")
  void toCreateResponse_WhenValidIntegration_ShouldMapCorrectly() {
    // Act
    IntegrationCreateResponse result = integrationServiceMapper.toCreateResponse(integration);

    // Assert
    assertNotNull(result);
    assertEquals(integration.getId(), result.getId());
  }

  @Test
  @DisplayName("toCreateResponse() - Should handle null Integration")
  void toCreateResponse_WhenNullIntegration_ShouldReturnNull() {
    // Act
    IntegrationCreateResponse result = integrationServiceMapper.toCreateResponse(null);

    // Assert
    assertNull(result);
  }

  @Test
  @DisplayName("toCreateResponse() - Should map with null values")
  void toCreateResponse_WhenIntegrationWithNullValues_ShouldMapCorrectly() {
    // Arrange
    integration.setId(null);

    // Act
    IntegrationCreateResponse result = integrationServiceMapper.toCreateResponse(integration);

    // Assert
    assertNotNull(result);
    assertNull(result.getId());
  }

  // ===== FIELD MAPPING VALIDATION TESTS =====

  @Test
  @DisplayName("toEntity() - Should ignore specified fields correctly")
  void toEntity_ShouldIgnoreSpecifiedFields() {
    // Act
    Integration result = integrationServiceMapper.toEntity(integrationCreateRequest);

    // Assert - Verify ignored fields are null
    assertNull(result.getId());
    assertNull(result.getCreatedAt());
    assertNull(result.getActive());
  }

  @Test
  @DisplayName("map() - Should ignore specified fields correctly")
  void map_ShouldIgnoreSpecifiedFields() {
    // Act
    IntegrationI18n result = integrationServiceMapper.map(integrationI18nCreate);

    // Assert - Verify ignored fields are null
    assertNull(result.getId());
    assertNull(result.getIntegration());
  }

  // ===== EDGE CASE TESTS =====

  @Test
  @DisplayName("toEntity() - Should handle empty string values")
  void toEntity_WithEmptyStringValues_ShouldMapCorrectly() {
    // Arrange
    integrationCreateRequest.setLogo("");

    // Act
    Integration result = integrationServiceMapper.toEntity(integrationCreateRequest);

    // Assert
    assertEquals("", result.getLogo());
  }

  @Test
  @DisplayName("map() - Should handle empty string values")
  void map_WithEmptyStringValues_ShouldMapCorrectly() {
    // Arrange
    integrationI18nCreate.setLanguage("");
    integrationI18nCreate.setName("");
    integrationI18nCreate.setDescription("");

    // Act
    IntegrationI18n result = integrationServiceMapper.map(integrationI18nCreate);

    // Assert
    assertEquals("", result.getLanguage());
    assertEquals("", result.getName());
    assertEquals("", result.getDescription());
  }

  @Test
  @DisplayName("toCreateResponse() - Should handle zero values")
  void toCreateResponse_WithZeroValues_ShouldMapCorrectly() {
    // Arrange
    integration.setId(0);

    // Act
    IntegrationCreateResponse result = integrationServiceMapper.toCreateResponse(integration);

    // Assert
    assertEquals(Integer.valueOf(0), result.getId());
  }
}
