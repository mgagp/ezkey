/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminTokenValidationServiceTest
 * Description: Unit tests for the administrator enrollment active check in
 *     AdminTokenValidationService.
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.config.AdminTokenRotationProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.AdminTokenPurpose;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.security.SensitiveDataHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the administrator enrollment active check in {@link AdminTokenValidationService}.
 *
 * <p>These tests specifically target the defence-in-depth guard added as part of enrollment
 * lifecycle revocation: if an admin's enrollment is inactive (revoked or deactivated), their bearer
 * token must be rejected even if it has not yet been explicitly invalidated.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminTokenValidationService — Enrollment Active Check Tests")
class AdminTokenValidationServiceTest {

  private static final String TOKEN = "test-bearer-token";

  @Mock private AdminTokenRepository tokenRepository;

  @Mock private AdminTokenRotationProperties rotationProperties;

  @Mock private AdminToken adminToken;

  @Mock private EzkeyAdmin admin;

  @Mock private Enrollment enrollment;

  private AdminTokenValidationService service;

  @BeforeEach
  void setUp() {
    service = new AdminTokenValidationService(tokenRepository, rotationProperties);
  }

  @Test
  @DisplayName("Should accept token when admin has no enrollment (null)")
  void validateTokenWithRelations_shouldReturnToken_whenEnrollmentIsNull() {
    stubValidNonExpiredToken();
    when(admin.getEnrollment()).thenReturn(null);

    Optional<AdminToken> result = service.validateTokenWithRelations(TOKEN);

    assertThat(result).isPresent();
  }

  @Test
  @DisplayName("Should accept token when admin enrollment is active")
  void validateTokenWithRelations_shouldReturnToken_whenEnrollmentIsActive() {
    stubValidNonExpiredToken();
    when(admin.getEnrollment()).thenReturn(enrollment);
    when(enrollment.getActive()).thenReturn(true);
    when(enrollment.getStatus()).thenReturn(EnrollmentStatus.VERIFIED);

    Optional<AdminToken> result = service.validateTokenWithRelations(TOKEN);

    assertThat(result).isPresent();
  }

