/* tslint:disable */
/* eslint-disable */
/**
 * Auth attempt creation data
 * @export
 * @interface AuthAttemptCreateRequestDto
 */
export interface AuthAttemptCreateRequestDto {
    /**
     * 
     * @type {number}
     * @memberof AuthAttemptCreateRequestDto
     */
    enrollmentId?: number;
    /**
     * 
     * @type {boolean}
     * @memberof AuthAttemptCreateRequestDto
     */
    challengeRequested?: boolean;
}
/**
 * Response DTO containing created authentication attempt details
 * @export
 * @interface AuthAttemptCreateResponseDto
 */
export interface AuthAttemptCreateResponseDto {
    /**
     * Unique identifier of the created authentication attempt
     * @type {number}
     * @memberof AuthAttemptCreateResponseDto
     */
    authAttemptId?: number;
}
/**
 * Complete authentication attempt data
 * @export
 * @interface AuthAttemptDto
 */
export interface AuthAttemptDto {
    /**
     * 
     * @type {number}
     * @memberof AuthAttemptDto
     */
    authAttemptId?: number;
    /**
     * 
     * @type {number}
     * @memberof AuthAttemptDto
     */
    enrollmentId?: number;
    /**
     * 
     * @type {boolean}
     * @memberof AuthAttemptDto
     */
    authAttemptRead?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof AuthAttemptDto
     */
    authAttemptResponded?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof AuthAttemptDto
     */
    authAttemptValid?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof AuthAttemptDto
     */
    authAttemptAccepted?: boolean;
    /**
     * 
     * @type {number}
     * @memberof AuthAttemptDto
     */
    authAttemptChallenge?: number;
    /**
     * 
     * @type {string}
     * @memberof AuthAttemptDto
     */
    authAttemptProofToken?: string;
    /**
     * 
     * @type {string}
     * @memberof AuthAttemptDto
     */
    deviceProofTokenValid?: string;
    /**
     * 
     * @type {string}
     * @memberof AuthAttemptDto
     */
    createdAt?: string;
    /**
     * 
     * @type {string}
     * @memberof AuthAttemptDto
     */
    expiresAt?: string;
}
/**
 * Response DTO for authentication wait operation
 * @export
 * @interface AuthAttemptWaitResponseDto
 */
export interface AuthAttemptWaitResponseDto {
    /**
     * 
     * @type {AuthAttemptDto}
     * @memberof AuthAttemptWaitResponseDto
     */
    authAttempt?: AuthAttemptDto;
    /**
     * Calculated authentication status
     * @type {string}
     * @memberof AuthAttemptWaitResponseDto
     */
    status?: AuthAttemptWaitResponseDtoStatusEnum;
    /**
     * Whether authentication process is complete
     * @type {boolean}
     * @memberof AuthAttemptWaitResponseDto
     */
    completed?: boolean;
    /**
     * Whether wait ended due to timeout
     * @type {boolean}
     * @memberof AuthAttemptWaitResponseDto
     */
    timeoutReached?: boolean;
    /**
     * Actual duration waited in seconds
     * @type {number}
     * @memberof AuthAttemptWaitResponseDto
     */
    waitDuration?: number;
    /**
     * Timestamp when wait operation completed
     * @type {string}
     * @memberof AuthAttemptWaitResponseDto
     */
    completedAt?: string;
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
}
/**
 * 
 * @export
 * @interface EnrollmentResponseDto
 */
export interface EnrollmentResponseDto {
    /**
     * 
     * @type {number}
     * @memberof EnrollmentResponseDto
     */
    enrollmentId?: number;
    /**
     * 
     * @type {number}
     * @memberof EnrollmentResponseDto
     */
    integrationId?: number;
    /**
     * 
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    enrollmentName?: string;
    /**
     * 
     * @type {boolean}
     * @memberof EnrollmentResponseDto
     */
    enrollmentRead?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof EnrollmentResponseDto
     */
    enrollmentVerified?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof EnrollmentResponseDto
     */
    enrollmentValid?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof EnrollmentResponseDto
     */
    enrollmentActive?: boolean;
    /**
     * 
     * @type {number}
     * @memberof EnrollmentResponseDto
     */
    enrollmentChallenge?: number;
    /**
     * 
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    enrollmentProofToken?: string;
    /**
     * 
     * @type {boolean}
     * @memberof EnrollmentResponseDto
     */
    authAttemptChallengeRequired?: boolean;
    /**
     * 
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    integrationPublicKey?: string;
    /**
     * 
     * @type {string}
     * @memberof EnrollmentResponseDto
     */
    devicePublicKey?: string;
}
/**
 * Integration creation data
 * @export
 * @interface IntegrationCreateRequestDto
 */
export interface IntegrationCreateRequestDto {
    /**
     * URL or path to the integration's logo image
     * @type {string}
     * @memberof IntegrationCreateRequestDto
     */
    logo?: string;
    /**
     * List of internationalization entries for multi-language support
     * @type {Array<IntegrationI18nCreateDto>}
     * @memberof IntegrationCreateRequestDto
     */
    i18n?: Array<IntegrationI18nCreateDto>;
}
/**
 * Response DTO for creating new Integration entities
 * @export
 * @interface IntegrationCreateResponseDto
 */
export interface IntegrationCreateResponseDto {
    /**
     * 
     * @type {number}
     * @memberof IntegrationCreateResponseDto
     */
    id?: number;
}
/**
 * Create DTO for integration internationalization data
 * @export
 * @interface IntegrationI18nCreateDto
 */
export interface IntegrationI18nCreateDto {
    /**
     * Language code for the localized content
     * @type {string}
     * @memberof IntegrationI18nCreateDto
     */
    language: string;
    /**
     * Localized name of the integration
     * @type {string}
     * @memberof IntegrationI18nCreateDto
     */
    name: string;
    /**
     * Localized description of the integration
     * @type {string}
     * @memberof IntegrationI18nCreateDto
     */
    description?: string;
}
/**
 * Response DTO for integration internationalization data
 * @export
 * @interface IntegrationI18nResponseDto
 */
export interface IntegrationI18nResponseDto {
    /**
     * Unique identifier for the internationalization record
     * @type {number}
     * @memberof IntegrationI18nResponseDto
     */
    id?: number;
    /**
     * Language code for the localized content
     * @type {string}
     * @memberof IntegrationI18nResponseDto
     */
    language?: string;
    /**
     * Localized name of the integration
     * @type {string}
     * @memberof IntegrationI18nResponseDto
     */
    name?: string;
    /**
     * Localized description of the integration
     * @type {string}
     * @memberof IntegrationI18nResponseDto
     */
    description?: string;
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
     * URL or path to the integration logo image
     * @type {string}
     * @memberof IntegrationResponseDto
     */
    logo?: string;
    /**
     * Integration status flag
     * @type {boolean}
     * @memberof IntegrationResponseDto
     */
    active?: boolean;
    /**
     * Timestamp when the integration was created
     * @type {string}
     * @memberof IntegrationResponseDto
     */
    createdAt?: string;
    /**
     * List of internationalized content for multiple languages
     * @type {Array<IntegrationI18nResponseDto>}
     * @memberof IntegrationResponseDto
     */
    i18n?: Array<IntegrationI18nResponseDto>;
}
