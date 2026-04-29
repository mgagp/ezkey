/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EzkeyClientProvider
 * Description: Provides EzkeyClient instances built from current demo credentials.
 */

package org.ezkey.demo.acme.config;

import jakarta.servlet.http.HttpSession;
import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyConfig;
import org.springframework.stereotype.Component;

/**
 * Provides {@link EzkeyClient} instances built from current credentials held by {@link
 * DemoApiKeyConfigService}.
 *
 * <p>Each call to {@link #getClient(HttpSession)} builds a fresh client from the current
 * session-scoped integration key and secret key, falling back to configured values when the session
 * does not provide an override. This allows runtime credential changes (via the demo "Apply API
 * Key" dialog) to take effect without restart while keeping concurrent sessions isolated.
 *
 * <p>The Ezkey SDK {@link EzkeyClient} targets the configured Integration API base URL for API-key
 * auth attempts; {@link AcmeProperties#getAdminApiUrl()} supplies that URL despite the legacy
 * {@code admin} naming.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
public class EzkeyClientProvider {

  private final DemoApiKeyConfigService credentials;
  private final AcmeProperties acmeProperties;

  public EzkeyClientProvider(DemoApiKeyConfigService credentials, AcmeProperties acmeProperties) {
    this.credentials = credentials;
    this.acmeProperties = acmeProperties;
  }

  /**
   * Returns an {@link EzkeyClient} configured with current credentials, or null if not configured.
   *
   * @param session the current HTTP session, used to resolve session-scoped credentials
   * @return configured client, or null if integration key or secret key are not set
   */
  public EzkeyClient getClient(HttpSession session) {
    DemoApiKeyConfigService.DemoApiKeyCredentials resolvedCredentials =
        credentials.resolveCredentials(session);
    if (resolvedCredentials == null) {
      return null;
    }
    String baseUrl = acmeProperties.getAdminApiUrl();
    return EzkeyClient.builder()
        .integrationKey(resolvedCredentials.integrationKey())
        .secretKey(resolvedCredentials.secretKey())
        .baseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl : EzkeyConfig.DEFAULT_BASE_URL)
        .build();
  }
}
