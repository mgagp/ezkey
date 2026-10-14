/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AccessCodeStartupFailureTest
 * Description: Spring context fails fast on short or duplicate access codes.
 */

package org.ezkey.demo.acme.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.ezkey.demo.acme.DemoAcmeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

class AccessCodeStartupFailureTest {

  @Test
  void shouldFailContextStartupWhenAccessCodeTooShort() {
    SpringApplication app = new SpringApplication(DemoAcmeApplication.class);
    Throwable thrown =
        catchThrowable(
            () ->
                app.run(
                    "--server.port=0",
                    "--spring.main.web-application-type=none",
                    "--ezkey.rate-limit.enabled=false",
                    "--ezkey.access-codes.bad.code=abcd",
                    "--ezkey.access-codes.bad.integration-key=ikey",
                    "--ezkey.access-codes.bad.secret-key=skey",
                    "--ezkey.access-codes.bad.label=Bad"));

    assertThat(rootCause(thrown))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid code");
  }

  @Test
  void shouldFailContextStartupWhenAccessCodesDuplicate() {
    SpringApplication app = new SpringApplication(DemoAcmeApplication.class);
    Throwable thrown =
        catchThrowable(
            () ->
                app.run(
                    "--server.port=0",
                    "--spring.main.web-application-type=none",
                    "--ezkey.rate-limit.enabled=false",
                    "--ezkey.access-codes.a.code=0123456789abcdef0123456789abcdef",
                    "--ezkey.access-codes.a.integration-key=ikey-a",
                    "--ezkey.access-codes.a.secret-key=skey-a",
                    "--ezkey.access-codes.a.label=A",
                    "--ezkey.access-codes.b.code=0123456789abcdef0123456789abcdef",
                    "--ezkey.access-codes.b.integration-key=ikey-b",
                    "--ezkey.access-codes.b.secret-key=skey-b",
                    "--ezkey.access-codes.b.label=B"));

    assertThat(rootCause(thrown))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate");
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
