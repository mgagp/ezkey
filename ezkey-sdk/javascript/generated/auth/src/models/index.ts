/* tslint:disable */
/* eslint-disable */
/**
 * Request DTO for checking pending authentication attempts
 * @export
 * @interface AuthAttemptPendingRequestDto
 */
export interface AuthAttemptPendingRequestDto {
    /**
     * Enrollment ID to check for pending authentication attempts
     * @type {number}
     * @memberof AuthAttemptPendingRequestDto
     */
    enrollmentId: number;
    /**
     * Cryptographic proof token that authenticates the enrollment
     * @type {string}
     * @memberof AuthAttemptPendingRequestDto
     */
    enrollmentProofToken: string;
    /**
     * Device proof token for authentication
     * @type {string}
     * @memberof AuthAttemptPendingRequestDto
     */
    deviceProofToken: string;
    /**
     * Cryptographically signed device proof token
     * @type {string}
     * @memberof AuthAttemptPendingRequestDto
     */
    deviceProofTokenSigned: string;
}
/**
 * Response DTO containing pending authentication attempt details
 * @export
 * @interface AuthAttemptPendingResponseDto
 */
export interface AuthAttemptPendingResponseDto {
    /**
     * Unique identifier of the authentication attempt
     * @type {number}
     * @memberof AuthAttemptPendingResponseDto
     */
    authAttemptId: number;
    /**
     * Authentication proof token containing challenge data
     * @type {string}
     * @memberof AuthAttemptPendingResponseDto
     */
    authAttemptProofToken: string;
    /**
     * Integration-signed authentication proof token for integrity
     * @type {string}
     * @memberof AuthAttemptPendingResponseDto
     */
    authAttemptProofTokenSignedByIntegration: string;
    /**
     * Whether additional challenge validation is required
     * @type {boolean}
     * @memberof AuthAttemptPendingResponseDto
     */
    authAttemptChallengeRequired: boolean;
    /**
     * Optional short title for the approval request (null if no context provided). Example: "Payment Approval"
     * @type {string}
     * @memberof AuthAttemptPendingResponseDto
     */
    contextTitle?: string;
    /**
     * Optional descriptive message for the approver (null if no context provided). Example: "Authorize payment batch #1497 to Acme Corp for $1,400"
     * @type {string}
     * @memberof AuthAttemptPendingResponseDto
     */
    contextMessage?: string;
}
/**
 * Request DTO for submitting authentication attempt responses
 * @export
 * @interface AuthAttemptRespondRequestDto
 */
export interface AuthAttemptRespondRequestDto {
    /**
     * Authentication attempt ID being responded to
     * @type {number}
     * @memberof AuthAttemptRespondRequestDto
     */
    authAttemptId: number;
    /**
     * Device-signed proof token for authentication validation
     * @type {string}
     * @memberof AuthAttemptRespondRequestDto
     */
    authAttemptProofTokenSignedByDevice: string;
    /**
     * User's response to authentication challenge (if required)
     * @type {number}
     * @memberof AuthAttemptRespondRequestDto
     */
    authAttemptChallengeResponse?: number | null;
    /**
     * User's decision: true to approve, false to deny
     * @type {boolean}
     * @memberof AuthAttemptRespondRequestDto
     */
    authAttemptAccepted: boolean;
}
/**
 * Response DTO for authentication attempt submissions
 * @export
 * @interface AuthAttemptRespondResponseDto
 */
export interface AuthAttemptRespondResponseDto {
    /**
     * Authentication attempt identifier
     * @type {number}
     * @memberof AuthAttemptRespondResponseDto
     */
    authAttemptId: number;
    /**
     * Authentication result
     * @type {string}
     * @memberof AuthAttemptRespondResponseDto
     */
    authAttemptResult: AuthAttemptRespondResponseDtoAuthAttemptResultEnum;
    /**
     * Success confirmation or error details for user feedback
     * @type {string}
     * @memberof AuthAttemptRespondResponseDto
     */
    authAttemptMessage: string;
    /**
     * Ed25519 signature (Base64URL, no padding, raw 64 bytes) over proofToken|authAttemptId|result|message (see AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)
     * @type {string}
     * @memberof AuthAttemptRespondResponseDto
     */
    authAttemptProofTokenResultSignedByIntegration?: string;
}


