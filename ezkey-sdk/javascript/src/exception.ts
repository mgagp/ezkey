/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyException
 * Description: Exception class for Ezkey SDK operations
 */

/**
 * Exception thrown by Ezkey SDK operations.
 * Wraps underlying API exceptions and provides consistent error handling.
 */
export class EzkeyException extends Error {
  public readonly statusCode?: number;
  public readonly responseBody?: string;

  constructor(message: string, statusCode?: number, responseBody?: string) {
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
  isClientError(): boolean {
    return this.statusCode !== undefined && this.statusCode >= 400 && this.statusCode < 500;
  }

  /**
   * Returns whether this exception represents a server error (5xx status code).
   */
  isServerError(): boolean {
    return this.statusCode !== undefined && this.statusCode >= 500 && this.statusCode < 600;
  }

  /**
   * Creates an EzkeyException from a fetch response or error.
   */
  static async fromResponse(message: string, response?: Response): Promise<EzkeyException> {
    if (response) {
      let responseBody: string | undefined;
      try {
        responseBody = await response.text();
      } catch {
        // Ignore errors when reading response body
      }
      return new EzkeyException(message, response.status, responseBody);
    }
    return new EzkeyException(message);
  }

  /**
   * Creates an EzkeyException from a generic error.
   */
  static fromError(message: string, error: any): EzkeyException {
    if (error && typeof error === 'object' && 'status' in error) {
      return new EzkeyException(message, error.status, error.body);
    }
    return new EzkeyException(`${message}: ${error?.message || error}`);
  }
}