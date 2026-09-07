/* tslint:disable */
/* eslint-disable */
/**
 * 
 * @export
 * @interface AdminActivationRequestDto
 */
export interface AdminActivationRequestDto {
    /**
     * 
     * @type {string}
     * @memberof AdminActivationRequestDto
     */
    activationCode: string;
}
/**
 * Response for first-time administrator activation
 * @export
 * @interface AdminActivationResponseDto
 */
export interface AdminActivationResponseDto {
    /**
     * Indicates whether activation succeeded
     * @type {boolean}
     * @memberof AdminActivationResponseDto
     */
    success?: boolean;
    /**
     * Response message
     * @type {string}
     * @memberof AdminActivationResponseDto
     */
    message?: string;
    /**
     * Activated administrator username
     * @type {string}
     * @memberof AdminActivationResponseDto
     */
    username?: string;
    /**
     * Enrollment ID created during activation
     * @type {number}
     * @memberof AdminActivationResponseDto
     */
    enrollmentId?: number;
    /**
     * Enrollment proof token shown once for binding
     * @type {string}
     * @memberof AdminActivationResponseDto
     */
    enrollmentProofToken?: string;
    /**
     * Enrollment challenge code shown once for binding
     * @type {number}
     * @memberof AdminActivationResponseDto
     */
    enrollmentChallenge?: number;
    /**
     * Recovery codes are intentionally omitted from this unauthenticated activation response and must be revealed later through an authenticated recovery-code management flow
     * @type {Array<string>}
     * @memberof AdminActivationResponseDto
     */
    recoveryCodes?: Array<string>;
}
/**
 * Request DTO for creating an administrator
 * @export
 * @interface AdminCreateRequestDto
 */
export interface AdminCreateRequestDto {
    /**
     * Unique username for the administrator
     * @type {string}
     * @memberof AdminCreateRequestDto
     */
    username: string;
    /**
     * Email address (required for global admins, optional for tenant admins)
     * @type {string}
     * @memberof AdminCreateRequestDto
     */
    email?: string;
    /**
     * Phone number (optional contact metadata; accepts common separators and is normalized to E.164 on write)
     * @type {string}
     * @memberof AdminCreateRequestDto
     */
    phoneNumber?: string;
    /**
     * First name (required for global admins, optional for tenant admins)
     * @type {string}
     * @memberof AdminCreateRequestDto
     */
    firstName?: string;
    /**
     * Last name (required for global admins, optional for tenant admins)
     * @type {string}
     * @memberof AdminCreateRequestDto
     */
    lastName?: string;
    /**
     * Tenant ID (required for tenant admin creation, ignored for global admin)
     * @type {number}
     * @memberof AdminCreateRequestDto
     */
    tenantId?: number;
    /**
     * Onboarding mode. IMMEDIATE creates enrollment and recovery codes now. ACTIVATION_CODE creates a pending admin and returns a one-time activation code instead.
     * @type {string}
     * @memberof AdminCreateRequestDto
     */
    onboardingMode?: AdminCreateRequestDtoOnboardingModeEnum;
}


/**
 * @export
 */
export const AdminCreateRequestDtoOnboardingModeEnum = {
    Immediate: 'IMMEDIATE',
    ActivationCode: 'ACTIVATION_CODE'
} as const;
export type AdminCreateRequestDtoOnboardingModeEnum = typeof AdminCreateRequestDtoOnboardingModeEnum[keyof typeof AdminCreateRequestDtoOnboardingModeEnum];

/**
 * Request DTO for passwordless administrator login
 * @export
 * @interface AdminLoginRequestDto
 */
export interface AdminLoginRequestDto {
    /**
     * Administrator username for passwordless authentication
     * @type {string}
     * @memberof AdminLoginRequestDto
     */
    username: string;
    /**
     * Request challenge verification on device (6-digit code). When true, returns authAttemptId and challengeCode for two-step flow
     * @type {boolean}
     * @memberof AdminLoginRequestDto
     */
    challengeRequested?: boolean;
    /**
     * Request immediate response with authAttemptId and expiresAt instead of blocking until device responds. Allows client to display countdown timer and poll /passwordless-wait endpoint. When false (default), blocking wait is used when no challenge is required (backward compatible). This flag has no effect when challenge is required (challenge flow is always non-blocking).
     * @type {boolean}
     * @memberof AdminLoginRequestDto
     */
    nonBlocking?: boolean;
}
/**
 * Response DTO for passwordless administrator login
 * @export
 * @interface AdminLoginResponseDto
 */
export interface AdminLoginResponseDto {
    /**
     * Indicates if the authentication was successful
     * @type {boolean}
     * @memberof AdminLoginResponseDto
     */
    success?: boolean;
    /**
     * Response message describing authentication result
     * @type {string}
     * @memberof AdminLoginResponseDto
     */
    message?: string;
    /**
     * Authentication attempt status
     * @type {string}
     * @memberof AdminLoginResponseDto
     */
    status?: AdminLoginResponseDtoStatusEnum;
    /**
     * Bearer token for authenticated API requests. Omitted when the API issues an HttpOnly session cookie (browser split UI/API).
     * @type {string}
     * @memberof AdminLoginResponseDto
     */
    token?: string;
    /**
     * Type of administrator
     * @type {string}
     * @memberof AdminLoginResponseDto
     */
    adminType?: AdminLoginResponseDtoAdminTypeEnum;
    /**
     * Administrator username
     * @type {string}
     * @memberof AdminLoginResponseDto
     */
    username?: string;
    /**
     * Expiration timestamp (UTC). When authentication succeeded: bearer token expiry. When status is pending (two-step passwordless): authentication attempt expiry — matches the persisted attempt and core setting ezkey.core.auth-attempt.ttl-seconds (not a fixed duration).
     * @type {string}
     * @memberof AdminLoginResponseDto
     */
    expiresAt?: string;
    /**
     * Authentication attempt ID for two-step flow
     * @type {number}
     * @memberof AdminLoginResponseDto
     */
    authAttemptId?: number;
    /**
     * 6-digit challenge code for device verification
     * @type {number}
     * @memberof AdminLoginResponseDto
     */
    challengeCode?: number;
    /**
     * Administrator ID for the authenticated session
     * @type {number}
     * @memberof AdminLoginResponseDto
     */
    adminId?: number;
    /**
     * Tenant scope ID when the administrator is tenant- or integration-scoped; null for global administrators
     * @type {number}
     * @memberof AdminLoginResponseDto
     */
    tenantId?: number;
    /**
     * Non-secret CSRF token to send in X-CSRF-TOKEN for cookie-authenticated unsafe requests. Present only in browser session cookie mode.
     * @type {string}
     * @memberof AdminLoginResponseDto
     */
    csrfToken?: string;
}


/**
 * @export
 */
export const AdminLoginResponseDtoStatusEnum = {
    Pending: 'pending',
    Accepted: 'accepted',
    Rejected: 'rejected'
} as const;
export type AdminLoginResponseDtoStatusEnum = typeof AdminLoginResponseDtoStatusEnum[keyof typeof AdminLoginResponseDtoStatusEnum];

/**
 * @export
 */
export const AdminLoginResponseDtoAdminTypeEnum = {
    GlobalAdmin: 'GLOBAL_ADMIN',
    TenantAdmin: 'TENANT_ADMIN',
    IntegrationAdmin: 'INTEGRATION_ADMIN'
} as const;
export type AdminLoginResponseDtoAdminTypeEnum = typeof AdminLoginResponseDtoAdminTypeEnum[keyof typeof AdminLoginResponseDtoAdminTypeEnum];

/**
 * 
 * @export
 * @interface AdminPasswordlessWaitRequestDto
 */
export interface AdminPasswordlessWaitRequestDto {
    /**
     * 
     * @type {number}
     * @memberof AdminPasswordlessWaitRequestDto
     */
    authAttemptId: number;
    /**
     * 
     * @type {number}
     * @memberof AdminPasswordlessWaitRequestDto
     */
    challengeCode?: number;
}
/**
 * 
 * @export
 * @interface AdminRecoveryRequestDto
 */
export interface AdminRecoveryRequestDto {
    /**
     * 
     * @type {string}
     * @memberof AdminRecoveryRequestDto
     */
    username: string;
    /**
     * 
     * @type {string}
     * @memberof AdminRecoveryRequestDto
     */
    recoveryCode: string;
}
/**
 * 
 * @export
 * @interface AdminRecoveryResponseDto
 */
export interface AdminRecoveryResponseDto {
    /**
     * 
     * @type {boolean}
     * @memberof AdminRecoveryResponseDto
     */
    success?: boolean;
    /**
     * 
     * @type {string}
     * @memberof AdminRecoveryResponseDto
     */
    message?: string;
    /**
     * 
     * @type {string}
     * @memberof AdminRecoveryResponseDto
     */
    recoveryToken?: string;
    /**
     * 
     * @type {string}
     * @memberof AdminRecoveryResponseDto
     */
    expiresAt?: string;
    /**
     * 
     * @type {number}
     * @memberof AdminRecoveryResponseDto
     */
    codesRemaining?: number;
    /**
     * 
     * @type {number}
     * @memberof AdminRecoveryResponseDto
     */
    enrollmentId?: number;
}
/**
 * Response DTO containing administrator information for listing purposes
 * @export
 * @interface AdminResponseDto
 */
export interface AdminResponseDto {
    /**
     * Unique identifier for the administrator
     * @type {number}
     * @memberof AdminResponseDto
     */
    adminId?: number;
    /**
     * Optimistic lock version. Include in PATCH requests to prevent concurrent update conflicts.
     * @type {number}
     * @memberof AdminResponseDto
     */
    version?: number;
    /**
     * Username for the administrator
     * @type {string}
     * @memberof AdminResponseDto
     */
    username?: string;
    /**
     * Email address
     * @type {string}
     * @memberof AdminResponseDto
     */
    email?: string;
    /**
     * Phone number stored in canonical E.164 format
     * @type {string}
     * @memberof AdminResponseDto
     */
    phoneNumber?: string;
    /**
     * First name
     * @type {string}
     * @memberof AdminResponseDto
     */
    firstName?: string;
    /**
     * Last name
     * @type {string}
     * @memberof AdminResponseDto
     */
    lastName?: string;
    /**
     * Type of administrator
     * @type {string}
     * @memberof AdminResponseDto
     */
    adminType?: AdminResponseDtoAdminTypeEnum;
    /**
     * Tenant ID (null for global admins)
     * @type {number}
     * @memberof AdminResponseDto
     */
    tenantId?: number;
    /**
     * Tenant display name from the tenant record (null for global administrators)
     * @type {string}
     * @memberof AdminResponseDto
     */
    tenantName?: string | null;
    /**
     * Enrollment ID for MFA (passwordless admin identity). Null if not linked to an enrollment.
     * @type {number}
     * @memberof AdminResponseDto
     */
    enrollmentId?: number | null;
    /**
     * Human-readable name of the linked MFA enrollment (null when enrollmentId is null)
     * @type {string}
     * @memberof AdminResponseDto
     */
    enrollmentName?: string | null;
    /**
     * Flag indicating if the administrator is currently active
     * @type {boolean}
     * @memberof AdminResponseDto
     */
    active?: boolean;
    /**
     * Explicit lifecycle status for the administrator account
     * @type {string}
     * @memberof AdminResponseDto
     */
    lifecycleStatus?: AdminResponseDtoLifecycleStatusEnum;
    /**
     * Timestamp when the administrator was created
     * @type {string}
     * @memberof AdminResponseDto
     */
    createdAt?: string;
    /**
     * Timestamp of last successful login (null if never logged in)
     * @type {string}
     * @memberof AdminResponseDto
     */
    lastLoginAt?: string | null;
    /**
     * Whether this administrator currently has a recovery-code set stored server-side
     * @type {boolean}
     * @memberof AdminResponseDto
     */
    hasRecoveryCodes?: boolean;
    /**
     * Whether this administrator is fully operational (active and, for tenant admins, tenant also active)
     * @type {boolean}
     * @memberof AdminResponseDto
     */
    operational?: boolean;
}


/**
 * @export
 */
export const AdminResponseDtoAdminTypeEnum = {
    GlobalAdmin: 'GLOBAL_ADMIN',
    TenantAdmin: 'TENANT_ADMIN',
    IntegrationAdmin: 'INTEGRATION_ADMIN'
} as const;
export type AdminResponseDtoAdminTypeEnum = typeof AdminResponseDtoAdminTypeEnum[keyof typeof AdminResponseDtoAdminTypeEnum];

/**
 * @export
 */
export const AdminResponseDtoLifecycleStatusEnum = {
    PendingActivation: 'PENDING_ACTIVATION',
    Active: 'ACTIVE',
    Deactivated: 'DEACTIVATED'
} as const;
export type AdminResponseDtoLifecycleStatusEnum = typeof AdminResponseDtoLifecycleStatusEnum[keyof typeof AdminResponseDtoLifecycleStatusEnum];

/**
 * Current administrator session metadata
 * @export
 * @interface AdminSessionResponseDto
 */
export interface AdminSessionResponseDto {
    /**
     * Administrator username
     * @type {string}
     * @memberof AdminSessionResponseDto
     */
    username?: string;
    /**
     * Type of administrator
     * @type {string}
     * @memberof AdminSessionResponseDto
     */
    adminType?: AdminSessionResponseDtoAdminTypeEnum;
    /**
     * Current session expiration timestamp
     * @type {string}
     * @memberof AdminSessionResponseDto
     */
    expiresAt?: string;
    /**
     * Administrator ID for the authenticated session
     * @type {number}
     * @memberof AdminSessionResponseDto
     */
    adminId?: number;
    /**
     * Tenant scope ID when applicable; null for global administrators
     * @type {number}
     * @memberof AdminSessionResponseDto
     */
    tenantId?: number;
    /**
     * Non-secret CSRF token to send in X-CSRF-TOKEN for cookie-authenticated unsafe requests
     * @type {string}
     * @memberof AdminSessionResponseDto
     */
    csrfToken?: string;
}


/**
 * @export
 */
export const AdminSessionResponseDtoAdminTypeEnum = {
    GlobalAdmin: 'GLOBAL_ADMIN',
    TenantAdmin: 'TENANT_ADMIN',
    IntegrationAdmin: 'INTEGRATION_ADMIN'
} as const;
export type AdminSessionResponseDtoAdminTypeEnum = typeof AdminSessionResponseDtoAdminTypeEnum[keyof typeof AdminSessionResponseDtoAdminTypeEnum];

/**
 * Request DTO for partial update of administrator profile
 * @export
 * @interface AdminUpdateRequestDto
 */
