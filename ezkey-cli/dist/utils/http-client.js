"use strict";
/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: HTTP Client
 * Description: HTTP client with error handling and response formatting
 */
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.HttpClient = void 0;
const axios_1 = __importDefault(require("axios"));
class HttpClient {
    constructor(config) {
        this.config = config;
        this.client = axios_1.default.create({
            timeout: config.get('timeout') || 30000,
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            }
        });
        // Add response interceptor for consistent error handling
        this.client.interceptors.response.use((response) => response, (error) => {
            return Promise.reject(this.formatError(error));
        });
    }
    /**
     * Make a GET request
     */
    async get(url, options) {
        try {
            const response = await this.client.get(url, options);
            return {
                success: true,
                data: response.data,
                status: response.status
            };
        }
        catch (error) {
            return this.handleError(error);
        }
    }
    /**
     * Make a POST request
     */
    async post(url, data, options) {
        try {
            const response = await this.client.post(url, data, options);
            return {
                success: true,
                data: response.data,
                status: response.status
            };
        }
        catch (error) {
            return this.handleError(error);
        }
    }
    /**
     * Make a PUT request
     */
    async put(url, data, options) {
        try {
            const response = await this.client.put(url, data, options);
            return {
                success: true,
                data: response.data,
                status: response.status
            };
        }
        catch (error) {
            return this.handleError(error);
        }
    }
    /**
     * Make a DELETE request
     */
    async delete(url, options) {
        try {
            const response = await this.client.delete(url, options);
            return {
                success: true,
                data: response.data,
                status: response.status
            };
        }
        catch (error) {
            return this.handleError(error);
        }
    }
    handleError(error) {
        if (error.response) {
            // Server responded with an error status
            return {
                success: false,
                error: error.response.data?.message || error.message || 'Request failed',
                status: error.response.status,
                data: error.response.data
            };
        }
        else if (error.request) {
            // Request was made but no response received
            return {
                success: false,
                error: 'No response from server. Please check if the API is running.'
            };
        }
        else {
            // Error in request setup
            return {
                success: false,
                error: error.message || 'Unknown error occurred'
            };
        }
    }
    formatError(error) {
        // Keep the original error structure for interceptor
        return error;
    }
}
exports.HttpClient = HttpClient;
//# sourceMappingURL=http-client.js.map