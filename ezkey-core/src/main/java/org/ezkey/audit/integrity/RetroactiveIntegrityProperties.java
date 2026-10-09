/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: RetroactiveIntegrityProperties
 * Description: Operator-triggered retroactive integrity validation limits.
 */

package org.ezkey.audit.integrity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for operator-initiated retroactive integrity validation.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.audit.integrity.retroactive}
 *
 * @since 2026
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.audit.integrity.retroactive")
public class RetroactiveIntegrityProperties {

  /**
   * Optional maximum operator-selected window length in hours for retroactive detect POST / async
   * {@code RUN_VALIDATION}. When {@code null}, those paths accept unbounded {@code [from, to)}.
   * When set, requests exceeding this length are rejected with HTTP 400. Distinct from the
   * synchronous report GET cap ({@code ezkey.audit.integrity.verify-report.max-window-hours}).
   */
  private Integer operatorMaxWindowHours;

  public Integer getOperatorMaxWindowHours() {
    return operatorMaxWindowHours;
  }

  public void setOperatorMaxWindowHours(Integer operatorMaxWindowHours) {
    this.operatorMaxWindowHours = operatorMaxWindowHours;
  }
}
