"use strict";
/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyAuthAPI
 * Description: Wrapper for Ezkey Auth API providing simplified access to enrollment and authentication flows
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.EzkeyAuthAPI = void 0;
const src_1 = require("../generated/auth/src");
const exception_1 = require("./exception");
/**
 * Wrapper for Ezkey Auth API.
 * Provides simplified access to the Auth API operations for device
 * enrollment and authentication flows.
 */
class EzkeyAuthAPI {
    constructor(config) {
        const configuration = new src_1.Configuration({
            basePath: config.authApiUrl
        });
        this.enrollmentApi = new src_1.EnrollmentsApi(configuration);
        this.authAttemptApi = new src_1.AuthenticationAttemptsApi(configuration);
    }
    // Enrollment Operations
    /**
     * Binds a device to an enrollment.
     *
     * @param enrollmentId enrollment to bind
     * @param enrollmentProofToken proof token from the enrollment QR / create flow
     */
    async bindEnrollment(enrollmentId, enrollmentProofToken) {
        try {
            return await this.enrollmentApi.bind({
                enrollmentBindRequestDto: {
                    enrollmentId,
                    enrollmentProofToken
                }
            });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to bind enrollment', error);
        }
    }
    /**
     * Verifies and completes the enrollment process.
     */
    async verifyEnrollment(enrollmentId, challengeResponse, devicePublicKey, enrollmentProofTokenSigned) {
        try {
            const request = {
                enrollmentId,
                challengeResponse,
                devicePublicKey,
                enrollmentProofTokenSigned
            };
            return await this.enrollmentApi.verify({ enrollmentVerifyRequestDto: request });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to verify enrollment', error);
        }
    }
    // Authentication Operations
    /**
     * Checks for pending authentication requests.
     * @returns Pending authentication details or null if no pending requests
     */
    async checkPendingAuth(enrollmentId, enrollmentProofToken, deviceProofToken, deviceProofTokenSigned) {
        var _a;
        try {
            const request = {
                enrollmentId,
                enrollmentProofToken,
                deviceProofToken,
                deviceProofTokenSigned
            };
            return (_a = await this.authAttemptApi.pending({
                authAttemptPendingRequestDto: request
            })) !== null && _a !== void 0 ? _a : null;
        }
        catch (error) {
            // Handle 204 No Content as no pending requests
            if ((error === null || error === void 0 ? void 0 : error.status) === 204) {
                return null;
            }
            throw exception_1.EzkeyException.fromError('Failed to check pending auth', error);
        }
    }
    /**
     * Responds to an authentication attempt.
     */
    async respondToAuth(authAttemptId, authAttemptProofTokenSigned, authAttemptAccepted, challengeResponse) {
        try {
            const request = {
                authAttemptId,
                authAttemptProofTokenSignedByDevice: authAttemptProofTokenSigned,
                authAttemptAccepted,
                authAttemptChallengeResponse: challengeResponse
            };
            return await this.authAttemptApi.respond({
                authAttemptRespondRequestDto: request
            });
        }
        catch (error) {
            throw exception_1.EzkeyException.fromError('Failed to respond to auth attempt', error);
        }
    }
}
exports.EzkeyAuthAPI = EzkeyAuthAPI;
//# sourceMappingURL=auth-api.js.map