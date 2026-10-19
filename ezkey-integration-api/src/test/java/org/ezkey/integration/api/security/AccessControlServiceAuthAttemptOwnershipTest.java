/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Ownership checks for Integration API {@code /wait} and {@code /cancel} (#895).
 *
 * @author Ezkey contributors
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControlService auth-attempt ownership (#895)")
class AccessControlServiceAuthAttemptOwnershipTest {

  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private EnrollmentRepository enrollmentRepository;

  private AccessControlService accessControlService;

  @BeforeEach
  void setUp() {
    accessControlService = new AccessControlService(authAttemptRepository, enrollmentRepository);
  }

  @Test
  @DisplayName("Same integration → canAccessAuthAttempt true (wait/cancel allowed)")
  void canAccess_sameIntegration_allowed() {
    stubAttemptOwnedBy(10, 77);
    Authentication auth = apiKeyAuth(77);

    assertThat(accessControlService.canAccessAuthAttempt(auth, 10)).isTrue();
  }

  @Test
  @DisplayName("Foreign integration → canAccessAuthAttempt false (wait/cancel denied)")
  void canAccess_foreignIntegration_denied() {
    stubAttemptOwnedBy(10, 77);
    Authentication auth = apiKeyAuth(88);

    assertThat(accessControlService.canAccessAuthAttempt(auth, 10)).isFalse();
  }

  @Test
  @DisplayName("Missing attempt → denied (no enumeration via allow)")
  void canAccess_missingAttempt_denied() {
    when(authAttemptRepository.findById(10)).thenReturn(Optional.empty());
    Authentication auth = apiKeyAuth(77);

    assertThat(accessControlService.canAccessAuthAttempt(auth, 10)).isFalse();
  }

  private void stubAttemptOwnedBy(int attemptId, int integrationId) {
    AuthAttempt attempt = new AuthAttempt();
    attempt.setAuthAttemptId(attemptId);
    attempt.setEnrollmentId(5);
    when(authAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(5);
    enrollment.setIntegrationId(integrationId);
    when(enrollmentRepository.findById(5)).thenReturn(Optional.of(enrollment));
  }

  private static Authentication apiKeyAuth(int integrationId) {
    return new UsernamePasswordAuthenticationToken(
        integrationId, null, List.of(new SimpleGrantedAuthority("ROLE_API_KEY")));
  }
}
