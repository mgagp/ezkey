/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrityBootstrapServiceTest
 * Description: Unit tests for Integrity atelier bootstrap assembly.
 */
package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.ezkey.admin.dto.response.IntegrityBootstrapResponseDto;
import org.ezkey.audit.integrity.AuditChainProperties;
import org.ezkey.audit.integrity.NightlyIntegrityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link IntegrityBootstrapService}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IntegrityBootstrapService Tests")
class IntegrityBootstrapServiceTest {

  @Mock private EzkeyRuntimeProfileResolver runtimeProfileResolver;

  private AuditChainProperties auditChainProperties;
  private NightlyIntegrityProperties nightlyIntegrityProperties;
  private IntegrityBootstrapService service;

  @BeforeEach
  void setUp() {
    auditChainProperties = new AuditChainProperties();
    auditChainProperties.setEnabled(true);
    nightlyIntegrityProperties = new NightlyIntegrityProperties();
    nightlyIntegrityProperties.setEnabled(true);
    service =
        new IntegrityBootstrapService(
            runtimeProfileResolver, auditChainProperties, nightlyIntegrityProperties);
  }

  @Test
  @DisplayName("Should expose integrity profile with monitoring enabled")
  void build_integrity_withMonitoringOn() {
    when(runtimeProfileResolver.resolve())
        .thenReturn(EzkeyRuntimeProfileResolver.PROFILE_INTEGRITY);

    IntegrityBootstrapResponseDto dto = service.build();

    assertThat(dto.runtimeProfile()).isEqualTo(EzkeyRuntimeProfileResolver.PROFILE_INTEGRITY);
    assertThat(dto.chainCheckpointsEnabled()).isTrue();
    assertThat(dto.nightlyValidationEnabled()).isTrue();
  }

  @Test
  @DisplayName("Should expose base profile with monitoring flags from config (not derived)")
  void build_base_withMonitoringOff() {
    when(runtimeProfileResolver.resolve()).thenReturn(EzkeyRuntimeProfileResolver.PROFILE_BASE);
    auditChainProperties.setEnabled(false);
    nightlyIntegrityProperties.setEnabled(false);

    IntegrityBootstrapResponseDto dto = service.build();

    assertThat(dto.runtimeProfile()).isEqualTo(EzkeyRuntimeProfileResolver.PROFILE_BASE);
    assertThat(dto.chainCheckpointsEnabled()).isFalse();
    assertThat(dto.nightlyValidationEnabled()).isFalse();
  }

  @Test
  @DisplayName(
      "Should keep integrity profile name when config disables monitoring (dual-source honesty)")
  void build_integrity_withMonitoringOffViaConfig() {
    when(runtimeProfileResolver.resolve())
        .thenReturn(EzkeyRuntimeProfileResolver.PROFILE_INTEGRITY);
    auditChainProperties.setEnabled(false);
    nightlyIntegrityProperties.setEnabled(true);

    IntegrityBootstrapResponseDto dto = service.build();

    assertThat(dto.runtimeProfile()).isEqualTo(EzkeyRuntimeProfileResolver.PROFILE_INTEGRITY);
    assertThat(dto.chainCheckpointsEnabled()).isFalse();
    assertThat(dto.nightlyValidationEnabled()).isTrue();
  }
}