export interface AdminUpdateRequestDto {
    /**
     * Optimistic lock version from GET response. When provided, update fails with 409 if resource was modified since last fetch.
     * @type {number}
     * @memberof AdminUpdateRequestDto
     */
    version?: number;
    /**
     * First name of the administrator
     * @type {string}
     * @memberof AdminUpdateRequestDto
     */
    firstName?: string;
    /**
     * Last name of the administrator
     * @type {string}
     * @memberof AdminUpdateRequestDto
     */
    lastName?: string;
    /**
     * Email address (must be unique)
     * @type {string}
     * @memberof AdminUpdateRequestDto
     */
    email?: string;
    /**
     * Phone number for the administrator. Accepts common separators and is normalized to E.164 on write.
     * @type {string}
     * @memberof AdminUpdateRequestDto
     */
    phoneNumber?: string;
    /**
     * Whether challenge verification is required during passwordless login
     * @type {boolean}
     * @memberof AdminUpdateRequestDto
     */
    challengeRequired?: boolean;
}
/**
 * Operator-facing alert (read model).
 * @export
 * @interface AlertResponseDto
 */
export interface AlertResponseDto {
    /**
     * 
     * @type {number}
     * @memberof AlertResponseDto
     */
    alertId?: number;
    /**
     * 
     * @type {string}
     * @memberof AlertResponseDto
     */
    alertType?: AlertResponseDtoAlertTypeEnum;
    /**
     * 
     * @type {string}
     * @memberof AlertResponseDto
     */
    severity?: AlertResponseDtoSeverityEnum;
    /**
     * 
     * @type {string}
     * @memberof AlertResponseDto
     */
    status?: AlertResponseDtoStatusEnum;
    /**
     * 
     * @type {string}
     * @memberof AlertResponseDto
     */
    dedupeKey?: string;
    /**
     * Producer-defined JSON payload (string). Shape depends on alertType; clients aware of the type render structured fields, others fall back to raw display.
     * @type {string}
     * @memberof AlertResponseDto
     */
    payload?: string;
    /**
     * 
     * @type {number}
     * @memberof AlertResponseDto
     */
    occurrenceCount?: number;
    /**
     * 
     * @type {string}
     * @memberof AlertResponseDto
     */
    createdAt?: string;
    /**
     * 
     * @type {string}
     * @memberof AlertResponseDto
     */
    lastSeenAt?: string;
    /**
     * 
     * @type {string}
     * @memberof AlertResponseDto
     */
    resolvedAt?: string;
    /**
     * 
     * @type {number}
     * @memberof AlertResponseDto
     */
    resolvedByAdminId?: number;
    /**
     * 
     * @type {string}
     * @memberof AlertResponseDto
     */
    resolutionReason?: AlertResponseDtoResolutionReasonEnum;
}


/**
 * @export
 */
export const AlertResponseDtoAlertTypeEnum = {
    ChainGapPending: 'AUDIT_CHAIN_GAP_PENDING',
    ChainHeartbeatStale: 'AUDIT_CHAIN_HEARTBEAT_STALE',
    IntegrityRupture: 'AUDIT_INTEGRITY_RUPTURE'
} as const;
export type AlertResponseDtoAlertTypeEnum = typeof AlertResponseDtoAlertTypeEnum[keyof typeof AlertResponseDtoAlertTypeEnum];

/**
 * @export
 */
export const AlertResponseDtoSeverityEnum = {
    Info: 'INFO',
    Warning: 'WARNING',
    Critical: 'CRITICAL'
} as const;
export type AlertResponseDtoSeverityEnum = typeof AlertResponseDtoSeverityEnum[keyof typeof AlertResponseDtoSeverityEnum];

/**
 * @export
 */
export const AlertResponseDtoStatusEnum = {
    Open: 'OPEN',
    Resolved: 'RESOLVED'
} as const;
export type AlertResponseDtoStatusEnum = typeof AlertResponseDtoStatusEnum[keyof typeof AlertResponseDtoStatusEnum];

/**
 * @export
 */
export const AlertResponseDtoResolutionReasonEnum = {
    GapDeclared: 'GAP_DECLARED',
    HeartbeatRestored: 'HEARTBEAT_RESTORED',
    Manual: 'MANUAL',
    IntegrityRuptureConciliated: 'INTEGRITY_RUPTURE_CONCILIATED'
} as const;
export type AlertResponseDtoResolutionReasonEnum = typeof AlertResponseDtoResolutionReasonEnum[keyof typeof AlertResponseDtoResolutionReasonEnum];

/**
 * Request to create a new API key for an integration
 * @export
 * @interface ApiKeyCreateRequestDto
 */
export interface ApiKeyCreateRequestDto {
    /**
     * Integration ID to create the API key for
     * @type {number}
     * @memberof ApiKeyCreateRequestDto
     */
    integrationId: number;
    /**
     * Optional human-readable description to identify this API key (e.g., 'Production Server')
     * @type {string}
     * @memberof ApiKeyCreateRequestDto
     */
    description?: string;
    /**
     * Optional expiration date for automatic key rotation enforcement (null = no expiration). Must be in the future.
     * @type {string}
     * @memberof ApiKeyCreateRequestDto
     */
    expiresAt?: string;
    /**
     * Optional array of IP addresses or CIDR ranges allowed to use this key (recommended for production)
     * @type {Array<string>}
     * @memberof ApiKeyCreateRequestDto
     */
    ipWhitelist?: Array<string>;
}
/**
 * Response containing newly created API key pair with secret key shown ONCE
 * @export
 * @interface ApiKeyCreateResponseDto
 */
export interface ApiKeyCreateResponseDto {
    /**
     * Unique identifier for the API key record
     * @type {number}
     * @memberof ApiKeyCreateResponseDto
     */
    apiKeyId?: number;
    /**
     * Public integration key (safe to display, used as HTTP Basic Auth username)
     * @type {string}
     * @memberof ApiKeyCreateResponseDto
     */
    integrationKey?: string;
    /**
     * Secret key (SHOWN ONCE ONLY - save immediately! Used as HTTP Basic Auth password)
     * @type {string}
     * @memberof ApiKeyCreateResponseDto
     */
    secretKey?: string;
    /**
     * Optional description to identify this API key
     * @type {string}
     * @memberof ApiKeyCreateResponseDto
     */
    description?: string;
    /**
     * Creation timestamp in UTC
     * @type {string}
     * @memberof ApiKeyCreateResponseDto
     */
    createdAt?: string;
    /**
     * Optional expiration date (null = no expiration)
     * @type {string}
     * @memberof ApiKeyCreateResponseDto
     */
    expiresAt?: string;
    /**
     * Optional IP whitelist (null = no restrictions)
     * @type {Array<string>}
     * @memberof ApiKeyCreateResponseDto
     */
    ipWhitelist?: Array<string>;
    /**
     * Security warning about saving the secret key
     * @type {string}
     * @memberof ApiKeyCreateResponseDto
     */
    warning?: string;
}
/**
 * Response containing API key details (secret key NOT included)
 * @export
 * @interface ApiKeyResponseDto
 */
export interface ApiKeyResponseDto {
    /**
     * Unique identifier for the API key record
     * @type {number}
     * @memberof ApiKeyResponseDto
     */
    apiKeyId?: number;
    /**
     * Optimistic lock version. Include in PATCH requests to prevent concurrent update
     * @type {number}
     * @memberof ApiKeyResponseDto
     */
    version?: number;
    /**
     * Integration ID this API key authenticates for
     * @type {number}
     * @memberof ApiKeyResponseDto
     */
    integrationId?: number;
    /**
     * Integration display name; populated when integration context is joined (list and GET by ID)
     * @type {string}
     * @memberof ApiKeyResponseDto
     */
    integrationName?: string;
    /**
     * Tenant identifier for the integration; populated when integration context is joined
     * @type {number}
     * @memberof ApiKeyResponseDto
     */
    tenantId?: number;
    /**
     * Tenant display name for the integration; populated when integration context is joined
     * @type {string}
     * @memberof ApiKeyResponseDto
     */
    tenantName?: string;
    /**
     * Public integration key (safe to display)
     * @type {string}
     * @memberof ApiKeyResponseDto
     */
    integrationKey?: string;
    /**
     * Optional description to identify this API key
     * @type {string}
     * @memberof ApiKeyResponseDto
     */
    description?: string;
    /**
     * Whether the key is active (false if revoked)
     * @type {boolean}
     * @memberof ApiKeyResponseDto
     */
    active?: boolean;
    /**
     * Creation timestamp in UTC
     * @type {string}
     * @memberof ApiKeyResponseDto
     */
    createdAt?: string;
    /**
     * Optional expiration date (null = no expiration)
     * @type {string}
     * @memberof ApiKeyResponseDto
     */
    expiresAt?: string;
    /**
     * Last successful authentication timestamp (null if never used)
     * @type {string}
     * @memberof ApiKeyResponseDto
     */
    lastUsedAt?: string;
    /**
     * Optional IP whitelist (null = no restrictions)
     * @type {Array<string>}
     * @memberof ApiKeyResponseDto
     */
    ipWhitelist?: Array<string>;
    /**
     * Revocation timestamp (null if active)
     * @type {string}
     * @memberof ApiKeyResponseDto
     */
    revokedAt?: string;
    /**
     * Admin who revoked this key (null if active)
     * @type {string}
     * @memberof ApiKeyResponseDto
     */
    revokedByUsername?: string;
    /**
     * Whether this API key is fully operational (active, not expired, integration and tenant also active)
     * @type {boolean}
     * @memberof ApiKeyResponseDto
     */
    operational?: boolean;
}
/**
 * Request DTO for partial update of API key configuration
 * @export
 * @interface ApiKeyUpdateRequestDto
 */
export interface ApiKeyUpdateRequestDto {
    /**
     * Optimistic lock version from GET response. When provided, update fails with 409 if resource was modified since last fetch.
     * @type {number}
     * @memberof ApiKeyUpdateRequestDto
     */
    version?: number;
    /**
     * IP whitelist (null or empty = no restrictions). Each entry: IP address or CIDR (e.g. 192.168.1.0/24)
     * @type {Array<string>}
     * @memberof ApiKeyUpdateRequestDto
     */
    ipWhitelist?: Array<string>;
    /**
     * Human-readable description for this API key
     * @type {string}
     * @memberof ApiKeyUpdateRequestDto
     */
    description?: string;
}
/**
 * 
 * @export
 * @interface ArchiveConfirmArchivedRequest
 */
export interface ArchiveConfirmArchivedRequest {
    /**
     * 
     * @type {string}
     * @memberof ArchiveConfirmArchivedRequest
     */
    periodStart?: string;
    /**
     * 
     * @type {string}
     * @memberof ArchiveConfirmArchivedRequest
     */
    periodEnd?: string;
    /**
     * 
     * @type {number}
     * @memberof ArchiveConfirmArchivedRequest
     */
    checkpointIdFrom?: number;
    /**
     * 
     * @type {number}
     * @memberof ArchiveConfirmArchivedRequest
     */
    checkpointIdTo?: number;
    /**
     * 
     * @type {string}
     * @memberof ArchiveConfirmArchivedRequest
     */
    exportBundleDigest: string;
    /**
     * 
     * @type {string}
     * @memberof ArchiveConfirmArchivedRequest
     */
    archivedAt?: string;
}
/**
 * 
 * @export
 * @interface ArchiveConfirmArchivedResult
 */
export interface ArchiveConfirmArchivedResult {
    /**
     * 
     * @type {string}
     * @memberof ArchiveConfirmArchivedResult
     */
    periodStart?: string;
    /**
     * 
     * @type {string}
     * @memberof ArchiveConfirmArchivedResult
     */
    periodEnd?: string;
    /**
     * 
     * @type {number}
     * @memberof ArchiveConfirmArchivedResult
     */
    checkpointsExported?: number;
    /**
     * 
     * @type {string}
     * @memberof ArchiveConfirmArchivedResult
     */
    exportBundleDigest?: string;
    /**
     * 
     * @type {string}
     * @memberof ArchiveConfirmArchivedResult
     */
    exportedAt?: string;
    /**
     * 
     * @type {number}
     * @memberof ArchiveConfirmArchivedResult
     */
    auditLogId?: number;
}
/**
 * 
 * @export
 * @interface ArchiveEligibilityResult
 */
export interface ArchiveEligibilityResult {
    /**
     * 
     * @type {boolean}
     * @memberof ArchiveEligibilityResult
     */
    externalArchivalEnabled?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof ArchiveEligibilityResult
     */
    confirmationRequired?: boolean;
    /**
     * 
     * @type {number}
     * @memberof ArchiveEligibilityResult
     */
    sealedCheckpointCount?: number;
    /**
     * 
     * @type {string}
     * @memberof ArchiveEligibilityResult
     */
    oldestSealedWindowStart?: string;
    /**
     * 
     * @type {string}
     * @memberof ArchiveEligibilityResult
     */
    newestSealedWindowEnd?: string;
    /**
     * 
     * @type {number}
     * @memberof ArchiveEligibilityResult
     */
    checkpointIdFrom?: number;
    /**
     * 
     * @type {number}
     * @memberof ArchiveEligibilityResult
     */
    checkpointIdTo?: number;
}
/**
 * 
 * @export
 * @interface ArchiveSealRequest
 */
export interface ArchiveSealRequest {
    /**
     * 
     * @type {string}
     * @memberof ArchiveSealRequest
     */
    periodStart?: string;
    /**
     * 
     * @type {string}
     * @memberof ArchiveSealRequest
     */
    periodEnd?: string;
    /**
     * 
     * @type {number}
     * @memberof ArchiveSealRequest
     */
    checkpointIdFrom?: number;
    /**
     * 
     * @type {number}
     * @memberof ArchiveSealRequest
     */
    checkpointIdTo?: number;
    /**
     * 
     * @type {string}
     * @memberof ArchiveSealRequest
     */
    justification: string;
}
/**
 * 
 * @export
 * @interface ArchiveSealResult
 */
export interface ArchiveSealResult {
    /**
     * 
     * @type {string}
     * @memberof ArchiveSealResult
     */
    periodStart?: string;
    /**
     * 
     * @type {string}
     * @memberof ArchiveSealResult
     */
    periodEnd?: string;
    /**
     * 
     * @type {number}
     * @memberof ArchiveSealResult
     */
    checkpointsSealed?: number;
    /**
     * 
     * @type {string}
     * @memberof ArchiveSealResult
     */
    sealChainHmac?: string;
    /**
     * 
     * @type {number}
     * @memberof ArchiveSealResult
     */
    auditLogId?: number;
    /**
     * 
     * @type {string}
     * @memberof ArchiveSealResult
     */
    justification?: string;
}
/**
 * 
 * @export
 * @interface AuditChainCheckpointResponseDto
 */
