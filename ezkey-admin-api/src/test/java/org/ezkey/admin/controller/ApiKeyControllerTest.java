/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * Test: ApiKeyControllerTest Description: Unit tests for ApiKeyController API key management
 * operations.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.dto.request.ApiKeyCreateRequestDto;
import org.ezkey.admin.dto.response.ApiKeyCreateResponseDto;
import org.ezkey.admin.dto.response.ApiKeyResponseDto;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.AdminOperationsRateLimitService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.integration.domain.entity.ApiKey;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.ApiKeyRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.service.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for ApiKeyController.
 *
 * <p>This test class provides minimal unit test coverage for the API key management operations to
 * prevent regressions during future development.
 *
 * <p><b>Test Coverage:</b>
 *
 * <ul>
 *   <li><b>createApiKey:</b> Happy path, validation errors, business logic exceptions
 *   <li><b>listApiKeys:</b> Successful listing, empty results
 *   <li><b>getApiKey:</b> Found case, not found case
 *   <li><b>revokeApiKey:</b> Successful revocation, not found case
 * </ul>
 *
 * <p><b>Note:</b> Rate limiting is not tested due to complexity and is covered by integration
 * tests.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyController Tests")
class ApiKeyControllerTest {

  @Mock private ApiKeyService apiKeyService;

  @Mock private ApiKeyRepository apiKeyRepository;

  @Mock private AdminOperationsRateLimitService adminOpsRateLimitService;

  @Mock private EzkeyAdminRepository adminRepository;

  @Mock private AccessControlService accessControlService;

  @Mock private AuditLogService auditLogService;

  @Mock private HttpServletRequest httpServletRequest;

  private ApiKeyController controller;

  @BeforeEach
  void setUp() {
    controller =
        new ApiKeyController(
            apiKeyService,
            apiKeyRepository,
            adminOpsRateLimitService,
            adminRepository,
            accessControlService,
            auditLogService);

    // Setup authentication context with admin user
    setupAdminAuthentication();

    // Mock rate limiting to always allow operations (bypass complexity)
    lenient().when(adminOpsRateLimitService.canCreateApiKey(anyString())).thenReturn(true);
    lenient().when(adminOpsRateLimitService.canRevokeApiKey(anyString())).thenReturn(true);
    lenient().when(adminOpsRateLimitService.canUpdateApiKey(anyString())).thenReturn(true);

    // Mock access control to always allow (for tests)
    lenient().when(accessControlService.canAccessIntegration(any(), anyInt())).thenReturn(true);

    // Mock admin repository to return a mock admin for getCurrentAdmin() calls
    // getCurrentAdmin() now uses AdminPrincipal.adminId() to load the admin
    EzkeyAdmin mockAdmin = createMockAdmin();
    lenient().when(adminRepository.findById(1)).thenReturn(Optional.of(mockAdmin));
  }

  @Nested
  @DisplayName("createApiKey Tests")
  class CreateApiKeyTests {

    @Test
    @DisplayName("Should create API key successfully and return 201 CREATED")
    void shouldCreateApiKeySuccessfully() {
      // Arrange
      ApiKeyCreateRequestDto request = new ApiKeyCreateRequestDto(123, "Test API Key", null, null);

      ApiKeyService.ApiKeyCreationResult mockResult = createMockCreationResult();
      when(apiKeyService.createApiKey(anyInt(), any(EzkeyAdmin.class), anyString(), any(), any()))
          .thenReturn(mockResult);

      // Act
      ResponseEntity<ApiKeyCreateResponseDto> response =
          controller.createApiKey(request, httpServletRequest);

      // Assert
      assertEquals(HttpStatus.CREATED, response.getStatusCode());
      assertNotNull(response.getBody());
      ApiKeyCreateResponseDto responseBody = response.getBody();
      assertNotNull(responseBody);
      assertEquals(mockResult.getApiKeyId(), responseBody.apiKeyId());
      assertEquals(mockResult.getIntegrationKey(), responseBody.integrationKey());
      assertEquals(mockResult.getSecretKey(), responseBody.secretKey());
      assertEquals(
          "IMPORTANT: Save the secret key now. It will not be shown again.",
          responseBody.warning());

      verify(apiKeyService)
          .createApiKey(eq(123), any(EzkeyAdmin.class), eq("Test API Key"), any(), any());
      verify(adminOpsRateLimitService).recordCreateApiKey("john.doe");
    }

