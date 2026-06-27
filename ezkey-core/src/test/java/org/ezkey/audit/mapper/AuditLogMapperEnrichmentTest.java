/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.dto.AuditLogResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/** Unit tests for {@link AuditLogMapper} label enrichment. */
@DisplayName("AuditLogMapper enrichment")
class AuditLogMapperEnrichmentTest {

  private AuditLogMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = Mappers.getMapper(AuditLogMapper.class);
  }

  @Test
  @DisplayName("toResponseDtoWithLabels copies base fields and sets enrichment")
  void toResponseDtoWithLabels_populatesEnrichmentFields() {
    AuditLog entity = new AuditLog();
    entity.setAuditLogId(99L);
    entity.setAdminId(1);
    entity.setIntegrationId(2);
    entity.setEnrollmentId(3);

    AuditLogResponseDto dto =
        mapper.toResponseDtoWithLabels(
            entity, "tuteur", "alice", "Acme SSO", "Alice Chen", "Acme Corp");

    assertThat(dto.getAuditLogId()).isEqualTo(99L);
    assertThat(dto.getAdminId()).isEqualTo(1);
    assertThat(dto.getAdminUsername()).isEqualTo("tuteur");
    assertThat(dto.getTargetAdminUsername()).isEqualTo("alice");
    assertThat(dto.getIntegrationName()).isEqualTo("Acme SSO");
    assertThat(dto.getEnrollmentName()).isEqualTo("Alice Chen");
    assertThat(dto.getTenantName()).isEqualTo("Acme Corp");
  }
}
