/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EncryptResponseDto
 * Description: Response DTO containing encrypted value and comprehensive debugging metadata.
 */

package org.ezkey.crypto.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Response DTO for encrypting plaintext values.
 *
 * <p>This DTO is intended for testing and debugging purposes. It provides both the encrypted value
 * (when available) and metadata that helps explain why a value could not be encrypted (key ID,
 * encryption availability, and error message).
 *
 * @since 2025
 */
@Schema(
    description = "Response DTO containing encrypted value and comprehensive debugging metadata")
public class EncryptResponseDto {

  @Schema(
      description =
          "Encrypted value in ENC:keyID:Base64(ciphertext) format, "
              + "or original plaintext if encryption unavailable/failed",
      example = "ENC:2865054995:AarFRRPHitKf32X1m/o8j0lJY71IGc1IN9dd65kd/...",
      requiredMode = RequiredMode.REQUIRED)
  private String encryptedValue;

  @Schema(
      description = "Indicates if the encryption operation succeeded",
      example = "true",
      requiredMode = RequiredMode.REQUIRED)
  private boolean encryptionSuccessful;

  @Schema(
      description = "Primary key ID used for encryption (null if encryption failed/unavailable)",
      example = "2865054995",
      requiredMode = RequiredMode.NOT_REQUIRED)
  private String keyId;

  @Schema(
      description =
          "Format of the output (\"ENC:keyID:Base64\", or \"PLAINTEXT\" if encryption unavailable)",
      example = "ENC:keyID:Base64",
      requiredMode = RequiredMode.REQUIRED)
  private String encryptedFormat;

  @Schema(
      description = "Error message if encryption failed (null if successful)",
      example = "Encryption service not available",
      requiredMode = RequiredMode.NOT_REQUIRED)
  private String errorMessage;

  @Schema(
      description = "Indicates if EncryptionService is initialized and available",
      example = "true",
      requiredMode = RequiredMode.REQUIRED)
  private boolean encryptionAvailable;

  public EncryptResponseDto() {}

  public EncryptResponseDto(
      String encryptedValue,
      boolean encryptionSuccessful,
      String keyId,
      String encryptedFormat,
      String errorMessage,
      boolean encryptionAvailable) {
    this.encryptedValue = encryptedValue;
    this.encryptionSuccessful = encryptionSuccessful;
    this.keyId = keyId;
    this.encryptedFormat = encryptedFormat;
    this.errorMessage = errorMessage;
    this.encryptionAvailable = encryptionAvailable;
  }

  public String getEncryptedValue() {
    return encryptedValue;
  }

  public void setEncryptedValue(String encryptedValue) {
    this.encryptedValue = encryptedValue;
  }

  public boolean isEncryptionSuccessful() {
    return encryptionSuccessful;
  }

  public void setEncryptionSuccessful(boolean encryptionSuccessful) {
    this.encryptionSuccessful = encryptionSuccessful;
  }

  public String getKeyId() {
    return keyId;
  }

  public void setKeyId(String keyId) {
    this.keyId = keyId;
  }

  public String getEncryptedFormat() {
    return encryptedFormat;
  }

  public void setEncryptedFormat(String encryptedFormat) {
    this.encryptedFormat = encryptedFormat;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public boolean isEncryptionAvailable() {
    return encryptionAvailable;
  }

  public void setEncryptionAvailable(boolean encryptionAvailable) {
    this.encryptionAvailable = encryptionAvailable;
  }
}
