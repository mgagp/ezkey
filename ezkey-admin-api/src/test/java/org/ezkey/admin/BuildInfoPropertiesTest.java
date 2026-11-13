/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: BuildInfoPropertiesTest
 * Description: Guards spring-boot build-info so actuator /info can expose product SemVer.
 */

package org.ezkey.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards spring-boot {@code build-info} generation so actuator {@code /info} can expose the product
 * SemVer (ADR-0017 / #1211 step 2).
 *
 * @since 2026
 */
class BuildInfoPropertiesTest {

  @Test
  @DisplayName("META-INF/build-info.properties carries product line 0.2.x")
  void buildInfoVersionMatchesProductLine() throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/META-INF/build-info.properties")) {
      assertThat(in)
          .as("spring-boot-maven-plugin build-info goal must run for ezkey-admin-api")
          .isNotNull();
      Properties props = new Properties();
      props.load(in);
      assertThat(props.getProperty("build.version")).startsWith("0.2.");
      assertThat(props.getProperty("build.artifact")).isEqualTo("ezkey-admin-api");
    }
  }
}
