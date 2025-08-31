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
     * The authentication result
     * @type {string}
     * @memberof AuthAttemptRespondResponseDto
     */
    result: AuthAttemptRespondResponseDtoResultEnum;
    /**
     * Success confirmation or error details for user feedback
     * @type {string}
     * @memberof AuthAttemptRespondResponseDto
     */
    message: string;
}
/**
 * @export
 */
export declare const AuthAttemptRespondResponseDtoResultEnum: {
    readonly Approved: "APPROVED";
    readonly Denied: "DENIED";
    readonly Failed: "FAILED";
    readonly Expired: "EXPIRED";
};
export type AuthAttemptRespondResponseDtoResultEnum = typeof AuthAttemptRespondResponseDtoResultEnum[keyof typeof AuthAttemptRespondResponseDtoResultEnum];
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
     * Enrollment proof token to be signed by the device
     * @type {string}
     * @memberof EnrollmentBindResponseDto
     */
    enrollmentProofToken: string;
    /**
     * Logo URL or base64-encoded image for the integration
     * @type {string}
     * @memberof EnrollmentBindResponseDto
     */
    integrationLogo?: string;
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
}
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
}
//# sourceMappingURL=index.d.ts.map