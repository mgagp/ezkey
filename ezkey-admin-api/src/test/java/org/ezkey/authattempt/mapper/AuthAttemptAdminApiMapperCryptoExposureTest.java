/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: AuthAttemptAdminApiMapperCryptoExposureTest
 * Description: Auth-attempt proof token exposure through the Admin API, per attempt status.
 */

package org.ezkey.authattempt.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

/**
 * Asserts that {@link AuthAttemptAdminApiMapper} never returns the auth-attempt proof token to
 * Admin API callers (detail, list, wait) in any {@link AuthAttemptStatus} (#1180).
 *
 * <p>The protocol hands this token to the device once, on the first valid pending poll; the device
 * then signs {@code proofToken|accepted}. No other party receives it.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@DisplayName("AuthAttemptAdminApiMapper crypto exposure (#1180)")
class AuthAttemptAdminApiMapperCryptoExposureTest {

  private static final String PROOF_TOKEN = "auth-attempt-proof-token";

  private AuthAttemptAdminApiMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = Mappers.getMapper(AuthAttemptAdminApiMapper.class);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(AuthAttemptStatus.class)
  @DisplayName("mapping an attempt in any status yields no proof token field")
  void mappingWorksInEveryStatus(AuthAttemptStatus status) {
    AuthAttempt attempt = new AuthAttempt(100, PROOF_TOKEN);
    attempt.setAuthAttemptId(55);
    attempt.setAuthAttemptStatus(status);

    AuthAttemptDto dto = mapper.toDtoWithLabels(attempt, null, null);

    assertThat(dto.authAttemptId()).isEqualTo(55);
    assertThat(dto.toString()).doesNotContain(PROOF_TOKEN);
  }

  @Test
  @DisplayName("the auth-attempt proof token is not part of the DTO contract")
  void proofTokenIsNotInTheContract() {
    assertThat(AuthAttemptDto.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .doesNotContain("authAttemptProofToken");
  }
}
