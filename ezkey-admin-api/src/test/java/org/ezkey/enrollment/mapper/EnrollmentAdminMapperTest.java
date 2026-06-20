/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: EnrollmentAdminMapperTest
 * Description: Unit tests for EnrollmentAdminMapper integration/tenant enrichment.
 */

package org.ezkey.enrollment.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.dto.EnrollmentResponseDto;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/**
 * Unit tests for {@link EnrollmentAdminMapper}.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("EnrollmentAdminMapper Tests")
class EnrollmentAdminMapperTest {

  private EnrollmentAdminMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = Mappers.getMapper(EnrollmentAdminMapper.class);
  }

  @Test
  @DisplayName("toResponseWithIntegration maps integration and tenant labels")
  void toResponseWithIntegration_mapsLabels() {
    Tenant tenant = new Tenant("Acme Corp", "Tenant for enrollment tests");
    tenant.setTenantId(42);
    tenant.setActive(true);

    Integration integration = new Integration();
    integration.setId(7);
    integration.setCode("web-portal");
    integration.setName("Web Portal");
    integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
    integration.setTenant(tenant);

    Enrollment enrollment = new Enrollment(7, "Alice Phone", "proof-token");
    enrollment.setEnrollmentId(100);
    enrollment.setStatus(EnrollmentStatus.VERIFIED);
    enrollment.setActive(true);

    EnrollmentResponseDto dto = mapper.toResponseWithIntegration(enrollment, integration);

    assertThat(dto.integrationName()).isEqualTo("Web Portal");
    assertThat(dto.tenantId()).isEqualTo(42);
    assertThat(dto.tenantName()).isEqualTo("Acme Corp");
    assertThat(dto.operational()).isTrue();
  }

  @Test
  @DisplayName("toResponseWithIntegration leaves labels null when integration is absent")
  void toResponseWithIntegration_nullIntegration() {
    Enrollment enrollment = new Enrollment(7, "Alice Phone", "proof-token");
    enrollment.setEnrollmentId(100);
    enrollment.setStatus(EnrollmentStatus.CREATED);

    EnrollmentResponseDto dto = mapper.toResponseWithIntegration(enrollment, null);

    assertThat(dto.integrationName()).isNull();
    assertThat(dto.tenantId()).isNull();
    assertThat(dto.tenantName()).isNull();
  }
}