/**
 * @export
 */
export const AuthAttemptRespondResponseDtoAuthAttemptResultEnum = {
    Approved: 'APPROVED',
    Denied: 'DENIED',
    Failed: 'FAILED',
    Expired: 'EXPIRED'
} as const;
export type AuthAttemptRespondResponseDtoAuthAttemptResultEnum = typeof AuthAttemptRespondResponseDtoAuthAttemptResultEnum[keyof typeof AuthAttemptRespondResponseDtoAuthAttemptResultEnum];

/**
 * Request DTO for enrollment binding initiation with proof token
 * @export
 * @interface EnrollmentBindRequestDto
 */
export interface EnrollmentBindRequestDto {
    /**
     * Enrollment ID to bind to the mobile device
     * @type {number}
     * @memberof EnrollmentBindRequestDto
     */
    enrollmentId: number;
    /**
     * Enrollment proof token for authentication
     * @type {string}
     * @memberof EnrollmentBindRequestDto
     */
    enrollmentProofToken: string;
}
/**
 * Response DTO containing enrollment binding information
 * @export
 * @interface EnrollmentBindResponseDto
 */
export interface EnrollmentBindResponseDto {
    /**
     * Enrollment ID that was bound to the mobile device
     * @type {number}
     * @memberof EnrollmentBindResponseDto
     */
    enrollmentId: number;
    /**
     * Integration's public key for cryptographic verification
     * @type {string}
     * @memberof EnrollmentBindResponseDto
     */
    integrationPublicKey: string;
    /**
     * Algorithm for integrationPublicKey (ed25519: raw 32-byte key, Base64URL no padding)
     * @type {string}
     * @memberof EnrollmentBindResponseDto
     */
    integrationKeyAlgorithm: string;
    /**
     * Enrollment proof token to be signed by the device
     * @type {string}
     * @memberof EnrollmentBindResponseDto
     */
    enrollmentProofToken: string;
    /**
     * Display name of the integration
     * @type {string}
     * @memberof EnrollmentBindResponseDto
     */
    integrationName?: string;
    /**
     * Description of the integration
     * @type {string}
     * @memberof EnrollmentBindResponseDto
     */
    integrationDescription?: string;
    /**
     * Human-readable name for the enrollment
     * @type {string}
     * @memberof EnrollmentBindResponseDto
     */
    enrollmentName?: string;
    /**
     * Tenant ID of the integration associated with this enrollment
     * @type {number}
     * @memberof EnrollmentBindResponseDto
     */
    tenantId?: number;
    /**
     * Tenant display name of the integration associated with this enrollment
     * @type {string}
     * @memberof EnrollmentBindResponseDto
     */
    tenantName?: string;
    /**
     * Tenant description of the integration associated with this enrollment
     * @type {string}
     * @memberof EnrollmentBindResponseDto
     */
    tenantDescription?: string;
    /**
     * Ed25519 signature (Base64URL, no padding, raw 64 bytes) over the canonical bind payload (see docs/ENROLLMENT_SIGNATURE_PAYLOAD.md)
     * @type {string}
     * @memberof EnrollmentBindResponseDto
     */
    enrollmentBindPayloadSignedByIntegration: string;
}
/**
 * Request DTO for integration-signed installation branding
 * @export
 * @interface EnrollmentInstanceInfoRequestDto
 */
export interface EnrollmentInstanceInfoRequestDto {
    /**
     * Enrollment proof token for authentication
     * @type {string}
     * @memberof EnrollmentInstanceInfoRequestDto
     */
    enrollmentProofToken: string;
}
/**
 * Integration-signed installation branding for enrolled clients
 * @export
 * @interface EnrollmentInstanceInfoResponseDto
 */
