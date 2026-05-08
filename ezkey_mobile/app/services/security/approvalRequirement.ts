/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: approvalRequirement
 * Description: Enrollment-level and device-level approval policy evaluation for authentication requests.
 * Security Context: Determines whether a protected confirmation (biometric or device credential) is required
 *                   before approving or denying a pending auth attempt, based on per-enrollment policy and
 *                   the device-wide security preference.
 * @since 2025
 */

import {
  DEFAULT_SECURITY_LEVEL,
  normalizeSecurityLevel,
  SecurityLevel,
} from '../storage/securityPreferenceStorage';

export const ENROLLMENT_APPROVAL_POLICIES = ['not-required', 'required'] as const;

export type EnrollmentApprovalPolicy = (typeof ENROLLMENT_APPROVAL_POLICIES)[number];

export const DEFAULT_ENROLLMENT_APPROVAL_POLICY: EnrollmentApprovalPolicy = 'not-required';

const isEnrollmentApprovalPolicy = (value: string): value is EnrollmentApprovalPolicy =>
  ENROLLMENT_APPROVAL_POLICIES.includes(value as EnrollmentApprovalPolicy);

export const normalizeEnrollmentApprovalPolicy = (
  value?: string,
): EnrollmentApprovalPolicy =>
  value && isEnrollmentApprovalPolicy(value) ? value : DEFAULT_ENROLLMENT_APPROVAL_POLICY;

export const requiresProtectedApproval = ({
  enrollmentApprovalPolicy,
  securityPreference = DEFAULT_SECURITY_LEVEL,
}: {
  enrollmentApprovalPolicy?: string;
  securityPreference?: SecurityLevel;
}): boolean => {
  return (
    normalizeEnrollmentApprovalPolicy(enrollmentApprovalPolicy) === 'required' ||
    normalizeSecurityLevel(securityPreference) === 'confirm-before-approvals'
  );
};

export const requiresAuthenticationForSecurityPreferenceChange = ({
  currentSecurityPreference,
  nextSecurityPreference,
}: {
  currentSecurityPreference?: SecurityLevel;
  nextSecurityPreference?: SecurityLevel;
}): boolean => {
  return (
    normalizeSecurityLevel(currentSecurityPreference) === 'confirm-before-approvals' &&
    normalizeSecurityLevel(nextSecurityPreference) === 'standard'
  );
};