"use strict";
/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
     */
    async createIntegration(logo, name, description) {
        try {
            const i18n = {
                language: 'en',
                name,
                description
            };
            const request = {
                logo,
                i18n: [i18n]
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
        try {
            return await this.integrationsApi.getAll();
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
            return await this.integrationsApi.getById({ id: integrationId });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to get integration', error);
        }
    }
    /**
     * Deletes an integration.
     */
    async deleteIntegration(integrationId) {
        try {
            await this.integrationsApi._delete({ id: integrationId });
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
        try {
            return await this.enrollmentsApi.getAll1();
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
            return await this.enrollmentsApi.getById1({ id: enrollmentId });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to get enrollment', error);
        }
    }
    /**
     * Deletes an enrollment.
     */
    async deleteEnrollment(enrollmentId) {
        try {
            await this.enrollmentsApi.delete1({ id: enrollmentId });
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
        try {
            return await this.authAttemptsApi.getAll2();
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
     * Deletes an authentication attempt.
     */
    async deleteAuthAttempt(authAttemptId) {
        try {
            await this.authAttemptsApi.delete2({ id: authAttemptId });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to delete auth attempt', error);
        }
    }
}
exports.EzkeyAdminAPI = EzkeyAdminAPI;
//# sourceMappingURL=admin-api.js.map