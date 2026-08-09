/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

jest.mock('../httpClient', () => ({
  httpClient: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

jest.mock('../../crypto', () => ({
  cryptoService: {
    verify: jest.fn(),
  },
}));

import {cryptoService} from '../../crypto';
import {httpClient} from '../httpClient';
import {fetchVerifiedInstanceInfo, instanceInfoApi} from '../instanceInfo';

const mockHttp = jest.mocked(httpClient);
const mockVerify = jest.mocked(cryptoService.verify);

describe('instanceInfoApi / fetchVerifiedInstanceInfo', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('getSigned posts enrollmentProofToken to enrollments/instance-info', async () => {
    mockHttp.post.mockResolvedValue({
      data: {
        enrollmentId: 9,
        instanceName: 'Acme',
        instanceInfoPayloadSignedByIntegration: 'sig',
      },
    } as never);

    const data = await instanceInfoApi.getSigned('token', 'https://auth.example');
    expect(mockHttp.post).toHaveBeenCalledWith(
      '/api/v1/enrollments/instance-info',
      {enrollmentProofToken: 'token'},
      {baseURL: 'https://auth.example'},
    );
    expect(data.enrollmentId).toBe(9);
  });

  it('fetchVerifiedInstanceInfo returns branding when signature verifies', async () => {
    mockHttp.post.mockResolvedValue({
      data: {
        enrollmentId: 9,
        authApiPublicBaseUrl: 'https://auth.example',
        instanceName: 'Acme',
        instanceDescription: 'Desc',
        aboutUrl: null,
        instanceInfoPayloadSignedByIntegration: 'sig',
      },
    } as never);
    mockVerify.mockResolvedValue(true);

    const branding = await fetchVerifiedInstanceInfo({
      authUrl: 'https://auth.example',
      enrollmentProofToken: 'token',
      integrationPublicKey: 'pk',
    });

    expect(branding).toEqual({
      authApiPublicBaseUrl: 'https://auth.example',
      instanceName: 'Acme',
      instanceDescription: 'Desc',
      aboutUrl: null,
    });
    expect(mockVerify).toHaveBeenCalled();
  });

  it('fetchVerifiedInstanceInfo returns null when signature verification fails', async () => {
    mockHttp.post.mockResolvedValue({
      data: {
        enrollmentId: 9,
        instanceName: 'Evil',
        instanceInfoPayloadSignedByIntegration: 'bad-sig',
      },
    } as never);
    mockVerify.mockResolvedValue(false);

    const branding = await fetchVerifiedInstanceInfo({
      enrollmentProofToken: 'token',
      integrationPublicKey: 'pk',
    });

    expect(branding).toBeNull();
  });

  it('fetchVerifiedInstanceInfo returns null on HTTP failure', async () => {
    mockHttp.post.mockRejectedValue(new Error('network'));

    const branding = await fetchVerifiedInstanceInfo({
      enrollmentProofToken: 'token',
      integrationPublicKey: 'pk',
    });

    expect(branding).toBeNull();
    expect(mockVerify).not.toHaveBeenCalled();
  });
});
