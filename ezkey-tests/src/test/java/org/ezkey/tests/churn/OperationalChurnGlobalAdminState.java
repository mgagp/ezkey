/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * State file for the peer Global Admin dedicated to operational churn (gitignored via .ezkey-test/).
 */

package org.ezkey.tests.churn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Persisted credentials for the operational-churn peer Global Admin (not the bootstrap admin).
 *
 * <p>Written by {@link OperationalChurnInitTest}; read by {@link OperationalChurnTest}. Path:
 * {@code .ezkey-test/operational-churn-global-admin.json} relative to the JVM working directory
 * (typically repository root when running Maven from root).
 */
public record OperationalChurnGlobalAdminState(String username, String bearerToken) {

  public static final Path STATE_FILE =
      Path.of(
          System.getProperty("ezkey.test.state.dir", ".ezkey-test"),
          "operational-churn-global-admin.json");

  /**
   * Loads state from disk, or null if missing or unreadable.
   *
   * @return state or null
   */
  public static OperationalChurnGlobalAdminState load() {
    if (!Files.exists(STATE_FILE)) {
      return null;
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode node = (ObjectNode) mapper.readTree(STATE_FILE.toFile());
      String u = node.path("username").asText(null);
      String t = node.path("bearerToken").asText(null);
      if (u == null || u.isBlank() || t == null || t.isBlank()) {
        return null;
      }
      return new OperationalChurnGlobalAdminState(u, t);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Writes this state to {@link #STATE_FILE}, creating parent directories if needed.
   *
   * @throws IOException if write fails
   */
  public void save() throws IOException {
    Files.createDirectories(STATE_FILE.getParent());
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.put("username", username);
    node.put("bearerToken", bearerToken);
    mapper.writerWithDefaultPrettyPrinter().writeValue(STATE_FILE.toFile(), node);
  }
}
