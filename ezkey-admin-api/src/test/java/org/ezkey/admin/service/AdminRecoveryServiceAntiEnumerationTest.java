/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminRecoveryServiceAntiEnumerationTest
 * Description: Verifies pre-authentication recovery failures use one generic client message
 * (SEC-024) while retaining distinct internal reasons for audit mapping.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.ezkey.admin.audit.RecoveryAuditDetails;
import org.ezkey.admin.config.AdminRecoveryProperties;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminRecoveryServiceAntiEnumerationTest {

  private static final String GENERIC = AdminRecoveryService.GENERIC_RECOVERY_FAILURE_MESSAGE;
  private static final String SAMPLE_CODE = "1234-5678-9012-3456-7890-1234-5678-9012";

  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminTokenRepository tokenRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private SignatureService signatureService;
  @Mock private BCryptPasswordEncoder passwordEncoder;
  @Mock private AdminRecoveryProperties recoveryProperties;

  @InjectMocks private AdminRecoveryService recoveryService;

  @Test
  @DisplayName("validateRecoveryCode returns generic message for unknown username")
  void rejectsUnknownUsernameWithGenericMessage() {
    when(adminRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    AuthenticationException exception =
        assertThrows(
            AuthenticationException.class,
            () -> recoveryService.validateRecoveryCode("ghost", SAMPLE_CODE));

    assertEquals(GENERIC, exception.getMessage());
    assertEquals("Invalid credentials", exception.getInternalDetail());
    assertEquals(
        "unknown_user",
        RecoveryAuditDetails.recoveryRejectionReasonCode(exception.getInternalDetail()));
  }

  @Test
  @DisplayName("validateRecoveryCode returns generic message for inactive account")
  void rejectsInactiveAccountWithGenericMessage() {
    EzkeyAdmin admin = activeAdmin("inactive.admin");
    admin.setActive(false);
    when(adminRepository.findByUsername("inactive.admin")).thenReturn(Optional.of(admin));

    AuthenticationException exception =
        assertThrows(
            AuthenticationException.class,
            () -> recoveryService.validateRecoveryCode("inactive.admin", SAMPLE_CODE));

    assertEquals(GENERIC, exception.getMessage());
    assertEquals("Account is inactive", exception.getInternalDetail());
    assertEquals(
        "account_inactive",
        RecoveryAuditDetails.recoveryRejectionReasonCode(exception.getInternalDetail()));
  }

  @Test
  @DisplayName("validateRecoveryCode returns generic message when no recovery codes exist")
  void rejectsNoCodesWithGenericMessage() {
    EzkeyAdmin admin = activeAdmin("no.codes");
    admin.setRecoveryCodes(new String[0]);
    when(adminRepository.findByUsername("no.codes")).thenReturn(Optional.of(admin));

    AuthenticationException exception =
        assertThrows(
            AuthenticationException.class,
            () -> recoveryService.validateRecoveryCode("no.codes", SAMPLE_CODE));

    assertEquals(GENERIC, exception.getMessage());
    assertEquals("No recovery codes available for this account", exception.getInternalDetail());
    assertEquals(
        "no_codes_remaining",
        RecoveryAuditDetails.recoveryRejectionReasonCode(exception.getInternalDetail()));
  }

  @Test
  @DisplayName("validateRecoveryCode returns generic message for wrong recovery code")
  void rejectsWrongCodeWithGenericMessage() {
    EzkeyAdmin admin = activeAdmin("wrong.code");
    admin.setRecoveryCodes(new String[] {"$2a$10$hashedRecoveryCodePlaceholder000000"});
    when(adminRepository.findByUsername("wrong.code")).thenReturn(Optional.of(admin));
    when(passwordEncoder.matches(SAMPLE_CODE, admin.getRecoveryCodes()[0])).thenReturn(false);

    AuthenticationException exception =
        assertThrows(
            AuthenticationException.class,
            () -> recoveryService.validateRecoveryCode("wrong.code", SAMPLE_CODE));

    assertEquals(GENERIC, exception.getMessage());
    assertEquals("Invalid recovery code", exception.getInternalDetail());
    assertEquals(
        "invalid_code",
        RecoveryAuditDetails.recoveryRejectionReasonCode(exception.getInternalDetail()));
  }

  private static EzkeyAdmin activeAdmin(String username) {
    EzkeyAdmin admin = new EzkeyAdmin(username, AdminType.TENANT_ADMIN);
    admin.setActive(true);
    return admin;
  }
}
