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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.ezkey.demo.acme.config.AcmeProperties;
import org.ezkey.demo.acme.config.AcmeProperties.AccessCodeSlotProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class DemoApiKeyConfigServiceTest {

  @Test
  void shouldUseSessionScopedCredentialsWithoutAffectingOtherSessions() {
    AcmeProperties properties = new AcmeProperties();
    properties.setIntegrationKey("ezkey_ikey_default");
    properties.setSecretKey("ezkey_skey_default");
    AccessCodeService accessCodeService = new AccessCodeService(properties);
    accessCodeService.loadSlots(Map.of());
    DemoApiKeyConfigService service = new DemoApiKeyConfigService(properties, accessCodeService);
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
    AccessCodeService accessCodeService = new AccessCodeService(properties);
    accessCodeService.loadSlots(Map.of());
    DemoApiKeyConfigService service = new DemoApiKeyConfigService(properties, accessCodeService);
    MockHttpSession session = new MockHttpSession();

    boolean applied = service.applyApiKey(session, " ", " ");

    assertFalse(applied);
    assertFalse(service.isConfigured(session));
  }

  @Test
  void shouldPreferAccessCodeSlotOverPastedAndLegacyCredentials() {
    AcmeProperties properties = new AcmeProperties();
    properties.setIntegrationKey("ezkey_ikey_legacy");
    properties.setSecretKey("ezkey_skey_legacy");
    AccessCodeService accessCodeService = new AccessCodeService(properties);
    Map<String, AccessCodeSlotProperties> slots = new LinkedHashMap<>();
    AccessCodeSlotProperties slot = new AccessCodeSlotProperties();
    slot.setCode("0123456789abcdef0123456789abcdef");
    slot.setIntegrationKey("ezkey_ikey_slot");
    slot.setSecretKey("ezkey_skey_slot");
    slot.setLabel("Northwind Portal");
    slots.put("northwind", slot);
    accessCodeService.loadSlots(slots);
    DemoApiKeyConfigService service = new DemoApiKeyConfigService(properties, accessCodeService);
    MockHttpSession session = new MockHttpSession();
    service.applyApiKey(session, "ezkey_ikey_paste", "ezkey_skey_paste");
    service.activateAccessCodeSlot(session, "northwind");

    DemoApiKeyConfigService.DemoApiKeyCredentials resolved = service.resolveCredentials(session);

    assertNotNull(resolved);
    assertEquals("ezkey_ikey_slot", resolved.integrationKey());
    assertEquals("northwind", service.getActiveSlotId(session));
    assertNull(session.getAttribute(DemoApiKeyConfigService.SESSION_INTEGRATION_KEY));
  }
}
