/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: AuthAttemptIntegrationApiMapperCryptoExposureTest
 * Description: Auth-attempt proof token exposure through the Integration API, per attempt status.
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
 * Asserts that {@link AuthAttemptIntegrationApiMapper} never returns the auth-attempt proof token
 * to integrating applications (wait, cancel) in any {@link AuthAttemptStatus} (#1180).
 *
 * <p>A wait that times out returns the attempt while it is still {@code PENDING}; the token must
 * not leak there either. No other party than the device receives it.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@DisplayName("AuthAttemptIntegrationApiMapper crypto exposure (#1180)")
class AuthAttemptIntegrationApiMapperCryptoExposureTest {

  private static final String PROOF_TOKEN = "auth-attempt-proof-token";

  private AuthAttemptIntegrationApiMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = Mappers.getMapper(AuthAttemptIntegrationApiMapper.class);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(AuthAttemptStatus.class)
  @DisplayName("mapping an attempt in any status yields no proof token field")
  void mappingWorksInEveryStatus(AuthAttemptStatus status) {
    AuthAttempt attempt = new AuthAttempt(100, PROOF_TOKEN);
    attempt.setAuthAttemptId(55);
    attempt.setAuthAttemptStatus(status);

    AuthAttemptDto dto = mapper.toDto(attempt);

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
