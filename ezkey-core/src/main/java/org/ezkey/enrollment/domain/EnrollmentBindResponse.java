/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: EnrollmentBindResponse
 * Description: Domain response object containing enrollment binding and integration metadata.
 */

package org.ezkey.enrollment.domain;

/**
 * Domain response object containing enrollment binding and integration metadata.
 *
 * <p>This domain object represents the response data returned by the service layer when devices
 * request enrollment binding information. It contains cryptographic keys, proof tokens, and
 * integration metadata necessary for the device to complete the enrollment verification process
 * with proper context and branding.
 *
 * <p><b>Usage Context:</b> Returned by the EnrollmentService when processing enrollment binding
 * requests. The service layer creates this response object to provide comprehensive enrollment and
 * integration information for consumption by API layers and client applications.
 *
 * <p><b>Enrollment Flow:</b> This response bridges the enrollment creation and verification phases
 * by providing the device with necessary cryptographic material and user-friendly integration
 * information for display during the enrollment process.
 *
 * <p><b>Cryptographic Flow:</b> Contains the integration's public key and enrollment proof token
 * that the mobile device must validate and respond to with its own generated keys and cryptographic
 * signature to complete the secure enrollment binding process.
 *
 * <p><b>Integration Metadata:</b> Includes name and description to provide user-facing context
 * during enrollment. Values come from the integration record stored in the database (single display
 * name and description per integration; no bind-time locale selection).
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.service.EnrollmentService
 * @see org.ezkey.enrollment.domain.EnrollmentBindRequest
 * @see org.ezkey.enrollment.domain.EnrollmentVerifyRequest
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 */
public class EnrollmentBindResponse {
  /**
   * The enrollment ID for this binding operation.
   *
   * <p>Confirms the enrollment ID that was successfully bound to the mobile device. Used for
   * reference in subsequent verification requests and provides confirmation that the binding
   * request was processed for the correct enrollment.
   */
  private Integer enrollmentId;

  /**
   * The integration's public key for cryptographic operations.
   *
   * <p>Contains the public key of the integration that created this enrollment. Used by the mobile
   * device to verify signatures and validate integration authenticity during the enrollment
   * process. This key enables the device to cryptographically verify that enrollment challenges
   * originate from the legitimate integration.
   */
  private String integrationPublicKey;

  /**
   * Algorithm for {@link #integrationPublicKey} material (e.g. {@code ed25519} for raw 32-byte
   * keys, Base64URL without padding).
   */
  private String integrationKeyAlgorithm;

  /**
   * The enrollment proof token that needs to be signed by the device.
   *
   * <p>Contains the challenge data that the mobile device must sign with its private key to
   * complete enrollment verification. This token proves that the device possesses the cryptographic
   * keys it claims to have and establishes the secure binding between device and enrollment.
   */
  private String enrollmentProofToken;

  /**
   * The display name of the integration.
   *
   * <p>Human-readable name from the integration record, shown to the user during enrollment to help
   * identify the service or application being enrolled.
   */
  private String integrationName;

  /**
   * The description of the integration.
   *
   * <p>Additional context from the integration record, such as purpose or features, to assist the
   * user during the enrollment process.
   */
  private String integrationDescription;

  /**
   * The human-readable name for the enrollment.
   *
   * <p>Shown on the authenticator as the person (or device) hero. Admin MFA names are person-first
   * ({@code first last}); Global/Tenant Admin is Admin UI chrome. If two enrollments on one phone
   * cannot be told apart, add a dedicated bind field rather than encoding role here — see {@code
   * I-2026-09-15-mobile-admin-enrollment-account-label}.
   */
  private String enrollmentName;

  /**
   * The tenant ID of the integration associated with this enrollment.
   *
   * <p>This value is a stable technical identifier and is recommended as the primary grouping key
   * on clients that display enrollments grouped by tenant. It is derived from the integration's
   * tenant relationship.
   */
  private Integer tenantId;

  /**
   * The tenant display name of the integration associated with this enrollment.
   *
   * <p>This value is intended for UI display and may change over time. Clients should prefer {@link
   * #tenantId} as the grouping key when possible.
   */
  private String tenantName;

  /**
   * The tenant description of the integration associated with this enrollment.
   *
   * <p>This value is optional and is intended for UI display.
   */
  private String tenantDescription;

  /**
   * Whether this enrollment belongs to the system integration (administrator MFA).
   *
   * <p>Clients use this to show localized Administration as Purpose and a Role line. Included in
   * the signed bind payload.
   */
  private Boolean isSystemIntegration;

  /**
   * Linked administrator type when this enrollment is admin MFA.
   *
   * <p>Literal {@code GLOBAL_ADMIN} or {@code TENANT_ADMIN}. Absent for regular enrollments. Shown
   * as a localized Role line — never encoded in {@code enrollmentName}.
   */
  private String adminType;

  /**
   * Ed25519 signature (Base64URL, raw 64 bytes) over the canonical bind payload (see {@code
   * docs/ENROLLMENT_SIGNATURE_PAYLOAD.md}).
   */
  private String enrollmentBindPayloadSignedByIntegration;

  /**
   * Gets the enrollment ID.
   *
   * @return the enrollment ID
   */
  public Integer getEnrollmentId() {
    return enrollmentId;
  }

