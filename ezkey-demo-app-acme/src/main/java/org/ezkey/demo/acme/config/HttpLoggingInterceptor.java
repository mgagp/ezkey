/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Interceptor: HttpLoggingInterceptor
 * Description: HTTP request/response logging interceptor for debugging Admin API calls.
 */

package org.ezkey.demo.acme.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * HTTP interceptor that logs complete request and response details for debugging purposes.
 *
 * <p>Logs:
 *
 * <ul>
 *   <li>Request: Method, URI, Headers, Body
 *   <li>Response: Status Code, Headers, Body
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class HttpLoggingInterceptor implements ClientHttpRequestInterceptor {

  private static final Logger logger = LoggerFactory.getLogger(HttpLoggingInterceptor.class);

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

    // Log request
    logRequest(request, body);

    // Execute request and wrap response to buffer body
    ClientHttpResponse response = execution.execute(request, body);
    BufferingClientHttpResponseWrapper wrappedResponse =
        new BufferingClientHttpResponseWrapper(response);

    // Log response (this reads the body, but wrapper allows multiple reads)
    logResponse(wrappedResponse);

    return wrappedResponse;
  }

  /**
   * Logs the HTTP request details.
   *
   * @param request the HTTP request
   * @param body the request body
   */
  private void logRequest(HttpRequest request, byte[] body) {
    logger.info("=== HTTP REQUEST ===");
    logger.info("Method: {}", request.getMethod());
    logger.info("URI: {}", request.getURI());

    // Log headers (mask Authorization header for security)
    logger.info("Headers:");
    request
        .getHeaders()
        .forEach(
            (name, values) -> {
              if ("Authorization".equalsIgnoreCase(name)) {
                logger.info("  {}: Basic ***MASKED***", name);
              } else {
                logger.info("  {}: {}", name, values);
              }
            });

    // Log body
    if (body != null && body.length > 0) {
      String bodyString = new String(body, StandardCharsets.UTF_8);
      logger.info("Body: {}", bodyString);
    } else {
      logger.info("Body: (empty)");
    }
    logger.info("====================");
  }

  /**
   * Logs the HTTP response details.
   *
   * @param response the HTTP response wrapper
   * @throws IOException if reading response body fails
   */
  private void logResponse(BufferingClientHttpResponseWrapper response) throws IOException {
    logger.info("=== HTTP RESPONSE ===");
    logger.info(
        "Status: {} {}", response.getStatusCode().value(), response.getStatusCode().toString());

    // Log headers
    logger.info("Headers:");
    response
        .getHeaders()
        .forEach(
            (name, values) -> {
              logger.info("  {}: {}", name, values);
            });

    // Log body (wrapper allows multiple reads)
    String body = response.getBodyAsString();
    if (body != null && !body.isEmpty()) {
      logger.info("Body: {}", body);
    } else {
      logger.info("Body: (empty)");
    }
    logger.info("====================");
  }
}