export interface AuditChainCheckpointResponseDto {
    /**
     * 
     * @type {number}
     * @memberof AuditChainCheckpointResponseDto
     */
    checkpointId?: number;
    /**
     * 
     * @type {string}
     * @memberof AuditChainCheckpointResponseDto
     */
    windowStart?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditChainCheckpointResponseDto
     */
    windowEnd?: string;
    /**
     * 
     * @type {number}
     * @memberof AuditChainCheckpointResponseDto
     */
    entryCount?: number;
    /**
     * 
     * @type {number}
     * @memberof AuditChainCheckpointResponseDto
     */
    firstEntryId?: number;
    /**
     * 
     * @type {number}
     * @memberof AuditChainCheckpointResponseDto
     */
    lastEntryId?: number;
    /**
     * 
     * @type {string}
     * @memberof AuditChainCheckpointResponseDto
     */
    entriesDigest?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditChainCheckpointResponseDto
     */
    prevChainHmac?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditChainCheckpointResponseDto
     */
    chainHmac?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditChainCheckpointResponseDto
     */
    createdAt?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditChainCheckpointResponseDto
     */
    lifecycleState?: AuditChainCheckpointResponseDtoLifecycleStateEnum;
    /**
     * 
     * @type {string}
     * @memberof AuditChainCheckpointResponseDto
     */
    checkpointType?: AuditChainCheckpointResponseDtoCheckpointTypeEnum;
    /**
     * 
     * @type {string}
     * @memberof AuditChainCheckpointResponseDto
     */
    notes?: string;
}


/**
 * @export
 */
export const AuditChainCheckpointResponseDtoLifecycleStateEnum = {
    Active: 'ACTIVE',
    Sealed: 'SEALED',
    Exported: 'EXPORTED',
    Purgeable: 'PURGEABLE',
    Purged: 'PURGED'
} as const;
export type AuditChainCheckpointResponseDtoLifecycleStateEnum = typeof AuditChainCheckpointResponseDtoLifecycleStateEnum[keyof typeof AuditChainCheckpointResponseDtoLifecycleStateEnum];

/**
 * @export
 */
export const AuditChainCheckpointResponseDtoCheckpointTypeEnum = {
    Regular: 'REGULAR',
    ArchiveSeal: 'ARCHIVE_SEAL',
    GapDeclaration: 'GAP_DECLARATION',
    ManipulationConciliation: 'MANIPULATION_CONCILIATION'
} as const;
export type AuditChainCheckpointResponseDtoCheckpointTypeEnum = typeof AuditChainCheckpointResponseDtoCheckpointTypeEnum[keyof typeof AuditChainCheckpointResponseDtoCheckpointTypeEnum];

/**
 * Operational audit-chain heartbeat incident summary
 * @export
 * @interface AuditChainIncidentResponseDto
 */
export interface AuditChainIncidentResponseDto {
    /**
     * 
     * @type {number}
     * @memberof AuditChainIncidentResponseDto
     */
    incidentId?: number;
    /**
     * 
     * @type {string}
     * @memberof AuditChainIncidentResponseDto
     */
    status?: AuditChainIncidentResponseDtoStatusEnum;
    /**
     * 
     * @type {number}
     * @memberof AuditChainIncidentResponseDto
     */
    anchorCheckpointId?: number;
    /**
     * 
     * @type {string}
     * @memberof AuditChainIncidentResponseDto
     */
    staleSince?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditChainIncidentResponseDto
     */
    degradedSince?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditChainIncidentResponseDto
     */
    recoveredAt?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditChainIncidentResponseDto
     */
    justification?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditChainIncidentResponseDto
     */
    rootCause?: AuditChainIncidentResponseDtoRootCauseEnum;
    /**
     * 
     * @type {string}
     * @memberof AuditChainIncidentResponseDto
     */
    declaredAt?: string;
    /**
     * 
     * @type {number}
     * @memberof AuditChainIncidentResponseDto
     */
    declaredByAdminId?: number;
    /**
     * 
     * @type {string}
     * @memberof AuditChainIncidentResponseDto
     */
    createdAt?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditChainIncidentResponseDto
     */
    updatedAt?: string;
}


/**
 * @export
 */
export const AuditChainIncidentResponseDtoStatusEnum = {
    InProgress: 'IN_PROGRESS',
    RecoveredPendingDeclaration: 'RECOVERED_PENDING_DECLARATION',
    Closed: 'CLOSED'
} as const;
export type AuditChainIncidentResponseDtoStatusEnum = typeof AuditChainIncidentResponseDtoStatusEnum[keyof typeof AuditChainIncidentResponseDtoStatusEnum];

/**
 * @export
 */
export const AuditChainIncidentResponseDtoRootCauseEnum = {
    PlannedSystemUpgrade: 'PLANNED_SYSTEM_UPGRADE',
    AdminApiDown: 'ADMIN_API_DOWN',
    SchedulerFailure: 'SCHEDULER_FAILURE',
    DbUnavailable: 'DB_UNAVAILABLE',
    NetworkPartition: 'NETWORK_PARTITION',
    Misconfiguration: 'MISCONFIGURATION',
    Unknown: 'UNKNOWN'
} as const;
export type AuditChainIncidentResponseDtoRootCauseEnum = typeof AuditChainIncidentResponseDtoRootCauseEnum[keyof typeof AuditChainIncidentResponseDtoRootCauseEnum];

/**
 * 
 * @export
 * @interface AuditLogContextResponseDto
 */
export interface AuditLogContextResponseDto {
    /**
     * 
     * @type {number}
     * @memberof AuditLogContextResponseDto
     */
    anchorAuditLogId?: number;
    /**
     * 
     * @type {boolean}
     * @memberof AuditLogContextResponseDto
     */
    hasMoreBefore?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof AuditLogContextResponseDto
     */
    hasMoreAfter?: boolean;
    /**
     * 
     * @type {Array<AuditLogResponseDto>}
     * @memberof AuditLogContextResponseDto
     */
    items?: Array<AuditLogResponseDto>;
}
/**
 * 
 * @export
 * @interface AuditLogResponseDto
 */
export interface AuditLogResponseDto {
    /**
     * 
     * @type {number}
     * @memberof AuditLogResponseDto
     */
    auditLogId?: number;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    eventType?: AuditLogResponseDtoEventTypeEnum;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    eventAction?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    eventStatus?: AuditLogResponseDtoEventStatusEnum;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    apiName?: AuditLogResponseDtoApiNameEnum;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    ipAddress?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    userAgent?: string;
    /**
     * 
     * @type {number}
     * @memberof AuditLogResponseDto
     */
    adminId?: number;
    /**
     * 
     * @type {number}
     * @memberof AuditLogResponseDto
     */
    integrationId?: number;
    /**
     * 
     * @type {number}
     * @memberof AuditLogResponseDto
     */
    enrollmentId?: number;
    /**
     * 
     * @type {number}
     * @memberof AuditLogResponseDto
     */
    authAttemptId?: number;
    /**
     * 
     * @type {number}
     * @memberof AuditLogResponseDto
     */
    tenantId?: number;
    /**
     * 
     * @type {number}
     * @memberof AuditLogResponseDto
     */
    targetAdminId?: number;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    eventDetails?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    errorMessage?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    instanceId?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    entryHmac?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    createdAt?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    reason?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    adminUsername?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    targetAdminUsername?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    integrationName?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    enrollmentName?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditLogResponseDto
     */
    tenantName?: string;
}


/**
 * @export
 */
export const AuditLogResponseDtoEventTypeEnum = {
    AdminLogin: 'ADMIN_LOGIN',
    AdminLogout: 'ADMIN_LOGOUT',
    AdminPasswordChange: 'ADMIN_PASSWORD_CHANGE',
    AdminRecoveryUse: 'ADMIN_RECOVERY_USE',
    AdminRecoveryCodesIssued: 'ADMIN_RECOVERY_CODES_ISSUED',
    AdminRecoveryCodesRegenerated: 'ADMIN_RECOVERY_CODES_REGENERATED',
    AdminActivation: 'ADMIN_ACTIVATION',
    AdminActivationCodeReissued: 'ADMIN_ACTIVATION_CODE_REISSUED',
    AdminRecoveryEnrollmentReset: 'ADMIN_RECOVERY_ENROLLMENT_RESET',
    AdminCreated: 'ADMIN_CREATED',
    AdminProfileUpdated: 'ADMIN_PROFILE_UPDATED',
    AdminDeactivated: 'ADMIN_DEACTIVATED',
    AdminActivated: 'ADMIN_ACTIVATED',
    EnrollmentCreated: 'ENROLLMENT_CREATED',
    EnrollmentUpdated: 'ENROLLMENT_UPDATED',
    EnrollmentDeleted: 'ENROLLMENT_DELETED',
    EnrollmentBind: 'ENROLLMENT_BIND',
    EnrollmentVerify: 'ENROLLMENT_VERIFY',
    EnrollmentRevoked: 'ENROLLMENT_REVOKED',
    EnrollmentDeactivated: 'ENROLLMENT_DEACTIVATED',
    EnrollmentReactivated: 'ENROLLMENT_REACTIVATED',
    EnrollmentAuthAttemptBlocked: 'ENROLLMENT_AUTH_ATTEMPT_BLOCKED',
    EnrollmentExpired: 'ENROLLMENT_EXPIRED',
    AuthAttemptCreated: 'AUTH_ATTEMPT_CREATED',
    AuthAttemptPending: 'AUTH_ATTEMPT_PENDING',
    AuthAttemptRespond: 'AUTH_ATTEMPT_RESPOND',
    AuthAttemptCancelled: 'AUTH_ATTEMPT_CANCELLED',
    AuthAttemptExpired: 'AUTH_ATTEMPT_EXPIRED',
    ApiKeyCreated: 'API_KEY_CREATED',
    ApiKeyUpdated: 'API_KEY_UPDATED',
    ApiKeyRevoked: 'API_KEY_REVOKED',
    ApiKeyExpired: 'API_KEY_EXPIRED',
    ApiKeyAuthSuccess: 'API_KEY_AUTH_SUCCESS',
    ApiKeyAuthFailed: 'API_KEY_AUTH_FAILED',
    ApiKeyIpBlocked: 'API_KEY_IP_BLOCKED',
    SystemError: 'SYSTEM_ERROR',
    KeyIntroduced: 'KEY_INTRODUCED',
    KeyPromotedPrimary: 'KEY_PROMOTED_PRIMARY',
    KeyDemoted: 'KEY_DEMOTED',
    KeyDisabled: 'KEY_DISABLED',
    KeysetBackupCreated: 'KEYSET_BACKUP_CREATED',
    ReencryptionStarted: 'REENCRYPTION_STARTED',
    ReencryptionBatchProgress: 'REENCRYPTION_BATCH_PROGRESS',
    ReencryptionCompleted: 'REENCRYPTION_COMPLETED',
    ReencryptionFailed: 'REENCRYPTION_FAILED',
    ReencryptionResumed: 'REENCRYPTION_RESUMED',
    ReencryptionPaused: 'REENCRYPTION_PAUSED',
    IntegrationCreated: 'INTEGRATION_CREATED',
    IntegrationUpdated: 'INTEGRATION_UPDATED',
    IntegrationRetired: 'INTEGRATION_RETIRED',
    IntegrationDeleted: 'INTEGRATION_DELETED',
    TenantCreated: 'TENANT_CREATED',
    TenantUpdated: 'TENANT_UPDATED',
    TenantDeactivated: 'TENANT_DEACTIVATED',
    TenantActivated: 'TENANT_ACTIVATED',
    EvaluatorSelfRegistration: 'EVALUATOR_SELF_REGISTRATION',
    AuditChainArchiveSealed: 'AUDIT_CHAIN_ARCHIVE_SEALED',
    AuditChainArchiveExported: 'AUDIT_CHAIN_ARCHIVE_EXPORTED',
    AuditChainGapDeclared: 'AUDIT_CHAIN_GAP_DECLARED',
    AuditChainIncidentDeclared: 'AUDIT_CHAIN_INCIDENT_DECLARED',
    AuditIntegrityRuptureConciliated: 'AUDIT_INTEGRITY_RUPTURE_CONCILIATED',
    AuditEntryIntegrityConciliated: 'AUDIT_ENTRY_INTEGRITY_CONCILIATED',
    NightlyIntegrityValidationCompleted: 'NIGHTLY_INTEGRITY_VALIDATION_COMPLETED',
    AlertRaised: 'ALERT_RAISED',
    AlertResolved: 'ALERT_RESOLVED'
} as const;
export type AuditLogResponseDtoEventTypeEnum = typeof AuditLogResponseDtoEventTypeEnum[keyof typeof AuditLogResponseDtoEventTypeEnum];

/**
 * @export
 */
export const AuditLogResponseDtoEventStatusEnum = {
    Success: 'SUCCESS',
    Failure: 'FAILURE',
    Error: 'ERROR'
} as const;
export type AuditLogResponseDtoEventStatusEnum = typeof AuditLogResponseDtoEventStatusEnum[keyof typeof AuditLogResponseDtoEventStatusEnum];

/**
 * @export
 */
export const AuditLogResponseDtoApiNameEnum = {
    AdminApi: 'ADMIN_API',
    AuthApi: 'AUTH_API',
    IntegrationApi: 'INTEGRATION_API'
} as const;
export type AuditLogResponseDtoApiNameEnum = typeof AuditLogResponseDtoApiNameEnum[keyof typeof AuditLogResponseDtoApiNameEnum];

/**
 * Auth attempt creation data
 * @export
 * @interface AuthAttemptCreateRequestDto
 */
