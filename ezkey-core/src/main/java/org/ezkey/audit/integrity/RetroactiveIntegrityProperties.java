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
   * Maximum operator-selected window length in hours. When {@code null}, {@link
   * NightlyIntegrityProperties#getWindowHours()} is used at runtime.
   */
  private Integer operatorMaxWindowHours;

  public Integer getOperatorMaxWindowHours() {
    return operatorMaxWindowHours;
  }

  public void setOperatorMaxWindowHours(Integer operatorMaxWindowHours) {
    this.operatorMaxWindowHours = operatorMaxWindowHours;
  }
}
