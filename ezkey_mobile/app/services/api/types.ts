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
  logoUri?: string;
  favorited?: boolean;
  /** Base URL of the Ezkey Auth API for this enrollment (e.g. "https://ezkey.acme.com"). */
  authUrl?: string;
};

export type BindEnrollmentRequest = {
  enrollmentId: string;
  enrollmentProofToken: string;
  language?: string;
};

export type BindEnrollmentResponse = {
  enrollmentId: string;
  enrollmentProofToken: string;
  integrationPublicKey: string;
  integrationName: string;
  integrationDescription?: string;
  integrationLogo?: string;
  enrollmentName?: string;
  tenantId?: number;
  tenantName?: string;
  tenantDescription?: string;
};

export type VerifyEnrollmentRequest = {
  enrollmentId: string;
  challengeResponse?: string;
  devicePublicKey: string;
  enrollmentProofTokenSigned: string;
};

export type VerifyEnrollmentResponse = {
  active: boolean;
};

export type PendingAuthRequest = {
  enrollmentId: string;
  enrollmentProofToken: string;
  deviceProofToken: string;
  deviceProofTokenSigned: string;
};

export type PendingAuthResponse = {
  authAttemptId: string;
  authAttemptProofToken: string;
  authAttemptProofTokenSignedByIntegration: string;
  authAttemptChallengeRequired: boolean;
};

export type AuthAttemptDecision = 'APPROVED' | 'REJECTED' | 'FAILED';

export type RespondAuthRequest = {
  authAttemptId: string;
  authAttemptAccepted: boolean;
  authAttemptProofTokenSignedByDevice: string;
  authAttemptChallengeResponse?: string;
};

export type RespondAuthResponse = {
  result: AuthAttemptDecision;
  message?: string;
};
