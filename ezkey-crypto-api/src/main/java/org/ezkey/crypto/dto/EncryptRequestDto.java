/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EncryptRequestDto
 * Description: Request DTO for encrypting plaintext values.
 */

package org.ezkey.crypto.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for the encrypt endpoint.
 *
 * <p>The encrypt endpoint accepts plaintext values and returns them in the Ezkey encryption format
 * {@code ENC:keyID:Base64(ciphertext)} along with diagnostic metadata.
 *
 * @since 2025
 */
@Schema(description = "Request DTO for encrypting plaintext values")
public class EncryptRequestDto {

  @Schema(
      description = "Plaintext value to encrypt",
      example = "my-secret-value",
      requiredMode = RequiredMode.REQUIRED)
  @NotBlank(message = "Plaintext value cannot be blank")
  private String plaintext;

  public EncryptRequestDto() {}

  public EncryptRequestDto(String plaintext) {
    this.plaintext = plaintext;
  }

  public String getPlaintext() {
    return plaintext;
  }

  public void setPlaintext(String plaintext) {
    this.plaintext = plaintext;
  }
}
