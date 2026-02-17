/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: DemoApiKeyConfigService
 * Description: Holds API key credentials for the demo app with support for runtime override.
 */

package org.ezkey.demo.acme.service;

import org.ezkey.demo.acme.config.AcmeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Holds API key credentials (integration key + secret key) for demo app M2M authentication.
 *
 * <p><b>Demo only:</b> Credentials are held in memory and are not persisted. At startup, values are
 * loaded from {@link AcmeProperties}. Callers can apply a runtime override via {@link
 * #applyApiKey(String, String)}, which takes effect immediately for subsequent API calls.
 *
 * <p>Thread-safe: Uses volatile for credential fields to ensure visibility across threads.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class DemoApiKeyConfigService {

  private static final Logger LOG = LoggerFactory.getLogger(DemoApiKeyConfigService.class);

  private volatile String integrationKey;
  private volatile String secretKey;

  public DemoApiKeyConfigService(AcmeProperties properties) {
    this.integrationKey = properties.getIntegrationKey();
    this.secretKey = properties.getSecretKey();
    if (isConfigured()) {
      LOG.info(
          "Demo API key loaded from config — Integration Key: {}...",
          integrationKey.substring(0, Math.min(20, integrationKey.length())));
    } else {
      LOG.warn(
          "EZKEY SDK not configured. Set credentials via config or use the 'Apply API Key'"
              + " dialog.");
    }
  }

  /**
   * Applies API key credentials as runtime override.
   *
   * <p>Replaces current credentials (from properties or previous override). Takes effect
   * immediately for subsequent {@link org.ezkey.demo.acme.config.EzkeyClientProvider#getClient()}
   * calls.
   *
   * @param integrationKey the integration key (e.g. ezkey_ikey_xxx)
   * @param secretKey the secret key (e.g. ezkey_skey_xxx)
   * @return true if both values are non-blank and were applied
   */
  public boolean applyApiKey(String integrationKey, String secretKey) {
    if (integrationKey == null
        || integrationKey.isBlank()
        || secretKey == null
        || secretKey.isBlank()) {
      return false;
    }
    this.integrationKey = integrationKey.trim();
    this.secretKey = secretKey.trim();
    LOG.info(
        "API key applied via demo UI — Integration Key: {}...",
        this.integrationKey.substring(0, Math.min(20, this.integrationKey.length())));
    return true;
  }

  public String getIntegrationKey() {
    return integrationKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  /** Returns true if both integration key and secret key are configured (non-blank). */
  public boolean isConfigured() {
    return integrationKey != null
        && !integrationKey.isBlank()
        && secretKey != null
        && !secretKey.isBlank();
  }
}
