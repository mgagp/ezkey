/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Wrapper: BufferingClientHttpResponseWrapper
 * Description: Wraps ClientHttpResponse to buffer response body for multiple reads.
 */

package org.ezkey.demo.acme.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Wraps a ClientHttpResponse to buffer the response body, allowing it to be read multiple times.
 *
 * <p>This is necessary because HTTP response streams can typically only be read once. By buffering
 * the body, we can log it and still allow the original caller to read it.
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class BufferingClientHttpResponseWrapper implements ClientHttpResponse {

  private final ClientHttpResponse response;
  private byte[] body;

  public BufferingClientHttpResponseWrapper(ClientHttpResponse response) {
    this.response = response;
  }

  @Override
  public HttpStatusCode getStatusCode() throws IOException {
    return response.getStatusCode();
  }

  @Override
  public String getStatusText() throws IOException {
    return response.getStatusText();
  }

  @Override
  public void close() {
    response.close();
  }

  @Override
  public InputStream getBody() throws IOException {
    if (body == null) {
      // Read and buffer the body
      InputStream inputStream = response.getBody();
      if (inputStream != null) {
        body = inputStream.readAllBytes();
      } else {
        body = new byte[0];
      }
    }
    return new ByteArrayInputStream(body);
  }

  @Override
  public HttpHeaders getHeaders() {
    return response.getHeaders();
  }

  /**
   * Gets the buffered response body as a string.
   *
   * @return the response body as a string, or empty string if body is empty or error occurs
   */
  public String getBodyAsString() {
    if (body == null) {
      try {
        InputStream inputStream = response.getBody();
        if (inputStream != null) {
          body = inputStream.readAllBytes();
        } else {
          body = new byte[0];
        }
      } catch (IOException e) {
        return "";
      }
    }
    if (body.length == 0) {
      return "";
    }
    return new String(body, java.nio.charset.StandardCharsets.UTF_8);
  }
}
