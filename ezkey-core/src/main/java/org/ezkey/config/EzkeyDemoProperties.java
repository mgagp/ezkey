/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional demonstration / lab settings. Disabled by default; must never weaken production security
 * unless explicitly enabled by operators.
 *
 * <p><b>MITM simulation:</b> When {@link #mitmSignatureEnabled} is true and a specific auth attempt
 * is flagged in the database, the Auth API returns a Pending payload whose body no longer matches
 * the integration signature (sign-then-tamper). Used for presentations and security training.
 */
@ConfigurationProperties(prefix = "ezkey.demo")
public class EzkeyDemoProperties {

  /**
   * When {@code true}, auth attempts with {@code demo_mitm_signature_enabled} may receive a
   * tampered Pending response. Default {@code false}.
   */
  private boolean mitmSignatureEnabled = false;

  public boolean isMitmSignatureEnabled() {
    return mitmSignatureEnabled;
  }

  public void setMitmSignatureEnabled(boolean mitmSignatureEnabled) {
    this.mitmSignatureEnabled = mitmSignatureEnabled;
  }
}
