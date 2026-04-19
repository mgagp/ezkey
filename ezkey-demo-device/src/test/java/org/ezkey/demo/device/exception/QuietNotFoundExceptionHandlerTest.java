/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.demo.device.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@DisplayName("QuietNotFoundExceptionHandler")
class QuietNotFoundExceptionHandlerTest {

  private final QuietNotFoundExceptionHandler handler = new QuietNotFoundExceptionHandler();

  @Test
  @DisplayName("NoResourceFoundException returns 404")
  void noResourceFound_returns404() {
    NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/x", "/x");
    ResponseEntity<Void> response = handler.handleNoResourceFound(ex);
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }

  @Test
  @DisplayName("NoHandlerFoundException returns 404")
  void noHandlerFound_returns404() {
    NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/y", HttpHeaders.EMPTY);
    ResponseEntity<Void> response = handler.handleNoHandlerFound(ex);
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }
}
