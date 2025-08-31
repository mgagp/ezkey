import { IntegrationCreateResponseDto, IntegrationResponseDto, EnrollmentCreateResponseDto, EnrollmentResponseDto, AuthAttemptCreateResponseDto, AuthAttemptDto, AuthAttemptWaitResponseDto } from '../generated/admin/src/models';
import { EzkeyConfig } from './config';
/**
 * Wrapper for Ezkey Admin API.
 * Provides simplified access to the Admin API operations for managing
 * integrations, enrollments, and authentication attempts.
 */
export declare class EzkeyAdminAPI {
    private readonly integrationsApi;
    private readonly enrollmentsApi;
    private readonly authAttemptsApi;
    constructor(config: EzkeyConfig);
    /**
     * Creates a new integration.
     */
    createIntegration(logo: string, name: string, description: string): Promise<IntegrationCreateResponseDto>;
    /**
     * Gets all integrations.
     */
    getAllIntegrations(): Promise<IntegrationResponseDto[]>;
    /**
     * Gets an integration by ID.
     */
    getIntegration(integrationId: number): Promise<IntegrationResponseDto>;
    /**
     * Deletes an integration.
     */
    deleteIntegration(integrationId: number): Promise<void>;
    /**
     * Creates a new enrollment.
     */
    createEnrollment(integrationId: number, name: string, challengeRequired: boolean): Promise<EnrollmentCreateResponseDto>;
    /**
     * Gets all enrollments.
     */
    getAllEnrollments(): Promise<EnrollmentResponseDto[]>;
    /**
     * Gets an enrollment by ID.
     */
    getEnrollment(enrollmentId: number): Promise<EnrollmentResponseDto>;
    /**
     * Deletes an enrollment.
     */
    deleteEnrollment(enrollmentId: number): Promise<void>;
    /**
     * Creates a new authentication attempt.
     */
    createAuthAttempt(enrollmentId: number, challengeRequested: boolean): Promise<AuthAttemptCreateResponseDto>;
    /**
     * Gets all authentication attempts.
     */
    getAllAuthAttempts(): Promise<AuthAttemptDto[]>;
    /**
     * Gets an authentication attempt by ID.
     */
    getAuthAttempt(authAttemptId: number): Promise<AuthAttemptDto>;
    /**
     * Waits for authentication response with default timeout (30s) and polling (2s).
     */
    waitForResponse(authAttemptId: number): Promise<AuthAttemptWaitResponseDto>;
    /**
     * Waits for authentication response with custom timeout and polling.
     */
    waitForResponseWithOptions(authAttemptId: number, timeout: string, polling: string): Promise<AuthAttemptWaitResponseDto>;
    /**
     * Deletes an authentication attempt.
     */
    deleteAuthAttempt(authAttemptId: number): Promise<void>;
}
//# sourceMappingURL=admin-api.d.ts.map