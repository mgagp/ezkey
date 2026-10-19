/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: JavaMelodyStartupGuardConfigTest
 * Description: Unit tests for demo-app-acme JavaMelody / management-port startup guard.
 */

package org.ezkey.demo.acme.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.ezkey.demo.acme.DemoAcmeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

class JavaMelodyStartupGuardConfigTest {

  @Test
  void enforceFailsWhenJavaMelodyWithoutManagementEndpoint() {
    assertThatThrownBy(() -> JavaMelodyStartupGuardConfig.enforce(true, false, 8082, 8086))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("management-endpoint-monitoring-enabled");
  }

  @Test
  void enforceFailsWhenPortsEqual() {
    assertThatThrownBy(() -> JavaMelodyStartupGuardConfig.enforce(false, false, 8082, 8082))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must differ");
  }

  @Test
  void enforceAllowsSeparatedPorts() {
    assertThatCode(() -> JavaMelodyStartupGuardConfig.enforce(false, false, 8082, 8086))
        .doesNotThrowAnyException();
  }

  @Test
  void contextFailsWhenManagementPortEqualsBusinessPort() {
    SpringApplication app = new SpringApplication(DemoAcmeApplication.class);
    Throwable thrown =
        org.assertj.core.api.Assertions.catchThrowable(
            () ->
                app.run(
                    "--server.port=18082",
                    "--management.server.port=18082",
                    "--spring.main.web-application-type=none",
                    "--ezkey.rate-limit.enabled=false"));

    assertThat(rootCause(thrown))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must differ");
  }

  private static Throwable rootCause(Throwable thrown) {
    assertThat(thrown).isNotNull();
    Throwable root = thrown;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root;
  }
}