export interface AuthAttemptCreateRequestDto {
    /**
     * Enrollment ID (use this OR userIdentifier). Direct reference to enrollment.
     * @type {number}
     * @memberof AuthAttemptCreateRequestDto
     */
    enrollmentId?: number;
    /**
     * User identifier (username, user_id) for lookup within integration scope. Use this OR enrollmentId. When used with admin token, integrationId is required.
     * @type {string}
     * @memberof AuthAttemptCreateRequestDto
     */
    userIdentifier?: string;
    /**
     * Integration ID. Required when userIdentifier is used with admin token. Ignored when API key (derived from credentials).
     * @type {number}
     * @memberof AuthAttemptCreateRequestDto
     */
    integrationId?: number;
    /**
     * Whether a challenge code is requested for this attempt
     * @type {boolean}
     * @memberof AuthAttemptCreateRequestDto
     */
    challengeRequested: boolean;
    /**
     * Optional short title for the approval request, displayed as the mobile card header. Max 200 characters. Example: "Payment Approval"
     * @type {string}
     * @memberof AuthAttemptCreateRequestDto
     */
    contextTitle?: string;
    /**
     * Optional descriptive message explaining what the approver is authorizing. Max 2000 characters.
     * @type {string}
     * @memberof AuthAttemptCreateRequestDto
     */
    contextMessage?: string;
    /**
     * Demo only: when true, this attempt is flagged for simulated MITM (tampered Pending body after signing) if Auth API ezkey.demo.mitm-signature-enabled is true.
     * @type {boolean}
     * @memberof AuthAttemptCreateRequestDto
     */
    demoMitmSignatureRequested?: boolean;
}
/**
 * Authentication attempt details
 * @export
 * @interface AuthAttemptDto
 */
export interface AuthAttemptDto {
    /**
     * Unique auth attempt ID
     * @type {number}
     * @memberof AuthAttemptDto
     */
    authAttemptId: number;
    /**
     * Associated enrollment ID
     * @type {number}
     * @memberof AuthAttemptDto
     */
    enrollmentId: number;
    /**
     * Current status of the authentication attempt
     * @type {string}
     * @memberof AuthAttemptDto
     */
    authAttemptStatus: AuthAttemptDtoAuthAttemptStatusEnum;
    /**
     * Numeric challenge displayed to user for verification (null if not requested)
     * @type {number}
     * @memberof AuthAttemptDto
     */
    authAttemptChallenge?: number;
    /**
     * Signed JWT proof token (present only when status is ACCEPTED)
     * @type {string}
     * @memberof AuthAttemptDto
     */
    authAttemptProofToken?: string;
    /**
     * Creation timestamp with timezone
     * @type {string}
     * @memberof AuthAttemptDto
     */
    createdAt: string;
    /**
     * Expiration timestamp with timezone
     * @type {string}
     * @memberof AuthAttemptDto
     */
    expiresAt: string;
    /**
     * Short title for the approval request (null if no context provided)
     * @type {string}
     * @memberof AuthAttemptDto
     */
    contextTitle?: string;
    /**
     * Descriptive approval message (null if no context provided)
     * @type {string}
     * @memberof AuthAttemptDto
     */
    contextMessage?: string;
    /**
     * Demo MITM opt-in at creation time (Pending tampering only when Auth API demo flag is on)
     * @type {boolean}
     * @memberof AuthAttemptDto
     */
    demoMitmSignatureEnabled?: boolean;
    /**
     * Integration ID resolved via enrollment (admin enrichment)
     * @type {number}
     * @memberof AuthAttemptDto
     */
    integrationId?: number;
    /**
     * Integration display name (admin list enrichment)
     * @type {string}
     * @memberof AuthAttemptDto
     */
    integrationName?: string;
    /**
     * Enrollment display name (admin list enrichment)
     * @type {string}
     * @memberof AuthAttemptDto
     */
    enrollmentName?: string;
    /**
     * Tenant ID (admin list enrichment)
     * @type {number}
     * @memberof AuthAttemptDto
     */
    tenantId?: number;
    /**
     * Tenant display name (admin list enrichment)
     * @type {string}
     * @memberof AuthAttemptDto
     */
    tenantName?: string;
}


/**
 * @export
 */
export const AuthAttemptDtoAuthAttemptStatusEnum = {
    Pending: 'PENDING',
    Read: 'READ',
    Invalid: 'INVALID',
    Rejected: 'REJECTED',
    Accepted: 'ACCEPTED',
    Expired: 'EXPIRED'
} as const;
export type AuthAttemptDtoAuthAttemptStatusEnum = typeof AuthAttemptDtoAuthAttemptStatusEnum[keyof typeof AuthAttemptDtoAuthAttemptStatusEnum];

/**
 * Response from the wait endpoint after waiting for device authentication
 * @export
 * @interface AuthAttemptWaitResponseDto
 */
export interface AuthAttemptWaitResponseDto {
    /**
     * Current authentication attempt state
     * @type {AuthAttemptDto}
     * @memberof AuthAttemptWaitResponseDto
     */
    authAttempt: AuthAttemptDto;
    /**
     * Calculated authentication status
     * @type {string}
     * @memberof AuthAttemptWaitResponseDto
     */
    status: AuthAttemptWaitResponseDtoStatusEnum;
    /**
     * Whether authentication process is complete
     * @type {boolean}
     * @memberof AuthAttemptWaitResponseDto
     */
    completed: boolean;
    /**
     * Whether wait ended due to timeout
     * @type {boolean}
     * @memberof AuthAttemptWaitResponseDto
     */
    timeoutReached: boolean;
    /**
     * Actual duration waited in seconds
     * @type {number}
     * @memberof AuthAttemptWaitResponseDto
     */
    waitDuration: number;
    /**
     * Timestamp when wait operation completed (with timezone)
     * @type {string}
     * @memberof AuthAttemptWaitResponseDto
     */
    completedAt: string;
}


/**
 * @export
 */
export const AuthAttemptWaitResponseDtoStatusEnum = {
    Pending: 'PENDING',
    Read: 'READ',
    Invalid: 'INVALID',
    Rejected: 'REJECTED',
    Accepted: 'ACCEPTED'
} as const;
export type AuthAttemptWaitResponseDtoStatusEnum = typeof AuthAttemptWaitResponseDtoStatusEnum[keyof typeof AuthAttemptWaitResponseDtoStatusEnum];

/**
 * 
 * @export
 * @interface BatchCreationResponse
 */
export interface BatchCreationResponse {
    /**
     * 
     * @type {number}
     * @memberof BatchCreationResponse
     */
    batchesCreated?: number;
    /**
     * 
     * @type {string}
     * @memberof BatchCreationResponse
     */
    message?: string;
}
/**
 * 
 * @export
 * @interface BatchResumeResponse
 */
export interface BatchResumeResponse {
    /**
     * 
     * @type {number}
     * @memberof BatchResumeResponse
     */
    batchId?: number;
    /**
     * 
     * @type {string}
     * @memberof BatchResumeResponse
     */
    message?: string;
}
/**
 * 
 * @export
 * @interface BulkEnrollmentOperationResultDto
 */
export interface BulkEnrollmentOperationResultDto {
    /**
     * 
     * @type {number}
     * @memberof BulkEnrollmentOperationResultDto
     */
    affectedCount?: number;
    /**
     * 
     * @type {number}
     * @memberof BulkEnrollmentOperationResultDto
     */
    skippedCount?: number;
    /**
     * 
     * @type {boolean}
     * @memberof BulkEnrollmentOperationResultDto
     */
    noOp?: boolean;
}
/**
 * 
 * @export
 * @interface ChainIntegrityViolation
 */
export interface ChainIntegrityViolation {
    /**
     * 
     * @type {number}
     * @memberof ChainIntegrityViolation
     */
    checkpointId?: number;
    /**
     * 
     * @type {string}
     * @memberof ChainIntegrityViolation
     */
    windowStart?: string;
    /**
     * 
     * @type {string}
     * @memberof ChainIntegrityViolation
     */
    windowEnd?: string;
    /**
     * 
     * @type {string}
     * @memberof ChainIntegrityViolation
     */
    violationType?: string;
    /**
     * 
     * @type {string}
     * @memberof ChainIntegrityViolation
     */
    detail?: string;
}
/**
 * 
 * @export
 * @interface ChainVerificationReport
 */
export interface ChainVerificationReport {
    /**
     * 
     * @type {number}
     * @memberof ChainVerificationReport
     */
    totalCheckpoints?: number;
    /**
     * 
     * @type {number}
     * @memberof ChainVerificationReport
     */
    validCheckpoints?: number;
    /**
     * 
     * @type {number}
     * @memberof ChainVerificationReport
     */
    invalidCheckpoints?: number;
    /**
     * 
     * @type {number}
     * @memberof ChainVerificationReport
     */
    archivedCheckpoints?: number;
    /**
     * 
     * @type {number}
     * @memberof ChainVerificationReport
     */
    gapDeclaredCheckpoints?: number;
    /**
     * 
     * @type {Array<string>}
     * @memberof ChainVerificationReport
     */
    violations?: Array<string>;
    /**
     * 
     * @type {Array<ChainIntegrityViolation>}
     * @memberof ChainVerificationReport
     */
    chainViolations?: Array<ChainIntegrityViolation>;
    /**
     * 
     * @type {Array<UndeclaredGap>}
     * @memberof ChainVerificationReport
     */
    undeclaredGaps?: Array<UndeclaredGap>;
    /**
     * 
     * @type {string}
     * @memberof ChainVerificationReport
     */
    coverageStart?: string;
    /**
     * 
     * @type {string}
     * @memberof ChainVerificationReport
     */
    coverageEnd?: string;
    /**
     * 
     * @type {string}
     * @memberof ChainVerificationReport
     */
    effectiveFrom?: string;
    /**
     * 
     * @type {string}
     * @memberof ChainVerificationReport
     */
    effectiveTo?: string;
    /**
     * 
     * @type {boolean}
     * @memberof ChainVerificationReport
     */
    continuousCoverage?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof ChainVerificationReport
     */
    intact?: boolean;
    /**
     * 
     * @type {string}
     * @memberof ChainVerificationReport
     */
    status?: string;
}
/**
 * Admin console alert for dashboard overview (Global Admin only)
 * @export
 * @interface DashboardAlertItemDto
 */
export interface DashboardAlertItemDto {
    /**
     * 
     * @type {number}
     * @memberof DashboardAlertItemDto
     */
    alertId?: number;
    /**
     * 
     * @type {string}
     * @memberof DashboardAlertItemDto
     */
    alertType?: DashboardAlertItemDtoAlertTypeEnum;
    /**
     * 
     * @type {string}
     * @memberof DashboardAlertItemDto
     */
    severity?: DashboardAlertItemDtoSeverityEnum;
    /**
     * 
     * @type {string}
     * @memberof DashboardAlertItemDto
     */
    status?: DashboardAlertItemDtoStatusEnum;
    /**
     * 
     * @type {string}
     * @memberof DashboardAlertItemDto
     */
    createdAt?: string;
    /**
     * Producer-defined JSON payload (string). Shape depends on alertType; clients aware of the type render structured fields.
     * @type {string}
     * @memberof DashboardAlertItemDto
     */
    payload?: string;
}


/**
 * @export
 */
export const DashboardAlertItemDtoAlertTypeEnum = {
    ChainGapPending: 'AUDIT_CHAIN_GAP_PENDING',
    ChainHeartbeatStale: 'AUDIT_CHAIN_HEARTBEAT_STALE',
    IntegrityRupture: 'AUDIT_INTEGRITY_RUPTURE'
} as const;
export type DashboardAlertItemDtoAlertTypeEnum = typeof DashboardAlertItemDtoAlertTypeEnum[keyof typeof DashboardAlertItemDtoAlertTypeEnum];

/**
 * @export
 */
export const DashboardAlertItemDtoSeverityEnum = {
    Info: 'INFO',
    Warning: 'WARNING',
    Critical: 'CRITICAL'
} as const;
export type DashboardAlertItemDtoSeverityEnum = typeof DashboardAlertItemDtoSeverityEnum[keyof typeof DashboardAlertItemDtoSeverityEnum];

/**
 * @export
 */
export const DashboardAlertItemDtoStatusEnum = {
    Open: 'OPEN',
    Resolved: 'RESOLVED'
} as const;
export type DashboardAlertItemDtoStatusEnum = typeof DashboardAlertItemDtoStatusEnum[keyof typeof DashboardAlertItemDtoStatusEnum];

/**
 * Auth attempt counts and terminal-outcome rates in the last 24h (dashboard)
 * @export
 * @interface DashboardAuth24hStatsDto
 */
export interface DashboardAuth24hStatsDto {
    /**
     * All attempts created in the rolling 24h window
     * @type {number}
     * @memberof DashboardAuth24hStatsDto
     */
    total?: number;
    /**
     * Attempts still pending on the device
     * @type {number}
     * @memberof DashboardAuth24hStatsDto
     */
    pending?: number;
    /**
     * Attempts claimed by the device (read) but not yet completed
     * @type {number}
     * @memberof DashboardAuth24hStatsDto
     */
    readCount?: number;
    /**
     * User approved
     * @type {number}
     * @memberof DashboardAuth24hStatsDto
     */
    accepted?: number;
    /**
     * User explicitly denied
     * @type {number}
     * @memberof DashboardAuth24hStatsDto
     */
    rejected?: number;
    /**
     * Cryptographic validation failed
     * @type {number}
     * @memberof DashboardAuth24hStatsDto
     */
    invalid?: number;
    /**
     * Timed out or superseded
     * @type {number}
     * @memberof DashboardAuth24hStatsDto
     */
    expired?: number;
    /**
     * Terminal outcomes: accepted + rejected + invalid + expired (denominator for rate fields)
     * @type {number}
     * @memberof DashboardAuth24hStatsDto
     */
    terminalTotal?: number;
    /**
     * Accepted as % of terminal outcomes; null if terminalTotal is 0
     * @type {number}
     * @memberof DashboardAuth24hStatsDto
     */
    successRatePct?: number | null;
    /**
     * Invalid as % of terminal outcomes; null if terminalTotal is 0
     * @type {number}
     * @memberof DashboardAuth24hStatsDto
     */
    invalidRatePct?: number | null;
    /**
     * Expired as % of terminal outcomes; null if terminalTotal is 0
     * @type {number}
     * @memberof DashboardAuth24hStatsDto
     */
    expiredRatePct?: number | null;
    /**
     * Rejected as % of terminal outcomes; null if terminalTotal is 0
     * @type {number}
     * @memberof DashboardAuth24hStatsDto
     */
    rejectedRatePct?: number | null;
}
/**
 * Operational enrollment counts (by status and active flag) for dashboard overview
 * @export
 * @interface DashboardEnrollmentStatsDto
 */
export interface DashboardEnrollmentStatsDto {
    /**
     * Devices ready for MFA: VERIFIED + active=true
     * @type {number}
     * @memberof DashboardEnrollmentStatsDto
     */
    verified?: number;
    /**
     * Onboarding in progress: CREATED or BOUND (any active state)
     * @type {number}
     * @memberof DashboardEnrollmentStatsDto
     */
    inProgress?: number;
    /**
     * Admin-disabled devices: VERIFIED + active=false
     * @type {number}
     * @memberof DashboardEnrollmentStatsDto
     */
    suspended?: number;
    /**
     * Timed out before verification: EXPIRED (any active state)
     * @type {number}
     * @memberof DashboardEnrollmentStatsDto
     */
    expired?: number;
    /**
     * Failed validation: INVALID (any active state)
     * @type {number}
     * @memberof DashboardEnrollmentStatsDto
     */
    invalid?: number;
    /**
     * Administrator revocations: REVOKED (any active state)
     * @type {number}
     * @memberof DashboardEnrollmentStatsDto
     */
    revoked?: number;
}
/**
 * Integration counts for dashboard overview
 * @export
 * @interface DashboardIntegrationStatsDto
 */
