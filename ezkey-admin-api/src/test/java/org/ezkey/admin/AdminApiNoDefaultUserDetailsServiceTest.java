/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminApiNoDefaultUserDetailsServiceTest
 * Description: Guards against Spring Boot's default in-memory user / generated security password.
 */

package org.ezkey.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Ensures Admin API excludes {@link UserDetailsServiceAutoConfiguration} so startup does not
 * register Spring's default in-memory user or log {@code Using generated security password}.
 *
 * @since 2026
 */
class AdminApiNoDefaultUserDetailsServiceTest {

  @Test
  void springBootApplicationExcludesUserDetailsServiceAutoConfiguration() {
    SpringBootApplication annotation =
        AdminApplication.class.getAnnotation(SpringBootApplication.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.exclude()).contains(UserDetailsServiceAutoConfiguration.class);
  }
}
