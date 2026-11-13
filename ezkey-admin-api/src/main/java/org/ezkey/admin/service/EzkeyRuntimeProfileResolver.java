/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: EzkeyRuntimeProfileResolver
 * Description: Resolves the product runtime profile (base | integrity) from Spring environment.
 */
package org.ezkey.admin.service;

import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Resolves the operator-facing product runtime profile from the live Spring environment.
 *
 * <p>Product vocabulary (locked): {@code base} (opt-in; integrity monitoring jobs off) and {@code
 * integrity} (default). Spring mechanism for base is active profile {@code docker-base} (see {@code
 * EZKEY_RUNTIME_PROFILE} / {@code --runtime=base}). This resolver does not expose the job matrix —
 * only the product label.
 *
 * @since 2026
 */
@Component
public class EzkeyRuntimeProfileResolver {

  /** Spring profile that activates the opt-in base runtime overlay. */
  public static final String DOCKER_BASE_SPRING_PROFILE = "docker-base";

  /** Product runtime profile when base overlay is active. */
  public static final String PROFILE_BASE = "base";

  /** Product runtime profile when base overlay is not active (default). */
  public static final String PROFILE_INTEGRITY = "integrity";

  private final Environment environment;

  /**
   * Constructs the resolver.
   *
   * @param environment Spring environment (active profiles)
   */
  public EzkeyRuntimeProfileResolver(Environment environment) {
    this.environment = environment;
  }

  /**
   * Returns the product runtime profile for this Admin API process.
   *
   * @return {@code base} when Spring profile {@code docker-base} is active; otherwise {@code
   *     integrity}
   */
  public String resolve() {
    if (Arrays.asList(environment.getActiveProfiles()).contains(DOCKER_BASE_SPRING_PROFILE)) {
      return PROFILE_BASE;
    }
    return PROFILE_INTEGRITY;
  }
}
