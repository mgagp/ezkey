/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EncryptionKeyControllerRotateMockMvcTest
 * Description: MockMvc + real exception advice for POST /encryption-keys/rotate CAS 409 and
 *     non-CAS 500 characterization (#961 Lot 4).
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.exception.AdminApiProblemCatalog;
import org.ezkey.exception.GlobalExceptionHandler;
import org.ezkey.exception.ValidationExceptionHandler;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.security.KeyRotationService;
import org.ezkey.security.KeyUsageVerificationService;
import org.ezkey.security.ReencryptionService;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.ezkey.security.exception.KeysetCasConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Standalone MockMvc proofs for {@code POST /api/v1/encryption-keys/rotate} with production
 * exception advice ({@link ValidationExceptionHandler} {@code @Order(10)} then {@link
 * GlobalExceptionHandler} {@code @Order(99)}).
 *
 * <p>#961 Lot 4 (Patrick m1/m2): CAS loss must reach the real advice chain as HTTP 409; widening
 * the CAS catch must not silently swallow ordinary failures (500 + {@code REENCRYPTION_FAILED}
 * ERROR audit).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EncryptionKeyController rotate MockMvc (#961 Lot 4)")
class EncryptionKeyControllerRotateMockMvcTest {

  private static final String ROTATE_PATH = "/api/v1/encryption-keys/rotate";
  private static final String OPERATOR_CAS_DETAIL =
      "The encryption keyset was modified concurrently; no key was added by this request."
          + " Retry is safe.";

  @Mock private EncryptionKeyRepository keyRepository;
  @Mock private ReencryptionBatchRepository batchRepository;
  @Mock private KeyRotationService rotationService;
  @Mock private ReencryptionService reencryptionService;
  @Mock private AuditLogService auditLogService;
  @Mock private KeyUsageVerificationService keyUsageVerificationService;

  private MockMvc mockMvc;
  private Authentication globalAdminAuth;

  @BeforeEach
  void setUp() {
    EncryptionKeyController controller =
        new EncryptionKeyController(
            keyRepository,
            batchRepository,
            rotationService,
            reencryptionService,
            auditLogService,
            keyUsageVerificationService);

    // Order annotations on the advice types are respected by AnnotationAwareOrderComparator
    // (ValidationExceptionHandler @Order(10) before GlobalExceptionHandler @Order(99)).
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ValidationExceptionHandler(), new GlobalExceptionHandler())
            .build();

    globalAdminAuth =
        new UsernamePasswordAuthenticationToken(
            new AdminPrincipal(7, AdminType.GLOBAL_ADMIN, null, null),
            null,
            List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN")));
  }

  @Test
  @DisplayName(
      "m1: CAS KeysetCasConflictException → 409 admin/state-conflict via real advice, no ERROR"
          + " audit")
  void rotate_whenKeysetCasConflict_mockMvcAdviceReturns409WithoutErrorAudit() throws Exception {
    when(rotationService.introduceNewKey("ADMIN_MANUAL"))
        .thenThrow(
            new KeysetCasConflictException(
                "Keyset mutation CAS failed (updatedBy=KEY_ADDED_PENDING); staging discarded and"
                    + " blob reloaded",
                new IllegalStateException("StaleObjectStateException: kid=42 version=3")));

    MvcResult result =
        mockMvc
            .perform(
                post(ROTATE_PATH)
                    .principal(globalAdminAuth)
                    .accept(MediaType.APPLICATION_JSON)
                    .queryParam("reason", "HA concurrent rotate loser"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.type").value(AdminApiProblemCatalog.TYPE_STATE_CONFLICT))
            .andExpect(jsonPath("$.title").value(AdminApiProblemCatalog.TITLE_STATE_CONFLICT))
            .andExpect(jsonPath("$.detail").value(OPERATOR_CAS_DETAIL))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertFalse(body.contains("updatedBy"), "body must not leak updatedBy");
    assertFalse(body.contains("KEY_ADDED_PENDING"), "body must not leak CAS updatedBy token");
    assertFalse(body.contains("kid="), "body must not leak kid");
    assertFalse(body.contains("StaleObjectStateException"), "body must not leak cause type");
    assertFalse(body.contains("at org."), "body must not include stack frames");
    assertFalse(body.contains("CAS failed"), "body must not leak internal CAS wording");
    assertFalse(body.contains("\"cause\""), "body must not include a cause field");

    verify(auditLogService, never()).log(any(AuditLog.class));
  }

  @Test
  @DisplayName(
      "m2: plain RuntimeException → HTTP 500 and exactly one REENCRYPTION_FAILED/ERROR audit")
  void rotate_whenRuntimeException_returns500AndLogsReencryptionFailedError() throws Exception {
    when(rotationService.introduceNewKey("ADMIN_MANUAL"))
        .thenThrow(new RuntimeException("simulated rotate boom"));

    mockMvc
        .perform(
            post(ROTATE_PATH)
                .principal(globalAdminAuth)
                .accept(MediaType.APPLICATION_JSON)
                .queryParam("reason", "characterization runtime failure"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.newPrimaryKeyId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.message").value("Key rotation failed: simulated rotate boom"));

    assertExactlyOneReencryptionFailedErrorAudit("characterization runtime failure");
  }

  @Test
  @DisplayName(
      "m2: non-CAS IllegalStateException → HTTP 500 and exactly one REENCRYPTION_FAILED/ERROR"
          + " audit")
  void rotate_whenNonCasIllegalStateException_returns500AndLogsReencryptionFailedError()
      throws Exception {
    when(rotationService.introduceNewKey("ADMIN_MANUAL"))
        .thenThrow(new IllegalStateException("non-writer keyset state invariant broken"));

    mockMvc
        .perform(
            post(ROTATE_PATH)
                .principal(globalAdminAuth)
                .accept(MediaType.APPLICATION_JSON)
                .queryParam("reason", "characterization non-cas ise"))
        .andExpect(status().isInternalServerError())
        .andExpect(
            jsonPath("$.message")
                .value("Key rotation failed: non-writer keyset state invariant broken"));

    assertExactlyOneReencryptionFailedErrorAudit("characterization non-cas ise");
  }

  private void assertExactlyOneReencryptionFailedErrorAudit(String expectedReason) {
    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(captor.capture());
    AuditLog logged = captor.getValue();
    assertNotNull(logged);
    assertEquals(EventType.REENCRYPTION_FAILED, logged.getEventType());
    assertEquals(EventStatus.ERROR, logged.getEventStatus());
    assertEquals(7, logged.getAdminId());
    assertEquals(expectedReason, logged.getReason());
  }
}
