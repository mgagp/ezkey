/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DecryptResponseDto
 * Description: Response DTO containing decrypted plaintext and comprehensive debugging metadata.
 */

package org.ezkey.crypto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Response DTO for decrypting encrypted database column values.
 *
 * <p>This DTO is intended for debugging and investigation purposes. It provides both the decrypted
 * plaintext (when available) and metadata that helps explain why a value could not be decrypted
 * (format detection, key ID, encryption availability, and error message).
 *
 * @since 2025
 */
@Schema(
    description =
        "Response DTO containing decrypted plaintext and comprehensive debugging metadata")
public class DecryptResponseDto {

  @Schema(
      description =
          "Decrypted plaintext value, original input if not encrypted, "
              + "or null if decryption failed",
      example = "my-secret-token-value",
      requiredMode = RequiredMode.NOT_REQUIRED)
  private String plaintext;

  @Schema(
      description = "Indicates if the input value had the ENC: prefix (was encrypted)",
      example = "true",
      requiredMode = RequiredMode.REQUIRED)
  private boolean isEncrypted;

  @Schema(
      description = "Indicates if the decryption operation succeeded",
      example = "true",
      requiredMode = RequiredMode.REQUIRED)
  private boolean decryptionSuccessful;

  @Schema(
      description = "Extracted key ID from encrypted value (null if not encrypted)",
      example = "1234567890",
      requiredMode = RequiredMode.NOT_REQUIRED)
  private String keyId;

  @Schema(
      description = "Detected format of the input value",
      example = "ENC:keyID:Base64",
      requiredMode = RequiredMode.REQUIRED)
  private String encryptedFormat;

  @Schema(
      description = "Error message if decryption failed (null if successful)",
      example = "Decryption failed: invalid key ID",
      requiredMode = RequiredMode.NOT_REQUIRED)
  private String errorMessage;

  @Schema(
      description = "Indicates if EncryptionService is initialized and available",
      example = "true",
      requiredMode = RequiredMode.REQUIRED)
  private boolean encryptionAvailable;

  public DecryptResponseDto() {}

  public DecryptResponseDto(
      String plaintext,
      boolean isEncrypted,
      boolean decryptionSuccessful,
      String keyId,
      String encryptedFormat,
      String errorMessage,
      boolean encryptionAvailable) {
    this.plaintext = plaintext;
    this.isEncrypted = isEncrypted;
    this.decryptionSuccessful = decryptionSuccessful;
    this.keyId = keyId;
    this.encryptedFormat = encryptedFormat;
    this.errorMessage = errorMessage;
    this.encryptionAvailable = encryptionAvailable;
  }

  public String getPlaintext() {
    return plaintext;
  }

  public void setPlaintext(String plaintext) {
    this.plaintext = plaintext;
  }

  @JsonProperty("isEncrypted")
  public boolean isEncrypted() {
    return isEncrypted;
  }

  @JsonProperty("isEncrypted")
  public void setEncrypted(boolean encrypted) {
    isEncrypted = encrypted;
  }

  public boolean isDecryptionSuccessful() {
    return decryptionSuccessful;
  }

  public void setDecryptionSuccessful(boolean decryptionSuccessful) {
    this.decryptionSuccessful = decryptionSuccessful;
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
