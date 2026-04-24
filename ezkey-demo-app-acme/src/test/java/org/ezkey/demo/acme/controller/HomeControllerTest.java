/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: HomeControllerTest
 * Description: Verifies logout clears authentication state without dropping session-scoped demo
 * API key configuration.
 */

package org.ezkey.demo.acme.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class HomeControllerTest {

  private static final String SESSION_INTEGRATION_KEY = "demoIntegrationKey";
  private static final String SESSION_SECRET_KEY = "demoSecretKey";

  private final HomeController controller = new HomeController();

  @Test
  void shouldPreserveDemoApiKeyConfigurationOnLogout() {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(SESSION_INTEGRATION_KEY, "ezkey_ikey_demo");
    session.setAttribute(SESSION_SECRET_KEY, "ezkey_skey_demo");
    session.setAttribute("user", new AuthenticatedUser("alice", "Alice", 42));
    session.setAttribute("pendingAuthAttemptId", 1001);
    session.setAttribute("pendingChallengeCode", 12);
    session.setAttribute("pendingUsername", "alice");
    session.setAttribute("pendingDisplayName", "Alice");
    session.setAttribute("pendingEnrollmentId", 42);
    session.setAttribute("pendingTimeoutSeconds", 90);
    session.setAttribute("pendingExpiresAt", "2026-04-24T12:00:00Z");
    session.setAttribute("authAttemptFinalStatus", "ACCEPTED");

    String viewName = controller.logout(session);

    assertThat(viewName).isEqualTo("redirect:/login?logout=true");
    assertThat(session.getAttribute(SESSION_INTEGRATION_KEY)).isEqualTo("ezkey_ikey_demo");
    assertThat(session.getAttribute(SESSION_SECRET_KEY)).isEqualTo("ezkey_skey_demo");
    assertThat(session.getAttribute("user")).isNull();
    assertThat(session.getAttribute("pendingAuthAttemptId")).isNull();
    assertThat(session.getAttribute("pendingChallengeCode")).isNull();
    assertThat(session.getAttribute("pendingUsername")).isNull();
    assertThat(session.getAttribute("pendingDisplayName")).isNull();
    assertThat(session.getAttribute("pendingEnrollmentId")).isNull();
    assertThat(session.getAttribute("pendingTimeoutSeconds")).isNull();
    assertThat(session.getAttribute("pendingExpiresAt")).isNull();
    assertThat(session.getAttribute("authAttemptFinalStatus")).isNull();
  }
}
