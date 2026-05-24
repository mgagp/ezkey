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

/** Binds {@link EvaluatorSelfRegistrationProperties} for EXP1 anonymous evaluator signup. */
@Configuration
@EnableConfigurationProperties(EvaluatorSelfRegistrationProperties.class)
public class EvaluatorSelfRegistrationConfig {}
