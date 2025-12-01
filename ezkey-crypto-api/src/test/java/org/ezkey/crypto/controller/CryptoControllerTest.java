/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: CryptoControllerTest
 * Description: Unit tests for CryptoController endpoints.
 */

package org.ezkey.crypto.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.crypto.config.SecurityConfig;
import org.ezkey.signature.ECP256KeyPair;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CryptoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class CryptoControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SignatureService signatureService;

  @Test
  void testProofTokenEndpoint() throws Exception {
    when(signatureService.generateProofToken()).thenReturn("test-proof-token");

    mockMvc
        .perform(get("/api/v1/crypto/prooftoken"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.proofToken").value("test-proof-token"));
  }

  @Test
  void testKeyPairGenerationEndpoint() throws Exception {
    ECP256KeyPair keyPair = new ECP256KeyPair("test-private-key", "test-public-key");
    when(signatureService.generateECP256KeyPair()).thenReturn(keyPair);

    mockMvc
        .perform(get("/api/v1/crypto/keypair"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.privateKey").value("test-private-key"))
        .andExpect(jsonPath("$.publicKey").value("test-public-key"));
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
            post("/api/v1/crypto/sign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.signature").value("test-signature"))
        .andExpect(jsonPath("$.originalData").value("Hello, World!"))
        .andExpect(jsonPath("$.algorithm").value("EC_P256"));
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
            post("/api/v1/crypto/sign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
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
            post("/api/v1/crypto/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.message").value("Signature is valid"))
        .andExpect(jsonPath("$.algorithm").value("EC_P256"));
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
            post("/api/v1/crypto/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.message").value("Signature is invalid"))
        .andExpect(jsonPath("$.algorithm").value("EC_P256"));
  }
}
