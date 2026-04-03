/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.exception.auth.AuthAttemptWaitValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthAttemptWaitService")
class AuthAttemptWaitServiceTest {

  @Mock private AuthAttemptRepository authAttemptRepository;

  @Test
  @DisplayName("Rejects timeout outside allowed range")
  void rejectsTimeoutOutsideAllowedRange() {
    AuthAttemptWaitService service = new AuthAttemptWaitService(authAttemptRepository);

    assertThatThrownBy(() -> service.waitForResponse(123, new AuthAttemptWaitRequest(0, 2)))
        .isInstanceOf(AuthAttemptWaitValidationException.class)
        .hasMessage("Timeout must be between 1 and 300 seconds");

    verifyNoInteractions(authAttemptRepository);
  }

  @Test
  @DisplayName("Rejects polling outside allowed range")
  void rejectsPollingOutsideAllowedRange() {
    AuthAttemptWaitService service = new AuthAttemptWaitService(authAttemptRepository);

    assertThatThrownBy(() -> service.waitForResponse(123, new AuthAttemptWaitRequest(30, 61)))
        .isInstanceOf(AuthAttemptWaitValidationException.class)
        .hasMessage("Polling must be between 1 and 60 seconds");

    verifyNoInteractions(authAttemptRepository);
  }

  @Test
  @DisplayName("Rejects polling greater than timeout")
  void rejectsPollingGreaterThanTimeout() {
    AuthAttemptWaitService service = new AuthAttemptWaitService(authAttemptRepository);

    assertThatThrownBy(() -> service.waitForResponse(123, new AuthAttemptWaitRequest(30, 31)))
        .isInstanceOf(AuthAttemptWaitValidationException.class)
        .hasMessage("Polling interval cannot be greater than timeout");

    verifyNoInteractions(authAttemptRepository);
  }
}
