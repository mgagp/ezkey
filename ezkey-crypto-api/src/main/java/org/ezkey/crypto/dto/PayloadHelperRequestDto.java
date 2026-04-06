/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: PayloadHelperRequestDto
 * Description: Request DTO for building canonical EZKey payloads.
 */

package org.ezkey.crypto.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request DTO for building canonical EZKey payloads")
public class PayloadHelperRequestDto {

  @Schema(
      description = "Payload type: pending, respond, or respond-result",
      example = "pending",
      requiredMode = RequiredMode.REQUIRED)
  @NotBlank(message = "Payload type cannot be blank")
  private String type;

  @Schema(
      description = "Proof token segment used by all EZKey canonical payloads",
      example = "tok_12345",
      requiredMode = RequiredMode.REQUIRED)
  @NotBlank(message = "Proof token cannot be blank")
  private String proofToken;

  @Schema(description = "Whether a challenge is required for the pending payload", example = "true")
  private Boolean challengeRequired;

  @Schema(
      description = "Optional context title for the pending payload",
      example = "Payment Approval")
  private String contextTitle;

  @Schema(
      description = "Optional context message for the pending payload",
      example = "Approve invoice INV-2026-0042")
  private String contextMessage;

  @Schema(description = "Whether the device accepted the authentication request", example = "true")
  private Boolean accepted;

  @Schema(
      description = "Authentication attempt identifier for the respond-result payload",
      example = "42")
  private Integer authAttemptId;

  @Schema(
      description = "Authentication result for the respond-result payload",
      example = "APPROVED")
  private String result;

  @Schema(
      description = "Optional message for the respond-result payload",
      example = "Auth attempt completed")
  private String message;

  public PayloadHelperRequestDto() {}

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getProofToken() {
    return proofToken;
  }

  public void setProofToken(String proofToken) {
    this.proofToken = proofToken;
  }

  public Boolean getChallengeRequired() {
    return challengeRequired;
  }

  public void setChallengeRequired(Boolean challengeRequired) {
    this.challengeRequired = challengeRequired;
  }

  public String getContextTitle() {
    return contextTitle;
  }

  public void setContextTitle(String contextTitle) {
    this.contextTitle = contextTitle;
  }

  public String getContextMessage() {
    return contextMessage;
  }

  public void setContextMessage(String contextMessage) {
    this.contextMessage = contextMessage;
  }

  public Boolean getAccepted() {
    return accepted;
  }

  public void setAccepted(Boolean accepted) {
    this.accepted = accepted;
  }

  public Integer getAuthAttemptId() {
    return authAttemptId;
  }

  public void setAuthAttemptId(Integer authAttemptId) {
    this.authAttemptId = authAttemptId;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
