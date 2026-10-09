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
 * IntegrityHeavyCryptoWindowLimits#DEFAULT_MAX_WINDOW_HOURS} with report GETs / VERIFY starts
 * (primitive {@code int} — empty/null env binding cannot remove the cap). Provisional — #730 will
 * align under a single {@code max-window-days} property.
 *
 * @since 2026
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.audit.integrity.retroactive")
@Validated
public class RetroactiveIntegrityProperties {

  /**
   * Maximum operator-selected window length in hours for retroactive detect POST / async {@code
   * RUN_VALIDATION}. Defaults to the shared heavy-crypto window limit. Primitive so an empty env
   * var cannot silently disable the cap.
   */
  @Min(1)
  private int operatorMaxWindowHours = IntegrityHeavyCryptoWindowLimits.DEFAULT_MAX_WINDOW_HOURS;

  public int getOperatorMaxWindowHours() {
    return operatorMaxWindowHours;
  }

  public void setOperatorMaxWindowHours(int operatorMaxWindowHours) {
    this.operatorMaxWindowHours = operatorMaxWindowHours;
  }
}
