/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AuthNativeConfiguration
 * Description: Native image configuration and AOT hints for Ezkey Auth API.
 */

package org.ezkey.auth.config;

import org.ezkey.authattempt.dto.AuthAttemptPendingRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptPendingResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondResponseDto;
import org.ezkey.enrollment.dto.EnrollmentBindRequestDto;
import org.ezkey.enrollment.dto.EnrollmentBindResponseDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyRequestDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyResponseDto;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Native image configuration for Ezkey Auth API.
 * <p>
 * This configuration provides AOT hints and runtime configuration needed
 * for native image compilation. It includes reflection configuration for DTOs,
 * resource access patterns, and other native image requirements.
 * </p>
 *
 * <p>
 * <b>AOT Processing:</b>
 * <ul>
 * <li><b>Reflection:</b> All DTOs used in REST endpoints</li>
 * <li><b>Resources:</b> Application properties and validation messages</li>
 * <li><b>Serialization:</b> Jackson serialization for all DTOs</li>
 * </ul>
 * </p>
 *
 * @since 2025
 */
@Configuration
@ImportRuntimeHints(AuthNativeConfiguration.AuthRuntimeHints.class)
public class AuthNativeConfiguration {

    /**
     * Runtime hints registrar for native image compilation.
     * <p>
     * Registers all DTOs and classes that need reflection access during
     * native image runtime. This includes all request/response DTOs used
     * by the REST endpoints.
     * </p>
     */
    static class AuthRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints,ClassLoader classLoader) {
            // Register enrollment DTOs for reflection
            hints.reflection().registerType(EnrollmentBindRequestDto.class).registerType(EnrollmentBindResponseDto.class).registerType(EnrollmentVerifyRequestDto.class)
                    .registerType(EnrollmentVerifyResponseDto.class);

            // Register auth attempt DTOs for reflection
            hints.reflection().registerType(AuthAttemptPendingRequestDto.class).registerType(AuthAttemptPendingResponseDto.class)
                    .registerType(AuthAttemptRespondRequestDto.class).registerType(AuthAttemptRespondResponseDto.class);

            // Register serialization hints for Jackson - using TypeReference
            hints.serialization().registerType(org.springframework.aot.hint.TypeReference.of(EnrollmentBindRequestDto.class))
                    .registerType(org.springframework.aot.hint.TypeReference.of(EnrollmentBindResponseDto.class))
                    .registerType(org.springframework.aot.hint.TypeReference.of(EnrollmentVerifyRequestDto.class))
                    .registerType(org.springframework.aot.hint.TypeReference.of(EnrollmentVerifyResponseDto.class))
                    .registerType(org.springframework.aot.hint.TypeReference.of(AuthAttemptPendingRequestDto.class))
                    .registerType(org.springframework.aot.hint.TypeReference.of(AuthAttemptPendingResponseDto.class))
                    .registerType(org.springframework.aot.hint.TypeReference.of(AuthAttemptRespondRequestDto.class))
                    .registerType(org.springframework.aot.hint.TypeReference.of(AuthAttemptRespondResponseDto.class));

            // Register resource patterns
            hints.resources().registerPattern("application*.properties").registerPattern("META-INF/native-image/org.ezkey/ezkey-auth-api/*")
                    .registerPattern("ValidationMessages.properties");
        }
    }
}