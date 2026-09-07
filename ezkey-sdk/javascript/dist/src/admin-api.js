"use strict";
/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyAdminAPI
 * Description: Wrapper for Ezkey Admin API providing simplified access to integrations, enrollments, and auth attempts
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.EzkeyAdminAPI = void 0;
const src_1 = require("../generated/admin/src");
const exception_1 = require("./exception");
/** Default page size when adapting paginated Admin list endpoints to array helpers. */
const DEFAULT_LIST_PAGE_SIZE = 100;
/** Default reason when the Admin API requires an operator reason and the caller omits one. */
const DEFAULT_OPERATOR_REASON = 'Deleted via Ezkey SDK';
/**
 * Wrapper for Ezkey Admin API.
 * Provides simplified access to the Admin API operations for managing
 * integrations, enrollments, and authentication attempts.
 */
class EzkeyAdminAPI {
    constructor(config) {
        const configuration = new src_1.Configuration({
            basePath: config.adminApiUrl
        });
        this.integrationsApi = new src_1.IntegrationsApi(configuration);
        this.enrollmentsApi = new src_1.EnrollmentsApi(configuration);
        this.authAttemptsApi = new src_1.AuthAttemptsApi(configuration);
    }
    // Integration Management
    /**
     * Creates a new integration.
     *
     * @param code unique business identifier
     * @param name display name
     * @param description optional description
     */
    async createIntegration(code, name, description) {
        try {
            const request = {
                code,
                name,
                description
            };
            return await this.integrationsApi.create({ integrationCreateRequestDto: request });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to create integration', error);
        }
    }
    /**
     * Gets all integrations.
     */
    async getAllIntegrations() {
        var _a;
        try {
            const page = await this.integrationsApi.search({ size: DEFAULT_LIST_PAGE_SIZE });
            return (_a = page.content) !== null && _a !== void 0 ? _a : [];
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to get integrations', error);
        }
    }
    /**
     * Gets an integration by ID.
     */
    async getIntegration(integrationId) {
        try {
            return await this.integrationsApi.getById1({ id: integrationId });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to get integration', error);
        }
    }
    /**
     * Deletes an integration.
     *
     * @param integrationId integration to delete
     * @param reason operator reason required by Admin API (default when omitted)
     */
    async deleteIntegration(integrationId, reason = DEFAULT_OPERATOR_REASON) {
        try {
            await this.integrationsApi.delete1({ id: integrationId, reason });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to delete integration', error);
        }
    }
    // Enrollment Management
    /**
     * Creates a new enrollment.
     */
    async createEnrollment(integrationId, name, challengeRequired) {
        try {
            const request = {
                integrationId,
                name,
                authAttemptChallengeRequired: challengeRequired
            };
            return await this.enrollmentsApi.create1({ enrollmentCreateRequestDto: request });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to create enrollment', error);
        }
    }
    /**
     * Gets all enrollments.
     */
    async getAllEnrollments() {
        var _a;
        try {
            const page = await this.enrollmentsApi.search1({ size: DEFAULT_LIST_PAGE_SIZE });
            return (_a = page.content) !== null && _a !== void 0 ? _a : [];
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to get enrollments', error);
        }
    }
    /**
     * Gets an enrollment by ID.
     */
    async getEnrollment(enrollmentId) {
        try {
            return await this.enrollmentsApi.getById({ id: enrollmentId });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to get enrollment', error);
        }
    }
    /**
     * Deletes an enrollment.
     *
     * @param enrollmentId enrollment to delete
     * @param reason operator reason required by Admin API (default when omitted)
     */
    async deleteEnrollment(enrollmentId, reason = DEFAULT_OPERATOR_REASON) {
        try {
            await this.enrollmentsApi._delete({ id: enrollmentId, reason });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to delete enrollment', error);
        }
    }
    // Auth Attempt Management
    /**
     * Creates a new authentication attempt.
     */
    async createAuthAttempt(enrollmentId, challengeRequested) {
        try {
            const request = {
                enrollmentId,
                challengeRequested
            };
            // Generator 7.9.0 types the create response as object; runtime payload is AuthAttemptDto.
            return await this.authAttemptsApi.create2({ authAttemptCreateRequestDto: request });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to create auth attempt', error);
        }
    }
    /**
     * Gets all authentication attempts.
     */
    async getAllAuthAttempts() {
        var _a;
        try {
            const page = await this.authAttemptsApi.search2({ size: DEFAULT_LIST_PAGE_SIZE });
            return (_a = page.content) !== null && _a !== void 0 ? _a : [];
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to get auth attempts', error);
        }
    }
    /**
     * Gets an authentication attempt by ID.
     */
    async getAuthAttempt(authAttemptId) {
        try {
            return await this.authAttemptsApi.getById2({ id: authAttemptId });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to get auth attempt', error);
        }
    }
    /**
     * Waits for authentication response with default timeout (30s) and polling (2s).
     */
    async waitForResponse(authAttemptId) {
        return this.waitForResponseWithOptions(authAttemptId, '30', '2');
    }
    /**
     * Waits for authentication response with custom timeout and polling.
     */
    async waitForResponseWithOptions(authAttemptId, timeout, polling) {
        try {
            return await this.authAttemptsApi.waitForResponse({ id: authAttemptId, timeout, polling });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to wait for auth response', error);
        }
    }
    /**
     * Cancels an authentication attempt (Admin API no longer exposes a hard delete).
     */
    async deleteAuthAttempt(authAttemptId) {
        try {
            await this.authAttemptsApi.cancel({ id: authAttemptId });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to delete auth attempt', error);
        }
    }
}
exports.EzkeyAdminAPI = EzkeyAdminAPI;
//# sourceMappingURL=admin-api.js.map