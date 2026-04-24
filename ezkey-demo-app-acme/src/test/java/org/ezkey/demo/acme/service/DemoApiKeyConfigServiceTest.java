/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: DemoApiKeyConfigServiceTest
 * Description: Verifies session-scoped API key behavior for the ACME demo application.
 */

package org.ezkey.demo.acme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ezkey.demo.acme.config.AcmeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class DemoApiKeyConfigServiceTest {

  @Test
  void shouldUseSessionScopedCredentialsWithoutAffectingOtherSessions() {
    AcmeProperties properties = new AcmeProperties();
    properties.setIntegrationKey("ezkey_ikey_default");
    properties.setSecretKey("ezkey_skey_default");
    DemoApiKeyConfigService service = new DemoApiKeyConfigService(properties);
    MockHttpSession firstSession = new MockHttpSession();
    MockHttpSession secondSession = new MockHttpSession();

    boolean applied = service.applyApiKey(firstSession, "ezkey_ikey_first", "ezkey_skey_first");

    assertTrue(applied);
    DemoApiKeyConfigService.DemoApiKeyCredentials firstSessionCredentials =
        service.resolveCredentials(firstSession);
    DemoApiKeyConfigService.DemoApiKeyCredentials secondSessionCredentials =
        service.resolveCredentials(secondSession);
    assertNotNull(firstSessionCredentials);
    assertNotNull(secondSessionCredentials);
    assertEquals("ezkey_ikey_first", firstSessionCredentials.integrationKey());
    assertEquals("ezkey_skey_first", firstSessionCredentials.secretKey());
    assertEquals("ezkey_ikey_default", secondSessionCredentials.integrationKey());
    assertEquals("ezkey_skey_default", secondSessionCredentials.secretKey());
  }

  @Test
  void shouldRejectBlankSessionScopedCredentials() {
    AcmeProperties properties = new AcmeProperties();
    DemoApiKeyConfigService service = new DemoApiKeyConfigService(properties);
    MockHttpSession session = new MockHttpSession();

    boolean applied = service.applyApiKey(session, " ", " ");

    assertFalse(applied);
    assertFalse(service.isConfigured(session));
  }
}
