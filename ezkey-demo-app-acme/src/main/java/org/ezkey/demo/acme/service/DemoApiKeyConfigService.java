/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: DemoApiKeyConfigService
 * Description: Holds API key credentials for the demo app with support for runtime override.
 */

package org.ezkey.demo.acme.service;

import jakarta.servlet.http.HttpSession;
import org.ezkey.demo.acme.config.AcmeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Holds API key credentials (integration key + secret key) for demo app Integration API
 * authentication.
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
  static final String SESSION_INTEGRATION_KEY = "demoIntegrationKey";
  static final String SESSION_SECRET_KEY = "demoSecretKey";

  private final DemoApiKeyCredentials configuredCredentials;

  public DemoApiKeyConfigService(AcmeProperties properties) {
    this.configuredCredentials =
        buildCredentials(properties.getIntegrationKey(), properties.getSecretKey());
    if (configuredCredentials != null) {
      LOG.info("Demo API key loaded from config for local/dev mode.");
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
   * <p>Credentials are stored only in the caller's HTTP session so concurrent demo users can use
   * different integration keys without affecting each other.
   *
   * @param session the current HTTP session
   * @param integrationKeyParam the integration key (e.g. ezkey_ikey_xxx)
   * @param secretKeyParam the secret key (e.g. ezkey_skey_xxx)
   * @return true if both values are non-blank and were applied
   */
  public boolean applyApiKey(
      HttpSession session, String integrationKeyParam, String secretKeyParam) {
    if (session == null) {
      return false;
    }

    DemoApiKeyCredentials sessionCredentials =
        buildCredentials(integrationKeyParam, secretKeyParam);
    if (sessionCredentials == null) {
      return false;
    }

    session.setAttribute(SESSION_INTEGRATION_KEY, sessionCredentials.integrationKey());
    session.setAttribute(SESSION_SECRET_KEY, sessionCredentials.secretKey());
    LOG.info("Session-scoped demo API key applied via demo UI.");
    return true;
  }

  public DemoApiKeyCredentials resolveCredentials(HttpSession session) {
    if (session != null) {
      Object sessionIntegrationKey = session.getAttribute(SESSION_INTEGRATION_KEY);
      Object sessionSecretKey = session.getAttribute(SESSION_SECRET_KEY);
      DemoApiKeyCredentials sessionCredentials =
          buildCredentials(
              sessionIntegrationKey instanceof String ? (String) sessionIntegrationKey : null,
              sessionSecretKey instanceof String ? (String) sessionSecretKey : null);
      if (sessionCredentials != null) {
        return sessionCredentials;
      }
    }
    return configuredCredentials;
  }

  /** Returns true if either session-scoped or configured credentials are available. */
  public boolean isConfigured(HttpSession session) {
    return resolveCredentials(session) != null;
  }

  private DemoApiKeyCredentials buildCredentials(String integrationKey, String secretKey) {
    if (integrationKey == null
        || integrationKey.isBlank()
        || secretKey == null
        || secretKey.isBlank()) {
      return null;
    }
    return new DemoApiKeyCredentials(integrationKey.trim(), secretKey.trim());
  }

  /**
   * Session- or configuration-scoped API key credentials used to build the Ezkey client.
   *
   * @param integrationKey the integration key presented to the Admin API
   * @param secretKey the companion secret key presented to the Admin API
   */
  public record DemoApiKeyCredentials(String integrationKey, String secretKey) {}
}
