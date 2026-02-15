import type {AxiosResponse} from 'axios';
import {authAttemptsApi} from '../authAttempts';
import {httpClient} from '../httpClient';
import type {
  PendingAuthRequest,
  PendingAuthResponse,
  RespondAuthRequest,
  RespondAuthResponse,
} from '../types';

jest.mock('../httpClient', () => ({
  httpClient: {
    post: jest.fn(),
  },
}));

const mockedPost = httpClient.post as jest.Mock;

describe('authAttemptsApi', () => {
  beforeEach(() => {
    mockedPost.mockReset();
  });

  it('pending hits the pending endpoint with payload', async () => {
    const payload: PendingAuthRequest = {
      enrollmentId: 'enr_123',
      enrollmentProofToken: 'proof-token',
      deviceProofToken: 'device-proof-token',
      deviceProofTokenSigned: 'signed-device-proof',
    };
    const responseData: PendingAuthResponse = {
      authAttemptId: 'auth_123',
      authAttemptProofToken: 'attempt-proof-token',
      authAttemptProofTokenSignedByIntegration: 'signed-proof',
      authAttemptChallengeRequired: true,
    };
    mockedPost.mockResolvedValueOnce({
      data: responseData,
    } as AxiosResponse<PendingAuthResponse>);

    const result = await authAttemptsApi.pending(payload);

    expect(mockedPost).toHaveBeenCalledWith('/api/v1/auth-attempts/pending', payload, undefined);
    expect(result).toEqual(responseData);
  });

  it('pending passes authUrl as baseURL config when provided', async () => {
    const payload: PendingAuthRequest = {
      enrollmentId: 'enr_456',
      enrollmentProofToken: 'proof',
      deviceProofToken: 'dpt',
      deviceProofTokenSigned: 'signed-dpt',
    };
    const responseData: PendingAuthResponse = {
      authAttemptId: 'auth_456',
      authAttemptProofToken: 'apt',
      authAttemptProofTokenSignedByIntegration: 'signed-apt',
      authAttemptChallengeRequired: false,
    };
    mockedPost.mockResolvedValueOnce({
      data: responseData,
    } as AxiosResponse<PendingAuthResponse>);

    const result = await authAttemptsApi.pending(payload, 'https://ezkey.globex.com');

    expect(mockedPost).toHaveBeenCalledWith('/api/v1/auth-attempts/pending', payload, {baseURL: 'https://ezkey.globex.com'});
    expect(result).toEqual(responseData);
  });

  it('respond hits the respond endpoint with payload', async () => {
    const payload: RespondAuthRequest = {
      authAttemptId: 'auth_123',
      authAttemptAccepted: true,
      authAttemptProofTokenSignedByDevice: 'signed-proof',
      authAttemptChallengeResponse: '123456',
    };
    const responseData: RespondAuthResponse = {
      result: 'APPROVED',
      message: 'Authentication approved',
    };
    mockedPost.mockResolvedValueOnce({
      data: responseData,
    } as AxiosResponse<RespondAuthResponse>);

    const result = await authAttemptsApi.respond(payload);

    expect(mockedPost).toHaveBeenCalledWith('/api/v1/auth-attempts/respond', payload, undefined);
    expect(result).toEqual(responseData);
  });

  it('respond passes authUrl as baseURL config when provided', async () => {
    const payload: RespondAuthRequest = {
      authAttemptId: 'auth_789',
      authAttemptAccepted: false,
      authAttemptProofTokenSignedByDevice: 'signed',
    };
    const responseData: RespondAuthResponse = {
      result: 'REJECTED',
      message: 'Denied by user',
    };
    mockedPost.mockResolvedValueOnce({
      data: responseData,
    } as AxiosResponse<RespondAuthResponse>);

    const result = await authAttemptsApi.respond(payload, 'https://ezkey.initech.com');

    expect(mockedPost).toHaveBeenCalledWith('/api/v1/auth-attempts/respond', payload, {baseURL: 'https://ezkey.initech.com'});
    expect(result).toEqual(responseData);
  });
});

