/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: controlledEnrollmentBypass tests
 * Description: Gate semantics for F2a production-clean posture.
 * @since 2025
 */

import {
  CONTROLLED_ENROLLMENT_BYPASS_ACK,
  isControlledEnrollmentBypassAvailable,
} from '../controlledEnrollmentBypass';

describe('isControlledEnrollmentBypassAvailable', () => {
  const enabledBase = {
    enrollmentSeedBypassEnabled: true,
    enrollmentSeedBypassAck: CONTROLLED_ENROLLMENT_BYPASS_ACK,
    isDebugBuild: true,
  };

  it('allows bypass only when debug build, enabled flag, and ack all match', () => {
    expect(isControlledEnrollmentBypassAvailable(enabledBase)).toBe(true);
  });

  it('denies bypass on release/native non-debug builds even when env flags are set', () => {
    expect(
      isControlledEnrollmentBypassAvailable({
        ...enabledBase,
        isDebugBuild: false,
      }),
    ).toBe(false);
  });

  it('denies bypass when enable flag is false', () => {
    expect(
      isControlledEnrollmentBypassAvailable({
        ...enabledBase,
        enrollmentSeedBypassEnabled: false,
      }),
    ).toBe(false);
  });

  it('denies bypass when acknowledgement token mismatches', () => {
    expect(
      isControlledEnrollmentBypassAvailable({
        ...enabledBase,
        enrollmentSeedBypassAck: 'WRONG',
      }),
    ).toBe(false);
  });

  it('denies bypass when acknowledgement token is missing', () => {
    expect(
      isControlledEnrollmentBypassAvailable({
        ...enabledBase,
        enrollmentSeedBypassAck: undefined,
      }),
    ).toBe(false);
  });
});
