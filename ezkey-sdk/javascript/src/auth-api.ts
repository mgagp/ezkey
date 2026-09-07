/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyAuthAPI
 * Description: Wrapper for Ezkey Auth API providing simplified access to enrollment and authentication flows
 */

import {
  AuthenticationAttemptsApi,
  EnrollmentsApi,
  Configuration
} from '../generated/auth/src';
import {
  EnrollmentBindResponseDto,
  EnrollmentVerifyRequestDto,
  EnrollmentVerifyResponseDto,
  AuthAttemptPendingRequestDto,
  AuthAttemptPendingResponseDto,
  AuthAttemptRespondRequestDto,
  AuthAttemptRespondResponseDto
} from '../generated/auth/src/models';
import { EzkeyConfig } from './config';
import { EzkeyException } from './exception';

/**
 * Wrapper for Ezkey Auth API.
 * Provides simplified access to the Auth API operations for device
 * enrollment and authentication flows.
 */
export class EzkeyAuthAPI {
  private readonly enrollmentApi: EnrollmentsApi;
  private readonly authAttemptApi: AuthenticationAttemptsApi;

  constructor(config: EzkeyConfig) {
    const configuration = new Configuration({
      basePath: config.authApiUrl
    });

    this.enrollmentApi = new EnrollmentsApi(configuration);
    this.authAttemptApi = new AuthenticationAttemptsApi(configuration);
  }

  // Enrollment Operations

  /**
   * Binds a device to an enrollment.
   *
   * @param enrollmentId enrollment to bind
   * @param enrollmentProofToken proof token from the enrollment QR / create flow
   */
  async bindEnrollment(enrollmentId: number, enrollmentProofToken: string): Promise<EnrollmentBindResponseDto> {
    try {
      return await this.enrollmentApi.bind({
        enrollmentBindRequestDto: {
          enrollmentId,
          enrollmentProofToken
        }
      });
    } catch (error) {
      throw EzkeyException.fromError('Failed to bind enrollment', error);
    }
  }

  /**
   * Verifies and completes the enrollment process.
   */
  async verifyEnrollment(
    enrollmentId: number,
    challengeResponse: number,
    devicePublicKey: string,
    enrollmentProofTokenSigned: string
  ): Promise<EnrollmentVerifyResponseDto> {
    try {
      const request: EnrollmentVerifyRequestDto = {
        enrollmentId,
        challengeResponse,
        devicePublicKey,
        enrollmentProofTokenSigned
      };

      return await this.enrollmentApi.verify({ enrollmentVerifyRequestDto: request });
    } catch (error) {
      throw EzkeyException.fromError('Failed to verify enrollment', error);
    }
  }

  // Authentication Operations

  /**
   * Checks for pending authentication requests.
   * @returns Pending authentication details or null if no pending requests
   */
  async checkPendingAuth(
    enrollmentId: number,
    enrollmentProofToken: string,
    deviceProofToken: string,
    deviceProofTokenSigned: string
  ): Promise<AuthAttemptPendingResponseDto | null> {
    try {
      const request: AuthAttemptPendingRequestDto = {
        enrollmentId,
        enrollmentProofToken,
        deviceProofToken,
        deviceProofTokenSigned
      };

      return await this.authAttemptApi.pending({
        authAttemptPendingRequestDto: request
      }) ?? null;
    } catch (error: any) {
      // Handle 204 No Content as no pending requests
      if (error?.status === 204) {
        return null;
      }
      throw EzkeyException.fromError('Failed to check pending auth', error);
    }
  }

  /**
   * Responds to an authentication attempt.
   */
  async respondToAuth(
    authAttemptId: number,
    authAttemptProofTokenSigned: string,
    authAttemptAccepted: boolean,
    challengeResponse?: number
  ): Promise<AuthAttemptRespondResponseDto> {
    try {
      const request: AuthAttemptRespondRequestDto = {
        authAttemptId,
        authAttemptProofTokenSignedByDevice: authAttemptProofTokenSigned,
        authAttemptAccepted,
        authAttemptChallengeResponse: challengeResponse
      };

      return await this.authAttemptApi.respond({
        authAttemptRespondRequestDto: request
      });
    } catch (error) {
      throw EzkeyException.fromError('Failed to respond to auth attempt', error);
    }
  }
}