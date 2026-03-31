/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import java.net.URI;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Builds RFC 9457 {@link ProblemDetail} responses for the Auth API with a stable extension property
 * {@code timestamp} and request path (for operators).
 */
public abstract class AuthExceptionHandlerBase {

  /**
   * Builds a ProblemDetail with safe client-facing strings only.
   *
   * @param status HTTP status
   * @param typeUri RFC 9457 {@code type} URI
   * @param title short stable title
   * @param safeDetail safe description (no identifiers or secrets)
   * @param path request path (may be null)
   * @return response entity with {@code application/problem+json} body
   */
  protected ResponseEntity<ProblemDetail> problemResponse(
      HttpStatus status, String typeUri, String title, String safeDetail, String path) {

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, safeDetail);
    problem.setType(URI.create(typeUri));
    problem.setTitle(title);
    if (path != null) {
      problem.setProperty("path", path);
    }
    problem.setProperty("timestamp", OffsetDateTime.now().toString());
    return ResponseEntity.status(status).body(problem);
  }
}
