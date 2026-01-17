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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.crypto.config.SecurityConfig;
import org.ezkey.security.EncryptionService;
import org.ezkey.signature.ECP256KeyPair;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CryptoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({SecurityConfig.class, CryptoControllerTest.TestConfig.class})
class CryptoControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private SignatureService signatureService;

  @Autowired private EncryptionService encryptionService;

  @TestConfiguration
  static class TestConfig {

    @Bean
    public SignatureService signatureService() {
      return mock(SignatureService.class);
    }

    @Bean
    public EncryptionService encryptionService() {
      return mock(EncryptionService.class);
    }
  }

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

  @Test
  void testDecryptEndpointSuccessful() throws Exception {
    String encryptedValue = "ENC:1234567890:YWJjZGVmZ2hpams=";
    String decryptedValue = "decrypted-plaintext-value";
    when(encryptionService.isEncryptionAvailable()).thenReturn(true);
    when(encryptionService.isEncrypted(encryptedValue)).thenReturn(true);
    when(encryptionService.parseKeyIdFromPrefix(encryptedValue)).thenReturn(1234567890L);
    when(encryptionService.decrypt(encryptedValue)).thenReturn(decryptedValue);

    String requestBody =
        """
        {
          "encryptedValue": "ENC:1234567890:YWJjZGVmZ2hpams="
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/decrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.plaintext").value(decryptedValue))
        .andExpect(jsonPath("$.isEncrypted").value(true))
        .andExpect(jsonPath("$.decryptionSuccessful").value(true))
        .andExpect(jsonPath("$.keyId").value("1234567890"))
        .andExpect(jsonPath("$.encryptedFormat").value("ENC:keyID:Base64"))
        .andExpect(jsonPath("$.errorMessage").isEmpty())
        .andExpect(jsonPath("$.encryptionAvailable").value(true));
  }

  @Test
  void testDecryptEndpointPlaintext() throws Exception {
    String plaintextValue = "plaintext-value";
    when(encryptionService.isEncryptionAvailable()).thenReturn(true);
    when(encryptionService.isEncrypted(plaintextValue)).thenReturn(false);

    String requestBody =
        """
        {
          "encryptedValue": "plaintext-value"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/decrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.plaintext").value(plaintextValue))
        .andExpect(jsonPath("$.isEncrypted").value(false))
        .andExpect(jsonPath("$.decryptionSuccessful").value(true))
        .andExpect(jsonPath("$.keyId").isEmpty())
        .andExpect(jsonPath("$.encryptedFormat").value("PLAINTEXT"))
        .andExpect(jsonPath("$.errorMessage").isEmpty())
        .andExpect(jsonPath("$.encryptionAvailable").value(true));
  }

  @Test
  void testDecryptEndpointDecryptionFailure() throws Exception {
    String encryptedValue = "ENC:1234567890:YWJjZGVmZ2hpams=";
    when(encryptionService.isEncryptionAvailable()).thenReturn(true);
    when(encryptionService.isEncrypted(encryptedValue)).thenReturn(true);
    when(encryptionService.parseKeyIdFromPrefix(encryptedValue)).thenReturn(1234567890L);
    // decrypt() returns original value on failure
    when(encryptionService.decrypt(encryptedValue)).thenReturn(encryptedValue);

    String requestBody =
        """
        {
          "encryptedValue": "ENC:1234567890:YWJjZGVmZ2hpams="
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/decrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.plaintext").isEmpty())
        .andExpect(jsonPath("$.isEncrypted").value(true))
        .andExpect(jsonPath("$.decryptionSuccessful").value(false))
        .andExpect(jsonPath("$.keyId").value("1234567890"))
        .andExpect(jsonPath("$.encryptedFormat").value("ENC:keyID:Base64"))
        .andExpect(jsonPath("$.errorMessage").isNotEmpty())
        .andExpect(jsonPath("$.encryptionAvailable").value(true));
  }

  @Test
  void testDecryptEndpointEncryptionServiceUnavailable() throws Exception {
    String value = "some-value";
    when(encryptionService.isEncryptionAvailable()).thenReturn(false);

    String requestBody =
        """
        {
          "encryptedValue": "some-value"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/decrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.plaintext").value(value))
        .andExpect(jsonPath("$.isEncrypted").value(false))
        .andExpect(jsonPath("$.decryptionSuccessful").value(true))
        .andExpect(jsonPath("$.encryptedFormat").value("PLAINTEXT"))
        .andExpect(jsonPath("$.encryptionAvailable").value(false))
        .andExpect(jsonPath("$.errorMessage").isEmpty());
  }

  @Test
  void testDecryptEndpointEncryptionServiceUnavailableEncryptedValue() throws Exception {
    String encryptedValue = "ENC:1234567890:YWJjZGVmZ2hpams=";
    when(encryptionService.isEncryptionAvailable()).thenReturn(false);
    when(encryptionService.isEncrypted(encryptedValue)).thenReturn(true);
    when(encryptionService.parseKeyIdFromPrefix(encryptedValue)).thenReturn(1234567890L);

    String requestBody =
        """
        {
          "encryptedValue": "ENC:1234567890:YWJjZGVmZ2hpams="
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/decrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.plaintext").isEmpty())
        .andExpect(jsonPath("$.isEncrypted").value(true))
        .andExpect(jsonPath("$.decryptionSuccessful").value(false))
        .andExpect(jsonPath("$.keyId").value("1234567890"))
        .andExpect(jsonPath("$.encryptedFormat").value("ENC:keyID:Base64"))
        .andExpect(
            jsonPath("$.errorMessage")
                .value("Encryption service not available - cannot decrypt encrypted value."))
        .andExpect(jsonPath("$.encryptionAvailable").value(false));
  }

  @Test
  void testDecryptEndpointInvalidEncryptedFormat() throws Exception {
    String invalidEncryptedValue = "ENC:not-a-number:@@@";
    when(encryptionService.isEncryptionAvailable()).thenReturn(true);
    when(encryptionService.isEncrypted(invalidEncryptedValue)).thenReturn(false);

    String requestBody =
        """
        {
          "encryptedValue": "ENC:not-a-number:@@@"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/decrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.plaintext").isEmpty())
        .andExpect(jsonPath("$.isEncrypted").value(true))
        .andExpect(jsonPath("$.decryptionSuccessful").value(false))
        .andExpect(jsonPath("$.keyId").isEmpty())
        .andExpect(jsonPath("$.encryptedFormat").value("ENC:INVALID"))
        .andExpect(
            jsonPath("$.errorMessage")
                .value("Invalid encrypted format. Expected ENC:keyID:Base64(ciphertext)."))
        .andExpect(jsonPath("$.encryptionAvailable").value(true));
  }

  @Test
  void testDecryptEndpointEmptyValue() throws Exception {
    String requestBody =
        """
        {
          "encryptedValue": ""
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/decrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }
}
