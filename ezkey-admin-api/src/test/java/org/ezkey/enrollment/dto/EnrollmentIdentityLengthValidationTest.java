/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentIdentityLengthValidationTest
 * Description: Name and User ID length bounds on the enrollment create and update requests.
 */

package org.ezkey.enrollment.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.ezkey.admin.dto.request.EnrollmentUpdateRequestDto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Create and update apply the column widths: Name 1 to 64 ({@code enrollment_name VARCHAR(64)}),
 * User ID at most 255 ({@code user_identifier VARCHAR(255)}) (#1457).
 *
 * @since 2026
 */
@DisplayName("Enrollment Name and User ID length bounds (#1457)")
class EnrollmentIdentityLengthValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeFactory() {
    factory.close();
  }

  private static EnrollmentCreateRequestDto create(String name, String userIdentifier) {
    return new EnrollmentCreateRequestDto(1, name, false, null, null, userIdentifier, null);
  }

  private static EnrollmentUpdateRequestDto update(String name, String userIdentifier) {
    return new EnrollmentUpdateRequestDto(
        null, name, null, null, null, null, userIdentifier, null, null);
  }

  private static <T> Set<String> invalidFields(T dto) {
    Set<ConstraintViolation<T>> violations = validator.validate(dto);
    return violations.stream()
        .map(v -> v.getPropertyPath().toString())
        .collect(java.util.stream.Collectors.toSet());
  }

  @Test
  @DisplayName("create: a 64-character Name and a 255-character User ID are accepted")
  void createAcceptsColumnWidths() {
    assertThat(invalidFields(create("n".repeat(64), "u".repeat(255)))).isEmpty();
  }

  @Test
  @DisplayName("create: a 65-character Name is rejected")
  void createRejectsNameOver64() {
    assertThat(invalidFields(create("n".repeat(65), null))).containsExactly("name");
  }

  @Test
  @DisplayName("create: a 256-character User ID is rejected")
  void createRejectsUserIdentifierOver255() {
    assertThat(invalidFields(create("Alice", "u".repeat(256)))).containsExactly("userIdentifier");
  }

  @Test
  @DisplayName("update: a 64-character Name and a 255-character User ID are accepted")
  void updateAcceptsColumnWidths() {
    assertThat(invalidFields(update("n".repeat(64), "u".repeat(255)))).isEmpty();
  }

  @Test
  @DisplayName("update: a 65-character Name is rejected")
  void updateRejectsNameOver64() {
    assertThat(invalidFields(update("n".repeat(65), null))).containsExactly("enrollmentName");
  }

  @Test
  @DisplayName("update: a 256-character User ID is rejected")
  void updateRejectsUserIdentifierOver255() {
    assertThat(invalidFields(update(null, "u".repeat(256)))).containsExactly("userIdentifier");
  }
}
