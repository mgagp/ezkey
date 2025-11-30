/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ValidateSignatureResponseDto
 * Description: Response DTO containing signature validation result.
 */

package org.ezkey.crypto.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(description = "Response DTO containing signature validation result")
public class ValidateSignatureResponseDto {

  @Schema(
      description = "Whether the signature is valid",
      example = "true",
      requiredMode = RequiredMode.REQUIRED)
  private boolean valid;

  @Schema(
      description = "Validation message or reason",
      example = "Signature is valid",
      requiredMode = RequiredMode.REQUIRED)
  private String message;

  @Schema(
      description = "Algorithm used for validation",
      example = "Ed25519",
      requiredMode = RequiredMode.REQUIRED)
  private String algorithm;

  public ValidateSignatureResponseDto(boolean valid, String message, String algorithm) {
    this.valid = valid;
    this.message = message;
    this.algorithm = algorithm;
  }

  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }
}
