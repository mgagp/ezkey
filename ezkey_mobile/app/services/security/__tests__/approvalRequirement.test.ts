import {
  DEFAULT_ENROLLMENT_APPROVAL_POLICY,
  normalizeEnrollmentApprovalPolicy,
  requiresAuthenticationForSecurityPreferenceChange,
  requiresProtectedApproval,
} from '../approvalRequirement';

describe('approvalRequirement', () => {
  it('defaults missing or unknown enrollment policy to not-required', () => {
    expect(normalizeEnrollmentApprovalPolicy()).toBe(DEFAULT_ENROLLMENT_APPROVAL_POLICY);
    expect(normalizeEnrollmentApprovalPolicy('future-policy')).toBe(
      DEFAULT_ENROLLMENT_APPROVAL_POLICY,
    );
  });

  it('requires protected approval when the user preference requests it', () => {
    expect(
      requiresProtectedApproval({
        enrollmentApprovalPolicy: 'not-required',
        securityPreference: 'confirm-before-approvals',
      }),
    ).toBe(true);
  });

  it('requires protected approval when enrollment policy requires it', () => {
    expect(
      requiresProtectedApproval({
        enrollmentApprovalPolicy: 'required',
        securityPreference: 'standard',
      }),
    ).toBe(true);
  });

  it('allows the standard path only when neither input requires confirmation', () => {
    expect(
      requiresProtectedApproval({
        enrollmentApprovalPolicy: 'not-required',
        securityPreference: 'standard',
      }),
    ).toBe(false);
  });

  it('requires authentication when the user downgrades the local preference', () => {
    expect(
      requiresAuthenticationForSecurityPreferenceChange({
        currentSecurityPreference: 'confirm-before-approvals',
        nextSecurityPreference: 'standard',
      }),
    ).toBe(true);
  });

  it('does not require authentication when the user upgrades or keeps the same preference', () => {
    expect(
      requiresAuthenticationForSecurityPreferenceChange({
        currentSecurityPreference: 'standard',
        nextSecurityPreference: 'confirm-before-approvals',
      }),
    ).toBe(false);
    expect(
      requiresAuthenticationForSecurityPreferenceChange({
        currentSecurityPreference: 'confirm-before-approvals',
        nextSecurityPreference: 'confirm-before-approvals',
      }),
    ).toBe(false);
  });
});