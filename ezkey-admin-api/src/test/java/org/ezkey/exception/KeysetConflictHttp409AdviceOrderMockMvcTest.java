/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeysetConflictHttp409AdviceOrderMockMvcTest
 * Description: MockMvc with all Admin advices — typed keyset conflicts stay domain HTTP 409.
 */

package org.ezkey.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.security.exception.KeysetConcurrentModificationException;
import org.ezkey.security.exception.ReencryptionTargetConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves {@link ReencryptionTargetConflictException} and {@link
 * KeysetConcurrentModificationException} map to domain HTTP 409 when {@link
 * ValidationExceptionHandler} ({@code @Order(10)}) and {@link DomainExceptionHandler}
 * ({@code @Order(80)}) are both registered — not swallowed by the catch-all {@code
 * IllegalStateException} handler.
 *
 * @since 2026
 */
@DisplayName("Keyset conflict HTTP 409 with all Admin exception advices")
class KeysetConflictHttp409AdviceOrderMockMvcTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ConflictProbeController())
            .setControllerAdvice(
                new ValidationExceptionHandler(),
                new DomainExceptionHandler(),
                new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("ReencryptionTargetConflictException → 409 domain type (not ISE catch-all)")
  void reencryptionTargetConflict_returnsDomain409() throws Exception {
    mockMvc
        .perform(get("/probe/reencryption-conflict").accept(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.title").value("Re-encryption Target Conflict"))
        .andExpect(
            jsonPath("$.type")
                .value("https://ezkey.io/problems/domain/reencryption-target-conflict"));
  }

  @Test
  @DisplayName("KeysetConcurrentModificationException → 409 domain type")
  void keysetConcurrentModification_returnsDomain409() throws Exception {
    mockMvc
        .perform(get("/probe/keyset-cas-conflict").accept(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.title").value("Keyset Concurrent Modification"))
        .andExpect(
            jsonPath("$.type")
                .value("https://ezkey.io/problems/domain/keyset-concurrent-modification"));
  }

  @RestController
  static class ConflictProbeController {

    @GetMapping("/probe/reencryption-conflict")
    void reencryptionConflict() {
      throw new ReencryptionTargetConflictException(
          42L, 42L, "Cannot re-encrypt: old key equals primary; refusing silent no-op");
    }

    @GetMapping("/probe/keyset-cas-conflict")
    void keysetCasConflict() {
      throw new KeysetConcurrentModificationException(
          3L, "Keyset blob CAS failed: expected version 3 was superseded");
    }
  }
}
