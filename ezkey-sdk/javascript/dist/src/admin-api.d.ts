import { IntegrationCreateResponseDto, IntegrationResponseDto, EnrollmentCreateResponseDto, EnrollmentResponseDto, AuthAttemptDto, AuthAttemptWaitResponseDto } from '../generated/admin/src/models';
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
     *
     * @param code unique business identifier
     * @param name display name
     * @param description optional description
     */
    createIntegration(code: string, name: string, description?: string): Promise<IntegrationCreateResponseDto>;
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
     *
     * @param integrationId integration to delete
     * @param reason operator reason required by Admin API (default when omitted)
     */
    deleteIntegration(integrationId: number, reason?: string): Promise<void>;
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
     *
     * @param enrollmentId enrollment to delete
     * @param reason operator reason required by Admin API (default when omitted)
     */
    deleteEnrollment(enrollmentId: number, reason?: string): Promise<void>;
    /**
     * Creates a new authentication attempt.
     */
    createAuthAttempt(enrollmentId: number, challengeRequested: boolean): Promise<AuthAttemptDto>;
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
     * Cancels an authentication attempt (Admin API no longer exposes a hard delete).
     */
    deleteAuthAttempt(authAttemptId: number): Promise<void>;
}
//# sourceMappingURL=admin-api.d.ts.map