export interface DashboardIntegrationStatsDto {
    /**
     * 
     * @type {number}
     * @memberof DashboardIntegrationStatsDto
     */
    total?: number;
    /**
     * 
     * @type {number}
     * @memberof DashboardIntegrationStatsDto
     */
    active?: number;
    /**
     * 
     * @type {number}
     * @memberof DashboardIntegrationStatsDto
     */
    retired?: number;
}
/**
 * Active integrity configuration summary for dashboard widgets
 * @export
 * @interface DashboardIntegrityConfigSummaryDto
 */
export interface DashboardIntegrityConfigSummaryDto {
    /**
     * Rolling checkpoint lookback window in minutes
     * @type {number}
     * @memberof DashboardIntegrityConfigSummaryDto
     */
    chainLookbackMinutes?: number;
    /**
     * Nightly retroactive validation window in hours
     * @type {number}
     * @memberof DashboardIntegrityConfigSummaryDto
     */
    nightlyWindowHours?: number;
    /**
     * Whether rolling audit chain checkpoints are enabled
     * @type {boolean}
     * @memberof DashboardIntegrityConfigSummaryDto
     */
    chainCheckpointsEnabled?: boolean;
    /**
     * Whether nightly retroactive integrity validation is enabled
     * @type {boolean}
     * @memberof DashboardIntegrityConfigSummaryDto
     */
    nightlyValidationEnabled?: boolean;
}
/**
 * Aggregated dashboard overview (stats, recent activity, optional alerts)
 * @export
 * @interface DashboardOverviewDto
 */
export interface DashboardOverviewDto {
    /**
     * 
     * @type {DashboardIntegrationStatsDto}
     * @memberof DashboardOverviewDto
     */
    integrations?: DashboardIntegrationStatsDto;
    /**
     * 
     * @type {DashboardEnrollmentStatsDto}
     * @memberof DashboardOverviewDto
     */
    enrollments?: DashboardEnrollmentStatsDto;
    /**
     * 
     * @type {DashboardAuth24hStatsDto}
     * @memberof DashboardOverviewDto
     */
    auth24h?: DashboardAuth24hStatsDto;
    /**
     * 
     * @type {Array<DashboardRecentActivityItemDto>}
     * @memberof DashboardOverviewDto
     */
    recentActivity?: Array<DashboardRecentActivityItemDto>;
    /**
     * Count of OPEN operator-facing alerts. Populated only for Global Admin.
     * @type {number}
     * @memberof DashboardOverviewDto
     */
    openAlertCount?: number;
    /**
     * Recent OPEN alerts (newest first, capped). Populated only for Global Admin.
     * @type {Array<DashboardAlertItemDto>}
     * @memberof DashboardOverviewDto
     */
    alerts?: Array<DashboardAlertItemDto>;
    /**
     * System/integrity scheduled jobs (checkpoint, nightly validation). Global Admin only.
     * @type {Array<DashboardScheduledJobRowDto>}
     * @memberof DashboardOverviewDto
     */
    integrityJobs?: Array<DashboardScheduledJobRowDto>;
    /**
     * Other operational scheduled jobs (e.g. re-encryption). Global Admin only.
     * @type {Array<DashboardScheduledJobRowDto>}
     * @memberof DashboardOverviewDto
     */
    operationalJobs?: Array<DashboardScheduledJobRowDto>;
    /**
     * Active integrity configuration summary (non-secret). Global Admin only.
     * @type {DashboardIntegrityConfigSummaryDto}
     * @memberof DashboardOverviewDto
     */
    integrityConfigSummary?: DashboardIntegrityConfigSummaryDto;
}
/**
 * Recent audit log entry for dashboard overview
 * @export
 * @interface DashboardRecentActivityItemDto
 */
export interface DashboardRecentActivityItemDto {
    /**
     * 
     * @type {number}
     * @memberof DashboardRecentActivityItemDto
     */
    auditLogId?: number;
    /**
     * 
     * @type {string}
     * @memberof DashboardRecentActivityItemDto
     */
    eventType?: string;
    /**
     * 
     * @type {string}
     * @memberof DashboardRecentActivityItemDto
     */
    eventStatus?: string;
    /**
     * 
     * @type {string}
     * @memberof DashboardRecentActivityItemDto
     */
    eventAction?: string;
    /**
     * 
     * @type {string}
     * @memberof DashboardRecentActivityItemDto
     */
    apiName?: string;
    /**
     * 
     * @type {number}
     * @memberof DashboardRecentActivityItemDto
     */
    adminId?: number;
    /**
     * 
     * @type {string}
     * @memberof DashboardRecentActivityItemDto
     */
    createdAt?: string;
}
/**
 * Scheduled job last-run row for dashboard batch health widgets
 * @export
 * @interface DashboardScheduledJobRowDto
 */
export interface DashboardScheduledJobRowDto {
    /**
     * Stable job identifier
     * @type {string}
     * @memberof DashboardScheduledJobRowDto
     */
    jobKey?: DashboardScheduledJobRowDtoJobKeyEnum;
    /**
     * Timestamp of the most recent execution (null when never run)
     * @type {string}
     * @memberof DashboardScheduledJobRowDto
     */
    lastExecutionAt?: string;
    /**
     * Outcome of the most recent execution
     * @type {string}
     * @memberof DashboardScheduledJobRowDto
     */
    lastStatus?: DashboardScheduledJobRowDtoLastStatusEnum;
    /**
     * Human-readable scope of the last run (e.g. validated window)
     * @type {string}
     * @memberof DashboardScheduledJobRowDto
     */
    lastRunScope?: string;
    /**
     * Safe operator-facing error summary when lastStatus is FAILED
     * @type {string}
     * @memberof DashboardScheduledJobRowDto
     */
    lastErrorSummary?: string;
}


/**
 * @export
 */
export const DashboardScheduledJobRowDtoJobKeyEnum = {
    AuditChainCheckpoint: 'AUDIT_CHAIN_CHECKPOINT',
    NightlyIntegrityValidation: 'NIGHTLY_INTEGRITY_VALIDATION',
    Reencryption: 'REENCRYPTION'
} as const;
export type DashboardScheduledJobRowDtoJobKeyEnum = typeof DashboardScheduledJobRowDtoJobKeyEnum[keyof typeof DashboardScheduledJobRowDtoJobKeyEnum];

/**
 * @export
 */
export const DashboardScheduledJobRowDtoLastStatusEnum = {
    Success: 'SUCCESS',
    Failed: 'FAILED',
    NeverRun: 'NEVER_RUN'
} as const;
export type DashboardScheduledJobRowDtoLastStatusEnum = typeof DashboardScheduledJobRowDtoLastStatusEnum[keyof typeof DashboardScheduledJobRowDtoLastStatusEnum];

/**
 * 
 * @export
 * @interface DeclareAuditChainIncidentRequest
 */
export interface DeclareAuditChainIncidentRequest {
    /**
     * 
     * @type {string}
     * @memberof DeclareAuditChainIncidentRequest
     */
    justification: string;
    /**
     * 
     * @type {string}
     * @memberof DeclareAuditChainIncidentRequest
     */
    rootCause: DeclareAuditChainIncidentRequestRootCauseEnum;
}


/**
 * @export
 */
export const DeclareAuditChainIncidentRequestRootCauseEnum = {
    PlannedSystemUpgrade: 'PLANNED_SYSTEM_UPGRADE',
    AdminApiDown: 'ADMIN_API_DOWN',
    SchedulerFailure: 'SCHEDULER_FAILURE',
    DbUnavailable: 'DB_UNAVAILABLE',
    NetworkPartition: 'NETWORK_PARTITION',
    Misconfiguration: 'MISCONFIGURATION',
    Unknown: 'UNKNOWN'
} as const;
export type DeclareAuditChainIncidentRequestRootCauseEnum = typeof DeclareAuditChainIncidentRequestRootCauseEnum[keyof typeof DeclareAuditChainIncidentRequestRootCauseEnum];

/**
 * Encryption key row with derived lifecycle fields for operators.
 * @export
 * @interface EncryptionKeyResponse
 */
export interface EncryptionKeyResponse {
    /**
     * Unique key identifier (Tink keyset id)
     * @type {number}
     * @memberof EncryptionKeyResponse
     */
    keyId?: number;
    /**
     * PRIMARY, ENABLED, DISABLED, or PENDING
     * @type {string}
     * @memberof EncryptionKeyResponse
     */
    keyStatus?: string;
    /**
     * Algorithm label, e.g. AES256_GCM
     * @type {string}
     * @memberof EncryptionKeyResponse
     */
    algorithm?: string;
    /**
     * When the key was introduced
     * @type {string}
     * @memberof EncryptionKeyResponse
     */
    introducedAt?: string;
    /**
     * When promoted to PRIMARY, if applicable
     * @type {string}
     * @memberof EncryptionKeyResponse
     */
    promotedPrimaryAt?: string;
    /**
     * When disabled, if applicable
     * @type {string}
     * @memberof EncryptionKeyResponse
     */
    disabledAt?: string;
    /**
     * Migration baseline: ciphertext units when key became ENABLED (demotion); 0 while PRIMARY
     * @type {number}
     * @memberof EncryptionKeyResponse
     */
    recordsEncrypted?: number;
    /**
     * Cumulative ciphertext units re-encrypted off this key (completed batches; reset at demotion)
     * @type {number}
     * @memberof EncryptionKeyResponse
     */
    recordsReencrypted?: number;
    /**
     * Creator label (SYSTEM or admin)
     * @type {string}
     * @memberof EncryptionKeyResponse
     */
    createdBy?: string;
    /**
     * Optional operator notes
     * @type {string}
     * @memberof EncryptionKeyResponse
     */
    notes?: string;
    /**
     * Derived lifecycle stage (e.g. PRIMARY, ENABLED_IN_USE, DRAINED, DISABLED)
     * @type {string}
     * @memberof EncryptionKeyResponse
     */
    lifecycleStage?: string;
    /**
     * Derived ciphertext units (prefix scan): ENABLED = migration backlog; PRIMARY = current live volume; null when not applicable
     * @type {number}
     * @memberof EncryptionKeyResponse
     */
    remainingRecords?: number;
    /**
     * Targets with rows counted for this key; null when not applicable
     * @type {number}
     * @memberof EncryptionKeyResponse
     */
    remainingTargets?: number;
    /**
     * When this snapshot was computed
     * @type {string}
     * @memberof EncryptionKeyResponse
     */
    lastVerifiedAt?: string;
    /**
     * Verification outcome (e.g. NOT_APPLICABLE, VERIFIED_ZERO)
     * @type {string}
     * @memberof EncryptionKeyResponse
     */
    verificationState?: string;
    /**
     * True when drained and ready for a future decommission workflow
     * @type {boolean}
     * @memberof EncryptionKeyResponse
     */
    decommissionEligible?: boolean;
    /**
     * True when migration batches for this key are not all completed
     * @type {boolean}
     * @memberof EncryptionKeyResponse
     */
    incompleteMigrationBatches?: boolean;
    /**
     * Retrospective wall-clock seconds for a fully drained migration, computed by merging completed batches' actual time windows (overlapping work counts once); null unless lifecycleStage is DRAINED and timing is available
     * @type {number}
     * @memberof EncryptionKeyResponse
     */
    reencryptionWallClockSeconds?: number;
}
/**
 * Enrollment creation data
 * @export
 * @interface EnrollmentCreateRequestDto
 */
export interface EnrollmentCreateRequestDto {
    /**
     * The integration ID to which this enrollment belongs
     * @type {number}
     * @memberof EnrollmentCreateRequestDto
     */
    integrationId: number;
    /**
     * Human-readable name for the enrollment
     * @type {string}
     * @memberof EnrollmentCreateRequestDto
     */
    name: string;
    /**
     * Whether authentication attempts require challenge validation
     * @type {boolean}
     * @memberof EnrollmentCreateRequestDto
     */
    authAttemptChallengeRequired?: boolean;
    /**
     * Optional contact email for the end-user
     * @type {string}
     * @memberof EnrollmentCreateRequestDto
     */
    contactEmail?: string;
    /**
     * Optional contact phone number for the end-user
     * @type {string}
     * @memberof EnrollmentCreateRequestDto
     */
    contactPhoneNumber?: string;
    /**
     * Optional user identifier from the integrating application
     * @type {string}
     * @memberof EnrollmentCreateRequestDto
     */
    userIdentifier?: string;
    /**
     * Optional invitation expiry (UTC instant). Pending phase only; must be in the future when set. Omit to use ezkey.enrollment.pending-expiration-days.
     * @type {string}
     * @memberof EnrollmentCreateRequestDto
     */
    expiresAt?: string;
}
/**
 * Response DTO containing created enrollment details
 * @export
 * @interface EnrollmentCreateResponseDto
 */
export interface EnrollmentCreateResponseDto {
    /**
     * Unique identifier of the created enrollment
     * @type {number}
     * @memberof EnrollmentCreateResponseDto
     */
    enrollmentId?: number;
    /**
     * Challenge number for enrollment verification
     * @type {number}
     * @memberof EnrollmentCreateResponseDto
     */
    enrollmentChallenge?: number;
    /**
     * Invitation expiry (UTC) for the pending enrollment phase, when set on the server
     * @type {string}
     * @memberof EnrollmentCreateResponseDto
     */
    expiresAt?: string;
}
/**
 * 
 * @export
 * @interface EnrollmentResetRequestDto
 */
export interface EnrollmentResetRequestDto {
    /**
     * 
     * @type {number}
     * @memberof EnrollmentResetRequestDto
     */
    enrollmentId: number;
    /**
     * 
     * @type {string}
     * @memberof EnrollmentResetRequestDto
     */
    reason?: string;
}
/**
 * Response for enrollment reset operation
 * @export
 * @interface EnrollmentResetResponseDto
 */
