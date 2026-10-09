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
 * <p><b>Demo only:</b> Credentials are held in memory and are not persisted. Resolution order for
 * each request: access-code slot id in session → pasted session keys → legacy single config from
 * {@link AcmeProperties}.
 *
 * <p>Thread-safe: Uses volatile for credential fields to ensure visibility across threads.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class DemoApiKeyConfigService {

  private static final Logger LOG = LoggerFactory.getLogger(DemoApiKeyConfigService.class);

  /** Session attribute for pasted integration key (temporary evaluator console access). */
  public static final String SESSION_INTEGRATION_KEY = "demoIntegrationKey";

  /** Session attribute for pasted secret key (temporary evaluator console access). */
  public static final String SESSION_SECRET_KEY = "demoSecretKey";

  /** Session attribute storing only the access-code slot id (never keys or the code). */
  public static final String SESSION_ACCESS_CODE_SLOT_ID = "demoAccessCodeSlotId";

  private final DemoApiKeyCredentials configuredCredentials;
  private final AccessCodeService accessCodeService;

  /**
   * Creates the service with legacy single-pair credentials and access-code slot resolution.
   *
   * @param properties ACME configuration
   * @param accessCodeService validated access-code slots
   */
  public DemoApiKeyConfigService(AcmeProperties properties, AccessCodeService accessCodeService) {
    this.accessCodeService = accessCodeService;
    this.configuredCredentials =
        buildCredentials(properties.getIntegrationKey(), properties.getSecretKey());
    if (configuredCredentials != null) {
      LOG.info("Demo API key loaded from config for local/dev mode.");
    } else {
      LOG.warn(
          "EZKEY SDK not configured. Set credentials via config, an access-code slot, or use the"
              + " 'Apply API Key' dialog.");
    }
  }

  /**
   * Applies API key credentials as a session-scoped paste override.
   *
   * <p>Callers must {@code invalidate()} the previous session when switching credential source
   * (slot → paste) before invoking this method on the new session.
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
    session.removeAttribute(SESSION_ACCESS_CODE_SLOT_ID);
    LOG.info("Session-scoped demo API key applied via demo UI.");
    return true;
  }

  /**
   * Stores only the access-code slot id in the session (keys stay server-side in {@link
   * AccessCodeService}).
   *
   * @param session the new HTTP session after invalidate
   * @param slotId configured slot id
   */
  public void activateAccessCodeSlot(HttpSession session, String slotId) {
    if (session == null || slotId == null || slotId.isBlank()) {
      return;
    }
    session.setAttribute(SESSION_ACCESS_CODE_SLOT_ID, slotId);
    session.removeAttribute(SESSION_INTEGRATION_KEY);
    session.removeAttribute(SESSION_SECRET_KEY);
  }

  /**
   * Resolves credentials: slot → pasted session keys → legacy single config.
   *
   * @param session the current HTTP session (may be null)
   * @return credentials or null when none are available
   */
  public DemoApiKeyCredentials resolveCredentials(HttpSession session) {
    if (session != null) {
      Object slotIdAttr = session.getAttribute(SESSION_ACCESS_CODE_SLOT_ID);
      if (slotIdAttr instanceof String slotId) {
        DemoApiKeyCredentials fromSlot = accessCodeService.resolveCredentials(slotId);
        if (fromSlot != null) {
          return fromSlot;
        }
      }
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

  /**
   * Returns the active access-code slot id from the session, if any.
   *
   * @param session the current HTTP session
   * @return slot id or null
   */
  public String getActiveSlotId(HttpSession session) {
    if (session == null) {
      return null;
    }
    Object slotId = session.getAttribute(SESSION_ACCESS_CODE_SLOT_ID);
    return slotId instanceof String value ? value : null;
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
   * @param integrationKey the integration key presented to the Integration API
   * @param secretKey the companion secret key presented to the Integration API
   */
  public record DemoApiKeyCredentials(String integrationKey, String secretKey) {}
}
