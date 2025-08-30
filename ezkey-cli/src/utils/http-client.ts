/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: HTTP Client
 * Description: HTTP client with error handling and response formatting
 */

import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';
import { ConfigManager } from '../config/config-manager';

export interface ApiResponse<T = any> {
    success: boolean;
    data?: T;
    error?: string;
    status?: number;
}

export class HttpClient {
    private client: AxiosInstance;
    private config: ConfigManager;

    constructor(config: ConfigManager) {
        this.config = config;
        this.client = axios.create({
            timeout: config.get('timeout') || 30000,
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            }
        });

        // Add response interceptor for consistent error handling
        this.client.interceptors.response.use(
            (response) => response,
            (error) => {
                return Promise.reject(this.formatError(error));
            }
        );
    }

    /**
     * Make a GET request
     */
    async get<T = any>(url: string, options?: AxiosRequestConfig): Promise<ApiResponse<T>> {
        try {
            const response = await this.client.get<T>(url, options);
            return {
                success: true,
                data: response.data,
                status: response.status
            };
        } catch (error) {
            return this.handleError(error);
        }
    }

    /**
     * Make a POST request
     */
    async post<T = any>(url: string, data?: any, options?: AxiosRequestConfig): Promise<ApiResponse<T>> {
        try {
            const response = await this.client.post<T>(url, data, options);
            return {
                success: true,
                data: response.data,
                status: response.status
            };
        } catch (error) {
            return this.handleError(error);
        }
    }

    /**
     * Make a PUT request
     */
    async put<T = any>(url: string, data?: any, options?: AxiosRequestConfig): Promise<ApiResponse<T>> {
        try {
            const response = await this.client.put<T>(url, data, options);
            return {
                success: true,
                data: response.data,
                status: response.status
            };
        } catch (error) {
            return this.handleError(error);
        }
    }

    /**
     * Make a DELETE request
     */
    async delete<T = any>(url: string, options?: AxiosRequestConfig): Promise<ApiResponse<T>> {
        try {
            const response = await this.client.delete<T>(url, options);
            return {
                success: true,
                data: response.data,
                status: response.status
            };
        } catch (error) {
            return this.handleError(error);
        }
    }

    private handleError(error: any): ApiResponse {
        if (error.response) {
            // Server responded with an error status
            return {
                success: false,
                error: error.response.data?.message || error.message || 'Request failed',
                status: error.response.status,
                data: error.response.data
            };
        } else if (error.request) {
            // Request was made but no response received
            return {
                success: false,
                error: 'No response from server. Please check if the API is running.'
            };
        } else {
            // Error in request setup
            return {
                success: false,
                error: error.message || 'Unknown error occurred'
            };
        }
    }

    private formatError(error: any): any {
        // Keep the original error structure for interceptor
        return error;
    }
}