export interface EnrollmentResetResponseDto {
    /**
     * Indicates if the reset was successful
     * @type {boolean}
     * @memberof EnrollmentResetResponseDto
     */
    success?: boolean;
    /**
     * The enrollment ID
     * @type {number}
     * @memberof EnrollmentResetResponseDto
     */
    enrollmentId?: number;
    /**
     * New enrollment proof token for binding
     * @type {string}
     * @memberof EnrollmentResetResponseDto
     */
    enrollmentProofToken?: string;
    /**
     * New enrollment challenge code for verification
     * @type {number}
     * @memberof EnrollmentResetResponseDto
     */
    enrollmentChallenge?: number;
    /**
     * Integration ID for reference
     * @type {number}
     * @memberof EnrollmentResetResponseDto
     */
    integrationId?: number;
    /**
     * Response message
     * @type {string}
     * @memberof EnrollmentResetResponseDto
     */
    message?: string;
}
/**
 * Response DTO containing complete enrollment information for administrative purposes
 * @export
 * @interface EnrollmentResponseDto
 */
export interface EnrollmentResponseDto {
    /**
     * Unique identifier for the enrollment
     * @type {number}
     * @memberof EnrollmentResponseDto
     */
    enrollmentId?: number;
    /**
     * Optimistic lock version. Include in PATCH requests to prevent concurrent update
     * @type {number}
     * @memberof EnrollmentResponseDto
     */
    version?: number;
    /**
     * Integration identifier this enrollment belongs to
     * @type {number}
     * @memberof EnrollmentResponseDto
     */
    integrationId?: number;
    /**
     * Human-readable name for the enrollment
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    enrollmentName?: string;
    /**
     * Enrollment lifecycle status
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    enrollmentStatus?: EnrollmentResponseDtoEnrollmentStatusEnum;
    /**
     * Flag indicating if the enrollment is currently active
     * @type {boolean}
     * @memberof EnrollmentResponseDto
     */
    enrollmentActive?: boolean;
    /**
     * Challenge value for enrollment verification
     * @type {number}
     * @memberof EnrollmentResponseDto
     */
    enrollmentChallenge?: number;
    /**
     * Unique code for enrollment verification
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    enrollmentProofToken?: string;
    /**
     * Flag indicating if authentication attempts require challenge
     * @type {boolean}
     * @memberof EnrollmentResponseDto
     */
    authAttemptChallengeRequired?: boolean;
    /**
     * Public key for integration communication
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    integrationPublicKey?: string;
    /**
     * Public key for the device
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    devicePublicKey?: string;
    /**
     * Client-reported device private key storage tier at verify (NONE, STANDARD, STRONG); null if unknown or legacy
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    devicePrivateKeyStorageTier?: EnrollmentResponseDtoDevicePrivateKeyStorageTierEnum;
    /**
     * When enrollment was verified (device completed binding)
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    verifiedAt?: string;
    /**
     * Optional expiration for pending enrollment (CREATED/BOUND); null = no expiration
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    expiresAt?: string;
    /**
     * When the enrollment row was created (audit / sorting)
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    createdAt?: string;
    /**
     * Admin who created this enrollment (null when via API key)
     * @type {number}
     * @memberof EnrollmentResponseDto
     */
    createdByAdminId?: number;
    /**
     * Username of the admin who created this enrollment; null when via API key, admin removed, or not resolved
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    createdByAdminUsername?: string | null;
    /**
     * When enrollment was last used for successful authentication
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    lastUsedAt?: string;
    /**
     * Optional contact email for the end-user
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    contactEmail?: string;
    /**
     * Optional contact phone number for the end-user in canonical E.164 format
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    contactPhoneNumber?: string;
    /**
     * Optional user identifier from the integrating application
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    userIdentifier?: string;
    /**
     * When the enrollment was deactivated (reversible soft-disable); null if never
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    deactivatedAt?: string;
    /**
     * Admin who deactivated this enrollment; null if never deactivated
     * @type {number}
     * @memberof EnrollmentResponseDto
     */
    deactivatedByAdminId?: number;
    /**
     * Username of the admin who deactivated this enrollment; null if unknown
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    deactivatedByAdminUsername?: string | null;
    /**
     * When the enrollment was permanently revoked; null if never
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    revokedAt?: string;
    /**
     * Admin who revoked this enrollment; null if never revoked
     * @type {number}
     * @memberof EnrollmentResponseDto
     */
    revokedByAdminId?: number;
    /**
     * Username of the admin who revoked this enrollment; null if unknown
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    revokedByAdminUsername?: string | null;
    /**
     * Display name for the enrollment's integration (e.g. Ezkey System); populated when integration context is joined (list and GET by ID)
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    integrationName?: string;
    /**
     * Tenant identifier for the enrollment's integration; populated when integration context is joined
     * @type {number}
     * @memberof EnrollmentResponseDto
     */
    tenantId?: number;
    /**
     * Tenant display name for the enrollment's integration; populated when integration context is joined (list and GET by ID)
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    tenantName?: string;
    /**
     * Whether this enrollment's integration is the system integration; populated when integration context is joined
     * @type {boolean}
     * @memberof EnrollmentResponseDto
     */
    isSystemIntegration?: boolean;
    /**
     * Whether the enrollment is currently operational: VERIFIED status, active flag true, and (when integration context is available) full parent chain also operational
     * @type {boolean}
     * @memberof EnrollmentResponseDto
     */
    operational?: boolean;
}


/**
 * @export
 */
export const EnrollmentResponseDtoEnrollmentStatusEnum = {
    Created: 'CREATED',
    Bound: 'BOUND',
    Verified: 'VERIFIED',
    Invalid: 'INVALID',
    Revoked: 'REVOKED',
    Expired: 'EXPIRED'
} as const;
export type EnrollmentResponseDtoEnrollmentStatusEnum = typeof EnrollmentResponseDtoEnrollmentStatusEnum[keyof typeof EnrollmentResponseDtoEnrollmentStatusEnum];

/**
 * @export
 */
export const EnrollmentResponseDtoDevicePrivateKeyStorageTierEnum = {
    None: 'NONE',
    Standard: 'STANDARD',
    Strong: 'STRONG'
} as const;
export type EnrollmentResponseDtoDevicePrivateKeyStorageTierEnum = typeof EnrollmentResponseDtoDevicePrivateKeyStorageTierEnum[keyof typeof EnrollmentResponseDtoDevicePrivateKeyStorageTierEnum];

/**
 * Request DTO for partial update of enrollment metadata
 * @export
 * @interface EnrollmentUpdateRequestDto
 */
export interface EnrollmentUpdateRequestDto {
    /**
     * Optimistic lock version from GET response. When provided, update fails with 409 if resource was modified since last fetch.
     * @type {number}
     * @memberof EnrollmentUpdateRequestDto
     */
    version?: number;
    /**
     * Human-readable name for the enrollment
     * @type {string}
     * @memberof EnrollmentUpdateRequestDto
     */
    enrollmentName?: string;
    /**
     * Optional contact email for the end-user (validated when non-blank; use clearContactEmail to remove)
     * @type {string}
     * @memberof EnrollmentUpdateRequestDto
     */
    contactEmail?: string;
    /**
     * Optional contact phone number for the end-user (accepts common separators and is normalized to E.164 on write)
     * @type {string}
     * @memberof EnrollmentUpdateRequestDto
     */
    contactPhoneNumber?: string;
    /**
     * Optional expiration timestamp (must be in future). Null = no expiration.
     * @type {string}
     * @memberof EnrollmentUpdateRequestDto
     */
    expiresAt?: string;
    /**
     * Whether authentication attempts require challenge
     * @type {boolean}
     * @memberof EnrollmentUpdateRequestDto
     */
    authAttemptChallengeRequired?: boolean;
    /**
     * Optional user identifier from the integrating application
     * @type {string}
     * @memberof EnrollmentUpdateRequestDto
     */
    userIdentifier?: string;
    /**
     * When true, clears contact email. Takes precedence over contactEmail in the same request.
     * @type {boolean}
     * @memberof EnrollmentUpdateRequestDto
     */
    clearContactEmail?: boolean;
    /**
     * When true, clears invitation expiry (expiresAt). Ignored if expiresAt is set to a non-null instant in the same request.
     * @type {boolean}
     * @memberof EnrollmentUpdateRequestDto
     */
    clearExpiresAt?: boolean;
}
/**
 * 
 * @export
 * @interface EntryIntegrityConciliationSummary
 */
export interface EntryIntegrityConciliationSummary {
    /**
     * 
     * @type {number}
     * @memberof EntryIntegrityConciliationSummary
     */
    conciliationId?: number;
    /**
     * 
     * @type {string}
     * @memberof EntryIntegrityConciliationSummary
     */
    conciliatedAt?: string;
    /**
     * 
     * @type {string}
     * @memberof EntryIntegrityConciliationSummary
     */
    category?: EntryIntegrityConciliationSummaryCategoryEnum;
    /**
     * 
     * @type {number}
     * @memberof EntryIntegrityConciliationSummary
     */
    conciliatedByAdminId?: number;
    /**
     * 
     * @type {number}
     * @memberof EntryIntegrityConciliationSummary
     */
    sourceAlertId?: number;
}


/**
 * @export
 */
export const EntryIntegrityConciliationSummaryCategoryEnum = {
    AccidentalDbaEdit: 'ACCIDENTAL_DBA_EDIT',
    Corruption: 'CORRUPTION',
    InvestigatedBenign: 'INVESTIGATED_BENIGN',
    Other: 'OTHER'
} as const;
export type EntryIntegrityConciliationSummaryCategoryEnum = typeof EntryIntegrityConciliationSummaryCategoryEnum[keyof typeof EntryIntegrityConciliationSummaryCategoryEnum];

/**
 * 
 * @export
 * @interface EntryIntegrityViolation
 */
export interface EntryIntegrityViolation {
    /**
     * 
     * @type {number}
     * @memberof EntryIntegrityViolation
     */
    auditLogId?: number;
    /**
     * 
     * @type {string}
     * @memberof EntryIntegrityViolation
     */
    eventType?: string;
    /**
     * 
     * @type {string}
     * @memberof EntryIntegrityViolation
     */
    createdAt?: string;
    /**
     * 
     * @type {string}
     * @memberof EntryIntegrityViolation
     */
    reason?: string;
    /**
     * 
     * @type {string}
     * @memberof EntryIntegrityViolation
     */
    conciliationStatus?: EntryIntegrityViolationConciliationStatusEnum;
    /**
     * 
     * @type {EntryIntegrityConciliationSummary}
     * @memberof EntryIntegrityViolation
     */
    conciliationSummary?: EntryIntegrityConciliationSummary;
}


/**
 * @export
 */
export const EntryIntegrityViolationConciliationStatusEnum = {
    None: 'NONE',
    Acknowledged: 'ACKNOWLEDGED',
    ReTamperSuspected: 'RE_TAMPER_SUSPECTED'
} as const;
export type EntryIntegrityViolationConciliationStatusEnum = typeof EntryIntegrityViolationConciliationStatusEnum[keyof typeof EntryIntegrityViolationConciliationStatusEnum];

/**
 * Anonymous evaluator self-registration request
 * @export
 * @interface EvaluatorSelfRegistrationRequestDto
 */
export interface EvaluatorSelfRegistrationRequestDto {
    /**
     * Optional short label for the preview tenant (max 40 characters; no email or URL)
     * @type {string}
     * @memberof EvaluatorSelfRegistrationRequestDto
     */
    tenantLabel?: string;
}
/**
 * Anonymous evaluator self-registration response
 * @export
 * @interface EvaluatorSelfRegistrationResponseDto
 */
export interface EvaluatorSelfRegistrationResponseDto {
    /**
     * One-time activation code (save immediately)
     * @type {string}
     * @memberof EvaluatorSelfRegistrationResponseDto
     */
    activationCode?: string;
    /**
     * Activation code expiration instant (UTC)
     * @type {string}
     * @memberof EvaluatorSelfRegistrationResponseDto
     */
    activationCodeExpiresAt?: string;
    /**
     * Admin UI URL for this preview instance
     * @type {string}
     * @memberof EvaluatorSelfRegistrationResponseDto
     */
    adminUiUrl?: string;
    /**
     * Guided tour URL on ezkey.org
     * @type {string}
     * @memberof EvaluatorSelfRegistrationResponseDto
     */
    guidedTourUrl?: string;
    /**
     * Server-generated tenant slug
     * @type {string}
     * @memberof EvaluatorSelfRegistrationResponseDto
     */
    tenantLabel?: string;
}
/**
 * 
 * @export
 * @interface GapDeclarationRequest
 */
export interface GapDeclarationRequest {
    /**
     * 
     * @type {string}
     * @memberof GapDeclarationRequest
     */
    gapStart?: string;
    /**
     * 
     * @type {number}
     * @memberof GapDeclarationRequest
     */
    anchorCheckpointId?: number;
    /**
     * 
     * @type {string}
     * @memberof GapDeclarationRequest
     */
    gapEnd?: string;
    /**
     * 
     * @type {string}
     * @memberof GapDeclarationRequest
     */
    justification: string;
}
/**
 * 
 * @export
 * @interface GapDeclarationResult
 */
export interface GapDeclarationResult {
    /**
     * 
     * @type {string}
     * @memberof GapDeclarationResult
     */
    gapStart?: string;
    /**
     * 
     * @type {string}
     * @memberof GapDeclarationResult
     */
    gapEnd?: string;
    /**
     * 
     * @type {number}
     * @memberof GapDeclarationResult
     */
    gapCheckpointId?: number;
    /**
     * 
     * @type {string}
     * @memberof GapDeclarationResult
     */
    gapChainHmac?: string;
    /**
     * 
     * @type {number}
     * @memberof GapDeclarationResult
     */
    auditLogId?: number;
    /**
     * 
     * @type {string}
     * @memberof GapDeclarationResult
     */
    justification?: string;
}
/**
 * Integration creation data including code, name, and description
 * @export
 * @interface IntegrationCreateRequestDto
 */
export interface IntegrationCreateRequestDto {
    /**
     * Unique business identifier code for the integration
     * @type {string}
     * @memberof IntegrationCreateRequestDto
     */
    code: string;
    /**
     * Display name for the integration
     * @type {string}
     * @memberof IntegrationCreateRequestDto
     */
    name: string;
    /**
     * Optional description of the integration
     * @type {string}
     * @memberof IntegrationCreateRequestDto
     */
    description?: string;
}
/**
 * Response DTO containing created integration details
 * @export
 * @interface IntegrationCreateResponseDto
 */
export interface IntegrationCreateResponseDto {
    /**
     * Unique business identifier code for the integration
     * @type {string}
     * @memberof IntegrationCreateResponseDto
     */
    code?: string;
    /**
     * Unique identifier of the newly created integration
     * @type {number}
     * @memberof IntegrationCreateResponseDto
     */
    id?: number;
}
/**
 * Response DTO containing complete integration details
 * @export
 * @interface IntegrationResponseDto
 */
