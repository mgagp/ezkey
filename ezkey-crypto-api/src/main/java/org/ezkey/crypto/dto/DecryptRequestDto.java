/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DecryptRequestDto
 * Description: Request DTO for decrypting encrypted database column values.
 */

package org.ezkey.crypto.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for the decrypt debugging endpoint.
 *
 * <p>The decrypt endpoint accepts values in the Ezkey encryption format {@code
 * ENC:keyID:Base64(ciphertext)} and returns diagnostic metadata along with the decrypted plaintext
 * (when possible).
 *
 * @since 2025
 */
@Schema(description = "Request DTO for decrypting encrypted database column values")
public class DecryptRequestDto {

  @Schema(
      description =
          "Encrypted value to decrypt (format: ENC:keyID:Base64(ciphertext)) or plaintext",
      example = "ENC:1234567890:YWJjZGVmZ2hpams=",
      requiredMode = RequiredMode.REQUIRED)
  @NotBlank(message = "Encrypted value cannot be blank")
  private String encryptedValue;

  public DecryptRequestDto() {}

  public DecryptRequestDto(String encryptedValue) {
    this.encryptedValue = encryptedValue;
  }

  public String getEncryptedValue() {
    return encryptedValue;
  }

  public void setEncryptedValue(String encryptedValue) {
    this.encryptedValue = encryptedValue;
  }
}
