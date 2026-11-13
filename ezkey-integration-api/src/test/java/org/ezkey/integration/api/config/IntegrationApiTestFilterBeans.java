/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test support: pass-through ApiKeyAuthenticationFilter for {@code @WebMvcTest} with
 * IntegrationApiSecurityConfig.
 */

package org.ezkey.integration.api.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.ezkey.integration.api.security.ApiKeyAuthenticationFilter;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Supplies a pass-through mock for {@link ApiKeyAuthenticationFilter} in slice tests.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Configuration
public class IntegrationApiTestFilterBeans {

  /**
   * Pass-through API key filter (does not authenticate).
   *
   * @return mock filter that always continues the chain
   */
  @Bean
  @Primary
  ApiKeyAuthenticationFilter apiKeyAuthenticationFilter() {
    ApiKeyAuthenticationFilter filter = mock(ApiKeyAuthenticationFilter.class);
    try {
      lenient()
          .doAnswer(
              new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                  ServletRequest req = invocation.getArgument(0);
                  ServletResponse res = invocation.getArgument(1);
                  FilterChain chain = invocation.getArgument(2);
                  chain.doFilter(req, res);
                  return null;
                }
              })
          .when(filter)
          .doFilter(any(), any(), any());
    } catch (IOException | ServletException e) {
      throw new IllegalStateException(e);
    }
    return filter;
  }
}
