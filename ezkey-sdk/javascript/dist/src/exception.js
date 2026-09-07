"use strict";
/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyException
 * Description: Exception class for Ezkey SDK operations
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.EzkeyException = void 0;
/**
 * Exception thrown by Ezkey SDK operations.
 * Wraps underlying API exceptions and provides consistent error handling.
 */
class EzkeyException extends Error {
    constructor(message, statusCode, responseBody) {
        super(message);
        this.name = 'EzkeyException';
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        // Maintains proper stack trace for where our error was thrown (only available on V8)
        if (Error.captureStackTrace) {
            Error.captureStackTrace(this, EzkeyException);
        }
    }
    /**
     * Returns whether this exception represents a client error (4xx status code).
     */
    isClientError() {
        return this.statusCode !== undefined && this.statusCode >= 400 && this.statusCode < 500;
    }
    /**
     * Returns whether this exception represents a server error (5xx status code).
     */
    isServerError() {
        return this.statusCode !== undefined && this.statusCode >= 500 && this.statusCode < 600;
    }
    /**
     * Creates an EzkeyException from a fetch response or error.
     */
    static async fromResponse(message, response) {
        if (response) {
            let responseBody;
            try {
                responseBody = await response.text();
            }
            catch (_a) {
                // Ignore errors when reading response body
            }
            return new EzkeyException(message, response.status, responseBody);
        }
        return new EzkeyException(message);
    }
    /**
     * Creates an EzkeyException from a generic error.
     */
    static fromError(message, error) {
        if (error && typeof error === 'object' && 'status' in error) {
            return new EzkeyException(message, error.status, error.body);
        }
        return new EzkeyException(`${message}: ${(error === null || error === void 0 ? void 0 : error.message) || error}`);
    }
}
exports.EzkeyException = EzkeyException;
//# sourceMappingURL=exception.js.map