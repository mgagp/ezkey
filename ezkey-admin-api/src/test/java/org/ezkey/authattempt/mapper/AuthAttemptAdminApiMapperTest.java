/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: AuthAttemptAdminApiMapperTest
 * Description: Unit tests for AuthAttemptAdminApiMapper list enrichment.
 */

package org.ezkey.authattempt.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/**
 * Unit tests for {@link AuthAttemptAdminApiMapper}.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("AuthAttemptAdminApiMapper Tests")
class AuthAttemptAdminApiMapperTest {

  private AuthAttemptAdminApiMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = Mappers.getMapper(AuthAttemptAdminApiMapper.class);
  }

  @Test
  @DisplayName("toDtoWithLabels maps enrollment, integration, and tenant labels")
  void toDtoWithLabels_mapsLabels() {
    Tenant tenant = new Tenant("Acme Corp", "Tenant for auth attempt tests");
    tenant.setTenantId(42);
    tenant.setActive(true);

    Integration integration = new Integration();
    integration.setId(7);
    integration.setCode("web-portal");
    integration.setName("Web Portal");
    integration.setTenant(tenant);

    Enrollment enrollment = new Enrollment(7, "Alice Phone", "proof-token");
    enrollment.setEnrollmentId(100);

    AuthAttempt attempt = new AuthAttempt(100, "attempt-proof");
    attempt.setAuthAttemptId(55);
    attempt.setAuthAttemptStatus(AuthAttemptStatus.ACCEPTED);

    AuthAttemptDto dto = mapper.toDtoWithLabels(attempt, enrollment, integration);

    assertThat(dto.authAttemptId()).isEqualTo(55);
    assertThat(dto.enrollmentId()).isEqualTo(100);
    assertThat(dto.enrollmentName()).isEqualTo("Alice Phone");
    assertThat(dto.integrationId()).isEqualTo(7);
    assertThat(dto.integrationName()).isEqualTo("Web Portal");
    assertThat(dto.tenantId()).isEqualTo(42);
    assertThat(dto.tenantName()).isEqualTo("Acme Corp");
  }

  @Test
  @DisplayName("toDtoWithLabels leaves enrichment null when context is absent")
  void toDtoWithLabels_nullContext() {
    AuthAttempt attempt = new AuthAttempt(100, "attempt-proof");
    attempt.setAuthAttemptId(55);

    AuthAttemptDto dto = mapper.toDtoWithLabels(attempt, null, null);

    assertThat(dto.integrationName()).isNull();
    assertThat(dto.enrollmentName()).isNull();
    assertThat(dto.tenantId()).isNull();
    assertThat(dto.tenantName()).isNull();
  }
}
