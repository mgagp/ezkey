/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.smtp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The connector's version, read once from the {@code build-info.properties} that the build writes,
 * so it follows the monorepo release instead of a constant.
 */
final class ConnectorVersion {

  /** The version reported as {@code connectorVersion}, or {@code unknown} outside a Maven build. */
  static final String VALUE = read();

  private ConnectorVersion() {}

  private static String read() {
    try (InputStream in =
        ConnectorVersion.class.getResourceAsStream("/META-INF/build-info.properties")) {
      if (in == null) {
        return "unknown";
      }
      Properties properties = new Properties();
      properties.load(in);
      return properties.getProperty("build.version", "unknown");
    } catch (IOException e) {
      return "unknown";
    }
  }
}