export interface IntegrationResponseDto {
    /**
     * Unique identifier for the integration
     * @type {number}
     * @memberof IntegrationResponseDto
     */
    id?: number;
    /**
     * Unique business identifier code for the integration
     * @type {string}
     * @memberof IntegrationResponseDto
     */
    code?: string;
    /**
     * Tenant ID that owns this integration
     * @type {number}
     * @memberof IntegrationResponseDto
     */
    tenantId?: number;
    /**
     * Display name of the owning tenant
     * @type {string}
     * @memberof IntegrationResponseDto
     */
    tenantName?: string;
    /**
     * Explicit integration lifecycle status
     * @type {string}
     * @memberof IntegrationResponseDto
     */
    lifecycleStatus?: IntegrationResponseDtoLifecycleStatusEnum;
    /**
     * Whether the integration is currently operational: ACTIVE lifecycle state and parent tenant active
     * @type {boolean}
     * @memberof IntegrationResponseDto
     */
    operational?: boolean;
    /**
     * Timestamp when the integration was created (with timezone)
     * @type {string}
     * @memberof IntegrationResponseDto
     */
    createdAt?: string;
    /**
     * Display name for the integration
     * @type {string}
     * @memberof IntegrationResponseDto
     */
    name?: string;
    /**
     * Optional description of the integration
     * @type {string}
     * @memberof IntegrationResponseDto
     */
    description?: string;
    /**
     * Whether this is the system integration (e.g. admin MFA); used by UI to adapt detail page
     * @type {boolean}
     * @memberof IntegrationResponseDto
     */
    isSystemIntegration?: boolean;
}


/**
 * @export
 */
export const IntegrationResponseDtoLifecycleStatusEnum = {
    Active: 'ACTIVE',
    Retired: 'RETIRED'
} as const;
export type IntegrationResponseDtoLifecycleStatusEnum = typeof IntegrationResponseDtoLifecycleStatusEnum[keyof typeof IntegrationResponseDtoLifecycleStatusEnum];

/**
 * 
 * @export
 * @interface IntegrityReport
 */
export interface IntegrityReport {
    /**
     * 
     * @type {number}
     * @memberof IntegrityReport
     */
    totalEntries?: number;
    /**
     * 
     * @type {number}
     * @memberof IntegrityReport
     */
    validEntries?: number;
    /**
     * 
     * @type {number}
     * @memberof IntegrityReport
     */
    invalidEntries?: number;
    /**
     * 
     * @type {number}
     * @memberof IntegrityReport
     */
    unsignedEntries?: number;
    /**
     * 
     * @type {boolean}
     * @memberof IntegrityReport
     */
    intact?: boolean;
    /**
     * 
     * @type {string}
     * @memberof IntegrityReport
     */
    status?: string;
    /**
     * 
     * @type {IntegrityViolationCappedListEntryIntegrityViolation}
     * @memberof IntegrityReport
     */
    entryViolations?: IntegrityViolationCappedListEntryIntegrityViolation;
}
/**
 * 
 * @export
 * @interface IntegrityRuptureReconciliationRequest
 */
export interface IntegrityRuptureReconciliationRequest {
    /**
     * 
     * @type {number}
     * @memberof IntegrityRuptureReconciliationRequest
     */
    alertId?: number;
    /**
     * 
     * @type {string}
     * @memberof IntegrityRuptureReconciliationRequest
     */
    dedupeKey?: string;
    /**
     * 
     * @type {string}
     * @memberof IntegrityRuptureReconciliationRequest
     */
    failBoundary: string;
    /**
     * 
     * @type {string}
     * @memberof IntegrityRuptureReconciliationRequest
     */
    resumeBoundary: string;
    /**
     * 
     * @type {string}
     * @memberof IntegrityRuptureReconciliationRequest
     */
    justification: string;
    /**
     * 
     * @type {string}
     * @memberof IntegrityRuptureReconciliationRequest
     */
    externalTicketReference?: string;
    /**
     * 
     * @type {string}
     * @memberof IntegrityRuptureReconciliationRequest
     */
    category: IntegrityRuptureReconciliationRequestCategoryEnum;
    /**
     * 
     * @type {Array<number>}
     * @memberof IntegrityRuptureReconciliationRequest
     */
    acknowledgedAuditLogIds?: Array<number>;
}


/**
 * @export
 */
export const IntegrityRuptureReconciliationRequestCategoryEnum = {
    AccidentalDbaEdit: 'ACCIDENTAL_DBA_EDIT',
    Corruption: 'CORRUPTION',
    InvestigatedBenign: 'INVESTIGATED_BENIGN',
    Other: 'OTHER'
} as const;
export type IntegrityRuptureReconciliationRequestCategoryEnum = typeof IntegrityRuptureReconciliationRequestCategoryEnum[keyof typeof IntegrityRuptureReconciliationRequestCategoryEnum];

/**
 * 
 * @export
 * @interface IntegrityRuptureReconciliationResult
 */
export interface IntegrityRuptureReconciliationResult {
    /**
     * 
     * @type {string}
     * @memberof IntegrityRuptureReconciliationResult
     */
    failBoundary?: string;
    /**
     * 
     * @type {string}
     * @memberof IntegrityRuptureReconciliationResult
     */
    resumeBoundary?: string;
    /**
     * 
     * @type {number}
     * @memberof IntegrityRuptureReconciliationResult
     */
    conciliationCheckpointId?: number;
    /**
     * 
     * @type {string}
     * @memberof IntegrityRuptureReconciliationResult
     */
    conciliationChainHmac?: string;
    /**
     * 
     * @type {number}
     * @memberof IntegrityRuptureReconciliationResult
     */
    auditLogId?: number;
    /**
     * 
     * @type {number}
     * @memberof IntegrityRuptureReconciliationResult
     */
    resolvedAlertId?: number;
    /**
     * 
     * @type {string}
     * @memberof IntegrityRuptureReconciliationResult
     */
    justification?: string;
    /**
     * 
     * @type {string}
     * @memberof IntegrityRuptureReconciliationResult
     */
    category?: IntegrityRuptureReconciliationResultCategoryEnum;
    /**
     * 
     * @type {Array<number>}
     * @memberof IntegrityRuptureReconciliationResult
     */
    conciliatedAuditLogIds?: Array<number>;
    /**
     * 
     * @type {number}
     * @memberof IntegrityRuptureReconciliationResult
     */
    entryConciliationCount?: number;
}


/**
 * @export
 */
export const IntegrityRuptureReconciliationResultCategoryEnum = {
    AccidentalDbaEdit: 'ACCIDENTAL_DBA_EDIT',
    Corruption: 'CORRUPTION',
    InvestigatedBenign: 'INVESTIGATED_BENIGN',
    Other: 'OTHER'
} as const;
export type IntegrityRuptureReconciliationResultCategoryEnum = typeof IntegrityRuptureReconciliationResultCategoryEnum[keyof typeof IntegrityRuptureReconciliationResultCategoryEnum];

/**
 * 
 * @export
 * @interface IntegrityViolationCappedListEntryIntegrityViolation
 */
export interface IntegrityViolationCappedListEntryIntegrityViolation {
    /**
     * 
     * @type {Array<EntryIntegrityViolation>}
     * @memberof IntegrityViolationCappedListEntryIntegrityViolation
     */
    items?: Array<EntryIntegrityViolation>;
    /**
     * 
     * @type {number}
     * @memberof IntegrityViolationCappedListEntryIntegrityViolation
     */
    totalCount?: number;
    /**
     * 
     * @type {number}
     * @memberof IntegrityViolationCappedListEntryIntegrityViolation
     */
    returnedCount?: number;
    /**
     * 
     * @type {boolean}
     * @memberof IntegrityViolationCappedListEntryIntegrityViolation
     */
    truncated?: boolean;
}
/**
 * 
 * @export
 * @interface KeyRotationResponse
 */
export interface KeyRotationResponse {
    /**
     * ID of the newly introduced key. Status is PENDING at response time; the key becomes PRIMARY automatically once the synchronization window elapses.
     * @type {number}
     * @memberof KeyRotationResponse
     */
    newPrimaryKeyId?: number;
    /**
     * Human-readable message describing the current state and next step
     * @type {string}
     * @memberof KeyRotationResponse
     */
    message?: string;
}
/**
 * 
 * @export
 * @interface PageMetadata
 */
export interface PageMetadata {
    /**
     * 
     * @type {number}
     * @memberof PageMetadata
     */
    size?: number;
    /**
     * 
     * @type {number}
     * @memberof PageMetadata
     */
    number?: number;
    /**
     * 
     * @type {number}
     * @memberof PageMetadata
     */
    totalElements?: number;
    /**
     * 
     * @type {number}
     * @memberof PageMetadata
     */
    totalPages?: number;
}
/**
 * 
 * @export
 * @interface PagedModelAdminResponseDto
 */
export interface PagedModelAdminResponseDto {
    /**
     * 
     * @type {Array<AdminResponseDto>}
     * @memberof PagedModelAdminResponseDto
     */
    content?: Array<AdminResponseDto>;
    /**
     * 
     * @type {PageMetadata}
     * @memberof PagedModelAdminResponseDto
     */
    page?: PageMetadata;
}
/**
 * 
 * @export
 * @interface PagedModelAlertResponseDto
 */
export interface PagedModelAlertResponseDto {
    /**
     * 
     * @type {Array<AlertResponseDto>}
     * @memberof PagedModelAlertResponseDto
     */
    content?: Array<AlertResponseDto>;
    /**
     * 
     * @type {PageMetadata}
     * @memberof PagedModelAlertResponseDto
     */
    page?: PageMetadata;
}
/**
 * 
 * @export
 * @interface PagedModelApiKeyResponseDto
 */
export interface PagedModelApiKeyResponseDto {
    /**
     * 
     * @type {Array<ApiKeyResponseDto>}
     * @memberof PagedModelApiKeyResponseDto
     */
    content?: Array<ApiKeyResponseDto>;
    /**
     * 
     * @type {PageMetadata}
     * @memberof PagedModelApiKeyResponseDto
     */
    page?: PageMetadata;
}
/**
 * 
 * @export
 * @interface PagedModelAuditChainCheckpointResponseDto
 */
export interface PagedModelAuditChainCheckpointResponseDto {
    /**
     * 
     * @type {Array<AuditChainCheckpointResponseDto>}
     * @memberof PagedModelAuditChainCheckpointResponseDto
     */
    content?: Array<AuditChainCheckpointResponseDto>;
    /**
     * 
     * @type {PageMetadata}
     * @memberof PagedModelAuditChainCheckpointResponseDto
     */
    page?: PageMetadata;
}
/**
 * 
 * @export
 * @interface PagedModelAuditChainIncidentResponseDto
 */
export interface PagedModelAuditChainIncidentResponseDto {
    /**
     * 
     * @type {Array<AuditChainIncidentResponseDto>}
     * @memberof PagedModelAuditChainIncidentResponseDto
     */
    content?: Array<AuditChainIncidentResponseDto>;
    /**
     * 
     * @type {PageMetadata}
     * @memberof PagedModelAuditChainIncidentResponseDto
     */
    page?: PageMetadata;
}
/**
 * 
 * @export
 * @interface PagedModelAuditLogResponseDto
 */
export interface PagedModelAuditLogResponseDto {
    /**
     * 
     * @type {Array<AuditLogResponseDto>}
     * @memberof PagedModelAuditLogResponseDto
     */
    content?: Array<AuditLogResponseDto>;
    /**
     * 
     * @type {PageMetadata}
     * @memberof PagedModelAuditLogResponseDto
     */
    page?: PageMetadata;
}
/**
 * 
 * @export
 * @interface PagedModelAuthAttemptDto
 */
export interface PagedModelAuthAttemptDto {
    /**
     * 
     * @type {Array<AuthAttemptDto>}
     * @memberof PagedModelAuthAttemptDto
     */
    content?: Array<AuthAttemptDto>;
    /**
     * 
     * @type {PageMetadata}
     * @memberof PagedModelAuthAttemptDto
     */
    page?: PageMetadata;
}
/**
 * 
 * @export
 * @interface PagedModelEncryptionKeyResponse
 */
export interface PagedModelEncryptionKeyResponse {
    /**
     * 
     * @type {Array<EncryptionKeyResponse>}
     * @memberof PagedModelEncryptionKeyResponse
     */
    content?: Array<EncryptionKeyResponse>;
    /**
     * 
     * @type {PageMetadata}
     * @memberof PagedModelEncryptionKeyResponse
     */
    page?: PageMetadata;
}
/**
 * 
 * @export
 * @interface PagedModelEnrollmentResponseDto
 */
export interface PagedModelEnrollmentResponseDto {
    /**
     * 
     * @type {Array<EnrollmentResponseDto>}
     * @memberof PagedModelEnrollmentResponseDto
     */
    content?: Array<EnrollmentResponseDto>;
    /**
     * 
     * @type {PageMetadata}
     * @memberof PagedModelEnrollmentResponseDto
     */
    page?: PageMetadata;
}
/**
 * 
 * @export
 * @interface PagedModelIntegrationResponseDto
 */
export interface PagedModelIntegrationResponseDto {
    /**
     * 
     * @type {Array<IntegrationResponseDto>}
     * @memberof PagedModelIntegrationResponseDto
     */
    content?: Array<IntegrationResponseDto>;
    /**
     * 
     * @type {PageMetadata}
     * @memberof PagedModelIntegrationResponseDto
     */
    page?: PageMetadata;
}
/**
 * 
 * @export
 * @interface PagedModelReencryptionBatchResponse
 */
export interface PagedModelReencryptionBatchResponse {
    /**
     * 
     * @type {Array<ReencryptionBatchResponse>}
     * @memberof PagedModelReencryptionBatchResponse
     */
    content?: Array<ReencryptionBatchResponse>;
    /**
     * 
     * @type {PageMetadata}
     * @memberof PagedModelReencryptionBatchResponse
     */
    page?: PageMetadata;
}
/**
 * 
 * @export
 * @interface PagedModelTenantResponseDto
 */
export interface PagedModelTenantResponseDto {
    /**
     * 
     * @type {Array<TenantResponseDto>}
     * @memberof PagedModelTenantResponseDto
     */
    content?: Array<TenantResponseDto>;
    /**
     * 
     * @type {PageMetadata}
     * @memberof PagedModelTenantResponseDto
     */
    page?: PageMetadata;
}
/**
 * 
 * @export
 * @interface ProblemDetail
 */
