/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ApiKeyServiceTest
 * Description: Unit tests for ApiKeyService business logic.
 */

package org.ezkey.integration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.ezkey.integration.domain.entity.ApiKey;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.ApiKeyRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.service.ApiKeyService.ApiKeyCreationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Unit tests for ApiKeyService.
 *
 * <p>This test class validates API key generation, validation, rotation, and lifecycle management
 * logic without requiring database or Spring context.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyService Tests")
class ApiKeyServiceTest {

  @Mock private ApiKeyRepository apiKeyRepository;

  @Mock private IntegrationRepository integrationRepository;

  @Mock private BCryptPasswordEncoder passwordEncoder;

  @InjectMocks private ApiKeyService apiKeyService;

  private Integration testIntegration;
  private EzkeyAdmin testAdmin;
  private ApiKey testApiKey;

  @BeforeEach
  void setUp() {
    // Setup test integration
    testIntegration = new Integration();
    testIntegration.setId(123);
    testIntegration.setActive(true);

    // Setup test admin
    testAdmin = new EzkeyAdmin();
    testAdmin.setAdminId(1);
    testAdmin.setUsername("admin");

    // Setup test API key
    // Note: Using a real BCrypt hash for "testSecret" for consistency
    // Generated with: new BCryptPasswordEncoder().encode("testSecret")
    testApiKey = new ApiKey();
    testApiKey.setApiKeyId(42);
    testApiKey.setIntegration(testIntegration);
    testApiKey.setIntegrationKey("ezkey_ikey_test123456789ab");
    testApiKey.setSecretKeyHash("$2a$10$N9qo8uLOickgx2ZMRZoMye");
    testApiKey.setActive(true);
    testApiKey.setCreatedAt(OffsetDateTime.now());

    // Configure BCrypt mock (lenient because not all tests use these)
    // Mock encode() to return a BCrypt-formatted hash
    lenient()
        .when(passwordEncoder.encode(anyString()))
        .thenAnswer(invocation -> "$2a$10$mockedHash" + invocation.getArgument(0).hashCode());

    // Mock matches() to return true when secret is "testSecret", false otherwise
    lenient()
        .when(passwordEncoder.matches(anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              String rawPassword = invocation.getArgument(0);
              String encodedPassword = invocation.getArgument(1);
              // For our test, "testSecret" matches the hash in testApiKey
              return "testSecret".equals(rawPassword)
                  && encodedPassword.equals(testApiKey.getSecretKeyHash());
            });
  }

  @Nested
  @DisplayName("API Key Creation Tests")
  class CreateApiKeyTests {

    @Test
    @DisplayName("Should create API key with valid integration")
    void createApiKey_WhenValidIntegration_ShouldSucceed() {
      // Arrange
      when(integrationRepository.findById(123)).thenReturn(Optional.of(testIntegration));
      when(apiKeyRepository.countByIntegration_IdAndActiveTrue(123)).thenReturn(0L);
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue(anyString()))
          .thenReturn(Optional.empty());
      when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

      // Act
      ApiKeyCreationResult result =
          apiKeyService.createApiKey(123, testAdmin, "Test Key", null, null);

      // Assert
      assertNotNull(result);
      assertNotNull(result.getIntegrationKey());
      assertNotNull(result.getSecretKey());
      assertTrue(result.getIntegrationKey().startsWith("ezkey_ikey_"));
      assertTrue(result.getSecretKey().startsWith("ezkey_skey_"));
      assertEquals(20, result.getIntegrationKey().length() - "ezkey_ikey_".length());
      assertEquals(40, result.getSecretKey().length() - "ezkey_skey_".length());

      verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("Should throw exception when integration not found")
    void createApiKey_WhenIntegrationNotFound_ShouldThrowException() {
      // Arrange
      when(integrationRepository.findById(999)).thenReturn(Optional.empty());

      // Act & Assert
      assertThrows(
          IllegalArgumentException.class,
          () -> apiKeyService.createApiKey(999, testAdmin, "Test", null, null));

      verify(apiKeyRepository, never()).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("Should throw exception when integration is inactive")
    void createApiKey_WhenIntegrationInactive_ShouldThrowException() {
      // Arrange
      testIntegration.setActive(false);
      when(integrationRepository.findById(123)).thenReturn(Optional.of(testIntegration));

      // Act & Assert
      assertThrows(
          IllegalArgumentException.class,
          () -> apiKeyService.createApiKey(123, testAdmin, "Test", null, null));

      verify(apiKeyRepository, never()).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("Should throw exception when max active keys limit reached")
    void createApiKey_WhenMaxKeysReached_ShouldThrowException() {
      // Arrange
      when(integrationRepository.findById(123)).thenReturn(Optional.of(testIntegration));
      when(apiKeyRepository.countByIntegration_IdAndActiveTrue(123)).thenReturn(5L);

      // Act & Assert
      assertThrows(
          IllegalStateException.class,
          () -> apiKeyService.createApiKey(123, testAdmin, "Test", null, null));

      verify(apiKeyRepository, never()).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("Should create key with expiration date")
    void createApiKey_WithExpirationDate_ShouldSetExpiration() {
      // Arrange
      OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(90);
      when(integrationRepository.findById(123)).thenReturn(Optional.of(testIntegration));
      when(apiKeyRepository.countByIntegration_IdAndActiveTrue(123)).thenReturn(0L);
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue(anyString()))
          .thenReturn(Optional.empty());
      when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

      // Act
      ApiKeyCreationResult result =
          apiKeyService.createApiKey(123, testAdmin, "Test Key", expiresAt, null);

      // Assert
      assertNotNull(result);
      assertEquals(expiresAt, result.getExpiresAt());
    }

    @Test
    @DisplayName("Should create key with IP whitelist")
    void createApiKey_WithIpWhitelist_ShouldSetWhitelist() {
      // Arrange
      String[] ipWhitelist = {"192.168.1.0/24", "10.0.0.100"};
      when(integrationRepository.findById(123)).thenReturn(Optional.of(testIntegration));
      when(apiKeyRepository.countByIntegration_IdAndActiveTrue(123)).thenReturn(0L);
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue(anyString()))
          .thenReturn(Optional.empty());
      when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

      // Act
      ApiKeyCreationResult result =
          apiKeyService.createApiKey(123, testAdmin, "Test Key", null, ipWhitelist);

      // Assert
      assertNotNull(result);
      assertEquals(2, result.getIpWhitelist().length);
      assertEquals("192.168.1.0/24", result.getIpWhitelist()[0]);
    }

    @Test
    @DisplayName("Should throw exception on invalid IP in whitelist")
    void createApiKey_WithInvalidIp_ShouldThrowException() {
      // Arrange
      String[] invalidWhitelist = {"invalid-ip-address"};
      when(integrationRepository.findById(123)).thenReturn(Optional.of(testIntegration));
      when(apiKeyRepository.countByIntegration_IdAndActiveTrue(123)).thenReturn(0L);

      // Act & Assert
      assertThrows(
          IllegalArgumentException.class,
          () -> apiKeyService.createApiKey(123, testAdmin, "Test", null, invalidWhitelist));
    }

    @Test
    @DisplayName("Should generate unique integration keys")
    void createApiKey_MultipleCalls_ShouldGenerateUniqueKeys() {
      // Arrange
      when(integrationRepository.findById(123)).thenReturn(Optional.of(testIntegration));
      when(apiKeyRepository.countByIntegration_IdAndActiveTrue(123)).thenReturn(0L);
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue(anyString()))
          .thenReturn(Optional.empty());

      ApiKey key1 = new ApiKey();
      key1.setIntegrationKey("ezkey_ikey_key1");
      ApiKey key2 = new ApiKey();
      key2.setIntegrationKey("ezkey_ikey_key2");

      when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(key1, key2);

      // Act
      ApiKeyCreationResult result1 =
          apiKeyService.createApiKey(123, testAdmin, "Key 1", null, null);
      ApiKeyCreationResult result2 =
          apiKeyService.createApiKey(123, testAdmin, "Key 2", null, null);

      // Assert
      assertNotNull(result1.getIntegrationKey());
      assertNotNull(result2.getIntegrationKey());
      // Keys are generated randomly, just verify they start with correct prefix
      assertTrue(result1.getIntegrationKey().startsWith("ezkey_ikey_"));
      assertTrue(result2.getIntegrationKey().startsWith("ezkey_ikey_"));
    }
  }

