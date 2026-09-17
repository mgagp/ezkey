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
      description =
          "Payload type: pending, respond, respond-result, enrollment-bind,"
              + " enrollment-verify-device, or enrollment-verify-result",
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

  @Schema(
      description = "Enrollment ID for enrollment-bind / enrollment-verify-* payloads",
      example = "42")
  private Integer enrollmentId;

  @Schema(
      description = "Numeric challenge response for enrollment-verify-device payload",
      example = "123456")
  private Integer challengeResponse;

  @Schema(
      description = "Device EC P-256 public key (SPKI Base64) for enrollment-verify-device payload")
  private String devicePublicKey;

  @Schema(description = "Integration Ed25519 public key (Base64URL) for enrollment-bind payload")
  private String integrationPublicKey;

  @Schema(
      description = "Integration key algorithm for enrollment-bind payload",
      example = "ed25519")
  private String integrationKeyAlgorithm;

  @Schema(description = "Integration display name for enrollment-bind payload")
  private String integrationName;

  @Schema(description = "Integration description for enrollment-bind payload")
  private String integrationDescription;

  @Schema(description = "Enrollment display name for enrollment-bind payload")
  private String enrollmentName;

  @Schema(description = "Tenant ID for enrollment-bind payload", example = "1")
  private Integer tenantId;

  @Schema(description = "Tenant display name for enrollment-bind payload")
  private String tenantName;

  @Schema(description = "Tenant description for enrollment-bind payload")
  private String tenantDescription;

  @Schema(
      description =
          "Whether the enrollment is admin MFA on the system integration (enrollment-bind payload)",
      example = "false")
  private Boolean isSystemIntegration;

  @Schema(
      description = "Administrator type for enrollment-bind payload (GLOBAL_ADMIN or TENANT_ADMIN)",
      example = "TENANT_ADMIN")
  private String adminType;

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

  public Integer getEnrollmentId() {
    return enrollmentId;
  }

  public void setEnrollmentId(Integer enrollmentId) {
    this.enrollmentId = enrollmentId;
  }

  public Integer getChallengeResponse() {
    return challengeResponse;
  }

  public void setChallengeResponse(Integer challengeResponse) {
    this.challengeResponse = challengeResponse;
  }

  public String getDevicePublicKey() {
    return devicePublicKey;
  }

  public void setDevicePublicKey(String devicePublicKey) {
    this.devicePublicKey = devicePublicKey;
  }

  public String getIntegrationPublicKey() {
    return integrationPublicKey;
  }

  public void setIntegrationPublicKey(String integrationPublicKey) {
    this.integrationPublicKey = integrationPublicKey;
  }

  public String getIntegrationKeyAlgorithm() {
    return integrationKeyAlgorithm;
  }

  public void setIntegrationKeyAlgorithm(String integrationKeyAlgorithm) {
    this.integrationKeyAlgorithm = integrationKeyAlgorithm;
  }

  public String getIntegrationName() {
    return integrationName;
  }

  public void setIntegrationName(String integrationName) {
    this.integrationName = integrationName;
  }

  public String getIntegrationDescription() {
    return integrationDescription;
  }

  public void setIntegrationDescription(String integrationDescription) {
    this.integrationDescription = integrationDescription;
  }

  public String getEnrollmentName() {
    return enrollmentName;
  }

  public void setEnrollmentName(String enrollmentName) {
    this.enrollmentName = enrollmentName;
  }

  public Integer getTenantId() {
    return tenantId;
  }

  public void setTenantId(Integer tenantId) {
    this.tenantId = tenantId;
  }

  public String getTenantName() {
    return tenantName;
  }

  public void setTenantName(String tenantName) {
    this.tenantName = tenantName;
  }

  public String getTenantDescription() {
    return tenantDescription;
  }

  public void setTenantDescription(String tenantDescription) {
    this.tenantDescription = tenantDescription;
  }

  public Boolean getIsSystemIntegration() {
    return isSystemIntegration;
  }

  public void setIsSystemIntegration(Boolean isSystemIntegration) {
    this.isSystemIntegration = isSystemIntegration;
  }

  public String getAdminType() {
    return adminType;
  }

  public void setAdminType(String adminType) {
    this.adminType = adminType;
  }
}