  @Test
  @DisplayName("Should reject token when admin enrollment is inactive (revoked or deactivated)")
  void validateTokenWithRelations_shouldReturnEmpty_whenEnrollmentIsInactive() {
    stubValidNonExpiredToken();
    when(admin.getEnrollment()).thenReturn(enrollment);
    when(enrollment.getActive()).thenReturn(false);

    Optional<AdminToken> result = service.validateTokenWithRelations(TOKEN);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should reject SESSION token when admin enrollment is no longer verified")
  void validateTokenWithRelations_shouldReturnEmpty_whenSessionEnrollmentIsNotVerified() {
    stubValidNonExpiredToken();
    when(admin.getEnrollment()).thenReturn(enrollment);
    when(enrollment.getActive()).thenReturn(true);
    when(enrollment.getStatus()).thenReturn(EnrollmentStatus.CREATED);

    Optional<AdminToken> result = service.validateTokenWithRelations(TOKEN);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("SEC-021: Should reject RECOVERY-purpose token for ordinary session auth")
  void validateTokenWithRelations_shouldReturnEmpty_whenTokenPurposeIsRecovery() {
    String hash = SensitiveDataHasher.sha256Hex(TOKEN);
    when(tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(eq(hash)))
        .thenReturn(Optional.of(adminToken));
    when(adminToken.getTokenPurpose()).thenReturn(AdminTokenPurpose.RECOVERY);

    Optional<AdminToken> result = service.validateTokenWithRelations(TOKEN);

    assertThat(result).isEmpty();
    verify(adminToken, never()).getAdmin();
  }

  @Test
  @DisplayName("EVALUATOR_TEMP rejected and deactivated when enrollment is already VERIFIED")
  void validateTokenWithRelations_rejectsAndDeactivatesTemp_whenEnrollmentVerified() {
    String hash = SensitiveDataHasher.sha256Hex(TOKEN);
    when(tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(eq(hash)))
        .thenReturn(Optional.of(adminToken));
    when(adminToken.getTokenPurpose()).thenReturn(AdminTokenPurpose.EVALUATOR_TEMP);
    when(adminToken.getExpiresAt()).thenReturn(OffsetDateTime.now().plusHours(1));
    when(adminToken.getAdmin()).thenReturn(admin);
    when(adminToken.getTenant()).thenReturn(null);
    when(admin.getUsername()).thenReturn("temp.admin");
    when(admin.getAdminType()).thenReturn(null);
    when(admin.getActive()).thenReturn(true);
    when(admin.getAdminId()).thenReturn(42);
    when(admin.getEnrollment()).thenReturn(enrollment);
    when(enrollment.getActive()).thenReturn(true);
    when(enrollment.getStatus()).thenReturn(EnrollmentStatus.VERIFIED);
    when(tokenRepository.deactivateTokensForAdminByPurpose(42, AdminTokenPurpose.EVALUATOR_TEMP))
        .thenReturn(1);

    Optional<AdminToken> result = service.validateTokenWithRelations(TOKEN);

    assertThat(result).isEmpty();
    verify(tokenRepository).deactivateTokensForAdminByPurpose(42, AdminTokenPurpose.EVALUATOR_TEMP);
  }

  @Test
  @DisplayName("EVALUATOR_TEMP accepted when enrollment is CREATED (pre-bind)")
  void validateTokenWithRelations_acceptsTemp_whenEnrollmentCreated() {
    String hash = SensitiveDataHasher.sha256Hex(TOKEN);
    when(tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(eq(hash)))
        .thenReturn(Optional.of(adminToken));
    when(adminToken.getTokenPurpose()).thenReturn(AdminTokenPurpose.EVALUATOR_TEMP);
    when(adminToken.getExpiresAt()).thenReturn(OffsetDateTime.now().plusHours(1));
    when(adminToken.getAdmin()).thenReturn(admin);
    when(adminToken.getTenant()).thenReturn(null);
    when(admin.getUsername()).thenReturn("temp.admin");
    when(admin.getAdminType()).thenReturn(null);
    when(admin.getActive()).thenReturn(true);
    when(admin.getEnrollment()).thenReturn(enrollment);
    when(enrollment.getActive()).thenReturn(true);
    when(enrollment.getStatus()).thenReturn(EnrollmentStatus.CREATED);

    Optional<AdminToken> result = service.validateTokenWithRelations(TOKEN);

    assertThat(result).isPresent();
    verify(tokenRepository, never())
        .deactivateTokensForAdminByPurpose(any(), eq(AdminTokenPurpose.EVALUATOR_TEMP));
  }

  @Test
  @DisplayName(
      "updateTokenLastUsed(String) finds by hash then touches if active; SESSION slides expiration")
  void updateTokenLastUsed_stringPath_findsAndExtendsExpiration_forSessionToken() {
    String normalToken = "ezkey_normal";
    String hash = SensitiveDataHasher.sha256Hex(normalToken);
    when(rotationProperties.getExpirationHours()).thenReturn(2);
    when(tokenRepository.findByBearerTokenHashAndActiveTrue(eq(hash)))
        .thenReturn(Optional.of(adminToken));
    when(adminToken.getTokenId()).thenReturn(7);
    when(adminToken.getTokenPurpose()).thenReturn(AdminTokenPurpose.SESSION);
    when(tokenRepository.touchLastUsedIfActive(eq(7), any(), any())).thenReturn(1);

    Optional<OffsetDateTime> result = service.updateTokenLastUsed(normalToken);

    assertThat(result).isPresent();
    assertThat(result.get()).isAfter(OffsetDateTime.now().plusMinutes(119));
    assertThat(result.get()).isBefore(OffsetDateTime.now().plusMinutes(121));
    verify(tokenRepository).findByBearerTokenHashAndActiveTrue(eq(hash));
    verify(tokenRepository).touchLastUsedIfActive(eq(7), any(), any());
    verify(tokenRepository, never()).save(any());
    verify(adminToken, never()).setLastUsedAt(any());
    verify(adminToken, never()).setExpiresAt(any());
  }

  @Test
  @DisplayName(
      "updateTokenLastUsed(String) finds by hash then touches if active; RECOVERY does not extend"
          + " (SEC-021)")
  void updateTokenLastUsed_stringPath_findsAndDoesNotExtendExpiration_forRecoveryToken() {
    String recoveryToken = "ezkey_recovery_abc123";
    String hash = SensitiveDataHasher.sha256Hex(recoveryToken);
    OffsetDateTime fixedExpiresAt = OffsetDateTime.now().plusMinutes(15);
    when(tokenRepository.findByBearerTokenHashAndActiveTrue(eq(hash)))
        .thenReturn(Optional.of(adminToken));
    when(adminToken.getTokenId()).thenReturn(7);
    when(adminToken.getTokenPurpose()).thenReturn(AdminTokenPurpose.RECOVERY);
    when(adminToken.getExpiresAt()).thenReturn(fixedExpiresAt);
    when(tokenRepository.touchLastUsedIfActive(eq(7), any(), eq(fixedExpiresAt))).thenReturn(1);

    Optional<OffsetDateTime> result = service.updateTokenLastUsed(recoveryToken);

    assertThat(result).contains(fixedExpiresAt);
    verify(tokenRepository).findByBearerTokenHashAndActiveTrue(eq(hash));
    verify(tokenRepository).touchLastUsedIfActive(eq(7), any(), eq(fixedExpiresAt));
    verify(tokenRepository, never()).save(any());
    verify(adminToken, never()).setLastUsedAt(any());
    verify(adminToken, never()).setExpiresAt(any());
  }

  @Test
  @DisplayName("updateTokenLastUsed(String) returns empty when token hash is not found")
  void updateTokenLastUsed_stringPath_returnsEmpty_whenTokenNotFound() {
    String missingToken = "ezkey_missing";
    String hash = SensitiveDataHasher.sha256Hex(missingToken);
    when(tokenRepository.findByBearerTokenHashAndActiveTrue(eq(hash))).thenReturn(Optional.empty());

    Optional<OffsetDateTime> result = service.updateTokenLastUsed(missingToken);

    assertThat(result).isEmpty();
    verify(tokenRepository).findByBearerTokenHashAndActiveTrue(eq(hash));
    verify(tokenRepository, never()).save(any());
    verify(tokenRepository, never()).touchLastUsedIfActive(any(), any(), any());
  }

  @Test
  @DisplayName(
      "updateTokenLastUsed(AdminToken) touches if active without merge; SESSION slides expiration")
  void updateTokenLastUsed_entityPath_neverReFinds_andExtendsExpiration_forSessionToken() {
    when(rotationProperties.getExpirationHours()).thenReturn(2);
    when(adminToken.getTokenId()).thenReturn(7);
    when(adminToken.getTokenPurpose()).thenReturn(AdminTokenPurpose.SESSION);
    when(tokenRepository.touchLastUsedIfActive(eq(7), any(), any())).thenReturn(1);

    Optional<OffsetDateTime> result = service.updateTokenLastUsed(adminToken);

    assertThat(result).isPresent();
    assertThat(result.get()).isAfter(OffsetDateTime.now().plusMinutes(119));
    assertThat(result.get()).isBefore(OffsetDateTime.now().plusMinutes(121));
    verify(tokenRepository, never()).findByBearerTokenHashAndActiveTrue(any());
    verify(tokenRepository, never()).findByBearerTokenHashAndActiveTrueWithRelations(any());
    verify(tokenRepository).touchLastUsedIfActive(eq(7), any(), any());
    verify(tokenRepository, never()).save(any());
    verify(adminToken, never()).setLastUsedAt(any());
    verify(adminToken, never()).setExpiresAt(any());
    verify(adminToken, never()).setActive(any());
  }

  @Test
  @DisplayName(
      "updateTokenLastUsed(AdminToken) touches if active without merge; RECOVERY does not extend"
          + " (SEC-021)")
  void updateTokenLastUsed_entityPath_neverReFinds_andDoesNotExtend_forRecoveryToken() {
    OffsetDateTime fixedExpiresAt = OffsetDateTime.now().plusMinutes(15);
    when(adminToken.getTokenId()).thenReturn(7);
    when(adminToken.getTokenPurpose()).thenReturn(AdminTokenPurpose.RECOVERY);
    when(adminToken.getExpiresAt()).thenReturn(fixedExpiresAt);
    when(tokenRepository.touchLastUsedIfActive(eq(7), any(), eq(fixedExpiresAt))).thenReturn(1);

    Optional<OffsetDateTime> result = service.updateTokenLastUsed(adminToken);

    assertThat(result).contains(fixedExpiresAt);
    verify(tokenRepository, never()).findByBearerTokenHashAndActiveTrue(any());
    verify(tokenRepository).touchLastUsedIfActive(eq(7), any(), eq(fixedExpiresAt));
    verify(tokenRepository, never()).save(any());
    verify(adminToken, never()).setLastUsedAt(any());
    verify(adminToken, never()).setExpiresAt(any());
  }

  @Test
  @DisplayName(
      "updateTokenLastUsed(AdminToken) does not slide expiration for EVALUATOR_TEMP (absolute TTL)")
  void updateTokenLastUsed_entityPath_doesNotExtendExpiration_forEvaluatorTempToken() {
    OffsetDateTime fixedExpiresAt = OffsetDateTime.now().plusHours(4);
    when(adminToken.getTokenId()).thenReturn(7);
    when(adminToken.getTokenPurpose()).thenReturn(AdminTokenPurpose.EVALUATOR_TEMP);
    when(adminToken.getExpiresAt()).thenReturn(fixedExpiresAt);
    when(tokenRepository.touchLastUsedIfActive(eq(7), any(), eq(fixedExpiresAt))).thenReturn(1);

    Optional<OffsetDateTime> result = service.updateTokenLastUsed(adminToken);

    assertThat(result).contains(fixedExpiresAt);
    verify(tokenRepository).touchLastUsedIfActive(eq(7), any(), eq(fixedExpiresAt));
    verify(tokenRepository, never()).save(any());
    verify(adminToken, never()).setLastUsedAt(any());
    verify(adminToken, never()).setExpiresAt(any());
  }

  @Test
  @DisplayName(
      "updateTokenLastUsed(AdminToken) does not merge when the token was concurrently deactivated")
  void updateTokenLastUsed_entityPath_returnsEmpty_whenTokenNoLongerActive() {
    when(rotationProperties.getExpirationHours()).thenReturn(2);
    when(adminToken.getTokenId()).thenReturn(7);
    when(adminToken.getTokenPurpose()).thenReturn(AdminTokenPurpose.SESSION);
    when(tokenRepository.touchLastUsedIfActive(eq(7), any(), any())).thenReturn(0);

    Optional<OffsetDateTime> result = service.updateTokenLastUsed(adminToken);

    assertThat(result).isEmpty();
    verify(tokenRepository).touchLastUsedIfActive(eq(7), any(), any());
    verify(tokenRepository, never()).save(any());
    verify(adminToken, never()).setActive(any());
  }

  @Test
  @DisplayName("updateTokenLastUsed(AdminToken) returns empty when token id is missing")
  void updateTokenLastUsed_entityPath_returnsEmpty_whenTokenIdNull() {
    when(adminToken.getTokenId()).thenReturn(null);

    Optional<OffsetDateTime> result = service.updateTokenLastUsed(adminToken);

    assertThat(result).isEmpty();
    verify(tokenRepository, never()).touchLastUsedIfActive(any(), any(), any());
    verify(tokenRepository, never()).save(any());
  }

  /**
   * Stubs the token repository to return a valid, non-expired SESSION token associated with a mock
   * admin. The tenant check is bypassed by returning null for the tenant (no tenant-inactive path).
   * Uses the hash of TOKEN for lookup.
   */
  private void stubValidNonExpiredToken() {
    String hash = SensitiveDataHasher.sha256Hex(TOKEN);
    when(tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(eq(hash)))
        .thenReturn(Optional.of(adminToken));
    when(adminToken.getTokenPurpose()).thenReturn(AdminTokenPurpose.SESSION);
    when(adminToken.getExpiresAt()).thenReturn(OffsetDateTime.now().plusHours(1));
    when(adminToken.getAdmin()).thenReturn(admin);
    when(adminToken.getTenant()).thenReturn(null);
  }
}
