/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EzkeyRuntimeProfileResolverTest
 * Description: Unit tests for product runtime profile resolution.
 */
package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

/**
 * Unit tests for {@link EzkeyRuntimeProfileResolver}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EzkeyRuntimeProfileResolver Tests")
class EzkeyRuntimeProfileResolverTest {

  @Mock private Environment environment;

  @Test
  @DisplayName("Should resolve integrity when docker-base is not active")
  void resolve_withoutDockerBase_returnsIntegrity() {
    when(environment.getActiveProfiles()).thenReturn(new String[] {"docker", "docker-test"});
    EzkeyRuntimeProfileResolver resolver = new EzkeyRuntimeProfileResolver(environment);

    assertThat(resolver.resolve()).isEqualTo(EzkeyRuntimeProfileResolver.PROFILE_INTEGRITY);
  }

  @Test
  @DisplayName("Should resolve base when docker-base is active")
  void resolve_withDockerBase_returnsBase() {
    when(environment.getActiveProfiles())
        .thenReturn(new String[] {"docker", "docker-test", "docker-base"});
    EzkeyRuntimeProfileResolver resolver = new EzkeyRuntimeProfileResolver(environment);

    assertThat(resolver.resolve()).isEqualTo(EzkeyRuntimeProfileResolver.PROFILE_BASE);
  }
}