export interface ProblemDetail {
    /**
     * 
     * @type {string}
     * @memberof ProblemDetail
     */
    type?: string;
    /**
     * 
     * @type {string}
     * @memberof ProblemDetail
     */
    title?: string;
    /**
     * 
     * @type {number}
     * @memberof ProblemDetail
     */
    status?: number;
    /**
     * 
     * @type {string}
     * @memberof ProblemDetail
     */
    detail?: string;
    /**
     * 
     * @type {string}
     * @memberof ProblemDetail
     */
    instance?: string;
    /**
     * 
     * @type {{ [key: string]: any; }}
     * @memberof ProblemDetail
     */
    properties?: { [key: string]: any; };
}
/**
 * Public instance metadata (branding, optional public Auth API URL for QR alignment)
 * @export
 * @interface PublicInstanceInfoResponseDto
 */
export interface PublicInstanceInfoResponseDto {
    /**
     * Public base URL of the Auth API (same as authUrl in enrollment QR JSON when configured)
     * @type {string}
     * @memberof PublicInstanceInfoResponseDto
     */
    authApiPublicBaseUrl?: string | null;
    /**
     * Instance / organization display name
     * @type {string}
     * @memberof PublicInstanceInfoResponseDto
     */
    instanceName?: string;
    /**
     * Optional instance or organization description
     * @type {string}
     * @memberof PublicInstanceInfoResponseDto
     */
    instanceDescription?: string | null;
    /**
     * Optional URL for About / learn more (e.g. company instance page)
     * @type {string}
     * @memberof PublicInstanceInfoResponseDto
     */
    aboutUrl?: string | null;
}
/**
 * 
 * @export
 * @interface ReencryptionBatchResponse
 */
export interface ReencryptionBatchResponse {
    /**
     * 
     * @type {number}
     * @memberof ReencryptionBatchResponse
     */
    batchId?: number;
    /**
     * 
     * @type {string}
     * @memberof ReencryptionBatchResponse
     */
    targetTable?: string;
    /**
     * 
     * @type {string}
     * @memberof ReencryptionBatchResponse
     */
    targetColumn?: string;
    /**
     * 
     * @type {number}
     * @memberof ReencryptionBatchResponse
     */
    oldKeyId?: number;
    /**
     * 
     * @type {number}
     * @memberof ReencryptionBatchResponse
     */
    newKeyId?: number;
    /**
     * 
     * @type {string}
     * @memberof ReencryptionBatchResponse
     */
    status?: string;
    /**
     * 
     * @type {number}
     * @memberof ReencryptionBatchResponse
     */
    recordsTotal?: number;
    /**
     * 
     * @type {number}
     * @memberof ReencryptionBatchResponse
     */
    recordsDone?: number;
    /**
     * 
     * @type {number}
     * @memberof ReencryptionBatchResponse
     */
    recordsFailed?: number;
    /**
     * 
     * @type {number}
     * @memberof ReencryptionBatchResponse
     */
    recordsSkipped?: number;
    /**
     * 
     * @type {number}
     * @memberof ReencryptionBatchResponse
     */
    progressPct?: number;
    /**
     * 
     * @type {string}
     * @memberof ReencryptionBatchResponse
     */
    startedAt?: string;
    /**
     * 
     * @type {string}
     * @memberof ReencryptionBatchResponse
     */
    completedAt?: string;
    /**
     * 
     * @type {string}
     * @memberof ReencryptionBatchResponse
     */
    errorMessage?: string;
    /**
     * 
     * @type {number}
     * @memberof ReencryptionBatchResponse
     */
    retryCount?: number;
    /**
     * 
     * @type {number}
     * @memberof ReencryptionBatchResponse
     */
    shardIndex?: number;
    /**
     * 
     * @type {number}
     * @memberof ReencryptionBatchResponse
     */
    shardCount?: number;
}
/**
 * 
 * @export
 * @interface ReencryptionKeyResponse
 */
export interface ReencryptionKeyResponse {
    /**
     * 
     * @type {number}
     * @memberof ReencryptionKeyResponse
     */
    keyId?: number;
    /**
     * 
     * @type {number}
     * @memberof ReencryptionKeyResponse
     */
    batchesEnqueued?: number;
    /**
     * 
     * @type {Array<number>}
     * @memberof ReencryptionKeyResponse
     */
    batchIds?: Array<number>;
    /**
     * 
     * @type {string}
     * @memberof ReencryptionKeyResponse
     */
    message?: string;
}
/**
 * 
 * @export
 * @interface ReencryptionTriggerResponse
 */
export interface ReencryptionTriggerResponse {
    /**
     * 
     * @type {number}
     * @memberof ReencryptionTriggerResponse
     */
    batchesEnqueued?: number;
    /**
     * 
     * @type {Array<number>}
     * @memberof ReencryptionTriggerResponse
     */
    batchIds?: Array<number>;
    /**
     * 
     * @type {string}
     * @memberof ReencryptionTriggerResponse
     */
    message?: string;
}
/**
 * 
 * @export
 * @interface RetroactiveIntegrityValidationRunRequest
 */
export interface RetroactiveIntegrityValidationRunRequest {
    /**
     * 
     * @type {string}
     * @memberof RetroactiveIntegrityValidationRunRequest
     */
    from: string;
    /**
     * 
     * @type {string}
     * @memberof RetroactiveIntegrityValidationRunRequest
     */
    to: string;
    /**
     * 
     * @type {boolean}
     * @memberof RetroactiveIntegrityValidationRunRequest
     */
    raiseAlert?: boolean;
}
/**
 * 
 * @export
 * @interface RetroactiveIntegrityValidationRunResponse
 */
export interface RetroactiveIntegrityValidationRunResponse {
    /**
     * 
     * @type {string}
     * @memberof RetroactiveIntegrityValidationRunResponse
     */
    windowStart?: string;
    /**
     * 
     * @type {string}
     * @memberof RetroactiveIntegrityValidationRunResponse
     */
    windowEnd?: string;
    /**
     * 
     * @type {string}
     * @memberof RetroactiveIntegrityValidationRunResponse
     */
    scope?: string;
    /**
     * 
     * @type {boolean}
     * @memberof RetroactiveIntegrityValidationRunResponse
     */
    skipped?: boolean;
    /**
     * 
     * @type {string}
     * @memberof RetroactiveIntegrityValidationRunResponse
     */
    skipReason?: string;
    /**
     * 
     * @type {boolean}
     * @memberof RetroactiveIntegrityValidationRunResponse
     */
    intact?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof RetroactiveIntegrityValidationRunResponse
     */
    alertRaised?: boolean;
    /**
     * 
     * @type {number}
     * @memberof RetroactiveIntegrityValidationRunResponse
     */
    alertId?: number;
    /**
     * 
     * @type {string}
     * @memberof RetroactiveIntegrityValidationRunResponse
     */
    chainStatus?: string;
    /**
     * 
     * @type {number}
     * @memberof RetroactiveIntegrityValidationRunResponse
     */
    entryHmacViolationCount?: number;
    /**
     * 
     * @type {number}
     * @memberof RetroactiveIntegrityValidationRunResponse
     */
    entryAlertEligibleCount?: number;
    /**
     * 
     * @type {number}
     * @memberof RetroactiveIntegrityValidationRunResponse
     */
    chainViolationCount?: number;
    /**
     * 
     * @type {string}
     * @memberof RetroactiveIntegrityValidationRunResponse
     */
    triggerSource?: RetroactiveIntegrityValidationRunResponseTriggerSourceEnum;
}


/**
 * @export
 */
export const RetroactiveIntegrityValidationRunResponseTriggerSourceEnum = {
    Scheduled: 'SCHEDULED',
    Operator: 'OPERATOR'
} as const;
export type RetroactiveIntegrityValidationRunResponseTriggerSourceEnum = typeof RetroactiveIntegrityValidationRunResponseTriggerSourceEnum[keyof typeof RetroactiveIntegrityValidationRunResponseTriggerSourceEnum];

/**
 * 
 * @export
 * @interface TenantActivateRequestDto
 */
export interface TenantActivateRequestDto {
    /**
     * 
     * @type {string}
     * @memberof TenantActivateRequestDto
     */
    reason?: string;
}
/**
 * Request DTO for creating a tenant
 * @export
 * @interface TenantCreateRequestDto
 */
export interface TenantCreateRequestDto {
    /**
     * Unique name for the tenant
     * @type {string}
     * @memberof TenantCreateRequestDto
     */
    tenantName: string;
    /**
     * Optional description of the tenant
     * @type {string}
     * @memberof TenantCreateRequestDto
     */
    tenantDescription?: string;
    /**
     * Legal name of the organization
     * @type {string}
     * @memberof TenantCreateRequestDto
     */
    organizationName?: string;
    /**
     * Primary domain of the organization
     * @type {string}
     * @memberof TenantCreateRequestDto
     */
    organizationDomain?: string;
    /**
     * ISO 3166-1 alpha-2 country code
     * @type {string}
     * @memberof TenantCreateRequestDto
     */
    countryCode?: string;
    /**
     * IANA timezone identifier
     * @type {string}
     * @memberof TenantCreateRequestDto
     */
    timezone?: string;
    /**
     * Primary contact full name
     * @type {string}
     * @memberof TenantCreateRequestDto
     */
    primaryContactName?: string;
    /**
     * Primary contact email address
     * @type {string}
     * @memberof TenantCreateRequestDto
     */
    primaryContactEmail?: string;
    /**
     * Primary contact phone number. Accepts common separators and is normalized to E.164 on write.
     * @type {string}
     * @memberof TenantCreateRequestDto
     */
    primaryContactPhoneNumber?: string;
}
/**
 * 
 * @export
 * @interface TenantDeactivateRequestDto
 */
export interface TenantDeactivateRequestDto {
    /**
     * 
     * @type {string}
     * @memberof TenantDeactivateRequestDto
     */
    reason?: string;
}
/**
 * Response DTO containing tenant information
 * @export
 * @interface TenantResponseDto
 */
export interface TenantResponseDto {
    /**
     * Unique identifier for the tenant
     * @type {number}
     * @memberof TenantResponseDto
     */
    tenantId?: number;
    /**
     * Optimistic lock version. Include in PATCH/PUT requests to prevent concurrent update conflicts.
     * @type {number}
     * @memberof TenantResponseDto
     */
    version?: number;
    /**
     * Unique name of the tenant
     * @type {string}
     * @memberof TenantResponseDto
     */
    tenantName?: string;
    /**
     * Optional description of the tenant
     * @type {string}
     * @memberof TenantResponseDto
     */
    tenantDescription?: string;
    /**
     * Legal name of the organization
     * @type {string}
     * @memberof TenantResponseDto
     */
    organizationName?: string;
    /**
     * Primary domain of the organization
     * @type {string}
     * @memberof TenantResponseDto
     */
    organizationDomain?: string;
    /**
     * ISO 3166-1 alpha-2 country code
     * @type {string}
     * @memberof TenantResponseDto
     */
    countryCode?: string;
    /**
     * IANA timezone identifier
     * @type {string}
     * @memberof TenantResponseDto
     */
    timezone?: string;
    /**
     * Primary contact full name
     * @type {string}
     * @memberof TenantResponseDto
     */
    primaryContactName?: string;
    /**
     * Primary contact email address
     * @type {string}
     * @memberof TenantResponseDto
     */
    primaryContactEmail?: string;
    /**
     * Primary contact phone number in canonical E.164 format
     * @type {string}
     * @memberof TenantResponseDto
     */
    primaryContactPhoneNumber?: string;
    /**
     * Timestamp when the tenant was created
     * @type {string}
     * @memberof TenantResponseDto
     */
    createdAt?: string;
    /**
     * Timestamp of the last modification
     * @type {string}
     * @memberof TenantResponseDto
     */
    updatedAt?: string;
    /**
     * Flag indicating if the tenant is active
     * @type {boolean}
     * @memberof TenantResponseDto
     */
    active?: boolean;
    /**
     * Flag indicating if this is the system tenant
     * @type {boolean}
     * @memberof TenantResponseDto
     */
    isSystemTenant?: boolean;
    /**
     * Timestamp when the tenant was deactivated (null if never deactivated; preserved for audit traceability)
     * @type {string}
     * @memberof TenantResponseDto
     */
    deactivatedAt?: string;
    /**
     * Whether the tenant is currently operational (active flag is true)
     * @type {boolean}
     * @memberof TenantResponseDto
     */
    operational?: boolean;
}
/**
 * Request DTO for updating a tenant (partial update)
 * @export
 * @interface TenantUpdateRequestDto
 */
export interface TenantUpdateRequestDto {
    /**
     * Optimistic lock version from GET response. When provided, update fails with 409 if resource was modified since last fetch.
     * @type {number}
     * @memberof TenantUpdateRequestDto
     */
    version?: number;
    /**
     * New unique name for the tenant
     * @type {string}
     * @memberof TenantUpdateRequestDto
     */
    tenantName?: string;
    /**
     * Updated description of the tenant
     * @type {string}
     * @memberof TenantUpdateRequestDto
     */
    tenantDescription?: string;
    /**
     * Legal name of the organization
     * @type {string}
     * @memberof TenantUpdateRequestDto
     */
    organizationName?: string;
    /**
     * Primary domain of the organization
     * @type {string}
     * @memberof TenantUpdateRequestDto
     */
    organizationDomain?: string;
    /**
     * ISO 3166-1 alpha-2 country code
     * @type {string}
     * @memberof TenantUpdateRequestDto
     */
    countryCode?: string;
    /**
     * IANA timezone identifier
     * @type {string}
     * @memberof TenantUpdateRequestDto
     */
    timezone?: string;
    /**
     * Primary contact full name
     * @type {string}
     * @memberof TenantUpdateRequestDto
     */
    primaryContactName?: string;
    /**
     * Primary contact email address
     * @type {string}
     * @memberof TenantUpdateRequestDto
     */
    primaryContactEmail?: string;
    /**
     * Primary contact phone number. Accepts common separators and is normalized to E.164 on write.
     * @type {string}
     * @memberof TenantUpdateRequestDto
     */
    primaryContactPhoneNumber?: string;
}
/**
 * 
 * @export
 * @interface UndeclaredGap
 */
export interface UndeclaredGap {
    /**
     * 
     * @type {string}
     * @memberof UndeclaredGap
     */
    gapStart?: string;
    /**
     * 
     * @type {string}
     * @memberof UndeclaredGap
     */
    gapEnd?: string;
    /**
     * 
     * @type {number}
     * @memberof UndeclaredGap
     */
    gapMinutes?: number;
}
