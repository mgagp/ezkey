/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthApiNoDefaultUserDetailsServiceTest
 * Description: Guards against Spring Boot's default in-memory user / generated security password.
 */

package org.ezkey.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Ensures Auth API excludes {@link UserDetailsServiceAutoConfiguration} so startup does not
 * register Spring's default in-memory user or log {@code Using generated security password}.
 *
 * @since 2026
 */
class AuthApiNoDefaultUserDetailsServiceTest {

  @Test
  void springBootApplicationExcludesUserDetailsServiceAutoConfiguration() {
    SpringBootApplication annotation =
        AuthApplication.class.getAnnotation(SpringBootApplication.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.exclude()).contains(UserDetailsServiceAutoConfiguration.class);
  }
}
