/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.ezkey.admin.dto.request.TenantCreateRequestDto;
import org.ezkey.admin.dto.request.TenantUpdateRequestDto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link IanaTimezone} on tenant request DTOs. */
class IanaTimezoneValidatorTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  @Test
  @DisplayName("Valid IANA timezone passes on create DTO")
  void validTimezonePassesCreate() {
    TenantCreateRequestDto dto =
        new TenantCreateRequestDto(
            "Acme Tenant", null, null, null, null, "Europe/Paris", null, null, null);
    assertTrue(validator.validate(dto).isEmpty());
  }

  @Test
  @DisplayName("Blank timezone passes on create DTO (optional)")
  void blankTimezonePassesCreate() {
    TenantCreateRequestDto dto =
        new TenantCreateRequestDto("Acme Tenant", null, null, null, null, "  ", null, null, null);
    assertTrue(validator.validate(dto).isEmpty());
  }

  @Test
  @DisplayName("Invalid timezone fails on create DTO")
  void invalidTimezoneFailsCreate() {
    TenantCreateRequestDto dto =
        new TenantCreateRequestDto(
            "Acme Tenant", null, null, null, null, "Invalid/Zone/Name", null, null, null);
    Set<ConstraintViolation<TenantCreateRequestDto>> violations = validator.validate(dto);
    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("timezone")));
  }

  @Test
  @DisplayName("Invalid timezone fails on update DTO")
  void invalidTimezoneFailsUpdate() {
    TenantUpdateRequestDto dto =
        new TenantUpdateRequestDto(null, null, null, null, null, null, "NotReal", null, null, null);
    Set<ConstraintViolation<TenantUpdateRequestDto>> violations = validator.validate(dto);
    assertFalse(violations.isEmpty());
  }
}
