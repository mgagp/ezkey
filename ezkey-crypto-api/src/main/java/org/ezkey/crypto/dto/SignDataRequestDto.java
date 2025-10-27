/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: SignDataRequestDto
 * Description: Request DTO for signing data with a private key.
 */

package org.ezkey.crypto.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request DTO for signing data with a private key")
public class SignDataRequestDto {

  @Schema(
      description = "Data to be signed",
      example = "Hello, World!",
      requiredMode = RequiredMode.REQUIRED)
  @NotBlank(message = "Data to sign cannot be blank")
  private String data;

  @Schema(
      description = "Base64-encoded PKCS#8 private key",
      example = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...",
      requiredMode = RequiredMode.REQUIRED)
  @NotBlank(message = "Private key cannot be blank")
  private String privateKey;

  public SignDataRequestDto() {}

  public SignDataRequestDto(String data, String privateKey) {
    this.data = data;
    this.privateKey = privateKey;
  }

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }

  public String getPrivateKey() {
    return privateKey;
  }

  public void setPrivateKey(String privateKey) {
    this.privateKey = privateKey;
  }
}
