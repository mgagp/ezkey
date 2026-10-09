/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: CryptoApiNoDefaultUserDetailsServiceTest
 * Description: Guards against Spring Boot's default in-memory user / generated security password.
 */

package org.ezkey.cryptotest;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.crypto.CryptoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Ensures Crypto API does not auto-configure Spring's default in-memory user (no {@code Using
 * generated security password} at startup). The lab tool permits all requests; it does not use
 * Spring form login or an in-memory user.
 *
 * <p>Lives under {@code org.ezkey.cryptotest} (not {@code org.ezkey.crypto}) so {@link
 * CryptoApplication}'s explicit {@code @ComponentScan} cannot pick up the nested test configuration
 * during {@code @WebMvcTest}.
 *
 * @since 2026
 */
class CryptoApiNoDefaultUserDetailsServiceTest {

  private final WebApplicationContextRunner securityOnlyRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
          .withUserConfiguration(PermitAllSecurityConfiguration.class);

  @Test
  void springBootApplicationExcludesUserDetailsServiceAutoConfiguration() {
    SpringBootApplication annotation =
        CryptoApplication.class.getAnnotation(SpringBootApplication.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.exclude()).contains(UserDetailsServiceAutoConfiguration.class);
  }

  @Test
  void withoutUserDetailsServiceAutoConfiguration_noInMemoryUserDetailsManager() {
    // Same effect as @SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
    securityOnlyRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(InMemoryUserDetailsManager.class);
          assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        });
  }

  @Test
  void withUserDetailsServiceAutoConfiguration_registersInMemoryUserDetailsManager() {
    // Control: the condition that logs "Using generated security password" at startup.
    securityOnlyRunner
        .withConfiguration(AutoConfigurations.of(UserDetailsServiceAutoConfiguration.class))
        .run(context -> assertThat(context).hasSingleBean(InMemoryUserDetailsManager.class));
  }

  @TestConfiguration
  @EnableWebSecurity
  static class PermitAllSecurityConfiguration {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      http.csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
      return http.build();
    }
  }
}
