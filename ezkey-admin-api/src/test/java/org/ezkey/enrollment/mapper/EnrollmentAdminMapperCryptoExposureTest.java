/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: EnrollmentAdminMapperCryptoExposureTest
 * Description: Crypto material exposed by the Admin API enrollment response, per lifecycle state.
 */

package org.ezkey.enrollment.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.dto.EnrollmentResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

/**
 * Characterizes which cryptographic fields {@link EnrollmentAdminMapper} returns to Admin API
 * callers (GET and search by enrollment) in each {@link EnrollmentStatus} (#1180).
 *
 * <p>Both GET and search go through {@link EnrollmentAdminMapper#toResponse(Enrollment)}, so this
 * mapper is the single exposure point for the Admin UI enrollment detail and list.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@DisplayName("EnrollmentAdminMapper crypto exposure (#1180)")
class EnrollmentAdminMapperCryptoExposureTest {

  private static final String PROOF_TOKEN = "enrollment-proof-token";
  private static final int CHALLENGE = 436767;
  private static final String INTEGRATION_PUBLIC_KEY = "integration-public-key";
  private static final String DEVICE_PUBLIC_KEY = "device-public-key";

  private EnrollmentAdminMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = Mappers.getMapper(EnrollmentAdminMapper.class);
  }

  private static Enrollment enrollmentIn(EnrollmentStatus status) {
    Enrollment enrollment = new Enrollment(7, "Alice Phone", PROOF_TOKEN);
    enrollment.setEnrollmentId(100);
    enrollment.setStatus(status);
    enrollment.setEnrollmentChallenge(CHALLENGE);
    enrollment.setIntegrationPublicKey(INTEGRATION_PUBLIC_KEY);
    enrollment.setDevicePublicKey(DEVICE_PUBLIC_KEY);
    return enrollment;
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(EnrollmentStatus.class)
  @DisplayName("characterization: enrollment proof token and challenge are returned in every state")
  void proofTokenAndChallengeReturnedInEveryState(EnrollmentStatus status) {
    EnrollmentResponseDto dto =
        mapper.toResponseWithIntegrationAndAdminUsernames(enrollmentIn(status), null, Map.of());

    assertThat(dto.enrollmentProofToken()).isEqualTo(PROOF_TOKEN);
    assertThat(dto.enrollmentChallenge()).isEqualTo(CHALLENGE);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(EnrollmentStatus.class)
  @DisplayName("characterization: integration and device public keys are returned in every state")
  void publicKeysReturnedInEveryState(EnrollmentStatus status) {
    EnrollmentResponseDto dto =
        mapper.toResponseWithIntegrationAndAdminUsernames(enrollmentIn(status), null, Map.of());

    assertThat(dto.integrationPublicKey()).isEqualTo(INTEGRATION_PUBLIC_KEY);
    assertThat(dto.devicePublicKey()).isEqualTo(DEVICE_PUBLIC_KEY);
  }
}