    @Test
    @DisplayName("Should return 400 BAD_REQUEST for IllegalArgumentException")
    void shouldReturnBadRequestForIllegalArgumentException() {
      // Arrange
      ApiKeyCreateRequestDto request = new ApiKeyCreateRequestDto(123, "Test API Key", null, null);

      when(apiKeyService.createApiKey(anyInt(), any(EzkeyAdmin.class), anyString(), any(), any()))
          .thenThrow(new IllegalArgumentException("Invalid integration ID"));

      // Act
      ResponseEntity<ApiKeyCreateResponseDto> response =
          controller.createApiKey(request, httpServletRequest);

      // Assert
      assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Should return 409 CONFLICT for IllegalStateException")
    void shouldReturnConflictForIllegalStateException() {
      // Arrange
      ApiKeyCreateRequestDto request = new ApiKeyCreateRequestDto(123, "Test API Key", null, null);

      when(apiKeyService.createApiKey(anyInt(), any(EzkeyAdmin.class), anyString(), any(), any()))
          .thenThrow(new IllegalStateException("Maximum keys limit reached"));

      // Act
      ResponseEntity<ApiKeyCreateResponseDto> response =
          controller.createApiKey(request, httpServletRequest);

      // Assert
      assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }
  }

  @Nested
  @DisplayName("listApiKeys Tests")
  class ListApiKeysTests {

