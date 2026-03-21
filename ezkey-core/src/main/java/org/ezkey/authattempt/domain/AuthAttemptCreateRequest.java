/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: AuthAttemptCreateRequest
 * Description: Domain request object for creating authentication attempts.
 */

package org.ezkey.authattempt.domain;

/**
 * Domain request object for creating authentication attempts.
 *
 * <p>This domain object represents the request data used internally by the service layer to create
 * new authentication attempts. It contains the enrollment information, challenge requirements, and
 * optional contextual fields that transform a binary approve/deny MFA event into a rich
 * business-context request visible on the mobile device.
 *
 * <p><b>Contextual Authentication (Phase 1):</b> The optional context fields ({@code contextTitle},
 * {@code contextMessage}) allow integrating applications to attach human-readable context to an
 * auth attempt so the approver sees exactly what they are approving on their mobile device.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.service.AuthAttemptService
 * @see org.ezkey.authattempt.domain.AuthAttemptCreateResponse
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
public class AuthAttemptCreateRequest {

  /**
   * The enrollment ID for which to create the authentication attempt.
   *
   * <p>Must reference an existing and active enrollment. This links the authentication request to a
   * specific user's enrolled device, ensuring that authentication notifications are sent to the
   * correct mobile device.
   */
  private Integer enrollmentId;

  /**
   * Flag indicating whether additional challenge validation is requested.
   *
   * <p>When true, the authentication flow will require the user to provide additional verification
   * (such as a numeric code) beyond the standard cryptographic signature.
   */
  private Boolean challengeRequested;

  /**
   * Optional short title displayed as the card header on the mobile device (max 200 characters).
   * Example: "Payment Approval", "Deploy Confirmation".
   */
  private String contextTitle;

  /**
   * Optional descriptive message providing the approver with full context (max 2 000 characters).
   * Example: "Authorize payment batch #1497 to Acme Corp for $1,400".
   */
  private String contextMessage;

  /**
   * When true, persists {@code demo_mitm_signature_enabled} on the attempt. Tampering applies only
   * if Auth API {@code ezkey.demo.mitm-signature-enabled} is also true.
   */
  private Boolean demoMitmSignatureRequested;

  /**
   * Gets the enrollment ID for this authentication attempt.
   *
   * @return the enrollment ID
   */
  public Integer getEnrollmentId() {
    return enrollmentId;
  }

  /**
   * Sets the enrollment ID for this authentication attempt.
   *
   * @param enrollmentId the enrollment ID to set
   */
  public void setEnrollmentId(Integer enrollmentId) {
    this.enrollmentId = enrollmentId;
  }

  /**
   * Gets whether additional challenge validation is requested.
   *
   * @return true if challenge is requested, false otherwise
   */
  public Boolean getChallengeRequested() {
    return challengeRequested;
  }

  /**
   * Sets whether additional challenge validation is requested.
   *
   * @param challengeRequested true to request challenge validation, false otherwise
   */
  public void setChallengeRequested(Boolean challengeRequested) {
    this.challengeRequested = challengeRequested;
  }

  /**
   * Gets the optional short context title.
   *
   * @return the context title, or null if not provided
   */
  public String getContextTitle() {
    return contextTitle;
  }

  /**
   * Sets the optional short context title.
   *
   * @param contextTitle the context title to set
   */
  public void setContextTitle(String contextTitle) {
    this.contextTitle = contextTitle;
  }

  /**
   * Gets the optional descriptive context message.
   *
   * @return the context message, or null if not provided
   */
  public String getContextMessage() {
    return contextMessage;
  }

  /**
   * Sets the optional descriptive context message.
   *
   * @param contextMessage the context message to set
   */
  public void setContextMessage(String contextMessage) {
    this.contextMessage = contextMessage;
  }

  /**
   * Whether the client requested demo MITM simulation for this attempt (stored on the row).
   *
   * @return true when the operator requested demo tampering for this attempt
   */
  public Boolean getDemoMitmSignatureRequested() {
    return demoMitmSignatureRequested;
  }

  /**
   * Sets whether demo MITM simulation is requested for this attempt.
   *
   * @param demoMitmSignatureRequested true to flag the attempt for tampered Pending in demo mode
   */
  public void setDemoMitmSignatureRequested(Boolean demoMitmSignatureRequested) {
    this.demoMitmSignatureRequested = demoMitmSignatureRequested;
  }
}
