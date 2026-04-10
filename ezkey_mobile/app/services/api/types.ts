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
  /** Phase 1: only `ed25519` (see docs/CRYPTO.md). Clients must validate before using the public key. */
  integrationKeyAlgorithm: 'ed25519';
  integrationName: string;
  integrationDescription?: string;
  enrollmentName?: string;
  tenantId?: number;
  tenantName?: string;
  tenantDescription?: string;
  /** Ed25519 over canonical bind payload (see ENROLLMENT_SIGNATURE_PAYLOAD.md) */
  enrollmentBindPayloadSignedByIntegration: string;
};

/** Matches Auth API `devicePrivateKeyStorageTier` at enrollment verify. */
export type DevicePrivateKeyStorageTier = 'NONE' | 'STANDARD' | 'STRONG';

/**
 * Request to complete enrollment verification (Auth API `EnrollmentVerifyRequestDto`).
 * `challengeResponse` is required: the user-entered value matching the server-stored challenge
 * (the app typically collects six digits). `enrollmentsApi.verify` serializes it as a JSON number.
 */
export type VerifyEnrollmentRequest = {
  enrollmentId: string;
  challengeResponse: string;
  devicePublicKey: string;
  enrollmentProofTokenSigned: string;
  /** Reported from native keystore introspection (Android); sent on verify so the server can persist tier. */
  devicePrivateKeyStorageTier?: DevicePrivateKeyStorageTier;
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
  /** Base64URL Ed25519 signature (raw 64 bytes); null only if the server could not sign (e.g. integration key unavailable). */
  authAttemptProofTokenResultSignedByIntegration: string | null;
};
