/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: PayloadHelperResponseDto
 * Description: Response DTO for canonical EZKey payload building.
 */

package org.ezkey.crypto.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(description = "Response DTO for canonical EZKey payload building")
public class PayloadHelperResponseDto {

  @Schema(
      description = "Payload type that was built",
      example = "pending",
      requiredMode = RequiredMode.REQUIRED)
  private String type;

  @Schema(
      description = "Canonical UTF-8 payload with NFC normalization applied where required",
      example = "tok_12345|true|Payment Approval|Approve invoice INV-2026-0042",
      requiredMode = RequiredMode.REQUIRED)
  private String payload;

  @Schema(
      description = "Encoding rule applied by the helper",
      example = "UTF-8 + NFC where applicable",
      requiredMode = RequiredMode.REQUIRED)
  private String encoding;

  public PayloadHelperResponseDto(String type, String payload, String encoding) {
    this.type = type;
    this.payload = payload;
    this.encoding = encoding;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public String getEncoding() {
    return encoding;
  }

  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }
}
