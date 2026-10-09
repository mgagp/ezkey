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

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for operator-initiated retroactive integrity validation.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.audit.integrity.retroactive}
 *
 * <p>Default operator window cap shares {@link
 * IntegrityHeavyCryptoWindowLimits#DEFAULT_MAX_WINDOW_HOURS} with report GETs / VERIFY starts.
 * Provisional — #730 will align under a single {@code max-window-days} property.
 *
 * @since 2026
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.audit.integrity.retroactive")
@Validated
public class RetroactiveIntegrityProperties {

  /**
   * Maximum operator-selected window length in hours for retroactive detect POST / async {@code
   * RUN_VALIDATION}. Defaults to the shared heavy-crypto window limit. Set explicitly to {@code
   * null} only when a deployment intentionally wants an unbounded detect path.
   */
  @Min(1)
  private Integer operatorMaxWindowHours =
      IntegrityHeavyCryptoWindowLimits.DEFAULT_MAX_WINDOW_HOURS;

  public Integer getOperatorMaxWindowHours() {
    return operatorMaxWindowHours;
  }

  public void setOperatorMaxWindowHours(Integer operatorMaxWindowHours) {
    this.operatorMaxWindowHours = operatorMaxWindowHours;
  }
}