export interface EnrollmentInstanceInfoResponseDto {
    /**
     * Enrollment ID that authenticated the request
     * @type {number}
     * @memberof EnrollmentInstanceInfoResponseDto
     */
    enrollmentId: number;
    /**
     * Public base URL of the Auth API (same as authUrl in enrollment QR JSON when configured)
     * @type {string}
     * @memberof EnrollmentInstanceInfoResponseDto
     */
    authApiPublicBaseUrl?: string | null;
    /**
     * Instance / organization display name
     * @type {string}
     * @memberof EnrollmentInstanceInfoResponseDto
     */
    instanceName?: string;
    /**
     * Optional instance or organization description
     * @type {string}
     * @memberof EnrollmentInstanceInfoResponseDto
     */
    instanceDescription?: string | null;
    /**
     * Optional URL for About / learn more
     * @type {string}
     * @memberof EnrollmentInstanceInfoResponseDto
     */
    aboutUrl?: string | null;
    /**
     * Base64URL (no padding) Ed25519 signature over the canonical INSTANCE_INFO payload
     * @type {string}
     * @memberof EnrollmentInstanceInfoResponseDto
     */
    instanceInfoPayloadSignedByIntegration: string;
}
/**
 * Request DTO for enrollment verification completion
 * @export
 * @interface EnrollmentVerifyRequestDto
 */
export interface EnrollmentVerifyRequestDto {
    /**
     * Enrollment ID being verified
     * @type {number}
     * @memberof EnrollmentVerifyRequestDto
     */
    enrollmentId: number;
    /**
     * User's response to the enrollment challenge
     * @type {number}
     * @memberof EnrollmentVerifyRequestDto
     */
    challengeResponse: number;
    /**
     * Mobile device's generated public key
     * @type {string}
     * @memberof EnrollmentVerifyRequestDto
     */
    devicePublicKey: string;
    /**
     * Device-signed enrollment proof token
     * @type {string}
     * @memberof EnrollmentVerifyRequestDto
     */
    enrollmentProofTokenSigned: string;
    /**
     * Client-reported tier for device private key protection: NONE (e.g. demo), STANDARD (hardware keystore), STRONG (StrongBox or equivalent)
     * @type {string}
     * @memberof EnrollmentVerifyRequestDto
     */
    devicePrivateKeyStorageTier?: EnrollmentVerifyRequestDtoDevicePrivateKeyStorageTierEnum;
}


/**
 * @export
 */
export const EnrollmentVerifyRequestDtoDevicePrivateKeyStorageTierEnum = {
    None: 'NONE',
    Standard: 'STANDARD',
    Strong: 'STRONG'
} as const;
export type EnrollmentVerifyRequestDtoDevicePrivateKeyStorageTierEnum = typeof EnrollmentVerifyRequestDtoDevicePrivateKeyStorageTierEnum[keyof typeof EnrollmentVerifyRequestDtoDevicePrivateKeyStorageTierEnum];

/**
 * Response DTO for enrollment verification completion
 * @export
 * @interface EnrollmentVerifyResponseDto
 */
export interface EnrollmentVerifyResponseDto {
    /**
     * Whether the enrollment is now active and ready for authentication
     * @type {boolean}
     * @memberof EnrollmentVerifyResponseDto
     */
    active: boolean;
    /**
     * Human-readable verify completion message (included in the integration signature)
     * @type {string}
     * @memberof EnrollmentVerifyResponseDto
     */
    enrollmentVerifyMessage: string;
    /**
     * Ed25519 signature (Base64URL, no padding, raw 64 bytes) over proofToken|enrollmentId|VERIFIED|message (see docs/ENROLLMENT_SIGNATURE_PAYLOAD.md)
     * @type {string}
     * @memberof EnrollmentVerifyResponseDto
     */
    enrollmentVerifyPayloadSignedByIntegration: string;
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
