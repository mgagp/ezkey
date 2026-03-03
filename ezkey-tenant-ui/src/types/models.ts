/** Integration — an application protected by Ezkey MFA. */
export interface Integration {
  id: number;
  code: string;
  active: boolean;
  createdAt: string;
  tenantId?: number;
  name?: string;
  description?: string;
}

/** Enrollment status values from the API. */
export type EnrollmentStatus = 'CREATED' | 'BOUND' | 'VERIFIED' | 'INVALID' | 'REVOKED' | 'EXPIRED';

/** Enrollment — the association between a user device and an integration. */
export interface Enrollment {
  enrollmentId: number;
  integrationId: number;
  enrollmentName: string;
  enrollmentStatus: EnrollmentStatus;
  enrollmentActive: boolean;
  /** Numeric binding challenge code displayed during initial device setup. */
  enrollmentChallenge?: number;
  /** Whether auth attempts for this enrollment require a challenge code. */
  authAttemptChallengeRequired?: boolean;
  enrollmentProofToken?: string;
  verifiedAt?: string;
  createdByAdminId?: number;
  lastUsedAt?: string;
  contactEmail?: string;
  userIdentifier?: string;
  createdAt?: string;
}

/** Auth attempt status values from the API. */
export type AuthAttemptStatus = 'PENDING' | 'READ' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'INVALID';

/** AuthAttempt — one MFA request/response cycle. */
export interface AuthAttempt {
  authAttemptId: number;
  enrollmentId: number;
  authAttemptStatus: AuthAttemptStatus;
  /** 2-digit challenge code (00–99), present only when challenge was requested. */
  authAttemptChallenge?: number;
  /** Proof token present only when status is ACCEPTED. */
  authAttemptProofToken?: string;
  createdAt: string;
  expiresAt: string;
}

/** Administrator type. */
export type AdminType = 'GLOBAL_ADMIN' | 'TENANT_ADMIN' | 'INTEGRATION_ADMIN';

/** Admin — a user with elevated privileges. */
export interface Admin {
  adminId: number;
  username: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  adminType: AdminType;
  tenantId?: number;
  active: boolean;
  createdAt: string;
}

/** AuditLog — a tamper-evident record of a system event. */
export interface AuditLog {
  auditLogId: number;
  eventType: string;
  eventAction?: string;
  eventStatus: 'SUCCESS' | 'FAILURE' | 'ERROR';
  apiName?: string;
  ipAddress?: string;
  userAgent?: string;
  adminId?: number;
  integrationId?: number;
  enrollmentId?: number;
  authAttemptId?: number;
  tenantId?: number;
  eventDetails?: string;
  errorMessage?: string;
  /** HMAC-SHA256 signature — presence confirms the chain is intact for this entry. */
  entryHmac?: string;
  instanceId?: string;
  createdAt: string;
}

/** ApiKey — machine-to-machine credentials scoped to an integration. */
export interface ApiKey {
  apiKeyId: number;
  integrationId: number;
  /** Public key identifier, format: ezkey_ikey_[20 hex chars]. Safe to display. */
  integrationKey: string;
  description?: string;
  active: boolean;
  createdAt: string;
  expiresAt?: string;
  lastUsedAt?: string;
  ipWhitelist?: string[];
  revokedAt?: string;
  revokedByUsername?: string;
}

/** Returned only at creation — secretKey is shown ONCE and cannot be retrieved later. */
export interface ApiKeyCreateResponse extends ApiKey {
  secretKey: string;
  warning?: string;
}

/** Provisioning result returned when creating a new admin. */
export interface AdminProvisioning {
  adminId: number;
  username: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  adminType: AdminType;
  tenantId?: number;
  enrollmentId: number;
  createdAt: string;
}

/** Onboarding credentials — enrollmentProofToken and challenge are shown once. */
export interface AdminOnboarding {
  enrollmentId: number;
  enrollmentProofToken: string;
  enrollmentChallenge: number;
  recoveryCodes: string[] | null;
}
