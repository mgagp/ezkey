/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: CachedBodyHttpServletRequest
 * Description: Replayable request wrapper for filters that must inspect the JSON body.
 */

package org.ezkey.admin.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * {@link HttpServletRequest} wrapper that caches the request body so a filter can inspect JSON
 * (e.g. {@code authAttemptId}) and the controller can still deserialize {@code @RequestBody}.
 *
 * @since 2026
 */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

  private final byte[] cachedBody;

  /**
   * Creates a replayable wrapper around an already-read request body.
   *
   * @param request original request
   * @param cachedBody body bytes already read from the original stream
   */
  CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
    super(request);
    this.cachedBody = cachedBody != null ? cachedBody : new byte[0];
  }

  /**
   * Returns the cached body bytes (never null).
   *
   * @return cached body
   */
  byte[] getCachedBody() {
    return cachedBody;
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream input = new ByteArrayInputStream(cachedBody);
    return new ServletInputStream() {
      @Override
      public boolean isFinished() {
        return input.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener readListener) {
        // no-op: non-async replay stream
      }

      @Override
      public int read() {
        return input.read();
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    Charset charset = StandardCharsets.UTF_8;
    String encoding = getCharacterEncoding();
    if (encoding != null && !encoding.isBlank() && Charset.isSupported(encoding)) {
      charset = Charset.forName(encoding);
    }
    return new BufferedReader(new InputStreamReader(getInputStream(), charset));
  }
}
