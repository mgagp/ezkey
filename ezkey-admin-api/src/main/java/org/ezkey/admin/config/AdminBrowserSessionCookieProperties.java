/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminBrowserSessionCookieProperties
 *
 * Description: Optional HttpOnly session cookie for browser-based Admin UI (split UI/API).
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser session cookie for administrator authentication.
 *
 * <p>When enabled, successful login issues an HttpOnly cookie holding the same opaque bearer value
 * already stored server-side; the JSON body omits the token so JavaScript cannot read it. Local dev
 * (Vite + HTTP) should keep this disabled and use Bearer + sessionStorage.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.admin.auth}
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ConfigurationProperties(prefix = "ezkey.admin.auth")
public class AdminBrowserSessionCookieProperties {

  /**
   * When true, login/passwordless-wait set {@link #browserSessionCookieName} and the filter reads
   * it.
   */
  private boolean browserSessionCookieEnabled = false;

  private String browserSessionCookieName = "EZKEY_ADMIN_SESSION";

  private String browserSessionCookieSameSite = "Strict";

  private String browserCsrfCookieName = "EZKEY_ADMIN_CSRF";

  private String browserCsrfHeaderName = "X-CSRF-TOKEN";

  /**
   * When true, the cookie is marked Secure (required for HTTPS split UI/API). Disable only for
   * exceptional local HTTPS experiments.
   */
  private boolean browserSessionCookieSecure = true;

  public boolean isBrowserSessionCookieEnabled() {
    return browserSessionCookieEnabled;
  }

  public void setBrowserSessionCookieEnabled(boolean browserSessionCookieEnabled) {
    this.browserSessionCookieEnabled = browserSessionCookieEnabled;
  }

  public String getBrowserSessionCookieName() {
    return browserSessionCookieName;
  }

  public void setBrowserSessionCookieName(String browserSessionCookieName) {
    this.browserSessionCookieName = browserSessionCookieName;
  }

  public String getBrowserSessionCookieSameSite() {
    return browserSessionCookieSameSite;
  }

  public void setBrowserSessionCookieSameSite(String browserSessionCookieSameSite) {
    this.browserSessionCookieSameSite =
        browserSessionCookieSameSite == null || browserSessionCookieSameSite.isBlank()
            ? "Strict"
            : browserSessionCookieSameSite;
  }

  public String getBrowserCsrfCookieName() {
    return browserCsrfCookieName;
  }

  public void setBrowserCsrfCookieName(String browserCsrfCookieName) {
    this.browserCsrfCookieName = browserCsrfCookieName;
  }

  public String getBrowserCsrfHeaderName() {
    return browserCsrfHeaderName;
  }

  public void setBrowserCsrfHeaderName(String browserCsrfHeaderName) {
    this.browserCsrfHeaderName = browserCsrfHeaderName;
  }

  public boolean isBrowserSessionCookieSecure() {
    return browserSessionCookieSecure;
  }

  public void setBrowserSessionCookieSecure(boolean browserSessionCookieSecure) {
    this.browserSessionCookieSecure = browserSessionCookieSecure;
  }
}
