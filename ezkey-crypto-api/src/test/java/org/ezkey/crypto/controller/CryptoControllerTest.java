/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import org.ezkey.crypto.exception.CryptoApiProblemCatalog;
import org.ezkey.security.EncryptionService;
import org.ezkey.security.SensitiveDataHasher;
import org.ezkey.signature.ECP256KeyPair;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
    when(signatureService.signEcdsaSha256(anyString(), anyString())).thenReturn("test-signature");

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
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(CryptoApiProblemCatalog.TYPE_VALIDATION_FAILED));
  }

  @Test
  void testSignEd25519Endpoint() throws Exception {
    when(signatureService.signIntegrationPayload(anyString(), anyString()))
        .thenReturn("test-ed25519-signature");

    String requestBody =
        """
        {
          "data": "tok|true|Title|Msg",
          "privateKey": "test-private-key"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/sign-ed25519")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.signature").value("test-ed25519-signature"))
        .andExpect(jsonPath("$.originalData").value("tok|true|Title|Msg"))
        .andExpect(jsonPath("$.algorithm").value("ED25519"));
  }

  @Test
  void testSignEd25519InternalError() throws Exception {
    when(signatureService.signIntegrationPayload(anyString(), anyString()))
        .thenThrow(new RuntimeException("malformed key"));

    String requestBody =
        """
        {
          "data": "tok|true|Title|Msg",
          "privateKey": "bad-private-key"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/sign-ed25519")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(CryptoApiProblemCatalog.TYPE_INTERNAL_ERROR));
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
  void testVerifyEd25519Endpoint() throws Exception {
    when(signatureService.verifyIntegrationSignature(anyString(), anyString(), anyString()))
        .thenReturn(true);

    String requestBody =
        """
        {
          "data": "tok|true|Title|Msg",
          "signature": "test-signature",
          "publicKey": "test-public-key"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/verify-ed25519")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.message").value("Signature is valid"))
        .andExpect(jsonPath("$.algorithm").value("ED25519"));
  }

  @Test
  void testVerifyEd25519Invalid() throws Exception {
    when(signatureService.verifyIntegrationSignature(anyString(), anyString(), anyString()))
        .thenReturn(false);

    String requestBody =
        """
        {
          "data": "tok|true|Title|Msg",
          "signature": "invalid-signature",
          "publicKey": "test-public-key"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/verify-ed25519")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.message").value("Signature is invalid"))
        .andExpect(jsonPath("$.algorithm").value("ED25519"));
  }

  @Test
  void testPayloadHelperPendingEndpoint() throws Exception {
    String requestBody =
        """
        {
          "type": "pending",
          "proofToken": "tok",
          "challengeRequired": true,
          "contextTitle": "Title",
          "contextMessage": "Msg"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/payload-helper")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.type").value("pending"))
        .andExpect(jsonPath("$.payload").value("tok|true|Title|Msg"))
        .andExpect(jsonPath("$.encoding").value("UTF-8 + NFC where applicable"));
  }

  @Test
  void testPayloadHelperRespondEndpoint() throws Exception {
    String requestBody =
        """
        {
          "type": "respond",
          "proofToken": "tok",
          "accepted": false
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/payload-helper")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("respond"))
        .andExpect(jsonPath("$.payload").value("tok|false"));
  }

  @Test
  void testPayloadHelperRespondResultEndpointNormalizesMessage() throws Exception {
    String requestBody =
        """
        {
          "type": "respond-result",
          "proofToken": "tok",
          "authAttemptId": 42,
          "result": "approved",
          "message": "Cafe\u0301"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/payload-helper")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("respond-result"))
        .andExpect(jsonPath("$.payload").value("tok|42|APPROVED|Caf\u00E9"));
  }

  @Test
  void testPayloadHelperEnrollmentBindEndpoint() throws Exception {
    String requestBody =
        """
        {
          "type": "enrollment-bind",
          "proofToken": "ptok",
          "enrollmentId": 99,
          "integrationPublicKey": "integPk",
          "integrationKeyAlgorithm": "ed25519",
          "integrationName": "Acme",
          "integrationDescription": "Desc",
          "enrollmentName": "Device A",
          "tenantId": 1,
          "tenantName": "Tenant",
          "tenantDescription": "TDesc"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/payload-helper")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("enrollment-bind"))
        .andExpect(
            jsonPath("$.payload")
                .value("ptok|99|integPk|ed25519|Acme|Desc|Device A|1|Tenant|TDesc|false|"))
        .andExpect(jsonPath("$.encoding").value("UTF-8 + NFC where applicable"));
  }

  @Test
  void testPayloadHelperEnrollmentVerifyDeviceEndpoint() throws Exception {
    String requestBody =
        """
        {
          "type": "enrollment-verify-device",
          "proofToken": "ptok",
          "enrollmentId": 42,
          "challengeResponse": 123456,
          "devicePublicKey": "MIIBDevice"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/payload-helper")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("enrollment-verify-device"))
        .andExpect(jsonPath("$.payload").value("ptok|42|123456|MIIBDevice"));
  }

  @Test
  void testPayloadHelperEnrollmentVerifyResultEndpoint() throws Exception {
    String requestBody =
        """
        {
          "type": "enrollment-verify-result",
          "proofToken": "ptok",
          "enrollmentId": 7,
          "result": "VERIFIED",
          "message": "Enrollment verified successfully"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/payload-helper")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("enrollment-verify-result"))
        .andExpect(jsonPath("$.payload").value("ptok|7|VERIFIED|Enrollment verified successfully"));
  }

  @Test
  void testPayloadHelperRejectsMissingEnrollmentVerifyDeviceFields() throws Exception {
    String requestBody =
        """
        {
          "type": "enrollment-verify-device",
          "proofToken": "ptok",
          "enrollmentId": 1
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/payload-helper")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(CryptoApiProblemCatalog.TYPE_INVALID_ARGUMENT));
  }

  @Test
  void testPayloadHelperRejectsMissingPendingFields() throws Exception {
    String requestBody =
        """
        {
          "type": "pending",
          "proofToken": "tok"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/payload-helper")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(CryptoApiProblemCatalog.TYPE_INVALID_ARGUMENT));
  }

  @Test
  void testHashTokenEndpoint() throws Exception {
    String token = "ezkey_a1b2c3d4e5f67890";
    String expectedHash = SensitiveDataHasher.sha256Hex(token);
    String requestBody =
        """
        {
          "token": "ezkey_a1b2c3d4e5f67890"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/hash-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tokenHash").value(expectedHash));
  }

  @Test
  void testHashTokenWithEmptyToken() throws Exception {
    String requestBody =
        """
        {
          "token": ""
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/hash-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest());
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
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(CryptoApiProblemCatalog.TYPE_VALIDATION_FAILED));
  }

  @Test
  void testEncryptEndpointSuccessful() throws Exception {
    String plaintext = "my-secret-value";
    String encryptedValue = "ENC:2865054995:YWJjZGVmZ2hpams=";
    when(encryptionService.isEncryptionAvailable()).thenReturn(true);
    when(encryptionService.encrypt(plaintext)).thenReturn(encryptedValue);
    when(encryptionService.parseKeyIdFromPrefix(encryptedValue)).thenReturn(2865054995L);

    String requestBody =
        """
        {
          "plaintext": "my-secret-value"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/encrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.encryptedValue").value(encryptedValue))
        .andExpect(jsonPath("$.encryptionSuccessful").value(true))
        .andExpect(jsonPath("$.keyId").value("2865054995"))
        .andExpect(jsonPath("$.encryptedFormat").value("ENC:keyID:Base64"))
        .andExpect(jsonPath("$.errorMessage").isEmpty())
        .andExpect(jsonPath("$.encryptionAvailable").value(true));
  }

  @Test
  void testEncryptEndpointEncryptionServiceUnavailable() throws Exception {
    String plaintext = "my-secret-value";
    when(encryptionService.isEncryptionAvailable()).thenReturn(false);

    String requestBody =
        """
        {
          "plaintext": "my-secret-value"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/encrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.encryptedValue").value(plaintext))
        .andExpect(jsonPath("$.encryptionSuccessful").value(false))
        .andExpect(jsonPath("$.keyId").isEmpty())
        .andExpect(jsonPath("$.encryptedFormat").value("PLAINTEXT"))
        .andExpect(
            jsonPath("$.errorMessage")
                .value("Encryption service not available - returning plaintext."))
        .andExpect(jsonPath("$.encryptionAvailable").value(false));
  }

  @Test
  void testEncryptEndpointAlreadyEncryptedValue() throws Exception {
    String alreadyEncrypted = "ENC:1234567890:YWJjZGVmZ2hpams=";
    when(encryptionService.isEncryptionAvailable()).thenReturn(true);
    when(encryptionService.encrypt(alreadyEncrypted)).thenReturn(alreadyEncrypted);
    when(encryptionService.isEncrypted(alreadyEncrypted)).thenReturn(true);

    String requestBody =
        """
        {
          "plaintext": "ENC:1234567890:YWJjZGVmZ2hpams="
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/encrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.encryptedValue").value(alreadyEncrypted))
        .andExpect(jsonPath("$.encryptionSuccessful").value(false))
        .andExpect(jsonPath("$.keyId").isEmpty())
        .andExpect(jsonPath("$.encryptedFormat").value("PLAINTEXT"))
        .andExpect(
            jsonPath("$.errorMessage").value("Value is already encrypted - skipped re-encryption."))
        .andExpect(jsonPath("$.encryptionAvailable").value(true));
  }

  @Test
  void testEncryptEndpointEncryptionFailure() throws Exception {
    String plaintext = "my-secret-value";
    when(encryptionService.isEncryptionAvailable()).thenReturn(true);
    when(encryptionService.encrypt(plaintext))
        .thenThrow(new IllegalStateException("Encryption failed"));

    String requestBody =
        """
        {
          "plaintext": "my-secret-value"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/encrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.encryptedValue").value(plaintext))
        .andExpect(jsonPath("$.encryptionSuccessful").value(false))
        .andExpect(jsonPath("$.keyId").isEmpty())
        .andExpect(jsonPath("$.encryptedFormat").value("PLAINTEXT"))
        .andExpect(jsonPath("$.errorMessage").value("Encryption failed: Encryption failed"))
        .andExpect(jsonPath("$.encryptionAvailable").value(true));
  }

  @Test
  void testEncryptEndpointEmptyValue() throws Exception {
    String requestBody =
        """
        {
          "plaintext": ""
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/encrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(CryptoApiProblemCatalog.TYPE_VALIDATION_FAILED));
  }

  @Test
  void testEncryptEndpointNullValue() throws Exception {
    String requestBody =
        """
        {
          "plaintext": null
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/crypto/encrypt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(CryptoApiProblemCatalog.TYPE_VALIDATION_FAILED));
  }
}
