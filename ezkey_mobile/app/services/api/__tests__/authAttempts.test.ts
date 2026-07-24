import {authAttemptsApi, MALFORMED_PENDING_RESPONSE} from '../authAttempts';
import {pending, respond} from '../generated/auth-api/authentication-attempts/authentication-attempts';
import type {
  PendingAuthRequest,
  PendingAuthResponse,
  RespondAuthRequest,
  RespondAuthResponse,
} from '../types';

jest.mock('../generated/auth-api/authentication-attempts/authentication-attempts', () => ({
  pending: jest.fn(),
  respond: jest.fn(),
}));

const mockedPending = pending as jest.Mock;
const mockedRespond = respond as jest.Mock;

const usablePendingBody: PendingAuthResponse = {
  authAttemptId: 123,
  authAttemptProofToken: 'attempt-proof-token',
  authAttemptProofTokenSignedByIntegration: 'signed-proof',
  authAttemptChallengeRequired: true,
};

const samplePendingRequest: PendingAuthRequest = {
  enrollmentId: '123',
  enrollmentProofToken: 'proof-token',
  deviceProofToken: 'device-proof-token',
  deviceProofTokenSigned: 'signed-device-proof',
};

describe('authAttemptsApi', () => {
  beforeEach(() => {
    mockedPending.mockReset();
    mockedRespond.mockReset();
  });

  it('pending hits the pending endpoint with normalized payload', async () => {
    mockedPending.mockResolvedValueOnce({
      data: usablePendingBody,
      status: 200,
      headers: new Headers(),
    });

    const result = await authAttemptsApi.pending(samplePendingRequest);

    expect(mockedPending).toHaveBeenCalledWith(
      {
        enrollmentId: 123,
        enrollmentProofToken: 'proof-token',
        deviceProofToken: 'device-proof-token',
        deviceProofTokenSigned: 'signed-device-proof',
      },
      undefined,
    );
    expect(result).toEqual(usablePendingBody);
  });

  it('pending returns undefined for HTTP 204 No Content', async () => {
    mockedPending.mockResolvedValueOnce({
      data: undefined,
      status: 204,
      headers: new Headers(),
    });

    await expect(authAttemptsApi.pending(samplePendingRequest)).resolves.toBeUndefined();
  });

  it('pending fails closed when HTTP 200 body is missing required fields', async () => {
    mockedPending.mockResolvedValueOnce({
      data: {
        authAttemptId: 123,
        authAttemptProofToken: 'attempt-proof-token',
      },
      status: 200,
      headers: new Headers(),
    });

    await expect(authAttemptsApi.pending(samplePendingRequest)).rejects.toMatchObject({
      name: MALFORMED_PENDING_RESPONSE,
    });
  });

  it('pending fails closed when HTTP 200 body has empty signature fields', async () => {
    mockedPending.mockResolvedValueOnce({
      data: {
        authAttemptId: 123,
        authAttemptProofToken: '',
        authAttemptProofTokenSignedByIntegration: '',
      },
      status: 200,
      headers: new Headers(),
    });

    await expect(authAttemptsApi.pending(samplePendingRequest)).rejects.toMatchObject({
      name: MALFORMED_PENDING_RESPONSE,
    });
  });

  it('pending fails closed when HTTP 200 body is not an object', async () => {
    mockedPending.mockResolvedValueOnce({
      data: null,
      status: 200,
      headers: new Headers(),
    });

    await expect(authAttemptsApi.pending(samplePendingRequest)).rejects.toMatchObject({
      name: MALFORMED_PENDING_RESPONSE,
    });
  });

  it('pending passes authUrl as baseURL config when provided', async () => {
    const payload: PendingAuthRequest = {
      enrollmentId: 456,
      enrollmentProofToken: 'proof',
      deviceProofToken: 'dpt',
      deviceProofTokenSigned: 'signed-dpt',
    };
    const responseData: PendingAuthResponse = {
      authAttemptId: 456,
      authAttemptProofToken: 'apt',
      authAttemptProofTokenSignedByIntegration: 'signed-apt',
      authAttemptChallengeRequired: false,
    };
    mockedPending.mockResolvedValueOnce({
      data: responseData,
      status: 200,
      headers: new Headers(),
    });

    const result = await authAttemptsApi.pending(payload, 'https://ezkey.globex.com');

    expect(mockedPending).toHaveBeenCalledWith(
      {
        enrollmentId: 456,
        enrollmentProofToken: 'proof',
        deviceProofToken: 'dpt',
        deviceProofTokenSigned: 'signed-dpt',
      },
      {baseURL: 'https://ezkey.globex.com'},
    );
    expect(result).toEqual(responseData);
  });

  it('respond hits the respond endpoint with normalized payload', async () => {
    const payload: RespondAuthRequest = {
      authAttemptId: '123',
      authAttemptAccepted: true,
      authAttemptProofTokenSignedByDevice: 'signed-proof',
      authAttemptChallengeResponse: '123456',
    };
    const responseData: RespondAuthResponse = {
      authAttemptId: 123,
      authAttemptResult: 'APPROVED',
      authAttemptMessage: 'Authentication approved',
      authAttemptProofTokenResultSignedByIntegration: 'c2lnLWJ5dGVz',
    };
    mockedRespond.mockResolvedValueOnce({
      data: responseData,
      status: 200,
      headers: new Headers(),
    });

    const result = await authAttemptsApi.respond(payload);

    expect(mockedRespond).toHaveBeenCalledWith(
      {
        authAttemptId: 123,
        authAttemptAccepted: true,
        authAttemptProofTokenSignedByDevice: 'signed-proof',
        authAttemptChallengeResponse: 123456,
      },
      undefined,
    );
    expect(result).toEqual(responseData);
  });

  it('respond passes authUrl as baseURL config when provided', async () => {
    const payload: RespondAuthRequest = {
      authAttemptId: 789,
      authAttemptAccepted: false,
      authAttemptProofTokenSignedByDevice: 'signed',
    };
    const responseData: RespondAuthResponse = {
      authAttemptId: 789,
      authAttemptResult: 'DENIED',
      authAttemptMessage: 'Denied by user',
      authAttemptProofTokenResultSignedByIntegration: 'c2lnLWJ5dGVz',
    };
    mockedRespond.mockResolvedValueOnce({
      data: responseData,
      status: 200,
      headers: new Headers(),
    });

    const result = await authAttemptsApi.respond(payload, 'https://ezkey.initech.com');

    expect(mockedRespond).toHaveBeenCalledWith(
      {
        authAttemptId: 789,
        authAttemptAccepted: false,
        authAttemptProofTokenSignedByDevice: 'signed',
      },
      {baseURL: 'https://ezkey.initech.com'},
    );
    expect(result).toEqual(responseData);
  });
});
