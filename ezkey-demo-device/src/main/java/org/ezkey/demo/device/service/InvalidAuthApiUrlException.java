package org.ezkey.demo.device.service;

/**
 * Thrown when an enrollment QR or form supplies a non-blank Auth API URL that fails validation.
 *
 * @since 2025
 */
public class InvalidAuthApiUrlException extends RuntimeException {

  /**
   * Creates the exception with a user-facing message.
   *
   * @param message clear error for the operator
   */
  public InvalidAuthApiUrlException(String message) {
    super(message);
  }
}
