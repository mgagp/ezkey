/**
 * Exception thrown by Ezkey SDK operations.
 * Wraps underlying API exceptions and provides consistent error handling.
 */
export declare class EzkeyException extends Error {
    readonly statusCode?: number;
    readonly responseBody?: string;
    constructor(message: string, statusCode?: number, responseBody?: string);
    /**
     * Returns whether this exception represents a client error (4xx status code).
     */
    isClientError(): boolean;
    /**
     * Returns whether this exception represents a server error (5xx status code).
     */
    isServerError(): boolean;
    /**
     * Creates an EzkeyException from a fetch response or error.
     */
    static fromResponse(message: string, response?: Response): Promise<EzkeyException>;
    /**
     * Creates an EzkeyException from a generic error.
     */
    static fromError(message: string, error: any): EzkeyException;
}
//# sourceMappingURL=exception.d.ts.map