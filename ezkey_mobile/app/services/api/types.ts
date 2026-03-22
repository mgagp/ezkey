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
  integrationName: string;
  integrationDescription?: string;
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

/** Values returned by Auth API for Respond authAttemptResult. */
export type AuthAttemptDecision = 'APPROVED' | 'DENIED' | 'FAILED' | 'EXPIRED';

export type RespondAuthRequest = {
  authAttemptId: string | number;
  authAttemptAccepted: boolean;
  authAttemptProofTokenSignedByDevice: string;
  authAttemptChallengeResponse?: string | number;
};

/**
 * Respond HTTP response (Auth API). Matches Auth API AuthAttemptRespondResponseDto; integration signs
 * authAttemptProofTokenResultSignedByIntegration over the canonical result payload (see AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md).
 */
export type RespondAuthResponse = {
  authAttemptId: number;
  authAttemptResult: AuthAttemptDecision;
  authAttemptMessage: string;
  /** Base64 ECDSA signature; null only if the server could not sign (e.g. integration key unavailable). */
  authAttemptProofTokenResultSignedByIntegration: string | null;
};
