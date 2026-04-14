/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Bean Validation implementation for {@link IanaTimezone}. Null and blank values are considered
 * valid so optional fields remain optional.
 */
public class IanaTimezoneValidator implements ConstraintValidator<IanaTimezone, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.isBlank()) {
      return true;
    }
    try {
      ZoneId.of(value.trim());
      return true;
    } catch (DateTimeException e) {
      return false;
    }
  }
}
