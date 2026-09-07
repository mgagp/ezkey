/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: Main exports
 * Description: Main entry point for Ezkey JavaScript/TypeScript SDK
 */

// Main client
export { EzkeyClient } from './client';

// API wrappers
export { EzkeyAdminAPI } from './admin-api';
export { EzkeyAuthAPI } from './auth-api';

// Configuration
export { EzkeyConfig, createDefaultConfig } from './config';

// Exception handling
export { EzkeyException } from './exception';

// Re-export types from generated clients for advanced usage
export type {
  IntegrationCreateResponseDto,
  IntegrationResponseDto,
  EnrollmentCreateResponseDto,
  EnrollmentResponseDto,
  AuthAttemptDto,
  AuthAttemptWaitResponseDto
} from '../generated/admin/src/models';

export type {
  EnrollmentBindResponseDto,
  EnrollmentVerifyResponseDto,
  AuthAttemptPendingResponseDto,
  AuthAttemptRespondResponseDto
} from '../generated/auth/src/models';