/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Options: RetroactiveIntegrityValidationOptions
 * Description: Per-run options for retroactive integrity validation orchestration.
 */

package org.ezkey.audit.integrity;

/**
 * Per-run options for {@link RetroactiveIntegrityValidationService#runValidation}.
 *
 * @param raiseAlert when {@code true}, may raise or touch {@code AUDIT_INTEGRITY_RUPTURE}
 * @param triggerSource scheduled batch vs operator POST
 * @param requestedByAdminId admin id when {@code triggerSource} is {@code OPERATOR}; otherwise null
 * @since 2026
 */
public record RetroactiveIntegrityValidationOptions(
    boolean raiseAlert,
    RetroactiveIntegrityValidationTriggerSource triggerSource,
    Integer requestedByAdminId) {

  /** Options for the nightly scheduled batch. */
  public static RetroactiveIntegrityValidationOptions scheduled() {
    return new RetroactiveIntegrityValidationOptions(
        true, RetroactiveIntegrityValidationTriggerSource.SCHEDULED, null);
  }

  /**
   * Options for a Global Admin operator POST.
   *
   * @param raiseAlert whether to raise or touch integrity rupture alerts
   * @param requestedByAdminId authenticated Global Admin id
   * @return operator options
   */
  public static RetroactiveIntegrityValidationOptions operator(
      boolean raiseAlert, Integer requestedByAdminId) {
    return new RetroactiveIntegrityValidationOptions(
        raiseAlert, RetroactiveIntegrityValidationTriggerSource.OPERATOR, requestedByAdminId);
  }
}
