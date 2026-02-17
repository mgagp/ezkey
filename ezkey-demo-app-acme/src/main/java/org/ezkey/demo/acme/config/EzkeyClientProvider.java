/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EzkeyClientProvider
 * Description: Provides EzkeyClient instances built from current demo credentials.
 */

package org.ezkey.demo.acme.config;

import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyConfig;
import org.springframework.stereotype.Component;

/**
 * Provides {@link EzkeyClient} instances built from current credentials held by {@link
 * DemoApiKeyConfigService}.
 *
 * <p>Each call to {@link #getClient()} builds a fresh client from the current integration key and
 * secret key. This allows runtime credential changes (via the demo "Apply API Key" dialog) to take
 * effect without restart.
 *
 * <p>The Ezkey SDK {@link EzkeyClient} remains the single source for M2M API calls; this provider
 * merely decides which credentials to use when constructing it.
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
   * @return configured client, or null if integration key or secret key are not set
   */
  public EzkeyClient getClient() {
    if (!credentials.isConfigured()) {
      return null;
    }
    String baseUrl = acmeProperties.getAdminApiUrl();
    return EzkeyClient.builder()
        .integrationKey(credentials.getIntegrationKey())
        .secretKey(credentials.getSecretKey())
        .baseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl : EzkeyConfig.DEFAULT_BASE_URL)
        .build();
  }
}