    @Test
    @DisplayName("Should return paginated list of API keys for integration")
    void shouldReturnPaginatedListOfApiKeys() {
      // Arrange
      Integer integrationId = 123;
      List<ApiKey> mockApiKeys = Arrays.asList(createMockApiKey(1), createMockApiKey(2));
      Pageable pageable = PageRequest.of(0, 20);
      when(apiKeyRepository.findAll(
              isA(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
          .thenReturn(new PageImpl<>(mockApiKeys, pageable, mockApiKeys.size()));

      // Act
      ResponseEntity<org.springframework.data.domain.Page<ApiKeyResponseDto>> response =
          controller.listApiKeys(integrationId, null, pageable);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      org.springframework.data.domain.Page<ApiKeyResponseDto> page = response.getBody();
      assertNotNull(page);
      assertEquals(2, page.getContent().size());
      assertEquals(2, page.getTotalElements());

      ApiKeyResponseDto firstKey = page.getContent().get(0);
      assertEquals(1, firstKey.apiKeyId());
      assertEquals(123, firstKey.integrationId());
      assertEquals("integration-key-1", firstKey.integrationKey());
    }

    @Test
    @DisplayName("Should return empty page when no API keys found for integration")
    void shouldReturnEmptyPageWhenNoApiKeysFound() {
      // Arrange
      Integer integrationId = 123;
      Pageable pageable = PageRequest.of(0, 20);
      when(apiKeyRepository.findAll(
              isA(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
          .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

      // Act
      ResponseEntity<org.springframework.data.domain.Page<ApiKeyResponseDto>> response =
          controller.listApiKeys(integrationId, null, pageable);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      assertTrue(response.getBody().getContent().isEmpty());
      assertEquals(0, response.getBody().getTotalElements());
    }
  }

  @Nested
  @DisplayName("listAllApiKeys Tests")
  class ListAllApiKeysTests {

    @Test
    @DisplayName("Should return paginated list of API keys for GlobalAdmin")
    void shouldReturnPaginatedListForGlobalAdmin() {
      // Arrange: setupAdminAuthentication() already sets GLOBAL_ADMIN
      List<ApiKey> mockApiKeys = Arrays.asList(createMockApiKey(1), createMockApiKey(2));
      Pageable pageable = PageRequest.of(0, 20);
      when(apiKeyRepository.findAll(
              isA(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
          .thenReturn(new PageImpl<>(mockApiKeys, pageable, mockApiKeys.size()));

      // Act
      ResponseEntity<org.springframework.data.domain.Page<ApiKeyResponseDto>> response =
          controller.listAllApiKeys(null, null, null, pageable);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      org.springframework.data.domain.Page<ApiKeyResponseDto> page = response.getBody();
      assertNotNull(page);
      assertEquals(2, page.getContent().size());
      assertEquals(1, page.getContent().get(0).apiKeyId());
    }

    @Test
    @DisplayName("Should return paginated list with filters for GlobalAdmin")
    void shouldReturnPaginatedListWithFilters() {
      List<ApiKey> mockApiKeys = Collections.singletonList(createMockApiKey(1));
      Pageable pageable = PageRequest.of(0, 20);
      when(apiKeyRepository.findAll(
              isA(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
          .thenReturn(new PageImpl<>(mockApiKeys, pageable, 1));

      ResponseEntity<org.springframework.data.domain.Page<ApiKeyResponseDto>> response =
          controller.listAllApiKeys(123, true, "Test", pageable);

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      assertEquals(1, response.getBody().getContent().size());
    }
  }

  @Nested
  @DisplayName("getApiKey Tests")
  class GetApiKeyTests {

    @Test
    @DisplayName("Should return API key details when found")
    void shouldReturnApiKeyDetailsWhenFound() {
      // Arrange
      Integer keyId = 42;
      ApiKey mockApiKey = createMockApiKey(keyId);
      when(apiKeyService.getApiKey(keyId)).thenReturn(Optional.of(mockApiKey));

      // Act
      ResponseEntity<ApiKeyResponseDto> response = controller.getApiKey(keyId);

      // Assert
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertNotNull(response.getBody());
      ApiKeyResponseDto responseBody = response.getBody();
      assertNotNull(responseBody);
      assertEquals(keyId, responseBody.apiKeyId());
      assertEquals(123, responseBody.integrationId());
      assertEquals("integration-key-" + keyId, responseBody.integrationKey());
    }

    @Test
    @DisplayName("Should return 404 NOT_FOUND when API key not found")
    void shouldReturnNotFoundWhenApiKeyNotFound() {
      // Arrange
      Integer keyId = 999;
      when(apiKeyService.getApiKey(keyId)).thenReturn(Optional.empty());

      // Act
      ResponseEntity<ApiKeyResponseDto> response = controller.getApiKey(keyId);

      // Assert
      assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
  }

  @Nested
  @DisplayName("revokeApiKey Tests")
  class RevokeApiKeyTests {

    @Test
    @DisplayName("Should revoke API key successfully and return 204 NO_CONTENT")
    void shouldRevokeApiKeySuccessfully() {
      // Arrange
      Integer keyId = 42;
      when(apiKeyService.revokeApiKey(eq(keyId), any(EzkeyAdmin.class))).thenReturn(true);

      // Act
      ResponseEntity<Void> response = controller.revokeApiKey(keyId, null, httpServletRequest);

      // Assert
      assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
      verify(apiKeyService).revokeApiKey(eq(keyId), any(EzkeyAdmin.class));
    }

    @Test
    @DisplayName("Should return 404 NOT_FOUND when API key not found for revocation")
    void shouldReturnNotFoundWhenApiKeyNotFoundForRevocation() {
      // Arrange
      Integer keyId = 999;
      when(apiKeyService.revokeApiKey(eq(keyId), any(EzkeyAdmin.class))).thenReturn(false);

      // Act
      ResponseEntity<Void> response = controller.revokeApiKey(keyId, null, httpServletRequest);

      // Assert
      assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
  }

  /** Sets up admin authentication context. */
  private void setupAdminAuthentication() {
    // Create AdminPrincipal for multi-tenant authentication
    AdminPrincipal principal =
        new AdminPrincipal(
            1,
            org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType.GLOBAL_ADMIN,
            null, // tenantId (null for Global Admin)
            null); // integrationId (null for Global Admin)

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            principal, // Principal: AdminPrincipal (multi-tenant)
            null, // Credentials
            Arrays.asList(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN")));

    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  /**
   * Creates a mock ApiKeyCreationResult for testing.
   *
   * @return mock creation result
   */
  private ApiKeyService.ApiKeyCreationResult createMockCreationResult() {
    return new ApiKeyService.ApiKeyCreationResult(
        42, // apiKeyId
        "int_key_12345", // integrationKey
        "secret_key_67890", // secretKey
        "Test API Key", // description
        OffsetDateTime.now(), // createdAt
        null, // expiresAt
        null // ipWhitelist
        );
  }

  /**
   * Creates a mock ApiKey entity for testing.
   *
   * @param keyId the API key ID
   * @return mock API key entity
   */
  private ApiKey createMockApiKey(Integer keyId) {
    ApiKey apiKey = new ApiKey();
    apiKey.setApiKeyId(keyId);
    apiKey.setVersion(0L);
    apiKey.setIntegrationKey("integration-key-" + keyId);
    apiKey.setDescription("Test API Key " + keyId);
    apiKey.setActive(true);
    apiKey.setCreatedAt(OffsetDateTime.now());
    apiKey.setExpiresAt(null);
    apiKey.setLastUsedAt(null);
    apiKey.setIpWhitelist(null);
    apiKey.setRevokedAt(null);
    apiKey.setRevokedByAdmin(null);

    // Setup integration
    Integration integration = new Integration();
    integration.setId(123);
    apiKey.setIntegration(integration);

    return apiKey;
  }

  /**
   * Creates a mock EzkeyAdmin entity for testing.
   *
   * @return mock admin entity
   */
  private EzkeyAdmin createMockAdmin() {
    EzkeyAdmin admin = new EzkeyAdmin();
    admin.setAdminId(1);
    admin.setUsername("john.doe"); // SOC 2 compliant: identifiable username
    admin.setEmail("john.doe@example.com"); // SOC 2 compliant: email required
    admin.setActive(true);
    admin.setAdminType(EzkeyAdmin.AdminType.GLOBAL_ADMIN);
    admin.setTenant(null);
    return admin;
  }
}
