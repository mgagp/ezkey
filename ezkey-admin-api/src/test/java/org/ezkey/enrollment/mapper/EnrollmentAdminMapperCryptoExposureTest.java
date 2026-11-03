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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

/**
 * Asserts which cryptographic fields {@link EnrollmentAdminMapper} returns to Admin API callers
 * (GET and search by enrollment) in each {@link EnrollmentStatus} (#1180).
 *
 * <p>Both GET and search go through {@link EnrollmentAdminMapper#toResponse(Enrollment)}, so this
 * mapper is the single exposure point for the Admin UI enrollment detail and list. Rule: the bind
 * secrets (proof token, challenge) are returned only while {@code CREATED}; public keys never.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@DisplayName("EnrollmentAdminMapper crypto exposure (#1180)")
class EnrollmentAdminMapperCryptoExposureTest {

  private static final String PROOF_TOKEN = "enrollment-proof-token";
  private static final int CHALLENGE = 436767;

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
    enrollment.setIntegrationPublicKey("integration-public-key");
    enrollment.setDevicePublicKey("device-public-key");
    return enrollment;
  }

  @Test
  @DisplayName("CREATED keeps the bind secrets for the QR / manual bind channel")
  void createdKeepsBindSecrets() {
    EnrollmentResponseDto dto =
        mapper.toResponseWithIntegrationAndAdminUsernames(
            enrollmentIn(EnrollmentStatus.CREATED), null, Map.of());

    assertThat(dto.enrollmentProofToken()).isEqualTo(PROOF_TOKEN);
    assertThat(dto.enrollmentChallenge()).isEqualTo(CHALLENGE);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(value = EnrollmentStatus.class, names = "CREATED", mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("every state after CREATED withholds the proof token and challenge")
  void afterCreatedWithholdsBindSecrets(EnrollmentStatus status) {
    EnrollmentResponseDto dto =
        mapper.toResponseWithIntegrationAndAdminUsernames(enrollmentIn(status), null, Map.of());

    assertThat(dto.enrollmentProofToken()).isNull();
    assertThat(dto.enrollmentChallenge()).isNull();
  }

  @Test
  @DisplayName("public keys are not part of the Admin API enrollment contract")
  void publicKeysAreNotInTheContract() {
    assertThat(EnrollmentResponseDto.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .doesNotContain("integrationPublicKey", "devicePublicKey");
  }
}
