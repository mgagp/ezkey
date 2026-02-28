/** Spring Data Page<T> response structure — returned by all paginated Admin API endpoints. */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  /** Zero-based current page number. */
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
}

/** RFC 9457 Problem Details error shape returned by the Admin API on failures. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
}

/**
 * Request body for POST /api/v1/admin/auth/login.
 * Always set nonBlocking: true so the response is immediate.
 */
export interface AdminLoginRequest {
  username: string;
  /** User can opt-in to a 2-digit challenge code for additional verification. */
  challengeRequested?: boolean;
  /** Must be true — returns authAttemptId immediately so the UI can show a countdown. */
  nonBlocking: true;
}

/**
 * Response from POST /api/v1/admin/auth/login and POST /api/v1/admin/auth/passwordless-wait.
 * Fields are nullable (@JsonInclude NON_NULL), present only when relevant.
 */
export interface AdminLoginResponse {
  success: boolean;
  message?: string;
  status?: 'pending' | 'accepted' | 'rejected';
  /** Bearer token — only present on success. */
  token?: string;
  adminType?: 'GLOBAL_ADMIN' | 'TENANT_ADMIN' | 'INTEGRATION_ADMIN';
  username?: string;
  /** ISO-8601 OffsetDateTime — expiration of the auth attempt or the token. */
  expiresAt?: string;
  /** Auth attempt ID — use to call /passwordless-wait. */
  authAttemptId?: number;
  /**
   * 2-digit challenge code.
   * Display as zero-padded string: String(code).padStart(2, '0').
   */
  challengeCode?: number;
}

/** Request body for POST /api/v1/admin/auth/passwordless-wait. */
export interface PasswordlessWaitRequest {
  authAttemptId: number;
  /** Required only when challengeRequested was true on login. */
  challengeCode?: number;
}

/** Request body for POST /api/v1/enrollments. */
export interface EnrollmentCreateRequest {
  integrationId: number;
  name: string;
  authAttemptChallengeRequired?: boolean;
  contactEmail?: string;
  userIdentifier?: string;
}

/** Request body for POST /api/v1/admins/tenant. */
export interface AdminCreateRequest {
  username: string;
  email?: string;
  firstName?: string;
  lastName?: string;
}

/** Request body for POST /api/v1/api-keys. */
export interface ApiKeyCreateRequest {
  integrationId: number;
  description?: string;
  /** ISO-8601 datetime; must be in the future. Omit for no expiration. */
  expiresAt?: string;
  /** Array of IP addresses or CIDR ranges. Omit for no restriction. */
  ipWhitelist?: string[];
}
