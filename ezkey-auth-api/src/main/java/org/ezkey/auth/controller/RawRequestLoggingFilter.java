package org.ezkey.auth.controller;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Filter to log the raw body of POST requests to /api/v1/auth-attempts/pending for debugging.
 *
 * <p>This filter is for development/debug use only. It reads and logs the raw request body before
 * Spring attempts to deserialize it. Do not use in production as it consumes the request stream.
 */
@Component
public class RawRequestLoggingFilter implements Filter {

  private static final Logger logger = LoggerFactory.getLogger(RawRequestLoggingFilter.class);

  private static boolean enabled = false; // Enable/disable this filter as needed

  public static boolean isEnabled() {
    return enabled;
  }

  public static void setEnabled(boolean enabled) {
    RawRequestLoggingFilter.enabled = enabled;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    // Only log if the filter is enabled
    if (!isEnabled()) {
      chain.doFilter(request, response);
      return;
    }
    if (request instanceof HttpServletRequest req) {
      if ("POST".equalsIgnoreCase(req.getMethod())
          && req.getRequestURI().contains("/api/v1/auth-attempts/pending")) {
        StringBuilder body = new StringBuilder();
        String line;
        try (BufferedReader reader = req.getReader()) {
          while ((line = reader.readLine()) != null) {
            body.append(line);
          }
        }
        logger.warn(
            "[RAW REQUEST BODY] {} {} : {}", req.getMethod(), req.getRequestURI(), body.toString());
      }
    }
    chain.doFilter(request, response);
  }
}
