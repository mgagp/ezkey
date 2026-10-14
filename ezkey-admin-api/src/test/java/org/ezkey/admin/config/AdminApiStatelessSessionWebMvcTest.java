/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminApiStatelessSessionWebMvcTest
 * Description: Anonymous 401 must not emit a servlet session Set-Cookie (#816).
 */

package org.ezkey.admin.config;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.admin.controller.EncryptionKeyController;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.security.KeyRotationService;
import org.ezkey.security.KeyUsageVerificationService;
import org.ezkey.security.ReencryptionService;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Verifies Admin API servlet session policy: anonymous 401 has no {@code Set-Cookie} (#816).
 *
 * @since 2026
 */
@WebMvcTest(controllers = EncryptionKeyController.class)
@Import({SecurityConfig.class, AdminCorsTestFilterBeans.class, TrustedProxyConfig.class})
@DisplayName("Admin API stateless servlet sessions (#816)")
class AdminApiStatelessSessionWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EncryptionKeyRepository keyRepository;
  @MockitoBean private ReencryptionBatchRepository batchRepository;
  @MockitoBean private KeyRotationService rotationService;
  @MockitoBean private ReencryptionService reencryptionService;
  @MockitoBean private AuditLogService auditLogService;
  @MockitoBean private KeyUsageVerificationService keyUsageVerificationService;

  @Test
  @DisplayName("Anonymous GET protected route returns 401 without Set-Cookie or servlet session")
  void anonymousProtectedGet_noSetCookieAndNoSession() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/encryption-keys"))
            .andExpect(status().isUnauthorized())
            .andReturn();

    assertNull(result.getResponse().getHeader("Set-Cookie"));
    assertTrue(result.getRequest().getSession(false) == null);
  }
}
