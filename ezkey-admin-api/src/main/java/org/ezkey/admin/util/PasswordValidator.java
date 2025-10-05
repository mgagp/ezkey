/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: PasswordValidator
 * Description: Utility class for password strength validation.
 */

package org.ezkey.admin.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for password strength validation.
 * <p>
 * This class provides comprehensive password validation including length,
 * character composition, and security requirements. It ensures passwords
 * meet minimum security standards before being accepted.
 * </p>
 *
 * <p>
 * <b>Validation Rules:</b>
 * <ul>
 * <li>Minimum 12 characters length</li>
 * <li>At least one uppercase letter (A-Z)</li>
 * <li>At least one lowercase letter (a-z)</li>
 * <li>At least one digit (0-9)</li>
 * <li>At least one special character (!@#$%^&*()_+-=[]{}|;:,.<>?)</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Usage Example:</b>
 * <pre>
 * PasswordValidationResult result = PasswordValidator.validate("MyPassword123!");
 * if (!result.isValid()) {
 *     System.out.println("Password validation failed: " + result.getErrors());
 * }
 * </pre>
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public final class PasswordValidator {

    /**
     * Minimum password length required.
     */
    private static final int MIN_LENGTH = 12;

    /**
     * Maximum password length allowed (to prevent DoS attacks).
     */
    private static final int MAX_LENGTH = 128;

    /**
     * Pattern for uppercase letters.
     */
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");

    /**
     * Pattern for lowercase letters.
     */
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");

    /**
     * Pattern for digits.
     */
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");

    /**
     * Pattern for special characters.
     */
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{}|;:,.<>?]");

    /**
     * Private constructor to prevent instantiation.
     */
    private PasswordValidator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Validate password strength and composition.
     * <p>
     * This method performs comprehensive validation of the password
     * and returns a result object containing validation status and errors.
     * </p>
     *
     * @param password the password to validate
     * @return PasswordValidationResult containing validation results
     */
    public static PasswordValidationResult validate(String password) {
        List<String> errors = new ArrayList<>();

        // Check for null or empty
        if (password == null || password.isEmpty()) {
            errors.add("Password is required");
            return new PasswordValidationResult(false, errors);
        }

        // Check minimum length
        if (password.length() < MIN_LENGTH) {
            errors.add(String.format("Password must be at least %d characters long", MIN_LENGTH));
        }

        // Check maximum length
        if (password.length() > MAX_LENGTH) {
            errors.add(String.format("Password must not exceed %d characters", MAX_LENGTH));
        }

        // Check for uppercase letter
        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one uppercase letter (A-Z)");
        }

        // Check for lowercase letter
        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one lowercase letter (a-z)");
        }

        // Check for digit
        if (!DIGIT_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one digit (0-9)");
        }

        // Check for special character
        if (!SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            errors.add("Password must contain at least one special character (!@#$%^&*()_+-=[]{}|;:,.<>?)");
        }

        return new PasswordValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Result of password validation.
     * <p>
     * This class encapsulates the validation result including
     * validation status and list of validation errors.
     * </p>
     */
    public static class PasswordValidationResult {
        
        /**
         * Indicates if the password passed validation.
         */
        private final boolean valid;

        /**
         * List of validation error messages.
         */
        private final List<String> errors;

        /**
         * Constructs a new validation result.
         *
         * @param valid indicates if validation passed
         * @param errors list of validation errors
         */
        public PasswordValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        /**
         * Gets the validation status.
         *
         * @return true if password is valid
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * Gets the list of validation errors.
         *
         * @return list of error messages
         */
        public List<String> getErrors() {
            return errors;
        }

        /**
         * Gets the first validation error message.
         *
         * @return first error message or null if no errors
         */
        public String getFirstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }

        /**
         * Gets all errors as a single string.
         *
         * @return concatenated error messages separated by semicolons
         */
        public String getAllErrorsAsString() {
            return String.join("; ", errors);
        }
    }
}

