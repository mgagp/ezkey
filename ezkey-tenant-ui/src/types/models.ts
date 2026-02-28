/** Integration — an application protected by Ezkey MFA. */
export interface Integration {
  id: number;
  code: string;
  logo?: string;
  active: boolean;
  createdAt: string;
  tenantId?: number;
  i18n: IntegrationI18n[];
}

export interface IntegrationI18n {
  language: string;
  name: string;
  description?: string;
}

/** Enrollment status values from the API. */
export type EnrollmentStatus = 'CREATED' | 'BOUND' | 'VERIFIED' | 'INVALID';

/** Enrollment — the association between a user device and an integration. */
export interface Enrollment {
  enrollmentId: number;
  integrationId: number;
  enrollmentName: string;
  enrollmentStatus: EnrollmentStatus;
  enrollmentActive: boolean;
  enrollmentChallenge?: number;
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
  authAttemptChallenge?: string;
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
  eventStatus: string;
  apiName?: string;
  ipAddress?: string;
  adminId?: number;
  integrationId?: number;
  enrollmentId?: number;
  authAttemptId?: number;
  tenantId?: number;
  eventDetails?: string;
  errorMessage?: string;
  createdAt: string;
}

/** ApiKey — machine-to-machine credentials scoped to an integration. */
export interface ApiKey {
  apiKeyId: number;
  integrationKey: string;
  description?: string;
  active: boolean;
  createdAt: string;
  expiresAt?: string;
  ipWhitelist?: string[];
  integrationId: number;
}
