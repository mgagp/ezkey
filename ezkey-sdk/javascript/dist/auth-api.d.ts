import { EnrollmentBindResponseDto, EnrollmentVerifyResponseDto, AuthAttemptPendingResponseDto, AuthAttemptRespondResponseDto } from '../generated/auth/src/models';
import { EzkeyConfig } from './config';
/**
 * Wrapper for Ezkey Auth API.
 * Provides simplified access to the Auth API operations for device
 * enrollment and authentication flows.
 */
export declare class EzkeyAuthAPI {
    private readonly enrollmentApi;
    private readonly authAttemptApi;
    constructor(config: EzkeyConfig);
    /**
     * Binds a device to an enrollment.
     */
    bindEnrollment(enrollmentId: number, acceptLanguage?: string): Promise<EnrollmentBindResponseDto>;
    /**
     * Verifies and completes the enrollment process.
     */
    verifyEnrollment(enrollmentId: number, challengeResponse: number, devicePublicKey: string, enrollmentProofTokenSigned: string): Promise<EnrollmentVerifyResponseDto>;
    /**
     * Checks for pending authentication requests.
     * @returns Pending authentication details or null if no pending requests
     */
    checkPendingAuth(enrollmentId: number, deviceProofToken: string, deviceProofTokenSigned: string): Promise<AuthAttemptPendingResponseDto | null>;
    /**
     * Responds to an authentication attempt.
     */
    respondToAuth(authAttemptId: number, authAttemptProofTokenSigned: string, authAttemptAccepted: boolean, challengeResponse?: number): Promise<AuthAttemptRespondResponseDto>;
}
//# sourceMappingURL=auth-api.d.ts.map