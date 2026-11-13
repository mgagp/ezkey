/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EvaluatorSelfRegistrationConfig
 * Description: Registers evaluator self-registration configuration properties.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@link EvaluatorSelfRegistrationProperties} and fail-closes at startup when signup is
 * enabled without required navigation URLs.
 */
@Configuration
@EnableConfigurationProperties(EvaluatorSelfRegistrationProperties.class)
public class EvaluatorSelfRegistrationConfig {

  /**
   * Validates evaluator self-registration URL configuration before the application accepts traffic.
   *
   * @param properties bound evaluator self-registration settings
   */
  public EvaluatorSelfRegistrationConfig(EvaluatorSelfRegistrationProperties properties) {
    EvaluatorSelfRegistrationStartupValidator.enforceUrlsWhenEnabled(
        properties.isEnabled(), properties.getAdminUiUrl(), properties.getGuidedTourUrl());
  }
}
