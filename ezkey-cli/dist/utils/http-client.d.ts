import { AxiosRequestConfig } from 'axios';
import { ConfigManager } from '../config/config-manager';
export interface ApiResponse<T = any> {
    success: boolean;
    data?: T;
    error?: string;
    status?: number;
}
export declare class HttpClient {
    private client;
    private config;
    constructor(config: ConfigManager);
    /**
     * Make a GET request
     */
    get<T = any>(url: string, options?: AxiosRequestConfig): Promise<ApiResponse<T>>;
    /**
     * Make a POST request
     */
    post<T = any>(url: string, data?: any, options?: AxiosRequestConfig): Promise<ApiResponse<T>>;
    /**
     * Make a PUT request
     */
    put<T = any>(url: string, data?: any, options?: AxiosRequestConfig): Promise<ApiResponse<T>>;
    /**
     * Make a DELETE request
     */
    delete<T = any>(url: string, options?: AxiosRequestConfig): Promise<ApiResponse<T>>;
    private handleError;
    private formatError;
}
//# sourceMappingURL=http-client.d.ts.map