/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrityAsyncJobControllerLastVerdictWebMvcTest
 * Description: Patrick T5 — GET /jobs/last-verdict 200 and 204 (+ Cache-Control).
 */

package org.ezkey.admin.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.ezkey.admin.config.AdminCorsTestFilterBeans;
import org.ezkey.admin.config.SecurityConfig;
import org.ezkey.admin.config.TrustedProxyConfig;
import org.ezkey.audit.asyncjob.IntegrityAsyncJobService;
import org.ezkey.audit.asyncjob.IntegrityAsyncJobStatus;
import org.ezkey.audit.asyncjob.IntegrityAsyncJobTrigger;
import org.ezkey.audit.asyncjob.IntegrityAsyncJobType;
import org.ezkey.audit.asyncjob.IntegrityChainStatus;
import org.ezkey.audit.dto.IntegrityAsyncJobResponse;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller contract for {@code GET …/integrity/jobs/last-verdict}.
 *
 * @since 2026
 */
@WebMvcTest(controllers = IntegrityAsyncJobController.class)
@Import({SecurityConfig.class, AdminCorsTestFilterBeans.class, TrustedProxyConfig.class})
@DisplayName("IntegrityAsyncJobController last-verdict")
class IntegrityAsyncJobControllerLastVerdictWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IntegrityAsyncJobService integrityAsyncJobService;
  @MockitoBean private EzkeyAdminRepository adminRepository;

  @Test
  @DisplayName("T5: last-verdict returns 200 + Cache-Control: no-store when a verdict exists")
  void lastVerdict_200_withNoStore() throws Exception {
    OffsetDateTime finished = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    IntegrityAsyncJobResponse body =
        new IntegrityAsyncJobResponse(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            IntegrityAsyncJobType.RUN_VALIDATION,
            IntegrityAsyncJobStatus.SUCCEEDED,
            IntegrityAsyncJobTrigger.NIGHTLY,
            "system",
            null,
            finished.minusHours(1),
            finished,
            finished,
            finished.minusHours(24),
            finished,
            "Nightly validation: intact=true, entryViolations=2, chainViolations=0",
            null,
            true,
            null,
            IntegrityChainStatus.OK,
            24,
            24,
            0,
            0,
            0,
            2,
            null,
            null,
            null);
    when(integrityAsyncJobService.getLastVerdict()).thenReturn(Optional.of(body));

    mockMvc
        .perform(get("/api/v1/audit-logs/integrity/jobs/last-verdict").with(user("ga").roles("GLOBAL_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
        .andExpect(jsonPath("$.chainStatus").value("OK"))
        .andExpect(jsonPath("$.entryHmacViolations").value(2))
        .andExpect(jsonPath("$.trigger").value("NIGHTLY"));
  }

  @Test
  @DisplayName("T5: last-verdict returns 204 + Cache-Control: no-store when none")
  void lastVerdict_204_withNoStore() throws Exception {
    when(integrityAsyncJobService.getLastVerdict()).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/audit-logs/integrity/jobs/last-verdict").with(user("ga").roles("GLOBAL_ADMIN")))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
  }

  @Test
  @DisplayName("current also sends Cache-Control: no-store on 204")
  void current_204_withNoStore() throws Exception {
    when(integrityAsyncJobService.getCurrent()).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/audit-logs/integrity/jobs/current").with(user("ga").roles("GLOBAL_ADMIN")))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
  }
}
