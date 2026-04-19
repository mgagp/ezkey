/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.demo.device.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps framework "not found" exceptions to HTTP 404 without logging them as errors (scanner noise).
 */
@RestControllerAdvice
public class QuietNotFoundExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(QuietNotFoundExceptionHandler.class);

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex) {
    LOG.debug("No resource: {}", ex.getResourcePath());
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<Void> handleNoHandlerFound(NoHandlerFoundException ex) {
    LOG.debug("No handler: {} {}", ex.getHttpMethod(), ex.getRequestURL());
    return ResponseEntity.notFound().build();
  }
}
