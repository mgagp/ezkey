/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: controlledEnrollmentBypass
 * Description: Pure gate for F2a controlled enrollment seed bypass (test harness only).
 * @since 2025
 */

/** Explicit acknowledgement token required in addition to the enable flag (build-time env). */
export const CONTROLLED_ENROLLMENT_BYPASS_ACK = 'F2A_TEST_ONLY';

/**
 * Inputs for the F2a controlled enrollment seed bypass availability gate.
 */
export type ControlledEnrollmentBypassGateInput = {
  /** {@code EZKEY_ENROLLMENT_SEED_BYPASS_ENABLED} baked via react-native-config. */
  enrollmentSeedBypassEnabled: boolean;
  /** {@code EZKEY_ENROLLMENT_SEED_BYPASS_ACK} baked via react-native-config. */
  enrollmentSeedBypassAck: string | undefined;
  /**
   * Native debug build type ({@code BuildConfig.DEBUG}), not React Native {@code __DEV__}.
   * Release / production builds must pass {@code false}.
   */
  isDebugBuild: boolean;
};

/**
 * Whether the controlled enrollment seed bypass may be shown or invoked.
 *
 * All three conditions are required:
 * 1. native **debug** build type,
 * 2. explicit enable flag,
 * 3. acknowledgement token {@link CONTROLLED_ENROLLMENT_BYPASS_ACK}.
 *
 * Bind/verify cryptographic checks still run when the bypass is used; this gate only controls the
 * camera/QR bootstrap shortcut for Maestro and related harnesses.
 *
 * @param input gate inputs
 * @returns true when the bypass UI/action is allowed
 */
export function isControlledEnrollmentBypassAvailable(
  input: ControlledEnrollmentBypassGateInput,
): boolean {
  return (
    input.isDebugBuild &&
    input.enrollmentSeedBypassEnabled &&
    input.enrollmentSeedBypassAck === CONTROLLED_ENROLLMENT_BYPASS_ACK
  );
}
