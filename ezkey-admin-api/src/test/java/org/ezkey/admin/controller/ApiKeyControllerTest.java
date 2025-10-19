/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ApiKeyControllerTest
 * Description: Unit tests for ApiKeyController REST endpoints.
 */

package org.ezkey.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import org.ezkey.admin.config.SecurityConfig;
import org.ezkey.admin.dto.request.ApiKeyCreateRequestDto;
import org.ezkey.integration.domain.entity.ApiKey;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.service.ApiKeyService;
import org.ezkey.integration.service.ApiKeyService.ApiKeyCreationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit tests for ApiKeyController.
 *
 * <p>This test class validates API key management REST endpoints including creation, listing,
 * retrieval, and revocation operations.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyController
 */
@WebMvcTest(controllers = ApiKeyController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
@DisplayName("ApiKeyController Tests")
class ApiKeyControllerTest {

  private static final String BASE_URL = "/api/v1/api-keys";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private ApiKeyService apiKeyService;

  // Mock security filters required by SecurityConfig
  @MockBean private org.ezkey.admin.security.AdminTokenAuthenticationFilter adminTokenAuthenticationFilter;
  @MockBean private org.ezkey.admin.security.ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

  private Integration testIntegration;
  private ApiKey testApiKey;
  private EzkeyAdmin testAdmin;
  private ApiKeyCreationResult creationResult;

  @BeforeEach
  void setUp() {
    // Setup test integration
    testIntegration = new Integration();
    testIntegration.setId(123);

    // Setup test API key
    testApiKey = new ApiKey();
    testApiKey.setApiKeyId(42);
    testApiKey.setIntegration(testIntegration);
    testApiKey.setIntegrationKey("ezkey_ikey_a1b2c3d4e5f6g7h8i9j0");
    testApiKey.setDescription("Test API Key");
    testApiKey.setActive(true);
    testApiKey.setCreatedAt(OffsetDateTime.now());

    // Setup creation result
    creationResult =
        new ApiKeyCreationResult(
            42,
            "ezkey_ikey_a1b2c3d4e5f6g7h8i9j0",
            "ezkey_skey_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0",
            "Test API Key",
            OffsetDateTime.now(),
            null,
            null);

    // Setup test admin
    testAdmin = new EzkeyAdmin();
    testAdmin.setAdminId(1);
    testAdmin.setUsername("admin");

    // Setup Security Context with authenticated admin
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            testAdmin,
            null,
            java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @Nested
  @DisplayName("Create API Key Tests")
  class CreateApiKeyTests {

    @Test
    @DisplayName("POST /api-keys - Should return 201 when API key created successfully")
    void createApiKey_WhenValidRequest_ShouldReturn201() throws Exception {
      // Arrange
      ApiKeyCreateRequestDto request = new ApiKeyCreateRequestDto(123, "Production Server", null, null);

      when(apiKeyService.createApiKey(any(), any(), any(), any(), any())).thenReturn(creationResult);

      String json = objectMapper.writeValueAsString(request);

      // Act & Assert
      mockMvc
          .perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.apiKeyId").value(42))
          .andExpect(jsonPath("$.integrationKey").value("ezkey_ikey_a1b2c3d4e5f6g7h8i9j0"))
          .andExpect(jsonPath("$.secretKey").value("ezkey_skey_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0"))
          .andExpect(jsonPath("$.warning").exists());

      verify(apiKeyService, times(1)).createApiKey(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /api-keys - Should return 400 when integration not found")
    void createApiKey_WhenIntegrationNotFound_ShouldReturn400() throws Exception {
      // Arrange
      ApiKeyCreateRequestDto request = new ApiKeyCreateRequestDto(999, null, null, null);

      when(apiKeyService.createApiKey(any(), any(), any(), any(), any()))
          .thenThrow(new IllegalArgumentException("Integration not found"));

      String json = objectMapper.writeValueAsString(request);

      // Act & Assert
      mockMvc
          .perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api-keys - Should return 409 when max keys limit reached")
    void createApiKey_WhenMaxKeysReached_ShouldReturn409() throws Exception {
      // Arrange
      ApiKeyCreateRequestDto request = new ApiKeyCreateRequestDto(123, null, null, null);

      when(apiKeyService.createApiKey(any(), any(), any(), any(), any()))
          .thenThrow(new IllegalStateException("Maximum active keys limit reached"));

      String json = objectMapper.writeValueAsString(request);

      // Act & Assert
      mockMvc
          .perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
          .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api-keys - Should accept expiration date")
    void createApiKey_WithExpirationDate_ShouldSucceed() throws Exception {
      // Arrange
      OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(90);
      ApiKeyCreateRequestDto request = new ApiKeyCreateRequestDto(123, "Production Key", expiresAt, null);

      when(apiKeyService.createApiKey(any(), any(), any(), any(), any()))
          .thenReturn(creationResult);

      String json = objectMapper.writeValueAsString(request);

      // Act & Assert
      mockMvc
          .perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
          .andExpect(status().isCreated());

      verify(apiKeyService).createApiKey(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /api-keys - Should accept IP whitelist")
    void createApiKey_WithIpWhitelist_ShouldSucceed() throws Exception {
      // Arrange
      String[] ipWhitelist = {"192.168.1.0/24", "10.0.0.100"};
      ApiKeyCreateRequestDto request = new ApiKeyCreateRequestDto(123, null, null, ipWhitelist);

      when(apiKeyService.createApiKey(any(), any(), any(), any(), any())).thenReturn(creationResult);

      String json = objectMapper.writeValueAsString(request);

      // Act & Assert
      mockMvc
          .perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
          .andExpect(status().isCreated());
    }
  }

  @Nested
  @DisplayName("List API Keys Tests")
  class ListApiKeysTests {

    @Test
    @DisplayName("GET /api-keys/integration/{id} - Should return list of active keys")
    void listApiKeys_WhenKeysExist_ShouldReturn200() throws Exception {
      // Arrange
      when(apiKeyService.listActiveApiKeys(123)).thenReturn(Arrays.asList(testApiKey));

      // Act & Assert
      mockMvc
          .perform(get(BASE_URL + "/integration/123"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray())
          .andExpect(jsonPath("$[0].apiKeyId").value(42))
          .andExpect(jsonPath("$[0].integrationKey").value("ezkey_ikey_a1b2c3d4e5f6g7h8i9j0"))
          .andExpect(jsonPath("$[0].secretKey").doesNotExist()); // Secret never returned

      verify(apiKeyService).listActiveApiKeys(123);
    }

    @Test
    @DisplayName("GET /api-keys/integration/{id} - Should return empty list when no keys")
    void listApiKeys_WhenNoKeys_ShouldReturnEmptyList() throws Exception {
      // Arrange
      when(apiKeyService.listActiveApiKeys(123)).thenReturn(Arrays.asList());

      // Act & Assert
      mockMvc
          .perform(get(BASE_URL + "/integration/123"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray())
          .andExpect(jsonPath("$").isEmpty());
    }
  }

  @Nested
  @DisplayName("Get API Key Tests")
  class GetApiKeyTests {

    @Test
    @DisplayName("GET /api-keys/{id} - Should return key details when found")
    void getApiKey_WhenExists_ShouldReturn200() throws Exception {
      // Arrange
      when(apiKeyService.getApiKey(42)).thenReturn(Optional.of(testApiKey));

      // Act & Assert
      mockMvc
          .perform(get(BASE_URL + "/42"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.apiKeyId").value(42))
          .andExpect(jsonPath("$.integrationKey").value("ezkey_ikey_a1b2c3d4e5f6g7h8i9j0"))
          .andExpect(jsonPath("$.secretKey").doesNotExist()); // Secret never returned

      verify(apiKeyService).getApiKey(42);
    }

    @Test
    @DisplayName("GET /api-keys/{id} - Should return 404 when not found")
    void getApiKey_WhenNotFound_ShouldReturn404() throws Exception {
      // Arrange
      when(apiKeyService.getApiKey(999)).thenReturn(Optional.empty());

      // Act & Assert
      mockMvc.perform(get(BASE_URL + "/999")).andExpect(status().isNotFound());

      verify(apiKeyService).getApiKey(999);
    }
  }

  @Nested
  @DisplayName("Revoke API Key Tests")
  class RevokeApiKeyTests {

    @Test
    @DisplayName("DELETE /api-keys/{id} - Should return 204 when revoked successfully")
    void revokeApiKey_WhenExists_ShouldReturn204() throws Exception {
      // Arrange
      when(apiKeyService.revokeApiKey(eq(42), any(EzkeyAdmin.class))).thenReturn(true);

      // Act & Assert
      mockMvc.perform(delete(BASE_URL + "/42")).andExpect(status().isNoContent());

      verify(apiKeyService).revokeApiKey(eq(42), any(EzkeyAdmin.class));
    }

    @Test
    @DisplayName("DELETE /api-keys/{id} - Should return 404 when not found")
    void revokeApiKey_WhenNotFound_ShouldReturn404() throws Exception {
      // Arrange
      when(apiKeyService.revokeApiKey(eq(999), any(EzkeyAdmin.class))).thenReturn(false);

      // Act & Assert
      mockMvc.perform(delete(BASE_URL + "/999")).andExpect(status().isNotFound());

      verify(apiKeyService).revokeApiKey(eq(999), any(EzkeyAdmin.class));
    }
  }

  @Nested
  @DisplayName("Security Tests")
  class SecurityTests {

    @Test
    @DisplayName("Create API key - Secret should be shown in response")
    void createApiKey_ShouldReturnSecretInResponse() throws Exception {
      // Arrange
      ApiKeyCreateRequestDto request = new ApiKeyCreateRequestDto(123, null, null, null);

      when(apiKeyService.createApiKey(any(), any(), any(), any(), any())).thenReturn(creationResult);

      String json = objectMapper.writeValueAsString(request);

      // Act & Assert
      mockMvc
          .perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.secretKey").exists())
          .andExpect(jsonPath("$.secretKey").value("ezkey_skey_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0"))
          .andExpect(jsonPath("$.warning").value("IMPORTANT: Save the secret key now. It will not be shown again."));
    }

    @Test
    @DisplayName("List API keys - Secret should never be included")
    void listApiKeys_ShouldNeverReturnSecret() throws Exception {
      // Arrange
      when(apiKeyService.listActiveApiKeys(123)).thenReturn(Arrays.asList(testApiKey));

      // Act & Assert
      mockMvc
          .perform(get(BASE_URL + "/integration/123"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].secretKey").doesNotExist())
          .andExpect(jsonPath("$[0].secretKeyHash").doesNotExist());
    }

    @Test
    @DisplayName("Get API key - Secret should never be included")
    void getApiKey_ShouldNeverReturnSecret() throws Exception {
      // Arrange
      when(apiKeyService.getApiKey(42)).thenReturn(Optional.of(testApiKey));

      // Act & Assert
      mockMvc
          .perform(get(BASE_URL + "/42"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.secretKey").doesNotExist())
          .andExpect(jsonPath("$.secretKeyHash").doesNotExist());
    }
  }
}