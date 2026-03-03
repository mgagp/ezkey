import type {AxiosResponse} from 'axios';
import {enrollmentsApi} from '../enrollments';
import {httpClient} from '../httpClient';
import type {BindEnrollmentRequest, BindEnrollmentResponse, VerifyEnrollmentRequest, VerifyEnrollmentResponse} from '../types';

jest.mock('../httpClient', () => ({
  httpClient: {
    post: jest.fn(),
  },
}));

const mockedPost = httpClient.post as jest.Mock;

describe('enrollmentsApi', () => {
  beforeEach(() => {
    mockedPost.mockReset();
  });

  it('bind calls the correct endpoint with normalized payload', async () => {
    const payload: BindEnrollmentRequest = {
      enrollmentId: '123',
      enrollmentProofToken: 'proof-token',
      language: 'en',
    };
    const responseData: BindEnrollmentResponse = {
      enrollmentId: 123,
      enrollmentProofToken: 'proof-token-updated',
      integrationPublicKey: 'public-key',
      integrationName: 'Acme Bank',
    };
    mockedPost.mockResolvedValueOnce({data: responseData} as AxiosResponse<BindEnrollmentResponse>);

    const result = await enrollmentsApi.bind(payload);

    expect(mockedPost).toHaveBeenCalledWith(
      '/api/v1/enrollments/bind',
      {enrollmentId: 123, enrollmentProofToken: 'proof-token', language: 'en'},
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
      integrationName: 'Globex Corp',
    };
    mockedPost.mockResolvedValueOnce({data: responseData} as AxiosResponse<BindEnrollmentResponse>);

    const result = await enrollmentsApi.bind(payload, 'https://ezkey.globex.com');

    expect(mockedPost).toHaveBeenCalledWith(
      '/api/v1/enrollments/bind',
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
    };
    mockedPost.mockResolvedValueOnce({data: responseData} as AxiosResponse<VerifyEnrollmentResponse>);

    const result = await enrollmentsApi.verify(payload);

    expect(mockedPost).toHaveBeenCalledWith(
      '/api/v1/enrollments/verify',
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
      devicePublicKey: 'device-pk',
      enrollmentProofTokenSigned: 'signed',
    };
    const responseData: VerifyEnrollmentResponse = {active: true};
    mockedPost.mockResolvedValueOnce({data: responseData} as AxiosResponse<VerifyEnrollmentResponse>);

    const result = await enrollmentsApi.verify(payload, 'https://ezkey.initech.com');

    expect(mockedPost).toHaveBeenCalledWith(
      '/api/v1/enrollments/verify',
      {
        enrollmentId: 789,
        challengeResponse: undefined,
        devicePublicKey: 'device-pk',
        enrollmentProofTokenSigned: 'signed',
      },
      {baseURL: 'https://ezkey.initech.com'},
    );
    expect(result).toEqual(responseData);
  });
});

