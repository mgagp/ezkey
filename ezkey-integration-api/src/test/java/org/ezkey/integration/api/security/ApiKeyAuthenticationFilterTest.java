/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ApiKeyAuthenticationFilterTest
 * Description: Locks fail-open API key usage recording after a successful validation (#1433).
 */

package org.ezkey.integration.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.ezkey.integration.api.config.TrustedProxyProperties;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.service.ApiKeyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Locks the Integration API key filter after #1433: last-used recording follows a successful
 * validation and a failure to record usage does not undo authentication.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyAuthenticationFilter — usage recording is fail-open (#1433)")
class ApiKeyAuthenticationFilterTest {

  private static final String INTEGRATION_KEY = "ezkey_ikey_test123";
  private static final String SECRET = "ezkey_skey_test123";
  private static final int INTEGRATION_ID = 17;

  @Mock private ApiKeyService apiKeyService;

  private ApiKeyAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    filter = new ApiKeyAuthenticationFilter(apiKeyService, new TrustedProxyProperties());
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Successful validation authenticates and records usage")
  void success_recordsUsageAndAuthenticates() throws Exception {
    when(apiKeyService.validateApiKey(eq(INTEGRATION_KEY), eq(SECRET), any()))
        .thenReturn(Optional.of(integration()));

    MockHttpServletRequest request = basicAuthRequest();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    verify(apiKeyService).recordUsage(INTEGRATION_KEY);
    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo(INTEGRATION_ID);
    assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_API_KEY");
  }

  @Test
  @DisplayName("A last-used write failure stays authenticated and the chain continues")
  void recordUsageFailure_staysAuthenticated() throws Exception {
    when(apiKeyService.validateApiKey(eq(INTEGRATION_KEY), eq(SECRET), any()))
        .thenReturn(Optional.of(integration()));
    doThrow(new IllegalStateException("optimistic lock"))
        .when(apiKeyService)
        .recordUsage(INTEGRATION_KEY);

    MockHttpServletRequest request = basicAuthRequest();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo(INTEGRATION_ID);
  }

  @Test
  @DisplayName("A rejected key is not authenticated and usage is not recorded")
  void rejectedKey_doesNotRecordUsage() throws Exception {
    when(apiKeyService.validateApiKey(eq(INTEGRATION_KEY), eq(SECRET), any()))
        .thenReturn(Optional.empty());

    MockHttpServletRequest request = basicAuthRequest();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    verify(apiKeyService, never()).recordUsage(any());
    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private static Integration integration() {
    Integration integration = new Integration();
    integration.setId(INTEGRATION_ID);
    return integration;
  }

  private static MockHttpServletRequest basicAuthRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth-attempts");
    String token =
        Base64.getEncoder()
            .encodeToString((INTEGRATION_KEY + ":" + SECRET).getBytes(StandardCharsets.UTF_8));
    request.addHeader("Authorization", "Basic " + token);
    return request;
  }
}
