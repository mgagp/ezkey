/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminActivationRequestDto
 * Description: Request DTO for first-time administrator activation using an activation code.
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for first-time administrator activation.
 *
 * <p>Used when an administrator was provisioned in activation-code mode and must consume the
 * one-time activation code before the first device enrollment can be created.
 *
 * @param activationCode One-time activation code in the Ezkey activation token format
 * @since 2025
 */
public record AdminActivationRequestDto(
    @NotBlank(message = "Activation code is required")
        @Pattern(
            regexp = "^ezkey_activation_[A-Za-z0-9]+$",
            message = "Activation code must use the Ezkey activation token format")
        String activationCode) {

  @Override
  public String toString() {
    return "AdminActivationRequestDto{activationCode='[PROTECTED]'}";
  }
}
