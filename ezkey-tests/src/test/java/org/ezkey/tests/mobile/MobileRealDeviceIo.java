/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: MobileRealDeviceIo
 * Description: System-property and JSON helpers for real-device Maestro JUnit building blocks.
 */

package org.ezkey.tests.mobile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Shared I/O for mobile real-device JUnit building blocks invoked from Bash.
 *
 * @since 2026
 */
public final class MobileRealDeviceIo {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private MobileRealDeviceIo() {}

  /**
   * Reads a required system property passed by the Bash orchestrator.
   *
   * @param name Maven/Surefire system property name (for example {@code mobile.enrollmentId})
   * @return trimmed value
   * @throws IllegalArgumentException if missing, blank, or an unresolved Maven placeholder
   */
  public static String requiredProperty(String name) {
    String value = optionalProperty(name);
    if (value == null) {
      throw new IllegalArgumentException("Missing required -D" + name + "=<value>");
    }
    return value;
  }

  /**
   * Reads an optional system property.
   *
   * @param name property name
   * @return trimmed value, or {@code null} if unset / placeholder
   */
  public static String optionalProperty(String name) {
    String value = System.getProperty(name);
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty() || trimmed.startsWith("${")) {
      return null;
    }
    return trimmed;
  }

  /**
   * Writes a JSON object to {@code path}, creating parent directories as needed.
   *
   * @param path output file
   * @param node JSON object
   * @throws IOException if the file cannot be written
   */
  public static void writeJson(Path path, ObjectNode node) throws IOException {
    if (path.getParent() != null) {
      Files.createDirectories(path.getParent());
    }
    MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), node);
  }

  /**
   * Creates a mutable JSON object node.
   *
   * @return empty object node
   */
  public static ObjectNode objectNode() {
    return MAPPER.createObjectNode();
  }
}
