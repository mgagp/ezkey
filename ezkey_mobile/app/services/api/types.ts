import type {
  AuthAttemptPendingRequestDto,
  AuthAttemptPendingResponseDto,
  AuthAttemptRespondRequestDto,
  AuthAttemptRespondResponseDto,
  AuthAttemptRespondResponseDtoAuthAttemptResult,
  EnrollmentBindRequestDto,
  EnrollmentBindResponseDto,
  EnrollmentVerifyRequestDto,
  EnrollmentVerifyRequestDtoDevicePrivateKeyStorageTier,
  EnrollmentVerifyResponseDto,
} from './generated/auth-api/model';

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
  enrollmentProofToken: EnrollmentBindRequestDto['enrollmentProofToken'];
};

export type BindEnrollmentResponse = EnrollmentBindResponseDto;

/** Matches Auth API `devicePrivateKeyStorageTier` at enrollment verify. */
export type DevicePrivateKeyStorageTier =
  EnrollmentVerifyRequestDtoDevicePrivateKeyStorageTier;

/**
 * Request to complete enrollment verification (Auth API `EnrollmentVerifyRequestDto`).
 * `challengeResponse` is required: the user-entered value matching the server-stored challenge
 * (the app typically collects six digits). `enrollmentsApi.verify` serializes it as a JSON number.
 */
export type VerifyEnrollmentRequest = {
  enrollmentId: string;
  challengeResponse: string;
  devicePublicKey: EnrollmentVerifyRequestDto['devicePublicKey'];
  enrollmentProofTokenSigned: EnrollmentVerifyRequestDto['enrollmentProofTokenSigned'];
  /** Reported from native keystore introspection (Android); sent on verify so the server can persist tier. */
  devicePrivateKeyStorageTier?: DevicePrivateKeyStorageTier;
};

export type VerifyEnrollmentResponse = EnrollmentVerifyResponseDto;

export type PendingAuthRequest = {
  enrollmentId: string | number;
  enrollmentProofToken: AuthAttemptPendingRequestDto['enrollmentProofToken'];
  deviceProofToken: AuthAttemptPendingRequestDto['deviceProofToken'];
  deviceProofTokenSigned: AuthAttemptPendingRequestDto['deviceProofTokenSigned'];
};

export type PendingAuthResponse = AuthAttemptPendingResponseDto;

/** Values returned by Auth API for Respond authAttemptResult. */
export type AuthAttemptDecision = AuthAttemptRespondResponseDtoAuthAttemptResult;

export type RespondAuthRequest = {
  authAttemptId: string | number;
  authAttemptAccepted: AuthAttemptRespondRequestDto['authAttemptAccepted'];
  authAttemptProofTokenSignedByDevice: AuthAttemptRespondRequestDto['authAttemptProofTokenSignedByDevice'];
  authAttemptChallengeResponse?: string | number;
};

/**
 * Respond HTTP response (Auth API). Matches Auth API AuthAttemptRespondResponseDto; integration signs
 * authAttemptProofTokenResultSignedByIntegration over the canonical result payload (see AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md).
 */
export type RespondAuthResponse = AuthAttemptRespondResponseDto;