  /**
   * Sets the enrollment ID.
   *
   * @param enrollmentId the enrollment ID to set
   */
  public void setEnrollmentId(Integer enrollmentId) {
    this.enrollmentId = enrollmentId;
  }

  /**
   * Gets the integration's public key for cryptographic operations.
   *
   * @return the integration's public key
   */
  public String getIntegrationPublicKey() {
    return integrationPublicKey;
  }

  /**
   * Sets the integration's public key for cryptographic operations.
   *
   * @param integrationPublicKey the integration's public key to set
   */
  public void setIntegrationPublicKey(String integrationPublicKey) {
    this.integrationPublicKey = integrationPublicKey;
  }

  /**
   * Gets the integration key algorithm identifier.
   *
   * @return algorithm name (e.g. ed25519)
   */
  public String getIntegrationKeyAlgorithm() {
    return integrationKeyAlgorithm;
  }

  /**
   * Sets the integration key algorithm identifier.
   *
   * @param integrationKeyAlgorithm the algorithm name
   */
  public void setIntegrationKeyAlgorithm(String integrationKeyAlgorithm) {
    this.integrationKeyAlgorithm = integrationKeyAlgorithm;
  }

  /**
   * Gets the enrollment proof token that needs to be signed by the device.
   *
   * @return the enrollment proof token
   */
  public String getEnrollmentProofToken() {
    return enrollmentProofToken;
  }

  /**
   * Sets the enrollment proof token that needs to be signed by the device.
   *
   * @param enrollmentProofToken the enrollment proof token to set
   */
  public void setEnrollmentProofToken(String enrollmentProofToken) {
    this.enrollmentProofToken = enrollmentProofToken;
  }

  /**
   * Gets the integration name.
   *
   * @return the integration name
   */
  public String getIntegrationName() {
    return integrationName;
  }

  /**
   * Sets the integration name.
   *
   * @param integrationName the integration name to set
   */
  public void setIntegrationName(String integrationName) {
    this.integrationName = integrationName;
  }

  /**
   * Gets the integration description.
   *
   * @return the integration description
   */
  public String getIntegrationDescription() {
    return integrationDescription;
  }

  /**
   * Sets the integration description.
   *
   * @param integrationDescription the integration description to set
   */
  public void setIntegrationDescription(String integrationDescription) {
    this.integrationDescription = integrationDescription;
  }

  /**
   * Gets the enrollment name.
   *
   * @return the enrollment name
   */
  public String getEnrollmentName() {
    return enrollmentName;
  }

  /**
   * Sets the enrollment name.
   *
   * @param enrollmentName the enrollment name to set
   */
  public void setEnrollmentName(String enrollmentName) {
    this.enrollmentName = enrollmentName;
  }

  /**
   * Gets the tenant ID.
   *
   * @return the tenant ID (may be null)
   */
  public Integer getTenantId() {
    return tenantId;
  }

  /**
   * Sets the tenant ID.
   *
   * @param tenantId the tenant ID to set
   */
  public void setTenantId(Integer tenantId) {
    this.tenantId = tenantId;
  }

  /**
   * Gets the tenant name.
   *
   * @return the tenant name (may be null)
   */
  public String getTenantName() {
    return tenantName;
  }

  /**
   * Sets the tenant name.
   *
   * @param tenantName the tenant name to set
   */
  public void setTenantName(String tenantName) {
    this.tenantName = tenantName;
  }

  /**
   * Gets the tenant description.
   *
   * @return the tenant description (may be null)
   */
  public String getTenantDescription() {
    return tenantDescription;
  }

  /**
   * Sets the tenant description.
   *
   * @param tenantDescription the tenant description to set
   */
  public void setTenantDescription(String tenantDescription) {
    this.tenantDescription = tenantDescription;
  }

  /**
   * Gets whether the enrollment belongs to the system integration.
   *
   * @return {@code true} for admin MFA; {@code false} or {@code null} otherwise
   */
  public Boolean getIsSystemIntegration() {
    return isSystemIntegration;
  }

  /**
   * Sets whether the enrollment belongs to the system integration.
   *
   * @param isSystemIntegration {@code true} for admin MFA
   */
  public void setIsSystemIntegration(Boolean isSystemIntegration) {
    this.isSystemIntegration = isSystemIntegration;
  }

  /**
   * Gets the linked administrator type for admin MFA.
   *
   * @return {@code GLOBAL_ADMIN}, {@code TENANT_ADMIN}, or {@code null}
   */
  public String getAdminType() {
    return adminType;
  }

  /**
   * Sets the linked administrator type for admin MFA.
   *
   * @param adminType {@code GLOBAL_ADMIN}, {@code TENANT_ADMIN}, or {@code null}
   */
  public void setAdminType(String adminType) {
    this.adminType = adminType;
  }

  /**
   * Gets the integration signature over the bind response payload.
   *
   * @return Base64URL Ed25519 signature
   */
  public String getEnrollmentBindPayloadSignedByIntegration() {
    return enrollmentBindPayloadSignedByIntegration;
  }

  /**
   * Sets the integration signature over the bind response payload.
   *
   * @param enrollmentBindPayloadSignedByIntegration signed canonical bind payload
   */
  public void setEnrollmentBindPayloadSignedByIntegration(
      String enrollmentBindPayloadSignedByIntegration) {
    this.enrollmentBindPayloadSignedByIntegration = enrollmentBindPayloadSignedByIntegration;
  }
}
