/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: CachedBodyHttpServletRequestWrapper
 *
 * Description: Wraps an HttpServletRequest to cache the request body for multiple reads.
 */

package org.ezkey.auth.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.util.StreamUtils;

/**
 * Wraps an {@link HttpServletRequest} so the request body can be read once, cached, and re-read by
 * downstream filters or the controller.
 *
 * <p>Used by {@link RateLimitFilter} when rate limiting the respond and pending endpoints: the
 * filter must read the JSON body to extract {@code authAttemptId} (respond) or {@code enrollmentId}
 * (pending) for the bucket key, then pass the request to the controller which reads the body again.
 * This wrapper ensures the body is only consumed once from the underlying stream.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class CachedBodyHttpServletRequestWrapper extends HttpServletRequestWrapper {

  private byte[] cachedContent;

  /**
   * Creates a wrapper for the given request. The body is not read until {@link
   * #getContentAsByteArray()}, {@link #getInputStream()}, or {@link #getReader()} is called.
   *
   * @param request the request to wrap
   */
  public CachedBodyHttpServletRequestWrapper(HttpServletRequest request) {
    super(request);
  }

  /**
   * Returns the request body as a byte array. On first call, reads from the underlying request
   * input stream and caches the result. Subsequent calls return the cached content.
   *
   * @return the request body bytes, or an empty array if the body was empty or could not be read
   */
  public byte[] getContentAsByteArray() throws IOException {
    if (cachedContent == null) {
      cachedContent = StreamUtils.copyToByteArray(super.getInputStream());
    }
    return cachedContent;
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    return new CachedBodyServletInputStream(getContentAsByteArray());
  }

  @Override
  public BufferedReader getReader() throws IOException {
    return new BufferedReader(
        new InputStreamReader(
            new ByteArrayInputStream(getContentAsByteArray()), StandardCharsets.UTF_8));
  }

  /** ServletInputStream that reads from a cached byte array. */
  private static final class CachedBodyServletInputStream extends ServletInputStream {

    private final ByteArrayInputStream inputStream;

    CachedBodyServletInputStream(byte[] content) {
      this.inputStream = new ByteArrayInputStream(content);
    }

    @Override
    public boolean isFinished() {
      return inputStream.available() == 0;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public int read() {
      return inputStream.read();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      throw new UnsupportedOperationException("ReadListener not supported");
    }
  }
}
