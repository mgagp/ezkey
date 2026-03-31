/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EzkeyClientErrorExtractionTest
 * Description: Tests for error message extraction supporting RFC 9457 and legacy formats
 */

package org.ezkey.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for error message extraction in EzkeyClient.
 *
 * <p>Tests the {@code extractErrorMessage(String body)} method which supports multiple error
 * response formats:
 *
 * <ul>
 *   <li>RFC 9457 "detail" field (priority 1 - most specific)
 *   <li>RFC 9457 "title" field (priority 2 - fallback)
 *   <li>Legacy "message" field (priority 3 - backward compatibility)
 *   <li>Raw body truncation (priority 4 - fallback)
 * </ul>
 *
 * @since 2025
 */
@DisplayName("EzkeyClient Error Message Extraction")
class EzkeyClientErrorExtractionTest {

  private static String extractErrorMessage(String body) throws Exception {
    // Use reflection to test the private method
    Method method;
    try {
      method = EzkeyClient.class.getDeclaredMethod("extractErrorMessage", String.class);
    } catch (NoSuchMethodException e) {
      // If the method is protected/package-private instead of private
      method = EzkeyClient.class.getDeclaredMethod("extractErrorMessage", String.class);
    }
    method.setAccessible(true);
    try {
      return (String) method.invoke(null, body);
    } catch (InvocationTargetException e) {
      throw (Exception) e.getCause();
    }
  }

  @Test
  @DisplayName("Extract RFC 9457 'detail' field (priority 1)")
  void testExtractRfc9457Detail() throws Exception {
    String json =
        """
        {\
          "type": "https://ezkey.io/problems/authentication/invalid-credentials",\
          "title": "Invalid Credentials",\
          "status": 401,\
          "detail": "Invalid username or password"\
        }\
        """;
    String result = extractErrorMessage(json);
    assertEquals("Invalid username or password", result);
  }

  @Test
  @DisplayName("Extract RFC 9457 'title' field when 'detail' is missing (priority 2)")
  void testExtractRfc9457TitleFallback() throws Exception {
    String json =
        """
        {\
          "type": "https://ezkey.io/problems/authentication/account-inactive",\
          "title": "Account Inactive",\
          "status": 403\
        }\
        """;
    String result = extractErrorMessage(json);
    assertEquals("Account Inactive", result);
  }

  @Test
  @DisplayName("Extract legacy 'message' field when RFC 9457 fields missing (priority 3)")
  void testExtractLegacyMessage() throws Exception {
    String json =
        """
        {\
          "success": false,\
          "message": "Authentication failed: Invalid credentials"\
        }\
        """;
    String result = extractErrorMessage(json);
    assertEquals("Authentication failed: Invalid credentials", result);
  }

  @Test
  @DisplayName("Prefer 'detail' over 'title' when both present")
  void testDetailPrerecrencesOverTitle() throws Exception {
    String json =
        """
        {\
          "type": "https://ezkey.io/problems/test",\
          "title": "Generic Error",\
          "detail": "Specific error details",\
          "status": 400\
        }\
        """;
    String result = extractErrorMessage(json);
    assertEquals("Specific error details", result);
  }

  @Test
  @DisplayName("Handle null body gracefully")
  void testNullBody() throws Exception {
    String result = extractErrorMessage(null);
    assertEquals("No details provided.", result);
  }

  @Test
  @DisplayName("Handle empty JSON object")
  void testEmptyJsonObject() throws Exception {
    String json = "{}";
    String result = extractErrorMessage(json);
    assertEquals("{}", result);
  }

  @Test
  @DisplayName("Truncate long bodies to 200 characters")
  void testLongBodyTruncation() throws Exception {
    String longBody = "x".repeat(300);
    String result = extractErrorMessage(longBody);
    assertEquals(200, result.length() - 3); // minus the "..."
    assertTrue(result.endsWith("..."));
  }

  @Test
  @DisplayName("Handle non-JSON responses")
  void testNonJsonResponse() throws Exception {
    String plainText = "Internal Server Error";
    String result = extractErrorMessage(plainText);
    assertEquals("Internal Server Error", result);
  }

  @Test
  @DisplayName("Ignore empty 'detail' field and use 'title'")
  void testIgnoreEmptyDetail() throws Exception {
    String json =
        """
        {\
          "type": "https://ezkey.io/problems/test",\
          "title": "Error Title",\
          "detail": "",\
          "status": 400\
        }\
        """;
    String result = extractErrorMessage(json);
    assertEquals("Error Title", result);
  }
}
