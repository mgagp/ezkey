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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class HomeControllerTest {

  private DemoApiKeyConfigService demoApiKeyConfigService;
  private HomeController controller;

  @BeforeEach
  void setUp() {
    demoApiKeyConfigService = mock(DemoApiKeyConfigService.class);
    controller = new HomeController(demoApiKeyConfigService);
  }

  @Test
  void shouldInvalidateSessionOnLogoutIncludingPastedKeys() {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(DemoApiKeyConfigService.SESSION_INTEGRATION_KEY, "ezkey_ikey_demo");
    session.setAttribute(DemoApiKeyConfigService.SESSION_SECRET_KEY, "ezkey_skey_demo");
    session.setAttribute(DemoApiKeyConfigService.SESSION_ACCESS_CODE_SLOT_ID, "northwind");
    session.setAttribute("user", new AuthenticatedUser("alice", "Alice", 42));
    session.setAttribute("pendingAuthAttemptId", 1001);
    when(demoApiKeyConfigService.getActiveSlotId(session)).thenReturn("northwind");

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setSession(session);

    String viewName = controller.logout(request);

    assertThat(viewName).isEqualTo("redirect:/login?logout=true&entry=link");
    assertThat(session.isInvalid()).isTrue();
  }

  @Test
  void logoutWithoutSlotKeepsSelfServiceRedirect() {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("user", new AuthenticatedUser("alice", "Alice", 42));
    when(demoApiKeyConfigService.getActiveSlotId(any())).thenReturn(null);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setSession(session);

    assertThat(controller.logout(request)).isEqualTo("redirect:/login?logout=true");
  }
}
