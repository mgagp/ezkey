/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

/**
 * Stable RFC 9457 {@code type} URIs and safe client-facing {@code title} / {@code detail} strings
 * for the Auth API. Exception messages from the Java stack must never be copied to clients verbatim
 * when they may leak identifiers or security-sensitive hints.
 */
public final class AuthApiProblemCatalog {

  /** Base URI for Auth API problem types. */
  public static final String BASE = "https://ezkey.io/problems/auth";

  public static final String TYPE_RESOURCE_NOT_FOUND = BASE + "/resource-not-found";
  public static final String TYPE_ENROLLMENT_ALREADY_BOUND = BASE + "/enrollment-already-bound";
  public static final String TYPE_ENROLLMENT_BINDING_FAILED = BASE + "/enrollment-binding-failed";
  public static final String TYPE_ENROLLMENT_INVITATION_EXPIRED =
      BASE + "/enrollment-invitation-expired";
  public static final String TYPE_ENROLLMENT_NOT_AVAILABLE = BASE + "/enrollment-not-available";
  public static final String TYPE_ENROLLMENT_INTEGRATION_NOT_FOUND =
      BASE + "/enrollment-integration-not-found";
  public static final String TYPE_ENROLLMENT_VERIFY_FAILED = BASE + "/enrollment-verify-failed";
  public static final String TYPE_ENROLLMENT_STATE_CONFLICT = BASE + "/enrollment-state-conflict";
  public static final String TYPE_AUTH_ATTEMPT_BINDING_FAILED =
      BASE + "/auth-attempt-binding-failed";
  public static final String TYPE_AUTH_ATTEMPT_STATE_CONFLICT =
      BASE + "/auth-attempt-state-conflict";
  public static final String TYPE_AUTH_ATTEMPT_RESPOND_FAILED =
      BASE + "/auth-attempt-respond-failed";
  public static final String TYPE_VALIDATION_FAILED = BASE + "/validation-failed";
  public static final String TYPE_INVALID_REQUEST_BODY = BASE + "/invalid-request-body";
  public static final String TYPE_INTERNAL_ERROR = BASE + "/internal-error";

  public static final String TITLE_RESOURCE_NOT_FOUND = "Resource not found";
  public static final String TITLE_CONFLICT = "Request cannot be completed";
  public static final String TITLE_BAD_REQUEST = "Request not acceptable";
  public static final String TITLE_VALIDATION_FAILED = "Validation failed";
  public static final String TITLE_INTERNAL_ERROR = "Internal error";

  /** Safe detail when the resource type must not be disclosed. */
  public static final String DETAIL_RESOURCE_NOT_FOUND =
      "The requested resource could not be found.";

  public static final String DETAIL_ENROLLMENT_ALREADY_BOUND =
      "This enrollment is no longer available for binding.";
  public static final String DETAIL_ENROLLMENT_BINDING_FAILED =
      "Enrollment could not be completed. Verify the invitation and try again.";
  public static final String DETAIL_ENROLLMENT_INVITATION_EXPIRED =
      "This enrollment invitation is no longer valid.";
  public static final String DETAIL_ENROLLMENT_NOT_AVAILABLE =
      "Enrollment could not be completed. The invitation may already have been used.";
  public static final String DETAIL_ENROLLMENT_INTEGRATION_NOT_FOUND =
      "Enrollment could not be completed due to a configuration issue.";
  public static final String DETAIL_ENROLLMENT_VERIFY_FAILED =
      "Verification could not be completed. Check the request and try again.";
  public static final String DETAIL_ENROLLMENT_STATE_CONFLICT =
      "Enrollment is in a state that does not allow this operation.";
  public static final String DETAIL_AUTH_ATTEMPT_FAILED =
      "The authentication request could not be processed.";
  public static final String DETAIL_AUTH_ATTEMPT_STATE_CONFLICT =
      "The authentication request is in a state that does not allow this operation.";
  public static final String DETAIL_AUTH_ATTEMPT_RESPOND_FAILED =
      "The response could not be processed.";
  public static final String DETAIL_VALIDATION_FAILED =
      "One or more fields are invalid or missing.";
  public static final String DETAIL_INVALID_JSON = "The request body could not be read.";
  public static final String DETAIL_INTERNAL = "An unexpected error occurred. Please try again.";

  private AuthApiProblemCatalog() {}
}