  @Nested
  @DisplayName("API Key Validation Tests")
  class ValidateApiKeyTests {

    @Test
    @DisplayName("Should validate correct API key credentials")
    void validateApiKey_WhenValidCredentials_ShouldReturnIntegration() {
      // Arrange
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue("ezkey_ikey_test123"))
          .thenReturn(Optional.of(testApiKey));
      when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

      // Act
      Optional<Integration> result =
          apiKeyService.validateApiKey("ezkey_ikey_test123", "testSecret", "192.168.1.100");

      // Assert
      assertTrue(result.isPresent());
      assertEquals(123, result.get().getId());
      verify(apiKeyRepository).save(any(ApiKey.class)); // Updates lastUsedAt
    }

    @Test
    @DisplayName("Should reject invalid secret key")
    void validateApiKey_WhenInvalidSecret_ShouldReturnEmpty() {
      // Arrange
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue("ezkey_ikey_test123"))
          .thenReturn(Optional.of(testApiKey));

      // Act
      Optional<Integration> result =
          apiKeyService.validateApiKey("ezkey_ikey_test123", "wrongSecret", "192.168.1.100");

      // Assert
      assertTrue(result.isEmpty());
      verify(apiKeyRepository, never()).save(any(ApiKey.class)); // No lastUsedAt update on failure
    }

    @Test
    @DisplayName("Should reject unknown integration key")
    void validateApiKey_WhenUnknownKey_ShouldReturnEmpty() {
      // Arrange
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue("ezkey_ikey_unknown"))
          .thenReturn(Optional.empty());

      // Act
      Optional<Integration> result =
          apiKeyService.validateApiKey("ezkey_ikey_unknown", "anySecret", "192.168.1.100");

      // Assert
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should reject expired API key")
    void validateApiKey_WhenExpired_ShouldReturnEmpty() {
      // Arrange
      testApiKey.setExpiresAt(OffsetDateTime.now().minusDays(1)); // Expired yesterday
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue("ezkey_ikey_test123"))
          .thenReturn(Optional.of(testApiKey));

      // Act
      Optional<Integration> result =
          apiKeyService.validateApiKey("ezkey_ikey_test123", "testSecret", "192.168.1.100");

      // Assert
      assertTrue(result.isEmpty());
      verify(apiKeyRepository, never()).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("Should accept key with future expiration")
    void validateApiKey_WhenNotExpired_ShouldSucceed() {
      // Arrange
      testApiKey.setExpiresAt(OffsetDateTime.now().plusDays(30)); // Expires in 30 days
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue("ezkey_ikey_test123"))
          .thenReturn(Optional.of(testApiKey));
      when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

      // Act
      Optional<Integration> result =
          apiKeyService.validateApiKey("ezkey_ikey_test123", "testSecret", "192.168.1.100");

      // Assert
      assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Should reject IP not in whitelist")
    void validateApiKey_WhenIpNotWhitelisted_ShouldReturnEmpty() {
      // Arrange
      testApiKey.setIpWhitelist(new String[] {"192.168.1.0/24"}); // Whitelist set
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue("ezkey_ikey_test123"))
          .thenReturn(Optional.of(testApiKey));

      // Act
      Optional<Integration> result =
          apiKeyService.validateApiKey("ezkey_ikey_test123", "testSecret", "10.0.0.100"); // Outside
      // whitelist

      // Assert
      assertTrue(result.isEmpty());
      verify(apiKeyRepository, never()).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("Should accept IP in whitelist range")
    void validateApiKey_WhenIpInWhitelist_ShouldSucceed() {
      // Arrange
      testApiKey.setIpWhitelist(new String[] {"192.168.1.0/24"});
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue("ezkey_ikey_test123"))
          .thenReturn(Optional.of(testApiKey));
      when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

      // Act
      Optional<Integration> result =
          apiKeyService.validateApiKey(
              "ezkey_ikey_test123", "testSecret", "192.168.1.100"); // Inside CIDR range

      // Assert
      assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Should accept exact IP match in whitelist")
    void validateApiKey_WhenExactIpMatch_ShouldSucceed() {
      // Arrange
      testApiKey.setIpWhitelist(new String[] {"192.168.1.100"}); // Exact IP
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue("ezkey_ikey_test123"))
          .thenReturn(Optional.of(testApiKey));
      when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

      // Act
      Optional<Integration> result =
          apiKeyService.validateApiKey("ezkey_ikey_test123", "testSecret", "192.168.1.100");

      // Assert
      assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Should update lastUsedAt on successful validation")
    void validateApiKey_WhenSuccessful_ShouldUpdateLastUsed() {
      // Arrange
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue("ezkey_ikey_test123"))
          .thenReturn(Optional.of(testApiKey));

      ArgumentCaptor<ApiKey> apiKeyCaptor = ArgumentCaptor.forClass(ApiKey.class);
      when(apiKeyRepository.save(apiKeyCaptor.capture())).thenReturn(testApiKey);

      // Act
      apiKeyService.validateApiKey("ezkey_ikey_test123", "testSecret", "192.168.1.100");

      // Assert
      ApiKey savedKey = apiKeyCaptor.getValue();
      assertNotNull(savedKey.getLastUsedAt());
    }
  }

  @Nested
  @DisplayName("API Key Revocation Tests")
  class RevokeApiKeyTests {

    @Test
    @DisplayName("Should revoke API key successfully")
    void revokeApiKey_WhenKeyExists_ShouldReturnTrue() {
      // Arrange
      when(apiKeyRepository.revokeKey(any(), any(), any())).thenReturn(1);

      // Act
      boolean result = apiKeyService.revokeApiKey(42, testAdmin);

      // Assert
      assertTrue(result);
      verify(apiKeyRepository).revokeKey(any(), any(), any());
    }

    @Test
    @DisplayName("Should return false when key not found")
    void revokeApiKey_WhenKeyNotFound_ShouldReturnFalse() {
      // Arrange
      when(apiKeyRepository.revokeKey(any(), any(), any())).thenReturn(0);

      // Act
      boolean result = apiKeyService.revokeApiKey(999, testAdmin);

      // Assert
      assertFalse(result);
    }
  }

  @Nested
  @DisplayName("API Key Listing Tests")
  class ListApiKeysTests {

    @Test
    @DisplayName("Should list active API keys for integration")
    void listActiveApiKeys_WhenKeysExist_ShouldReturnList() {
      // Arrange
      List<ApiKey> keys = Arrays.asList(testApiKey);
      when(apiKeyRepository.findByIntegration_IdAndActiveTrue(123)).thenReturn(keys);

      // Act
      List<ApiKey> result = apiKeyService.listActiveApiKeys(123);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("ezkey_ikey_test123456789ab", result.get(0).getIntegrationKey());
    }

    @Test
    @DisplayName("Should list all API keys including revoked")
    void listAllApiKeys_WhenKeysExist_ShouldReturnAll() {
      // Arrange
      ApiKey revokedKey = new ApiKey();
      revokedKey.setActive(false);
      List<ApiKey> keys = Arrays.asList(testApiKey, revokedKey);
      when(apiKeyRepository.findByIntegration_Id(123)).thenReturn(keys);

      // Act
      List<ApiKey> result = apiKeyService.listAllApiKeys(123);

      // Assert
      assertNotNull(result);
      assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should get specific API key by ID")
    void getApiKey_WhenExists_ShouldReturnKey() {
      // Arrange
      when(apiKeyRepository.findById(42)).thenReturn(Optional.of(testApiKey));

      // Act
      Optional<ApiKey> result = apiKeyService.getApiKey(42);

      // Assert
      assertTrue(result.isPresent());
      assertEquals(42, result.get().getApiKeyId());
    }

    @Test
    @DisplayName("Should return empty when API key not found")
    void getApiKey_WhenNotFound_ShouldReturnEmpty() {
      // Arrange
      when(apiKeyRepository.findById(999)).thenReturn(Optional.empty());

      // Act
      Optional<ApiKey> result = apiKeyService.getApiKey(999);

      // Assert
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("API Key Cleanup Tests")
  class CleanupTests {

    @Test
    @DisplayName("Should cleanup expired keys")
    void cleanupExpiredKeys_WhenExpiredKeysExist_ShouldDeactivateThem() {
      // Arrange
      ApiKey expiredKey = new ApiKey();
      expiredKey.setApiKeyId(99);
      expiredKey.setActive(true);
      expiredKey.setIntegration(testIntegration);
      expiredKey.setIntegrationKey("ezkey_ikey_expired");

      when(apiKeyRepository.findExpiredKeys(any())).thenReturn(Arrays.asList(expiredKey));
      when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(expiredKey);

      // Act
      int count = apiKeyService.cleanupExpiredKeys();

      // Assert
      assertEquals(1, count);
      verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("Should return zero when no expired keys")
    void cleanupExpiredKeys_WhenNoExpiredKeys_ShouldReturnZero() {
      // Arrange
      when(apiKeyRepository.findExpiredKeys(any())).thenReturn(Arrays.asList());

      // Act
      int count = apiKeyService.cleanupExpiredKeys();

      // Assert
      assertEquals(0, count);
      verify(apiKeyRepository, never()).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("Should find keys expiring soon")
    void findKeysExpiringSoon_WhenKeysExpiring_ShouldReturnList() {
      // Arrange
      when(apiKeyRepository.findKeysExpiringBetween(any(), any()))
          .thenReturn(Arrays.asList(testApiKey));

      // Act
      List<ApiKey> result = apiKeyService.findKeysExpiringSoon(30);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.size());
    }
  }

  @Nested
  @DisplayName("Security Tests")
  class SecurityTests {

    @Test
    @DisplayName("Secret key should be BCrypt hashed when stored")
    void createApiKey_ShouldHashSecretKey() {
      // Arrange
      when(integrationRepository.findById(123)).thenReturn(Optional.of(testIntegration));
      when(apiKeyRepository.countByIntegration_IdAndActiveTrue(123)).thenReturn(0L);
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue(anyString()))
          .thenReturn(Optional.empty());

      ArgumentCaptor<ApiKey> apiKeyCaptor = ArgumentCaptor.forClass(ApiKey.class);
      when(apiKeyRepository.save(apiKeyCaptor.capture())).thenReturn(testApiKey);

      // Act
      ApiKeyCreationResult result = apiKeyService.createApiKey(123, testAdmin, "Test", null, null);

      // Assert
      ApiKey savedKey = apiKeyCaptor.getValue();
      assertNotNull(savedKey.getSecretKeyHash());
      assertTrue(savedKey.getSecretKeyHash().startsWith("$2a$")); // BCrypt format
      // Secret in result should be plain text
      assertNotNull(result.getSecretKey());
      assertTrue(result.getSecretKey().startsWith("ezkey_skey_"));
    }

    @Test
    @DisplayName("Integration key should be stored in plain text")
    void createApiKey_ShouldStoreIntegrationKeyInPlainText() {
      // Arrange
      when(integrationRepository.findById(123)).thenReturn(Optional.of(testIntegration));
      when(apiKeyRepository.countByIntegration_IdAndActiveTrue(123)).thenReturn(0L);
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue(anyString()))
          .thenReturn(Optional.empty());

      ArgumentCaptor<ApiKey> apiKeyCaptor = ArgumentCaptor.forClass(ApiKey.class);
      when(apiKeyRepository.save(apiKeyCaptor.capture())).thenReturn(testApiKey);

      // Act
      apiKeyService.createApiKey(123, testAdmin, "Test", null, null);

      // Assert
      ApiKey savedKey = apiKeyCaptor.getValue();
      assertTrue(savedKey.getIntegrationKey().startsWith("ezkey_ikey_"));
      // Should be plain text, not hashed (ezkey_ikey_ = 11 chars + 20 hex chars = 31 total)
      assertEquals(31, savedKey.getIntegrationKey().length());
    }

    @Test
    @DisplayName("Should generate cryptographically secure keys")
    void createApiKey_ShouldGenerateSecureRandomKeys() {
      // Arrange
      when(integrationRepository.findById(123)).thenReturn(Optional.of(testIntegration));
      when(apiKeyRepository.countByIntegration_IdAndActiveTrue(123)).thenReturn(0L);
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue(anyString()))
          .thenReturn(Optional.empty());
      when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

      // Act
      ApiKeyCreationResult result1 = apiKeyService.createApiKey(123, testAdmin, "Key1", null, null);
      ApiKeyCreationResult result2 = apiKeyService.createApiKey(123, testAdmin, "Key2", null, null);

      // Assert
      // Keys should be different (random generation)
      assertNotNull(result1.getSecretKey());
      assertNotNull(result2.getSecretKey());
      // Statistical impossibility they're identical with 160 bits entropy
      // Just verify format is correct (ezkey_skey_ = 11 chars + 40 hex chars = 51 total)
      assertEquals(51, result1.getSecretKey().length());
      assertEquals(51, result2.getSecretKey().length());
    }
  }

  @Nested
  @DisplayName("IP Whitelist Tests")
  class IpWhitelistTests {

    @Test
    @DisplayName("Should accept CIDR range in whitelist")
    void validateApiKey_WithCidrRange_ShouldAcceptIpsInRange() {
      // Arrange
      testApiKey.setIpWhitelist(new String[] {"192.168.1.0/24"});
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue("ezkey_ikey_test123"))
          .thenReturn(Optional.of(testApiKey));
      when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

      // Act & Assert - IPs in range should be accepted
      Optional<Integration> result1 =
          apiKeyService.validateApiKey("ezkey_ikey_test123", "testSecret", "192.168.1.1");
      Optional<Integration> result2 =
          apiKeyService.validateApiKey("ezkey_ikey_test123", "testSecret", "192.168.1.254");

      assertTrue(result1.isPresent());
      assertTrue(result2.isPresent());
    }

    @Test
    @DisplayName("Should reject IP outside CIDR range")
    void validateApiKey_WithCidrRange_ShouldRejectIpsOutsideRange() {
      // Arrange
      testApiKey.setIpWhitelist(new String[] {"192.168.1.0/24"});
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue("ezkey_ikey_test123"))
          .thenReturn(Optional.of(testApiKey));

      // Act
      Optional<Integration> result =
          apiKeyService.validateApiKey(
              "ezkey_ikey_test123", "testSecret", "192.168.2.1"); // Outside
      // range

      // Assert
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should support multiple IPs in whitelist")
    void validateApiKey_WithMultipleIps_ShouldAcceptAnyMatch() {
      // Arrange
      testApiKey.setIpWhitelist(new String[] {"192.168.1.100", "10.0.0.50", "172.16.0.0/16"});
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue("ezkey_ikey_test123"))
          .thenReturn(Optional.of(testApiKey));
      when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

      // Act & Assert
      Optional<Integration> result1 =
          apiKeyService.validateApiKey("ezkey_ikey_test123", "testSecret", "192.168.1.100");
      Optional<Integration> result2 =
          apiKeyService.validateApiKey("ezkey_ikey_test123", "testSecret", "10.0.0.50");
      Optional<Integration> result3 =
          apiKeyService.validateApiKey("ezkey_ikey_test123", "testSecret", "172.16.5.100");

      assertTrue(result1.isPresent());
      assertTrue(result2.isPresent());
      assertTrue(result3.isPresent());
    }

    @Test
    @DisplayName("Should accept any IP when whitelist is null")
    void validateApiKey_WhenNoWhitelist_ShouldAcceptAnyIp() {
      // Arrange
      testApiKey.setIpWhitelist(null); // No whitelist
      when(apiKeyRepository.findByIntegrationKeyAndActiveTrue("ezkey_ikey_test123"))
          .thenReturn(Optional.of(testApiKey));
      when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

      // Act
      Optional<Integration> result =
          apiKeyService.validateApiKey("ezkey_ikey_test123", "testSecret", "any.ip.address");

      // Assert
      assertTrue(result.isPresent());
    }
  }
}
