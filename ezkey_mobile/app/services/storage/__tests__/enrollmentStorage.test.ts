jest.mock('@react-native-async-storage/async-storage', () => ({
  __esModule: true,
  default: {
    getItem: jest.fn(),
    setItem: jest.fn(),
    removeItem: jest.fn(),
  },
}));

jest.mock('../secureStorage', () => ({
  secureStorage: {
    getItem: jest.fn(),
    setItem: jest.fn(),
    removeItem: jest.fn(),
  },
}));

jest.mock('../../crypto/nativeCrypto', () => ({
  nativeCrypto: {
    deleteKeyPair: jest.fn(),
  },
}));

import AsyncStorage from '@react-native-async-storage/async-storage';
import {nativeCrypto} from '../../crypto/nativeCrypto';
import {enrollmentStorage} from '../enrollmentStorage';
import {secureStorage} from '../secureStorage';

const mockAsyncStorage = jest.mocked(AsyncStorage);
const mockSecureStorage = jest.mocked(secureStorage);
const mockDeleteKeyPair = jest.mocked(nativeCrypto.deleteKeyPair);

describe('enrollmentStorage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAsyncStorage.getItem.mockResolvedValue(null);
    mockAsyncStorage.setItem.mockResolvedValue();
    mockAsyncStorage.removeItem.mockResolvedValue();
    mockSecureStorage.getItem.mockResolvedValue(undefined);
    mockSecureStorage.setItem.mockResolvedValue();
    mockSecureStorage.removeItem.mockResolvedValue();
    mockDeleteKeyPair.mockResolvedValue(true);
  });

  it('rehydrates a nested installation object from legacy flat storage records', async () => {
    mockAsyncStorage.getItem.mockResolvedValue(
      JSON.stringify([
        {
          id: 'enrollment-1',
          integrationId: 'integration-1',
          integrationName: 'Admin Console',
          createdAt: '2026-05-01T12:00:00.000Z',
          lastActivityAt: '2026-05-01T12:00:00.000Z',
          enrollmentProofToken: 'token-1',
          integrationPublicKey: 'integration-public-key-1',
          securityLevel: 'confirm-before-approvals',
          authUrl: 'https://EZKEY.Example.com:443/',
          installationName: 'Acme EU',
          installationDescription: 'Primary European Ezkey installation',
        },
      ]),
    );

    const items = await enrollmentStorage.listEnrollments();

    expect(items).toHaveLength(1);
    expect(items[0].enrollmentProofToken).toBe('token-1');
    expect(items[0].integrationPublicKey).toBe('integration-public-key-1');
    expect(items[0].approvalPolicy).toBe('not-required');
    expect(items[0].installation).toEqual({
      id: 'https://ezkey.example.com',
      authUrl: 'https://ezkey.example.com',
      host: 'ezkey.example.com',
      name: 'Acme EU',
      description: 'Primary European Ezkey installation',
      aboutUrl: undefined,
      lastRefreshedAt: undefined,
    });
    expect(mockSecureStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/enrollment-proof-token.enrollment-1',
      'token-1',
    );
    expect(mockSecureStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/integration-public-key.enrollment-1',
      'integration-public-key-1',
    );
    expect(mockAsyncStorage.setItem).toHaveBeenCalledTimes(1);
    const [, rewrittenPayload] = mockAsyncStorage.setItem.mock.calls[0];
    expect(JSON.parse(rewrittenPayload as string)).toEqual([
      expect.not.objectContaining({
        enrollmentProofToken: expect.anything(),
        integrationPublicKey: expect.anything(),
        securityLevel: expect.anything(),
      }),
    ]);
    expect(JSON.parse(rewrittenPayload as string)[0]).toEqual(
      expect.objectContaining({approvalPolicy: 'not-required'}),
    );
  });

  it('persists enrollments with the nested installation object and stores proof tokens securely', async () => {
    await enrollmentStorage.saveEnrollment({
      id: 'enrollment-1',
      integrationId: 'integration-1',
      integrationName: 'Admin Console',
      createdAt: '2026-05-01T12:00:00.000Z',
      lastActivityAt: '2026-05-01T12:00:00.000Z',
      enrollmentProofToken: 'token-1',
      integrationPublicKey: 'integration-public-key-1',
      installation: {
        id: 'https://login.red.example',
        authUrl: 'https://login.red.example',
        host: 'login.red.example',
        name: 'Ezkey installation',
      },
    });

    expect(mockSecureStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/enrollment-proof-token.enrollment-1',
      'token-1',
    );
    expect(mockSecureStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/integration-public-key.enrollment-1',
      'integration-public-key-1',
    );
    expect(mockAsyncStorage.setItem).toHaveBeenCalledTimes(1);
    const [, payload] = mockAsyncStorage.setItem.mock.calls[0];
    expect(JSON.parse(payload as string)).toEqual([
      expect.objectContaining({
        id: 'enrollment-1',
        installation: {
          id: 'https://login.red.example',
          authUrl: 'https://login.red.example',
          host: 'login.red.example',
          name: 'Ezkey installation',
        },
      }),
    ]);
    expect(JSON.parse(payload as string)[0]).not.toHaveProperty('enrollmentProofToken');
    expect(JSON.parse(payload as string)[0]).not.toHaveProperty('integrationPublicKey');
  });

  it('rehydrates proof tokens from secure storage for current records', async () => {
    mockAsyncStorage.getItem.mockResolvedValue(
      JSON.stringify([
        {
          id: 'enrollment-1',
          integrationId: 'integration-1',
          integrationName: 'Admin Console',
          createdAt: '2026-05-01T12:00:00.000Z',
          lastActivityAt: '2026-05-01T12:00:00.000Z',
        },
      ]),
    );
    mockSecureStorage.getItem.mockImplementation(async key => {
      if (key === 'ezkey-mobile/enrollment-proof-token.enrollment-1') {
        return 'secure-token-1';
      }
      if (key === 'ezkey-mobile/integration-public-key.enrollment-1') {
        return 'secure-integration-public-key-1';
      }
      return undefined;
    });

    const items = await enrollmentStorage.listEnrollments();

    expect(items).toEqual([
      expect.objectContaining({
        id: 'enrollment-1',
        enrollmentProofToken: 'secure-token-1',
        integrationPublicKey: 'secure-integration-public-key-1',
        approvalPolicy: 'not-required',
      }),
    ]);
    expect(mockAsyncStorage.setItem).toHaveBeenCalledTimes(1);
    const [, rewrittenPayload] = mockAsyncStorage.setItem.mock.calls[0];
    expect(JSON.parse(rewrittenPayload as string)).toEqual([
      expect.objectContaining({
        id: 'enrollment-1',
        approvalPolicy: 'not-required',
      }),
    ]);
  });

  it('removes the secure proof token when deleting an enrollment', async () => {
    mockAsyncStorage.getItem.mockResolvedValue(
      JSON.stringify([
        {
          id: 'enrollment-1',
          integrationId: 'integration-1',
          integrationName: 'Admin Console',
          createdAt: '2026-05-01T12:00:00.000Z',
          lastActivityAt: '2026-05-01T12:00:00.000Z',
        },
      ]),
    );
    mockSecureStorage.getItem.mockImplementation(async key => {
      if (key === 'ezkey-mobile/enrollment-proof-token.enrollment-1') {
        return 'secure-token-1';
      }
      if (key === 'ezkey-mobile/integration-public-key.enrollment-1') {
        return 'secure-integration-public-key-1';
      }
      return undefined;
    });

    await enrollmentStorage.deleteEnrollment('enrollment-1');

    expect(mockDeleteKeyPair).toHaveBeenCalledWith('enrollment-1');
    expect(mockSecureStorage.removeItem).toHaveBeenCalledWith(
      'ezkey-mobile/enrollment-proof-token.enrollment-1',
    );
    expect(mockSecureStorage.removeItem).toHaveBeenCalledWith(
      'ezkey-mobile/integration-public-key.enrollment-1',
    );
    expect(mockAsyncStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/enrollments',
      JSON.stringify([]),
    );
  });

  it('continues enrollment delete when native key pair deletion fails', async () => {
    mockDeleteKeyPair.mockRejectedValue(new Error('keystore unavailable'));
    mockAsyncStorage.getItem.mockResolvedValue(
      JSON.stringify([
        {
          id: 'enrollment-1',
          integrationId: 'integration-1',
          integrationName: 'Admin Console',
          createdAt: '2026-05-01T12:00:00.000Z',
          lastActivityAt: '2026-05-01T12:00:00.000Z',
        },
      ]),
    );
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);

    await enrollmentStorage.deleteEnrollment('enrollment-1');

    expect(mockDeleteKeyPair).toHaveBeenCalledWith('enrollment-1');
    expect(mockSecureStorage.removeItem).toHaveBeenCalledWith(
      'ezkey-mobile/enrollment-proof-token.enrollment-1',
    );
    expect(mockAsyncStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/enrollments',
      JSON.stringify([]),
    );
    expect(warnSpy).toHaveBeenCalledWith(
      '[enrollmentStorage] Failed to delete enrollment key pair (continuing wipe):',
      'enrollment-1',
      expect.any(Error),
    );
    warnSpy.mockRestore();
  });

  it('replaces enrollments and removes secure values for records no longer present', async () => {
    mockAsyncStorage.getItem.mockResolvedValue(
      JSON.stringify([
        {
          id: 'enrollment-legacy',
          integrationId: 'integration-legacy',
          integrationName: 'Legacy Console',
          createdAt: '2026-05-01T12:00:00.000Z',
          lastActivityAt: '2026-05-01T12:00:00.000Z',
        },
      ]),
    );
    mockSecureStorage.getItem.mockImplementation(async key => {
      if (key === 'ezkey-mobile/enrollment-proof-token.enrollment-legacy') {
        return 'legacy-token';
      }
      if (key === 'ezkey-mobile/integration-public-key.enrollment-legacy') {
        return 'legacy-integration-public-key';
      }
      return undefined;
    });

    await enrollmentStorage.replaceAll([
      {
        id: 'enrollment-next',
        integrationId: 'integration-next',
        integrationName: 'Next Console',
        createdAt: '2026-05-02T12:00:00.000Z',
        lastActivityAt: '2026-05-02T12:00:00.000Z',
        enrollmentProofToken: 'next-token',
        integrationPublicKey: 'next-integration-public-key',
      },
    ]);

    expect(mockSecureStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/enrollment-proof-token.enrollment-next',
      'next-token',
    );
    expect(mockSecureStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/integration-public-key.enrollment-next',
      'next-integration-public-key',
    );
    expect(mockSecureStorage.removeItem).toHaveBeenCalledWith(
      'ezkey-mobile/enrollment-proof-token.enrollment-legacy',
    );
    expect(mockSecureStorage.removeItem).toHaveBeenCalledWith(
      'ezkey-mobile/integration-public-key.enrollment-legacy',
    );
    const [, payload] = mockAsyncStorage.setItem.mock.calls.at(-1) ?? [];
    expect(JSON.parse(payload as string)).toEqual([
      expect.objectContaining({
        id: 'enrollment-next',
        integrationId: 'integration-next',
        integrationName: 'Next Console',
        createdAt: '2026-05-02T12:00:00.000Z',
        lastActivityAt: '2026-05-02T12:00:00.000Z',
        approvalPolicy: 'not-required',
      }),
    ]);
    expect(JSON.parse(payload as string)[0]).not.toHaveProperty('enrollmentProofToken');
    expect(JSON.parse(payload as string)[0]).not.toHaveProperty('integrationPublicKey');
  });

  it('updates last activity without reintroducing cleartext secure fields into AsyncStorage', async () => {
    mockAsyncStorage.getItem.mockResolvedValue(
      JSON.stringify([
        {
          id: 'enrollment-1',
          integrationId: 'integration-1',
          integrationName: 'Admin Console',
          createdAt: '2026-05-01T12:00:00.000Z',
          lastActivityAt: '2026-05-01T12:00:00.000Z',
        },
      ]),
    );
    mockSecureStorage.getItem.mockImplementation(async key => {
      if (key === 'ezkey-mobile/enrollment-proof-token.enrollment-1') {
        return 'secure-token-1';
      }
      if (key === 'ezkey-mobile/integration-public-key.enrollment-1') {
        return 'secure-integration-public-key-1';
      }
      return undefined;
    });

    const updated = await enrollmentStorage.updateEnrollmentLastActivity(
      'enrollment-1',
      '2026-05-03T09:30:00.000Z',
    );

    expect(updated).toEqual(
      expect.objectContaining({
        id: 'enrollment-1',
        lastActivityAt: '2026-05-03T09:30:00.000Z',
        enrollmentProofToken: 'secure-token-1',
        integrationPublicKey: 'secure-integration-public-key-1',
      }),
    );
    const [, payload] = mockAsyncStorage.setItem.mock.calls[0];
    expect(JSON.parse(payload as string)[0]).not.toHaveProperty('enrollmentProofToken');
    expect(JSON.parse(payload as string)[0]).not.toHaveProperty('integrationPublicKey');
  });

  it('clears metadata and all secure enrollment values', async () => {
    mockAsyncStorage.getItem.mockResolvedValue(
      JSON.stringify([
        {
          id: 'enrollment-1',
          integrationId: 'integration-1',
          integrationName: 'Admin Console',
          createdAt: '2026-05-01T12:00:00.000Z',
          lastActivityAt: '2026-05-01T12:00:00.000Z',
        },
        {
          id: 'enrollment-2',
          integrationId: 'integration-2',
          integrationName: 'Support Console',
          createdAt: '2026-05-02T12:00:00.000Z',
          lastActivityAt: '2026-05-02T12:00:00.000Z',
        },
      ]),
    );
    mockSecureStorage.getItem.mockImplementation(async key => {
      if (key === 'ezkey-mobile/enrollment-proof-token.enrollment-1') {
        return 'secure-token-1';
      }
      if (key === 'ezkey-mobile/integration-public-key.enrollment-1') {
        return 'secure-integration-public-key-1';
      }
      if (key === 'ezkey-mobile/enrollment-proof-token.enrollment-2') {
        return 'secure-token-2';
      }
      if (key === 'ezkey-mobile/integration-public-key.enrollment-2') {
        return 'secure-integration-public-key-2';
      }
      return undefined;
    });

    await enrollmentStorage.clearAll();

    expect(mockDeleteKeyPair).toHaveBeenCalledWith('enrollment-1');
    expect(mockDeleteKeyPair).toHaveBeenCalledWith('enrollment-2');
    expect(mockSecureStorage.removeItem).toHaveBeenCalledWith(
      'ezkey-mobile/enrollment-proof-token.enrollment-1',
    );
    expect(mockSecureStorage.removeItem).toHaveBeenCalledWith(
      'ezkey-mobile/integration-public-key.enrollment-1',
    );
    expect(mockSecureStorage.removeItem).toHaveBeenCalledWith(
      'ezkey-mobile/enrollment-proof-token.enrollment-2',
    );
    expect(mockSecureStorage.removeItem).toHaveBeenCalledWith(
      'ezkey-mobile/integration-public-key.enrollment-2',
    );
    expect(mockAsyncStorage.removeItem).toHaveBeenCalledWith('ezkey-mobile/enrollments');
  });

  it('continues clear-all when native key pair deletion fails for one enrollment', async () => {
    mockDeleteKeyPair.mockImplementation(async enrollmentId => {
      if (enrollmentId === 'enrollment-1') {
        throw new Error('keystore unavailable');
      }
      return true;
    });
    mockAsyncStorage.getItem.mockResolvedValue(
      JSON.stringify([
        {
          id: 'enrollment-1',
          integrationId: 'integration-1',
          integrationName: 'Admin Console',
          createdAt: '2026-05-01T12:00:00.000Z',
          lastActivityAt: '2026-05-01T12:00:00.000Z',
        },
        {
          id: 'enrollment-2',
          integrationId: 'integration-2',
          integrationName: 'Support Console',
          createdAt: '2026-05-02T12:00:00.000Z',
          lastActivityAt: '2026-05-02T12:00:00.000Z',
        },
      ]),
    );
    mockSecureStorage.getItem.mockImplementation(async key => {
      if (key === 'ezkey-mobile/enrollment-proof-token.enrollment-1') {
        return 'secure-token-1';
      }
      if (key === 'ezkey-mobile/integration-public-key.enrollment-1') {
        return 'secure-integration-public-key-1';
      }
      if (key === 'ezkey-mobile/enrollment-proof-token.enrollment-2') {
        return 'secure-token-2';
      }
      if (key === 'ezkey-mobile/integration-public-key.enrollment-2') {
        return 'secure-integration-public-key-2';
      }
      return undefined;
    });
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);

    await enrollmentStorage.clearAll();

    expect(mockDeleteKeyPair).toHaveBeenCalledWith('enrollment-1');
    expect(mockDeleteKeyPair).toHaveBeenCalledWith('enrollment-2');
    expect(mockSecureStorage.removeItem).toHaveBeenCalledWith(
      'ezkey-mobile/enrollment-proof-token.enrollment-1',
    );
    expect(mockSecureStorage.removeItem).toHaveBeenCalledWith(
      'ezkey-mobile/enrollment-proof-token.enrollment-2',
    );
    expect(mockAsyncStorage.removeItem).toHaveBeenCalledWith('ezkey-mobile/enrollments');
    warnSpy.mockRestore();
  });

  it('keeps two enrollments with the same server id when local ids differ by installation', async () => {
    const {deriveLocalEnrollmentId} = require('../../../utils/localEnrollmentIdentity');
    const idA = deriveLocalEnrollmentId('https://auth-a.example.com', 1);
    const idB = deriveLocalEnrollmentId('https://auth-b.example.com', 1);
    let storedJson: string | null = null;
    mockAsyncStorage.getItem.mockImplementation(async () => storedJson);
    mockAsyncStorage.setItem.mockImplementation(async (_key, value) => {
      storedJson = value as string;
    });
    mockSecureStorage.getItem.mockImplementation(async key => {
      if (key === `ezkey-mobile/enrollment-proof-token.${idA}`) {
        return 'token-a';
      }
      if (key === `ezkey-mobile/integration-public-key.${idA}`) {
        return 'pk-a';
      }
      if (key === `ezkey-mobile/enrollment-proof-token.${idB}`) {
        return 'token-b';
      }
      if (key === `ezkey-mobile/integration-public-key.${idB}`) {
        return 'pk-b';
      }
      return undefined;
    });

    await enrollmentStorage.saveEnrollment({
      id: idA,
      integrationId: '1',
      integrationName: 'A',
      createdAt: '2026-07-20T00:00:00.000Z',
      lastActivityAt: '2026-07-20T00:00:00.000Z',
      enrollmentProofToken: 'token-a',
      enrollmentId: '1',
      integrationPublicKey: 'pk-a',
      installation: {
        id: 'https://auth-a.example.com',
        authUrl: 'https://auth-a.example.com',
        name: 'A',
      },
    });
    await enrollmentStorage.saveEnrollment({
      id: idB,
      integrationId: '1',
      integrationName: 'B',
      createdAt: '2026-07-20T00:00:00.000Z',
      lastActivityAt: '2026-07-20T00:00:00.000Z',
      enrollmentProofToken: 'token-b',
      enrollmentId: '1',
      integrationPublicKey: 'pk-b',
      installation: {
        id: 'https://auth-b.example.com',
        authUrl: 'https://auth-b.example.com',
        name: 'B',
      },
    });

    if (storedJson == null) {
      throw new Error('expected persisted enrollments JSON after dual save');
    }
    const persisted = JSON.parse(storedJson);
    expect(persisted).toHaveLength(2);
    expect(persisted.map((row: {id: string}) => row.id).sort()).toEqual([idA, idB].sort());
    expect(mockSecureStorage.setItem).toHaveBeenCalledWith(
      `ezkey-mobile/enrollment-proof-token.${idA}`,
      'token-a',
    );
    expect(mockSecureStorage.setItem).toHaveBeenCalledWith(
      `ezkey-mobile/enrollment-proof-token.${idB}`,
      'token-b',
    );
  });
});