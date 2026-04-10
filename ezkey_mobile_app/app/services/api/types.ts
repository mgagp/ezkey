export type EnrollmentStatus = 'active' | 'pending';

export type EnrollmentSummary = {
  id: string;
  integrationId: string;
  integrationName: string;
  tenantName: string;
  tenantId?: number;
  tenantDescription?: string;
  createdAt: string;
  lastActivityAt: string;
  status: EnrollmentStatus;
  favorited?: boolean;
  /** Base URL of the Ezkey Auth API for this enrollment (e.g. "https://ezkey.acme.com"). */
  authUrl?: string;
  /** Client-reported device private key storage tier at verify (NONE, STANDARD, STRONG). */
  devicePrivateKeyStorageTier?: 'NONE' | 'STANDARD' | 'STRONG';
};

export type BindEnrollmentRequest = {
  enrollmentId: string;
  enrollmentProofToken: string;
  language?: string;
};

export type BindEnrollmentResponse = {
  enrollmentId: number | string;
  enrollmentProofToken: string;
  integrationPublicKey: string;
  integrationKeyAlgorithm: string;
  integrationName: string;
  integrationDescription?: string;
  enrollmentName?: string;
  tenantId?: number;
  tenantName?: string;
  tenantDescription?: string;
  enrollmentBindPayloadSignedByIntegration: string;
};

export type VerifyEnrollmentRequest = {
  enrollmentId: string;
  challengeResponse?: string;
  devicePublicKey: string;
  enrollmentProofTokenSigned: string;
  /** Optional; sent when known (Android Keystore / StrongBox). */
  devicePrivateKeyStorageTier?: 'NONE' | 'STANDARD' | 'STRONG';
};

export type VerifyEnrollmentResponse = {
  active: boolean;
  enrollmentVerifyMessage: string;
  enrollmentVerifyPayloadSignedByIntegration: string;
};

export type PendingAuthRequest = {
  enrollmentId: string | number;
  enrollmentProofToken: string;
  deviceProofToken: string;
  deviceProofTokenSigned: string;
};

export type PendingAuthResponse = {
  authAttemptId: string | number;
  authAttemptProofToken: string;
  authAttemptProofTokenSignedByIntegration: string;
  authAttemptChallengeRequired: boolean;
  /** Optional short title for the approval request (e.g. "Payment Approval"). */
  contextTitle?: string;
  /** Optional descriptive message for the approver. */
  contextMessage?: string;
};

export type AuthAttemptDecision = 'APPROVED' | 'DENIED' | 'REJECTED' | 'FAILED' | 'EXPIRED';

export type RespondAuthRequest = {
  authAttemptId: string | number;
  authAttemptAccepted: boolean;
  authAttemptProofTokenSignedByDevice: string;
  authAttemptChallengeResponse?: string | number;
};

export type RespondAuthResponse = {
  result: AuthAttemptDecision;
  message?: string;
};
