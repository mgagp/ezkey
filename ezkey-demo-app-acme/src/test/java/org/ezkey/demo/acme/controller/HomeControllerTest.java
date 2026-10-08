/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: HomeControllerTest
 * Description: Verifies logout invalidates the session including pasted demo API keys.
 */

package org.ezkey.demo.acme.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class HomeControllerTest {

  private final HomeController controller = new HomeController();

  @Test
  void shouldInvalidateSessionOnLogoutIncludingPastedKeys() {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(DemoApiKeyConfigService.SESSION_INTEGRATION_KEY, "ezkey_ikey_demo");
    session.setAttribute(DemoApiKeyConfigService.SESSION_SECRET_KEY, "ezkey_skey_demo");
    session.setAttribute(DemoApiKeyConfigService.SESSION_ACCESS_CODE_SLOT_ID, "northwind");
    session.setAttribute("user", new AuthenticatedUser("alice", "Alice", 42));
    session.setAttribute("pendingAuthAttemptId", 1001);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setSession(session);

    String viewName = controller.logout(request);

    assertThat(viewName).isEqualTo("redirect:/login?logout=true");
    assertThat(session.isInvalid()).isTrue();
  }
}
