/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyAdminAPI
 * Description: Wrapper for Ezkey Admin API providing simplified access to integrations, enrollments, and auth attempts
 */

import { 
  AuthAttemptsApi, 
  EnrollmentsApi, 
  IntegrationsApi,
  Configuration
} from '../generated/admin/src';
import {
  IntegrationCreateRequestDto,
  IntegrationCreateResponseDto,
  IntegrationResponseDto,
  IntegrationI18nCreateDto,
  EnrollmentCreateRequestDto,
  EnrollmentCreateResponseDto,
  EnrollmentResponseDto,
  AuthAttemptCreateRequestDto,
  AuthAttemptCreateResponseDto,
  AuthAttemptDto,
  AuthAttemptWaitResponseDto
} from '../generated/admin/src/models';
import { EzkeyConfig } from './config';
import { EzkeyException } from './exception';

/**
 * Wrapper for Ezkey Admin API.
 * Provides simplified access to the Admin API operations for managing
 * integrations, enrollments, and authentication attempts.
 */
export class EzkeyAdminAPI {
  private readonly integrationsApi: IntegrationsApi;
  private readonly enrollmentsApi: EnrollmentsApi;
  private readonly authAttemptsApi: AuthAttemptsApi;

  constructor(config: EzkeyConfig) {
    const configuration = new Configuration({
      basePath: config.adminApiUrl
    });

    this.integrationsApi = new IntegrationsApi(configuration);
    this.enrollmentsApi = new EnrollmentsApi(configuration);
    this.authAttemptsApi = new AuthAttemptsApi(configuration);
  }

  // Integration Management

  /**
   * Creates a new integration.
   */
  async createIntegration(logo: string, name: string, description: string): Promise<IntegrationCreateResponseDto> {
    try {
      const i18n: IntegrationI18nCreateDto = {
        language: 'en',
        name,
        description
      };

      const request: IntegrationCreateRequestDto = {
        logo,
        i18n: [i18n]
      };

      return await this.integrationsApi.create({ integrationCreateRequestDto: request });
    } catch (error) {
      throw EzkeyException.fromError('Failed to create integration', error);
    }
  }

  /**
   * Gets all integrations.
   */
  async getAllIntegrations(): Promise<IntegrationResponseDto[]> {
    try {
      return await this.integrationsApi.getAll();
    } catch (error) {
      throw EzkeyException.fromError('Failed to get integrations', error);
    }
  }

  /**
   * Gets an integration by ID.
   */
  async getIntegration(integrationId: number): Promise<IntegrationResponseDto> {
    try {
      return await this.integrationsApi.getById({ id: integrationId });
    } catch (error) {
      throw EzkeyException.fromError('Failed to get integration', error);
    }
  }

  /**
   * Deletes an integration.
   */
  async deleteIntegration(integrationId: number): Promise<void> {
    try {
      await this.integrationsApi._delete({ id: integrationId });
    } catch (error) {
      throw EzkeyException.fromError('Failed to delete integration', error);
    }
  }

  // Enrollment Management

  /**
   * Creates a new enrollment.
   */
  async createEnrollment(integrationId: number, name: string, challengeRequired: boolean): Promise<EnrollmentCreateResponseDto> {
    try {
      const request: EnrollmentCreateRequestDto = {
        integrationId,
        name,
        authAttemptChallengeRequired: challengeRequired
      };

      return await this.enrollmentsApi.create1({ enrollmentCreateRequestDto: request });
    } catch (error) {
      throw EzkeyException.fromError('Failed to create enrollment', error);
    }
  }

  /**
   * Gets all enrollments.
   */
  async getAllEnrollments(): Promise<EnrollmentResponseDto[]> {
    try {
      return await this.enrollmentsApi.getAll1();
    } catch (error) {
      throw EzkeyException.fromError('Failed to get enrollments', error);
    }
  }

  /**
   * Gets an enrollment by ID.
   */
  async getEnrollment(enrollmentId: number): Promise<EnrollmentResponseDto> {
    try {
      return await this.enrollmentsApi.getById1({ id: enrollmentId });
    } catch (error) {
      throw EzkeyException.fromError('Failed to get enrollment', error);
    }
  }

  /**
   * Deletes an enrollment.
   */
  async deleteEnrollment(enrollmentId: number): Promise<void> {
    try {
      await this.enrollmentsApi.delete1({ id: enrollmentId });
    } catch (error) {
      throw EzkeyException.fromError('Failed to delete enrollment', error);
    }
  }

  // Auth Attempt Management

  /**
   * Creates a new authentication attempt.
   */
  async createAuthAttempt(enrollmentId: number, challengeRequested: boolean): Promise<AuthAttemptCreateResponseDto> {
    try {
      const request: AuthAttemptCreateRequestDto = {
        enrollmentId,
        challengeRequested
      };

      return await this.authAttemptsApi.create2({ authAttemptCreateRequestDto: request });
    } catch (error) {
      throw EzkeyException.fromError('Failed to create auth attempt', error);
    }
  }

  /**
   * Gets all authentication attempts.
   */
  async getAllAuthAttempts(): Promise<AuthAttemptDto[]> {
    try {
      return await this.authAttemptsApi.getAll2();
    } catch (error) {
      throw EzkeyException.fromError('Failed to get auth attempts', error);
    }
  }

  /**
   * Gets an authentication attempt by ID.
   */
  async getAuthAttempt(authAttemptId: number): Promise<AuthAttemptDto> {
    try {
      return await this.authAttemptsApi.getById2({ id: authAttemptId });
    } catch (error) {
      throw EzkeyException.fromError('Failed to get auth attempt', error);
    }
  }

  /**
   * Waits for authentication response with default timeout (30s) and polling (2s).
   */
  async waitForResponse(authAttemptId: number): Promise<AuthAttemptWaitResponseDto> {
    return this.waitForResponseWithOptions(authAttemptId, '30', '2');
  }

  /**
   * Waits for authentication response with custom timeout and polling.
   */
  async waitForResponseWithOptions(authAttemptId: number, timeout: string, polling: string): Promise<AuthAttemptWaitResponseDto> {
    try {
      return await this.authAttemptsApi.waitForResponse({ id: authAttemptId, timeout, polling });
    } catch (error) {
      throw EzkeyException.fromError('Failed to wait for auth response', error);
    }
  }

  /**
   * Deletes an authentication attempt.
   */
  async deleteAuthAttempt(authAttemptId: number): Promise<void> {
    try {
      await this.authAttemptsApi.delete2({ id: authAttemptId });
    } catch (error) {
      throw EzkeyException.fromError('Failed to delete auth attempt', error);
    }
  }
}