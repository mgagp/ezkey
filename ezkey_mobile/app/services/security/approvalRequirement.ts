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