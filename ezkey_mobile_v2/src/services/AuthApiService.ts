/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthApiService
 * Description: Service for interacting with the Ezkey Auth API
 */

import axios from 'axios';

// Configuration - update this to match your backend
const AUTH_API_BASE_URL = 'https://goateed-katalina-monsoonal.ngrok-free.dev/api/v1'; // Android emulator localhost

export interface EnrollmentBindRequest {
  enrollmentId: number;
  enrollmentProofToken: string;
  language?: string;
}

export interface EnrollmentBindResponse {
  enrollmentId: number;
  enrollmentProofToken: string;
  integrationPublicKey: string;
  integrationName: string;
  integrationDescription: string;
  integrationLogo?: string;
  enrollmentName: string;
}

export interface EnrollmentVerifyRequest {
  enrollmentId: number;
  challengeResponse: number;
  devicePublicKey: string;
  enrollmentProofTokenSigned: string;
}

export interface EnrollmentVerifyResponse {
  active: boolean;
}

export interface AuthAttemptPendingRequest {
  enrollmentId: number;
  enrollmentProofToken: string;
  deviceProofToken: string;
  deviceProofTokenSigned: string;
}

export interface AuthAttemptPendingResponse {
  authAttemptId: number;
  authAttemptProofToken: string;
  authAttemptProofTokenSignedByIntegration: string;
  authAttemptChallengeRequired: boolean;
}

export interface AuthAttemptRespondRequest {
  authAttemptId: number;
  authAttemptAccepted: boolean;
  authAttemptProofTokenSignedByDevice: string;
  authAttemptChallengeResponse?: number;
}

export interface AuthAttemptRespondResponse {
  result: string;
  message: string;
}

class AuthApiService {
  private baseUrl: string;

  constructor(baseUrl: string = AUTH_API_BASE_URL) {
    this.baseUrl = baseUrl;
  }

  async enrollmentBind(request: EnrollmentBindRequest): Promise<EnrollmentBindResponse> {
    try {
      const response = await axios.post(`${this.baseUrl}/enrollments/bind`, request);
      return response.data;
    } catch (error: any) {
      console.error('Enrollment bind failed:', error.response?.data || error.message);
      throw new Error(error.response?.data?.message || 'Enrollment bind failed');
    }
  }

  async enrollmentVerify(request: EnrollmentVerifyRequest): Promise<EnrollmentVerifyResponse> {
    try {
      const response = await axios.post(`${this.baseUrl}/enrollments/verify`, request);
      return response.data;
    } catch (error: any) {
      console.error('Enrollment verify failed:', error.response?.data || error.message);
      throw new Error(error.response?.data?.message || 'Enrollment verify failed');
    }
  }

  async checkPendingAuth(request: AuthAttemptPendingRequest): Promise<AuthAttemptPendingResponse | null> {
    try {
      const response = await axios.post(`${this.baseUrl}/auth-attempts/pending`, request);
      if (response.status === 204) {
        return null; // No pending auth attempt
      }
      return response.data;
    } catch (error: any) {
      if (error.response?.status === 204) {
        return null; // No pending auth attempt
      }
      console.error('Check pending auth failed:', error.response?.data || error.message);
      throw new Error(error.response?.data?.message || 'Check pending auth failed');
    }
  }

  async respondToAuth(request: AuthAttemptRespondRequest): Promise<AuthAttemptRespondResponse> {
    try {
      const response = await axios.post(`${this.baseUrl}/auth-attempts/respond`, request);
      return response.data;
    } catch (error: any) {
      console.error('Respond to auth failed:', error.response?.data || error.message);
      throw new Error(error.response?.data?.message || 'Respond to auth failed');
    }
  }

  setBaseUrl(url: string) {
    this.baseUrl = url;
  }
}

export default new AuthApiService();
