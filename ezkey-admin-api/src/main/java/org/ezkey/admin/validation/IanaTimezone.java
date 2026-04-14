/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is either blank or a valid IANA time zone identifier (e.g. {@code
 * Europe/Paris}), using {@link java.time.ZoneId#of(String)}.
 */
@Documented
@Constraint(validatedBy = IanaTimezoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface IanaTimezone {

  String message() default "Invalid IANA timezone identifier";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
