/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Filter: TrustedProxyClientIpFilter
 *
 * Description: Resolves client IP using trusted-proxy list and sets it as request attribute for audit.
 */

package org.ezkey.admin.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.ezkey.admin.config.TrustedProxyProperties;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.audit.util.ClientIpResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Filter that resolves client IP using the trusted-proxy CIDR list and sets it as a request
 * attribute. Controllers that use {@link ClientContext#from(HttpServletRequest)} will then get the
 * same resolved IP without each controller needing the trusted-proxy configuration.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrustedProxyClientIpFilter implements Filter {

  private final TrustedProxyProperties trustedProxyProperties;

  /**
   * Constructs the filter with trusted proxy configuration.
   *
   * @param trustedProxyProperties the trusted proxy CIDR list (never null)
   */
  public TrustedProxyClientIpFilter(TrustedProxyProperties trustedProxyProperties) {
    this.trustedProxyProperties = trustedProxyProperties;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (request instanceof HttpServletRequest httpRequest) {
      String clientIp = ClientIpResolver.resolve(httpRequest, trustedProxyProperties.getCidrs());
      httpRequest.setAttribute(ClientContext.CLIENT_IP_REQUEST_ATTRIBUTE, clientIp);
    }
    chain.doFilter(request, response);
  }
}
