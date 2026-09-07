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
  EnrollmentCreateRequestDto,
  EnrollmentCreateResponseDto,
  EnrollmentResponseDto,
  AuthAttemptCreateRequestDto,
  AuthAttemptDto,
  AuthAttemptWaitResponseDto
} from '../generated/admin/src/models';
import { EzkeyConfig } from './config';
import { EzkeyException } from './exception';

/** Default page size when adapting paginated Admin list endpoints to array helpers. */
const DEFAULT_LIST_PAGE_SIZE = 100;

/** Default reason when the Admin API requires an operator reason and the caller omits one. */
const DEFAULT_OPERATOR_REASON = 'Deleted via Ezkey SDK';

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
   *
   * @param code unique business identifier
   * @param name display name
   * @param description optional description
   */
  async createIntegration(code: string, name: string, description?: string): Promise<IntegrationCreateResponseDto> {
    try {
      const request: IntegrationCreateRequestDto = {
        code,
        name,
        description
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
      const page = await this.integrationsApi.search({ size: DEFAULT_LIST_PAGE_SIZE });
      return page.content ?? [];
    } catch (error) {
      throw EzkeyException.fromError('Failed to get integrations', error);
    }
  }

  /**
   * Gets an integration by ID.
   */
  async getIntegration(integrationId: number): Promise<IntegrationResponseDto> {
    try {
      return await this.integrationsApi.getById1({ id: integrationId });
    } catch (error) {
      throw EzkeyException.fromError('Failed to get integration', error);
    }
  }

  /**
   * Deletes an integration.
   *
   * @param integrationId integration to delete
   * @param reason operator reason required by Admin API (default when omitted)
   */
  async deleteIntegration(integrationId: number, reason: string = DEFAULT_OPERATOR_REASON): Promise<void> {
    try {
      await this.integrationsApi.delete1({ id: integrationId, reason });
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
      const page = await this.enrollmentsApi.search1({ size: DEFAULT_LIST_PAGE_SIZE });
      return page.content ?? [];
    } catch (error) {
      throw EzkeyException.fromError('Failed to get enrollments', error);
    }
  }

  /**
   * Gets an enrollment by ID.
   */
  async getEnrollment(enrollmentId: number): Promise<EnrollmentResponseDto> {
    try {
      return await this.enrollmentsApi.getById({ id: enrollmentId });
    } catch (error) {
      throw EzkeyException.fromError('Failed to get enrollment', error);
    }
  }

  /**
   * Deletes an enrollment.
   *
   * @param enrollmentId enrollment to delete
   * @param reason operator reason required by Admin API (default when omitted)
   */
  async deleteEnrollment(enrollmentId: number, reason: string = DEFAULT_OPERATOR_REASON): Promise<void> {
    try {
      await this.enrollmentsApi._delete({ id: enrollmentId, reason });
    } catch (error) {
      throw EzkeyException.fromError('Failed to delete enrollment', error);
    }
  }

  // Auth Attempt Management

  /**
   * Creates a new authentication attempt.
   */
  async createAuthAttempt(enrollmentId: number, challengeRequested: boolean): Promise<AuthAttemptDto> {
    try {
      const request: AuthAttemptCreateRequestDto = {
        enrollmentId,
        challengeRequested
      };

      // Generator 7.9.0 types the create response as object; runtime payload is AuthAttemptDto.
      return await this.authAttemptsApi.create2({ authAttemptCreateRequestDto: request }) as AuthAttemptDto;
    } catch (error) {
      throw EzkeyException.fromError('Failed to create auth attempt', error);
    }
  }

  /**
   * Gets all authentication attempts.
   */
  async getAllAuthAttempts(): Promise<AuthAttemptDto[]> {
    try {
      const page = await this.authAttemptsApi.search2({ size: DEFAULT_LIST_PAGE_SIZE });
      return page.content ?? [];
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
   * Cancels an authentication attempt (Admin API no longer exposes a hard delete).
   */
  async deleteAuthAttempt(authAttemptId: number): Promise<void> {
    try {
      await this.authAttemptsApi.cancel({ id: authAttemptId });
    } catch (error) {
      throw EzkeyException.fromError('Failed to delete auth attempt', error);
    }
  }
}