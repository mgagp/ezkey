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

import AsyncStorage from '@react-native-async-storage/async-storage';
import {enrollmentStorage} from '../enrollmentStorage';

const mockAsyncStorage = jest.mocked(AsyncStorage);

describe('enrollmentStorage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAsyncStorage.getItem.mockResolvedValue(null);
    mockAsyncStorage.setItem.mockResolvedValue();
    mockAsyncStorage.removeItem.mockResolvedValue();
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
          authUrl: 'https://EZKEY.Example.com:443/',
          installationName: 'Acme EU',
          installationDescription: 'Primary European Ezkey installation',
        },
      ]),
    );

    const items = await enrollmentStorage.listEnrollments();

    expect(items).toHaveLength(1);
    expect(items[0].installation).toEqual({
      id: 'https://ezkey.example.com',
      authUrl: 'https://ezkey.example.com',
      host: 'ezkey.example.com',
      name: 'Acme EU',
      description: 'Primary European Ezkey installation',
      aboutUrl: undefined,
      lastRefreshedAt: undefined,
    });
  });

  it('persists enrollments with the nested installation object', async () => {
    await enrollmentStorage.saveEnrollment({
      id: 'enrollment-1',
      integrationId: 'integration-1',
      integrationName: 'Admin Console',
      createdAt: '2026-05-01T12:00:00.000Z',
      lastActivityAt: '2026-05-01T12:00:00.000Z',
      enrollmentProofToken: 'token-1',
      installation: {
        id: 'https://login.red.example',
        authUrl: 'https://login.red.example',
        host: 'login.red.example',
        name: 'Ezkey installation',
      },
    });

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
  });
});