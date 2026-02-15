/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: QrCodePayloadServiceTest
 * Description: Unit tests for QR code payload composition with optional auth URL.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ezkey.admin.config.QrCodeProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link QrCodePayloadService}.
 *
 * <p>
 * Tests validate JSON payload composition with and without the optional
 * {@code authUrl} field,
 * ensuring backward compatibility and correct JSON structure.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@DisplayName("QrCodePayloadService Tests")
class QrCodePayloadServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Nested
  @DisplayName("Payload without authBaseUrl")
  class WithoutAuthUrl {

    @Test
    @DisplayName("Should produce JSON with enrollmentId and enrollmentProofToken only")
    void composePayload_withoutAuthUrl_shouldProduceMinimalJson() throws Exception {
      // Arrange
      QrCodeProperties properties = new QrCodeProperties();
      QrCodePayloadService service = new QrCodePayloadService(properties);

      // Act
      String payload = service.composePayload(4, "test-proof-token-abc123");

      // Assert
      assertNotNull(payload);
      JsonNode json = objectMapper.readTree(payload);
      assertEquals("4", json.get("enrollmentId").asText());
      assertEquals("test-proof-token-abc123", json.get("enrollmentProofToken").asText());
      assertFalse(json.has("authUrl"), "authUrl should not be present when not configured");
    }

    @Test
    @DisplayName("Should produce JSON without authUrl when property is empty string")
    void composePayload_withEmptyAuthUrl_shouldOmitAuthUrl() throws Exception {
      // Arrange
      QrCodeProperties properties = new QrCodeProperties();
      properties.setAuthBaseUrl("");
      QrCodePayloadService service = new QrCodePayloadService(properties);

      // Act
      String payload = service.composePayload(1, "token123");

      // Assert
      JsonNode json = objectMapper.readTree(payload);
      assertFalse(json.has("authUrl"), "authUrl should not be present when empty");
    }

    @Test
    @DisplayName("Should produce JSON without authUrl when property is blank")
    void composePayload_withBlankAuthUrl_shouldOmitAuthUrl() throws Exception {
      // Arrange
      QrCodeProperties properties = new QrCodeProperties();
      properties.setAuthBaseUrl("   ");
      QrCodePayloadService service = new QrCodePayloadService(properties);

      // Act
      String payload = service.composePayload(1, "token123");

      // Assert
      JsonNode json = objectMapper.readTree(payload);
      assertFalse(json.has("authUrl"), "authUrl should not be present when blank");
    }
  }

  @Nested
  @DisplayName("Payload with authBaseUrl")
  class WithAuthUrl {

    @Test
    @DisplayName("Should include authUrl when auth-base-url is configured")
    void composePayload_withAuthUrl_shouldIncludeAuthUrl() throws Exception {
      // Arrange
      QrCodeProperties properties = new QrCodeProperties();
      properties.setAuthBaseUrl("https://ezkey.acme.com:8080");
      QrCodePayloadService service = new QrCodePayloadService(properties);

      // Act
      String payload = service.composePayload(42, "proof-token-xyz");

      // Assert
      JsonNode json = objectMapper.readTree(payload);
      assertEquals("42", json.get("enrollmentId").asText());
      assertEquals("proof-token-xyz", json.get("enrollmentProofToken").asText());
      assertTrue(json.has("authUrl"), "authUrl should be present when configured");
      assertEquals("https://ezkey.acme.com:8080", json.get("authUrl").asText());
    }

    @Test
    @DisplayName("Should strip whitespace from authBaseUrl")
    void composePayload_withWhitespaceAuthUrl_shouldStrip() throws Exception {
      // Arrange
      QrCodeProperties properties = new QrCodeProperties();
      properties.setAuthBaseUrl("  https://ezkey.acme.com:8080  ");
      QrCodePayloadService service = new QrCodePayloadService(properties);

      // Act
      String payload = service.composePayload(1, "token");

      // Assert
      JsonNode json = objectMapper.readTree(payload);
      assertEquals("https://ezkey.acme.com:8080", json.get("authUrl").asText());
    }
  }

  @Nested
  @DisplayName("JSON format validation")
  class JsonFormatValidation {

    @Test
    @DisplayName("Should produce valid parseable JSON")
    void composePayload_shouldProduceValidJson() throws Exception {
      // Arrange
      QrCodeProperties properties = new QrCodeProperties();
      properties.setAuthBaseUrl("https://example.com");
      QrCodePayloadService service = new QrCodePayloadService(properties);

      // Act
      String payload = service.composePayload(99, "complex.token.with.dots.1234567890");

      // Assert — simply parsing without exception proves validity
      JsonNode json = objectMapper.readTree(payload);
      assertNotNull(json);
      assertEquals(3, json.size(), "JSON should have exactly 3 fields");
    }

    @Test
    @DisplayName("Should handle proof tokens with special characters")
    void composePayload_withSpecialCharsInToken_shouldEscapeProperly() throws Exception {
      // Arrange
      QrCodeProperties properties = new QrCodeProperties();
      QrCodePayloadService service = new QrCodePayloadService(properties);
      String tokenWithSpecialChars = "abc+/=.123456789.def+/=";

      // Act
      String payload = service.composePayload(1, tokenWithSpecialChars);

      // Assert
      JsonNode json = objectMapper.readTree(payload);
      assertEquals(tokenWithSpecialChars, json.get("enrollmentProofToken").asText());
    }

    @Test
    @DisplayName("enrollmentId should be serialized as string for mobile parser compatibility")
    void composePayload_enrollmentId_shouldBeString() throws Exception {
      // Arrange
      QrCodeProperties properties = new QrCodeProperties();
      QrCodePayloadService service = new QrCodePayloadService(properties);

      // Act
      String payload = service.composePayload(123, "token");

      // Assert
      JsonNode json = objectMapper.readTree(payload);
      assertTrue(
          json.get("enrollmentId").isTextual(),
          "enrollmentId should be a JSON string for mobile parser compatibility");
    }
  }
}
