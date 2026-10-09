/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminMfaBootstrapTtlValidationTest
 * Description: enrollment-expiration-hours must be >= 1 (no unbounded TTL).
 */

package org.ezkey.admin.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Ensures {@code ezkey.admin.mfa.bootstrap.enrollment-expiration-hours} rejects {@code 0}.
 *
 * @since 2026
 */
class AdminMfaBootstrapTtlValidationTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  @DisplayName("enrollmentExpirationHours=0 fails Bean Validation @Min(1)")
  void zeroHoursFailsBeanValidation() {
    AdminMfaProperties props = new AdminMfaProperties();
    props.getBootstrap().setEnrollmentExpirationHours(0);

    Set<ConstraintViolation<AdminMfaProperties>> violations = validator.validate(props);

    assertThat(violations).isNotEmpty();
    assertThat(violations)
        .anyMatch(v -> v.getPropertyPath().toString().contains("enrollmentExpirationHours"));
  }

  @Test
  @DisplayName("Binder with ValidationBindHandler rejects enrollment-expiration-hours=0")
  void binderRejectsZeroHours() {
    MapConfigurationPropertySource source =
        new MapConfigurationPropertySource(
            java.util.Map.of("ezkey.admin.mfa.bootstrap.enrollment-expiration-hours", "0"));
    LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
    factoryBean.afterPropertiesSet();
    ValidationBindHandler handler = new ValidationBindHandler(factoryBean);

    assertThatThrownBy(
            () ->
                new Binder(source)
                    .bind("ezkey.admin.mfa", Bindable.of(AdminMfaProperties.class), handler)
                    .get())
        .isInstanceOf(BindException.class);
  }

  @Test
  @DisplayName("enrollmentExpirationHours=24 is valid")
  void twentyFourHoursValid() {
    AdminMfaProperties props = new AdminMfaProperties();
    props.getBootstrap().setEnrollmentExpirationHours(24);
    assertThat(validator.validate(props)).isEmpty();
  }
}
