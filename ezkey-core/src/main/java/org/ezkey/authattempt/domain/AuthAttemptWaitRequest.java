/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: AuthAttemptWaitRequest
 * Description: Domain object for authentication wait request parameters.
 */

package org.ezkey.authattempt.domain;

/**
 * Domain object for authentication wait request parameters.
 *
 * <p>This domain object represents the request parameters for the authentication wait operation
 * that allows applications to poll for authentication completion. It provides configurable timeout
 * and polling interval parameters for flexible waiting behavior.
 *
 * <p><b>Usage Context:</b> Used by the service layer to handle authentication wait operations. This
 * domain object is mapped from the corresponding DTO and contains the business logic parameters for
 * polling configuration.
 *
 * <p><b>Parameters:</b>
 *
 * <ul>
 *   <li><b>timeout:</b> Maximum duration to wait for authentication completion (seconds)
 *   <li><b>polling:</b> Interval between status checks during waiting (seconds)
 * </ul>
 *
 * <p><b>Validation Rules:</b>
 *
 * <ul>
 *   <li>timeout must be between 1 and 300 seconds
 *   <li>polling must be between 1 and 60 seconds
 *   <li>polling must be less than or equal to timeout
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.dto.AuthAttemptWaitRequestDto
 */
public class AuthAttemptWaitRequest {

  /**
   * Maximum duration to wait for authentication completion in seconds. Must be between 1 and 300
   * seconds. Controls how long the service will wait before returning a timeout response.
   */
  private Integer timeout;

  /**
   * Interval between status checks during waiting in seconds. Must be between 1 and 60 seconds, and
   * less than or equal to timeout. Controls how frequently the system checks for authentication
   * completion.
   */
  private Integer polling;

  /**
   * Default constructor for AuthAttemptWaitRequest. Initializes with default values: timeout=30,
   * polling=2.
   */
  public AuthAttemptWaitRequest() {
    this.timeout = 30;
    this.polling = 2;
  }

  /**
   * Constructor with custom timeout and polling values.
   *
   * @param timeout maximum wait duration in seconds
   * @param polling polling interval in seconds
   */
  public AuthAttemptWaitRequest(Integer timeout, Integer polling) {
    this.timeout = timeout;
    this.polling = polling;
  }

  /**
   * Gets the maximum wait duration in seconds.
   *
   * @return the timeout value in seconds
   */
  public Integer getTimeout() {
    return timeout;
  }

  /**
   * Sets the maximum wait duration in seconds.
   *
   * @param timeout the timeout value in seconds to set
   */
  public void setTimeout(Integer timeout) {
    this.timeout = timeout;
  }

  /**
   * Gets the polling interval in seconds.
   *
   * @return the polling interval in seconds
   */
  public Integer getPolling() {
    return polling;
  }

  /**
   * Sets the polling interval in seconds.
   *
   * @param polling the polling interval in seconds to set
   */
  public void setPolling(Integer polling) {
    this.polling = polling;
  }
}
