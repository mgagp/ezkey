/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: IntegrationControllerMapperTest
 * Description: Unit tests for IntegrationControllerMapper list/detail tenant enrichment.
 */

package org.ezkey.integration.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.dto.IntegrationResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/**
 * Unit tests for {@link IntegrationControllerMapper}.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("IntegrationControllerMapper Tests")
class IntegrationControllerMapperTest {

  private IntegrationControllerMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = Mappers.getMapper(IntegrationControllerMapper.class);
  }

  @Test
  @DisplayName("toResponse maps tenantId and tenantName from joined tenant")
  void toResponse_mapsTenantName() {
    Tenant tenant = new Tenant("Acme Corp", "Tenant for integration tests");
    tenant.setTenantId(42);
    tenant.setActive(true);

    Integration integration = new Integration();
    integration.setId(7);
    integration.setCode("web-portal");
    integration.setName("Web Portal");
    integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
    integration.setCreatedAt(OffsetDateTime.of(2025, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC));
    integration.setTenant(tenant);

    IntegrationResponseDto dto = mapper.toResponse(integration);

    assertThat(dto.tenantId()).isEqualTo(42);
    assertThat(dto.tenantName()).isEqualTo("Acme Corp");
  }

  @Test
  @DisplayName("toResponse leaves tenantName null when tenant is absent")
  void toResponse_nullTenant_leavesTenantNameNull() {
    Integration integration = new Integration();
    integration.setId(1);
    integration.setCode("orphan");
    integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);

    IntegrationResponseDto dto = mapper.toResponse(integration);

    assertThat(dto.tenantId()).isNull();
    assertThat(dto.tenantName()).isNull();
  }
}
