/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminTokenValidationServiceTest
 * Description: Unit tests for the MFA enrollment active check in AdminTokenValidationService.
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the MFA enrollment active check in {@link AdminTokenValidationService}.
 *
 * <p>These tests specifically target the defence-in-depth guard added as part of enrollment
 * lifecycle revocation: if an admin's MFA enrollment is inactive (revoked or deactivated), their
 * bearer token must be rejected even if it has not yet been explicitly invalidated.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminTokenValidationService — MFA Enrollment Active Check Tests")
class AdminTokenValidationServiceTest {

  private static final String TOKEN = "test-bearer-token";

  @Mock private AdminTokenRepository tokenRepository;

  @Mock private AdminToken adminToken;

  @Mock private EzkeyAdmin admin;

  @Mock private Enrollment mfaEnrollment;

  private AdminTokenValidationService service;

  @BeforeEach
  void setUp() {
    service = new AdminTokenValidationService(tokenRepository);
  }

  @Test
  @DisplayName("Should accept token when admin has no MFA enrollment (null)")
  void validateTokenWithRelations_shouldReturnToken_whenMfaEnrollmentIsNull() {
    stubValidNonExpiredToken();
    when(admin.getMfaEnrollment()).thenReturn(null);

    Optional<AdminToken> result = service.validateTokenWithRelations(TOKEN);

    assertThat(result).isPresent();
  }

  @Test
  @DisplayName("Should accept token when admin MFA enrollment is active")
  void validateTokenWithRelations_shouldReturnToken_whenMfaEnrollmentIsActive() {
    stubValidNonExpiredToken();
    when(admin.getMfaEnrollment()).thenReturn(mfaEnrollment);
    when(mfaEnrollment.getActive()).thenReturn(true);

    Optional<AdminToken> result = service.validateTokenWithRelations(TOKEN);

    assertThat(result).isPresent();
  }

  @Test
  @DisplayName("Should reject token when admin MFA enrollment is inactive (revoked or deactivated)")
  void validateTokenWithRelations_shouldReturnEmpty_whenMfaEnrollmentIsInactive() {
    stubValidNonExpiredToken();
    when(admin.getMfaEnrollment()).thenReturn(mfaEnrollment);
    when(mfaEnrollment.getActive()).thenReturn(false);

    Optional<AdminToken> result = service.validateTokenWithRelations(TOKEN);

    assertThat(result).isEmpty();
  }

  /**
   * Stubs the token repository to return a valid, non-expired token associated with a mock admin.
   * The tenant check is bypassed by returning null for the tenant (no tenant-inactive path).
   */
  private void stubValidNonExpiredToken() {
    when(tokenRepository.findByBearerTokenAndActiveTrueWithRelations(TOKEN))
        .thenReturn(Optional.of(adminToken));
    when(adminToken.getExpiresAt()).thenReturn(OffsetDateTime.now().plusHours(1));
    when(adminToken.getAdmin()).thenReturn(admin);
    when(adminToken.getTenant()).thenReturn(null);
  }
}
