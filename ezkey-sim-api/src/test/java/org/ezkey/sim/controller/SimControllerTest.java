/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: SimControllerTest
 * Description: Unit tests for SimController endpoints.
 */

package org.ezkey.sim.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.signature.RsaKeyPair;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SimController.class)
class SimControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SignatureService signatureService;

  @Test
  void testProofTokenEndpoint() throws Exception {
    when(signatureService.generateProofToken()).thenReturn("test-proof-token");

    mockMvc
        .perform(get("/api/v1/sim/prooftoken"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.proofToken").value("test-proof-token"));
  }

  @Test
  void testKeyPairGenerationEndpoint() throws Exception {
    RsaKeyPair keyPair = new RsaKeyPair("test-private-key", "test-public-key");
    when(signatureService.generateRsaKeyPair(2048)).thenReturn(keyPair);

    mockMvc
        .perform(get("/api/v1/sim/keypair"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.privateKey").value("test-private-key"))
        .andExpect(jsonPath("$.publicKey").value("test-public-key"))
        .andExpect(jsonPath("$.keySize").value(2048));
  }

  @Test
  void testKeyPairGenerationWithCustomSize() throws Exception {
    RsaKeyPair keyPair = new RsaKeyPair("test-private-key-4096", "test-public-key-4096");
    when(signatureService.generateRsaKeyPair(4096)).thenReturn(keyPair);

    mockMvc
        .perform(get("/api/v1/sim/keypair?keySize=4096"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.privateKey").value("test-private-key-4096"))
        .andExpect(jsonPath("$.publicKey").value("test-public-key-4096"))
        .andExpect(jsonPath("$.keySize").value(4096));
  }

  @Test
  void testKeyPairGenerationWithInvalidSize() throws Exception {
    mockMvc
        .perform(get("/api/v1/sim/keypair?keySize=512"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
  }

  @Test
  void testSignDataEndpoint() throws Exception {
    when(signatureService.generateSignature(anyString(), anyString())).thenReturn("test-signature");

    String requestBody =
        """
                {
                  "data": "Hello, World!",
                  "privateKey": "test-private-key"
                }
                """;

    mockMvc
        .perform(
            post("/api/v1/sim/sign").contentType(MediaType.APPLICATION_JSON).content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.signature").value("test-signature"))
        .andExpect(jsonPath("$.originalData").value("Hello, World!"))
        .andExpect(jsonPath("$.algorithm").value("SHA256withRSA"));
  }

  @Test
  void testSignDataWithEmptyData() throws Exception {
    String requestBody =
        """
                {
                  "data": "",
                  "privateKey": "test-private-key"
                }
                """;

    mockMvc
        .perform(
            post("/api/v1/sim/sign").contentType(MediaType.APPLICATION_JSON).content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void testValidateSignatureEndpoint() throws Exception {
    when(signatureService.validateSignature(anyString(), anyString(), anyString()))
        .thenReturn(true);

    String requestBody =
        """
                {
                  "data": "Hello, World!",
                  "signature": "test-signature",
                  "publicKey": "test-public-key"
                }
                """;

    mockMvc
        .perform(
            post("/api/v1/sim/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.message").value("Signature is valid"))
        .andExpect(jsonPath("$.algorithm").value("SHA256withRSA"));
  }

  @Test
  void testValidateSignatureInvalid() throws Exception {
    when(signatureService.validateSignature(anyString(), anyString(), anyString()))
        .thenReturn(false);

    String requestBody =
        """
                {
                  "data": "Hello, World!",
                  "signature": "invalid-signature",
                  "publicKey": "test-public-key"
                }
                """;

    mockMvc
        .perform(
            post("/api/v1/sim/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.message").value("Signature is invalid"))
        .andExpect(jsonPath("$.algorithm").value("SHA256withRSA"));
  }
}
