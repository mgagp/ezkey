/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test support: pass-through security filters for {@code @WebMvcTest} with {@link SecurityConfig}.
 */

package org.ezkey.admin.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.ezkey.admin.security.AdminCookieCsrfFilter;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.security.AdminTokenAuthenticationFilter;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Supplies pass-through mocks for filters required by {@link SecurityConfig} in slice tests.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Configuration
public class AdminCorsTestFilterBeans {

  @Bean
  @Primary
  AdminTokenAuthenticationFilter adminTokenAuthenticationFilter() {
    return passThrough(AdminTokenAuthenticationFilter.class);
  }

  @Bean
  @Primary
  AdminRateLimitFilter adminRateLimitFilter() {
    return passThrough(AdminRateLimitFilter.class);
  }

  @Bean
  @Primary
  AdminCookieCsrfFilter adminCookieCsrfFilter() {
    return passThrough(AdminCookieCsrfFilter.class);
  }

  private static <T extends Filter> T passThrough(Class<T> clazz) {
    T filter = mock(clazz);
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
