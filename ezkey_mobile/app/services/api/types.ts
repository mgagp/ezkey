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

export type PublicInstanceInfoResponse = {
  authApiPublicBaseUrl?: string | null;
  instanceName?: string | null;
  instanceDescription?: string | null;
  aboutUrl?: string | null;
};

/**
 * Canonical local representation of an Ezkey installation trust zone.
 *
 * Product posture: an installation is a trust zone. Enrollments belong to it.
 * Identity is always the normalized Auth API URL (`normalizeInstallationId`).
 * Public instance-info fields enrich presentation only; they are not a
 * cryptographic trust anchor and must never replace URL identity.
 * There is no installation UUID.
 */
export type Installation = {
  /** Canonical trust-zone id (= normalized Auth API URL). */
  id: string;
  authUrl?: string;
  host?: string;
  name: string;
  description?: string;
  aboutUrl?: string;
  lastRefreshedAt?: string;
};

/**
 * Local enrollment summary. Each enrollment belongs to one {@link Installation}
 * trust zone (`installation`). Nested persistence of that object is packaging;
 * conceptually the trust zone owns the enrollment set.
 *
 * Note: `id` is still the local primary key used for navigation/storage and today
 * still mirrors the server enrollment id — installation-scoped local identity is
 * the follow-on MOB-011 program slice.
 */
export type EnrollmentSummary = {
  id: string;
  integrationId: string;
  integrationName: string;
  tenantName?: string;
  tenantId?: number;
  tenantDescription?: string;
  createdAt: string;
  lastActivityAt: string;
  favorited?: boolean;
  /** Trust zone this enrollment belongs to (normalized Auth URL identity). */
  installation?: Installation;
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
