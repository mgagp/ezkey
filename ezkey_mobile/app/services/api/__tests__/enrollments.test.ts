import {enrollmentsApi} from '../enrollments';
import {bind, verify} from '../generated/auth-api/enrollments/enrollments';
import type {BindEnrollmentRequest, BindEnrollmentResponse, VerifyEnrollmentRequest, VerifyEnrollmentResponse} from '../types';

jest.mock('../generated/auth-api/enrollments/enrollments', () => ({
  bind: jest.fn(),
  verify: jest.fn(),
}));

const mockedBind = bind as jest.Mock;
const mockedVerify = verify as jest.Mock;

describe('enrollmentsApi', () => {
  beforeEach(() => {
    mockedBind.mockReset();
    mockedVerify.mockReset();
  });

  it('bind calls the correct endpoint with normalized payload', async () => {
    const payload: BindEnrollmentRequest = {
      enrollmentId: '123',
      enrollmentProofToken: 'proof-token',
    };
    const responseData: BindEnrollmentResponse = {
      enrollmentId: 123,
      enrollmentProofToken: 'proof-token-updated',
      integrationPublicKey: 'public-key',
      integrationKeyAlgorithm: 'ed25519',
      integrationName: 'Acme Bank',
      enrollmentBindPayloadSignedByIntegration: 'bind-signature',
    };
    mockedBind.mockResolvedValueOnce({data: responseData, status: 200, headers: new Headers()});

    const result = await enrollmentsApi.bind(payload);

    expect(mockedBind).toHaveBeenCalledWith(
      {enrollmentId: 123, enrollmentProofToken: 'proof-token'},
      undefined,
    );
    expect(result).toEqual(responseData);
  });

  it('bind passes authUrl as baseURL config when provided', async () => {
    const payload: BindEnrollmentRequest = {
      enrollmentId: '456',
      enrollmentProofToken: 'proof-token-2',
    };
    const responseData: BindEnrollmentResponse = {
      enrollmentId: 456,
      enrollmentProofToken: 'proof-token-2',
      integrationPublicKey: 'pk',
      integrationKeyAlgorithm: 'ed25519',
      integrationName: 'Globex Corp',
      enrollmentBindPayloadSignedByIntegration: 'bind-signature',
    };
    mockedBind.mockResolvedValueOnce({data: responseData, status: 200, headers: new Headers()});

    const result = await enrollmentsApi.bind(payload, 'https://ezkey.globex.com');

    expect(mockedBind).toHaveBeenCalledWith(
      {enrollmentId: 456, enrollmentProofToken: 'proof-token-2'},
      {baseURL: 'https://ezkey.globex.com'},
    );
    expect(result).toEqual(responseData);
  });

  it('verify calls the correct endpoint with normalized payload', async () => {
    const payload: VerifyEnrollmentRequest = {
      enrollmentId: '123',
      challengeResponse: '654321',
      devicePublicKey: 'device-public-key',
      enrollmentProofTokenSigned: 'signed-proof',
    };
    const responseData: VerifyEnrollmentResponse = {
      active: true,
      enrollmentVerifyMessage: 'ok',
      enrollmentVerifyPayloadSignedByIntegration: 'verify-signature',
    };
    mockedVerify.mockResolvedValueOnce({data: responseData, status: 200, headers: new Headers()});

    const result = await enrollmentsApi.verify(payload);

    expect(mockedVerify).toHaveBeenCalledWith(
      {
        enrollmentId: 123,
        challengeResponse: 654321,
        devicePublicKey: 'device-public-key',
        enrollmentProofTokenSigned: 'signed-proof',
      },
      undefined,
    );
    expect(result).toEqual(responseData);
  });

  it('verify passes authUrl as baseURL config when provided', async () => {
    const payload: VerifyEnrollmentRequest = {
      enrollmentId: '789',
      challengeResponse: '123456',
      devicePublicKey: 'device-pk',
      enrollmentProofTokenSigned: 'signed',
    };
    const responseData: VerifyEnrollmentResponse = {
      active: true,
      enrollmentVerifyMessage: 'ok',
      enrollmentVerifyPayloadSignedByIntegration: 'verify-signature',
    };
    mockedVerify.mockResolvedValueOnce({data: responseData, status: 200, headers: new Headers()});

    const result = await enrollmentsApi.verify(payload, 'https://ezkey.initech.com');

    expect(mockedVerify).toHaveBeenCalledWith(
      {
        enrollmentId: 789,
        challengeResponse: 123456,
        devicePublicKey: 'device-pk',
        enrollmentProofTokenSigned: 'signed',
      },
      {baseURL: 'https://ezkey.initech.com'},
    );
    expect(result).toEqual(responseData);
  });

  it('verify includes devicePrivateKeyStorageTier when provided', async () => {
    const payload: VerifyEnrollmentRequest = {
      enrollmentId: '42',
      challengeResponse: '111111',
      devicePublicKey: 'pk',
      enrollmentProofTokenSigned: 'sig',
      devicePrivateKeyStorageTier: 'STRONG',
    };
    const responseData: VerifyEnrollmentResponse = {
      active: true,
      enrollmentVerifyMessage: 'ok',
      enrollmentVerifyPayloadSignedByIntegration: 'verify-signature',
    };
    mockedVerify.mockResolvedValueOnce({data: responseData, status: 200, headers: new Headers()});

    await enrollmentsApi.verify(payload);

    expect(mockedVerify).toHaveBeenCalledWith(
      {
        enrollmentId: 42,
        challengeResponse: 111111,
        devicePublicKey: 'pk',
        enrollmentProofTokenSigned: 'sig',
        devicePrivateKeyStorageTier: 'STRONG',
      },
      undefined,
    );
  });
});

