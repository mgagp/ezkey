/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Internal: JsonHelper
 * Description: Minimal JSON serializer/deserializer for flat objects. Zero external dependencies.
 */

package org.ezkey.sdk;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON helper for flat key-value objects.
 *
 * <p>Handles only the subset of JSON needed by the SDK: flat objects with string, integer, boolean,
 * and null values. No arrays, no nested objects. This keeps the SDK at zero external dependencies.
 *
 * <p>This class is internal to the SDK and not part of the public API.
 *
 * @since 2025
 */
final class JsonHelper {

  private JsonHelper() {}

  /**
   * Serializes a map of key-value pairs to a JSON object string.
   *
   * @param fields the fields to serialize (values may be String, Number, Boolean, or null)
   * @return JSON object string
   */
  static String toJson(Map<String, Object> fields) {
    var sb = new StringBuilder("{");
    var first = true;
    for (var entry : fields.entrySet()) {
      if (!first) {
        sb.append(",");
      }
      first = false;
      sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
      appendValue(sb, entry.getValue());
    }
    sb.append("}");
    return sb.toString();
  }

  /**
   * Parses a JSON object string into a map of string keys to raw string values.
   *
   * <p>Values are returned as raw strings (unquoted for strings, literal for numbers/booleans).
   * Null JSON values are stored as {@code null} in the map. This parser handles the simple flat
   * JSON objects returned by the Ezkey Admin API.
   *
   * @param json the JSON string to parse
   * @return map of field names to raw string values
   * @throws EzkeyException if the JSON cannot be parsed
   */
  static Map<String, String> parseObject(String json) throws EzkeyException {
    if (json == null || json.isBlank()) {
      throw new EzkeyException("Empty or null JSON response");
    }

    var result = new LinkedHashMap<String, String>();
    var trimmed = json.strip();

    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
      throw new EzkeyException("Invalid JSON object: does not start with '{' and end with '}'");
    }

    // Remove outer braces
    var content = trimmed.substring(1, trimmed.length() - 1).strip();
    if (content.isEmpty()) {
      return result;
    }

    int pos = 0;
    while (pos < content.length()) {
      // Skip whitespace
      pos = skipWhitespace(content, pos);
      if (pos >= content.length()) {
        break;
      }

      // Parse key
      if (content.charAt(pos) != '"') {
        throw new EzkeyException("Expected '\"' at position " + pos + " in JSON: " + json);
      }
      int keyEnd = findClosingQuote(content, pos + 1);
      String key = unescapeJson(content.substring(pos + 1, keyEnd));
      pos = keyEnd + 1;

      // Skip whitespace and colon
      pos = skipWhitespace(content, pos);
      if (pos >= content.length() || content.charAt(pos) != ':') {
        throw new EzkeyException("Expected ':' after key '" + key + "' in JSON: " + json);
      }
      pos++;
      pos = skipWhitespace(content, pos);

      // Parse value
      String value;
      if (content.charAt(pos) == '"') {
        // String value
        int valueEnd = findClosingQuote(content, pos + 1);
        value = unescapeJson(content.substring(pos + 1, valueEnd));
        pos = valueEnd + 1;
      } else if (content.charAt(pos) == '{') {
        // Nested object — skip it entirely (for fields like authAttempt in wait response)
        int depth = 1;
        int start = pos;
        pos++;
        while (pos < content.length() && depth > 0) {
          char c = content.charAt(pos);
          if (c == '{') {
            depth++;
          } else if (c == '}') {
            depth--;
          } else if (c == '"') {
            pos = findClosingQuote(content, pos + 1);
          }
          pos++;
        }
        value = content.substring(start, pos);
      } else {
        // Number, boolean, or null — read until comma or end
        int start = pos;
        while (pos < content.length() && content.charAt(pos) != ',' && content.charAt(pos) != '}') {
          pos++;
        }
        String raw = content.substring(start, pos).strip();
        value = "null".equals(raw) ? null : raw;
      }

      result.put(key, value);

      // Skip whitespace and comma
      pos = skipWhitespace(content, pos);
      if (pos < content.length() && content.charAt(pos) == ',') {
        pos++;
      }
    }

    return result;
  }

  /**
   * Gets a string value from the parsed map, returning {@code null} if absent or null.
   *
   * @param map the parsed JSON map
   * @param key the field name
   * @return the string value, or {@code null}
   */
  static String getString(Map<String, String> map, String key) {
    return map.get(key);
  }

  /**
   * Gets an integer value from the parsed map.
   *
   * @param map the parsed JSON map
   * @param key the field name
   * @param defaultValue the default if the field is absent or null
   * @return the integer value
   */
  static int getInt(Map<String, String> map, String key, int defaultValue) {
    String val = map.get(key);
    if (val == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(val);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * Gets a nullable Integer value from the parsed map.
   *
   * @param map the parsed JSON map
   * @param key the field name
   * @return the Integer value, or {@code null} if absent or null
   */
  static Integer getInteger(Map<String, String> map, String key) {
    String val = map.get(key);
    if (val == null) {
      return null;
    }
    try {
      return Integer.parseInt(val);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Gets a boolean value from the parsed map.
   *
   * @param map the parsed JSON map
   * @param key the field name
   * @param defaultValue the default if the field is absent or null
   * @return the boolean value
   */
  static boolean getBoolean(Map<String, String> map, String key, boolean defaultValue) {
    String val = map.get(key);
    if (val == null) {
      return defaultValue;
    }
    return "true".equalsIgnoreCase(val);
  }

  // --- Internal helpers ---

  private static void appendValue(StringBuilder sb, Object value) {
    if (value == null) {
      sb.append("null");
    } else if (value instanceof String s) {
      sb.append("\"").append(escapeJson(s)).append("\"");
    } else if (value instanceof Number || value instanceof Boolean) {
      sb.append(value);
    } else {
      sb.append("\"").append(escapeJson(value.toString())).append("\"");
    }
  }

  private static String escapeJson(String s) {
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private static String unescapeJson(String s) {
    var sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        char next = s.charAt(i + 1);
        switch (next) {
          case '"', '\\', '/' -> {
            sb.append(next);
            i++;
          }
          case 'n' -> {
            sb.append('\n');
            i++;
          }
          case 'r' -> {
            sb.append('\r');
            i++;
          }
          case 't' -> {
            sb.append('\t');
            i++;
          }
          default -> sb.append(c);
        }
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static int skipWhitespace(String s, int pos) {
    while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
      pos++;
    }
    return pos;
  }

  private static int findClosingQuote(String s, int start) throws EzkeyException {
    for (int i = start; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\') {
        i++; // skip escaped char
      } else if (c == '"') {
        return i;
      }
    }
    throw new EzkeyException("Unterminated string in JSON starting at position " + start);
  }
}
