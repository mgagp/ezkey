/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.domain.entity.Integration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Critical unit tests for {@link IntegrationServiceMapper}.
 *
 * <p>This test class provides coverage of the IntegrationServiceMapper MapStruct interface for
 * entity-to-DTO transformations and mapping of flat name/description fields.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("Integration Service Mapper Critical Tests")
class IntegrationServiceMapperTest {

  private IntegrationServiceMapper integrationServiceMapper;

  private IntegrationCreateRequest integrationCreateRequest;
  private Integration integration;

  @BeforeEach
  void setUp() {
    integrationServiceMapper =
        org.mapstruct.factory.Mappers.getMapper(IntegrationServiceMapper.class);

    integrationCreateRequest = new IntegrationCreateRequest();
    integrationCreateRequest.setCode("test-code");
    integrationCreateRequest.setName("Test Name");
    integrationCreateRequest.setDescription("Test Description");

    integration = new Integration();
    integration.setId(456);
    integration.setCode("test-code");
    integration.setName("Test Name");
    integration.setDescription("Test Description");
    integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
    integration.setCreatedAt(OffsetDateTime.now().minusMinutes(10));
  }

  @Test
  @DisplayName("toEntity() - Should map IntegrationCreateRequest to Integration correctly")
  void toEntity_WhenValidRequest_ShouldMapCorrectly() {
    Integration result = integrationServiceMapper.toEntity(integrationCreateRequest);

    assertNotNull(result);
    assertEquals(integrationCreateRequest.getCode(), result.getCode());
    assertEquals(integrationCreateRequest.getName(), result.getName());
    assertEquals(integrationCreateRequest.getDescription(), result.getDescription());
    assertNull(result.getId());
    assertNull(result.getCreatedAt());
    assertTrue(result.isOperational());
  }

  @Test
  @DisplayName("toEntity() - Should handle null IntegrationCreateRequest")
  void toEntity_WhenNullRequest_ShouldReturnNull() {
    Integration result = integrationServiceMapper.toEntity(null);
    assertNull(result);
  }

  @Test
  @DisplayName("toEntity() - Should map with null name and description")
  void toEntity_WhenRequestWithNullNameAndDescription_ShouldMapCorrectly() {
    integrationCreateRequest.setName(null);
    integrationCreateRequest.setDescription(null);

    Integration result = integrationServiceMapper.toEntity(integrationCreateRequest);

    assertNotNull(result);
    assertNull(result.getName());
    assertNull(result.getDescription());
  }

  @Test
  @DisplayName("toCreateResponse() - Should map Integration to IntegrationCreateResponse correctly")
  void toCreateResponse_WhenValidIntegration_ShouldMapCorrectly() {
    IntegrationCreateResponse result = integrationServiceMapper.toCreateResponse(integration);

    assertNotNull(result);
    assertEquals(integration.getId(), result.getId());
    assertEquals(integration.getCode(), result.getCode());
  }

  @Test
  @DisplayName("toCreateResponse() - Should handle null Integration")
  void toCreateResponse_WhenNullIntegration_ShouldReturnNull() {
    IntegrationCreateResponse result = integrationServiceMapper.toCreateResponse(null);
    assertNull(result);
  }

  @Test
  @DisplayName("toEntity() - Should ignore specified fields correctly")
  void toEntity_ShouldIgnoreSpecifiedFields() {
    Integration result = integrationServiceMapper.toEntity(integrationCreateRequest);

    assertNull(result.getId());
    assertNull(result.getCreatedAt());
    assertTrue(result.isOperational());
  }
}
