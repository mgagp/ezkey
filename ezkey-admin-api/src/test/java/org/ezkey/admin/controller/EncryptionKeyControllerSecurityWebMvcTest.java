/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EncryptionKeyControllerSecurityWebMvcTest
 * Description: SEC-017 — encryption-key routes require GLOBAL_ADMIN (Tenant Admin / API key → 403).
 */

package org.ezkey.admin.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.config.AdminCorsTestFilterBeans;
import org.ezkey.admin.config.SecurityConfig;
import org.ezkey.admin.config.TrustedProxyConfig;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.security.KeyRotationService;
import org.ezkey.security.KeyUsageVerificationService;
import org.ezkey.security.ReencryptionService;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.EncryptionKey.KeyStatus;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * SEC-017 regression: every encryption-key route requires {@code ROLE_GLOBAL_ADMIN}.
 *
 * <p>Tenant Admins still carry {@code ROLE_ADMIN} and must receive 403. HTTP Basic API-key
 * credentials do not authenticate on Admin API and must receive 401. Global Admin retains read
 * access verified here; mutation behavior remains in unit tests.
 *
 * @since 2026
 */
@WebMvcTest(controllers = EncryptionKeyController.class)
@Import({SecurityConfig.class, AdminCorsTestFilterBeans.class, TrustedProxyConfig.class})
@DisplayName("SEC-017 EncryptionKeyController authorization")
class EncryptionKeyControllerSecurityWebMvcTest {

  private static final String[] GET_PATHS = {
    "/api/v1/encryption-keys",
    "/api/v1/encryption-keys/primary",
    "/api/v1/encryption-keys/42",
    "/api/v1/encryption-keys/reencryption-batches",
  };

  private static final String[] POST_PATHS = {
    "/api/v1/encryption-keys/rotate",
    "/api/v1/encryption-keys/reencryption-batches/1/resume",
    "/api/v1/encryption-keys/reencrypt/trigger",
    "/api/v1/encryption-keys/42/reencrypt",
    "/api/v1/encryption-keys/reencrypt/create-batches",
  };

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EncryptionKeyRepository keyRepository;
  @MockitoBean private ReencryptionBatchRepository batchRepository;
  @MockitoBean private KeyRotationService rotationService;
  @MockitoBean private ReencryptionService reencryptionService;
  @MockitoBean private AuditLogService auditLogService;
  @MockitoBean private KeyUsageVerificationService keyUsageVerificationService;

  @Test
  @DisplayName("Tenant Admin (ROLE_ADMIN + ROLE_TENANT_ADMIN) receives 403 on all routes")
  void tenantAdminForbiddenOnAllRoutes() throws Exception {
    assertForbiddenOnAllRoutes(user("tenant").roles("ADMIN", "TENANT_ADMIN"));
  }

  @Test
  @DisplayName("Generic ADMIN role alone receives 403 on all routes")
  void adminOnlyForbiddenOnAllRoutes() throws Exception {
    assertForbiddenOnAllRoutes(user("admin").roles("ADMIN"));
  }

  @Test
  @DisplayName("HTTP Basic API-key credentials receive 401 on all routes")
  void apiKeyBasicUnauthorizedOnAllRoutes() throws Exception {
    String basic =
        "Basic "
            + java.util.Base64.getEncoder()
                .encodeToString("ezkey_ikey_test:ezkey_skey_test".getBytes());
    for (String path : GET_PATHS) {
      mockMvc
          .perform(get(path).header("Authorization", basic))
          .andExpect(status().isUnauthorized());
    }
    for (String path : POST_PATHS) {
      mockMvc
          .perform(post(path).header("Authorization", basic))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  @WithMockUser(roles = {"ADMIN", "GLOBAL_ADMIN"})
  @DisplayName("Global Admin can list encryption keys (200)")
  void globalAdminCanListKeys() throws Exception {
    Mockito.when(
            keyRepository.findAll(
                Mockito.<org.springframework.data.jpa.domain.Specification<EncryptionKey>>any(),
                Mockito.any(Pageable.class)))
        .thenReturn(new PageImpl<>(java.util.List.of()));

    mockMvc.perform(get("/api/v1/encryption-keys")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {"ADMIN", "GLOBAL_ADMIN"})
  @DisplayName("Global Admin can get primary key (404 when absent)")
  void globalAdminCanGetPrimary() throws Exception {
    Mockito.when(rotationService.getCurrentPrimaryKey()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/v1/encryption-keys/primary")).andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = {"ADMIN", "GLOBAL_ADMIN"})
  @DisplayName("Global Admin can get key by id (200)")
  void globalAdminCanGetKey() throws Exception {
    EncryptionKey key =
        new EncryptionKey(42L, KeyStatus.PRIMARY, "AES256_GCM", OffsetDateTime.now(), "SYSTEM");
    Mockito.when(keyRepository.findById(42L)).thenReturn(Optional.of(key));
    Mockito.when(keyUsageVerificationService.computeSnapshot(Mockito.any()))
        .thenReturn(
            new KeyUsageVerificationService.KeyUsageSnapshot(
                KeyUsageVerificationService.LIFECYCLE_PRIMARY,
                0L,
                0,
                OffsetDateTime.now(),
                KeyUsageVerificationService.VERIFICATION_PRIMARY_USAGE,
                false,
                false,
                null));

    mockMvc.perform(get("/api/v1/encryption-keys/42")).andExpect(status().isOk());
  }

  private void assertForbiddenOnAllRoutes(RequestPostProcessor auth) throws Exception {
    for (String path : GET_PATHS) {
      mockMvc.perform(get(path).with(auth)).andExpect(status().isForbidden());
    }
    for (String path : POST_PATHS) {
      mockMvc.perform(post(path).with(auth)).andExpect(status().isForbidden());
    }
  }
}
