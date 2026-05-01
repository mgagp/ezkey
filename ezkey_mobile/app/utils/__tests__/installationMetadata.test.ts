describe('resolveEnrollmentAuthUrl', () => {
  const loadResolveEnrollmentAuthUrl = (config: Record<string, string> = {}) => {
    jest.resetModules();
    jest.doMock('react-native-config', () => ({
      __esModule: true,
      default: config,
    }));

    return require('../installationMetadata')
      .resolveEnrollmentAuthUrl as (authUrl?: string) => string | undefined;
  };

  it('does not fall back to the default loopback base URL when no base is configured', () => {
    const resolveEnrollmentAuthUrl = loadResolveEnrollmentAuthUrl();

    expect(resolveEnrollmentAuthUrl(undefined)).toBeUndefined();
  });

  it('uses the configured base URL when one is explicitly provided', () => {
    const resolveEnrollmentAuthUrl = loadResolveEnrollmentAuthUrl({
      EZKEY_API_BASE_URL: ' https://EZKEY.Example.com:443/ ',
    });

    expect(resolveEnrollmentAuthUrl(undefined)).toBe('https://ezkey.example.com');
  });
});

describe('buildInstallation', () => {
  const loadBuildInstallation = () => {
    jest.resetModules();

    return require('../installationMetadata').buildInstallation as (
      authUrl: string,
      instanceInfo?: {
        instanceName?: string | null;
        instanceDescription?: string | null;
        aboutUrl?: string | null;
      },
      refreshedAt?: string,
    ) => {
      id: string;
      authUrl?: string;
      host?: string;
      name: string;
      description?: string;
      aboutUrl?: string;
      lastRefreshedAt?: string;
    };
  };

  it('builds a first-class installation from authUrl and public instance info', () => {
    const buildInstallation = loadBuildInstallation();

    expect(
      buildInstallation(
        'https://EZKEY.Example.com:443/',
        {
          instanceName: 'Acme EU',
          instanceDescription: 'Primary European Ezkey installation',
          aboutUrl: 'https://acme.example/about',
        },
        '2026-05-01T12:00:00.000Z',
      ),
    ).toEqual({
      id: 'https://ezkey.example.com',
      authUrl: 'https://ezkey.example.com',
      host: 'ezkey.example.com',
      name: 'Acme EU',
      description: 'Primary European Ezkey installation',
      aboutUrl: 'https://acme.example/about',
      lastRefreshedAt: '2026-05-01T12:00:00.000Z',
    });
  });
});

describe('hydrateInstallationMetadata', () => {
  const loadHydrateInstallationMetadata = () => {
    jest.resetModules();

    return require('../installationMetadata').hydrateInstallationMetadata as <T>(record: T) => T & {
      installation?: {
        id: string;
        authUrl?: string;
        host?: string;
        name: string;
        description?: string;
        aboutUrl?: string;
        lastRefreshedAt?: string;
      };
    };
  };

  it('hydrates a nested installation from legacy flat fields', () => {
    const hydrateInstallationMetadata = loadHydrateInstallationMetadata();

    const result = hydrateInstallationMetadata({
      id: 'enrollment-1',
      authUrl: 'https://EZKEY.Example.com:443/',
      installationName: 'Acme EU',
      installationDescription: 'Primary European Ezkey installation',
      installationAboutUrl: 'https://acme.example/about',
    });

    expect(result.installation).toEqual({
      id: 'https://ezkey.example.com',
      authUrl: 'https://ezkey.example.com',
      host: 'ezkey.example.com',
      name: 'Acme EU',
      description: 'Primary European Ezkey installation',
      aboutUrl: 'https://acme.example/about',
      lastRefreshedAt: undefined,
    });
  });
});

describe('needsInstallationMetadataRefresh', () => {
  const loadNeedsInstallationMetadataRefresh = () => {
    jest.resetModules();

    return require('../installationMetadata')
      .needsInstallationMetadataRefresh as (record: {
      authUrl?: string;
      installation?: {
        authUrl?: string;
        host?: string;
        name?: string;
        description?: string;
      };
    }) => boolean;
  };

  it('refreshes host-only fallback metadata without waiting for staleness', () => {
    const needsInstallationMetadataRefresh = loadNeedsInstallationMetadataRefresh();

    expect(
      needsInstallationMetadataRefresh({
        installation: {
          authUrl: 'https://ezkey.example.com',
          name: 'ezkey.example.com',
        },
      }),
    ).toBe(true);
  });

  it('does not refresh when branded metadata is already present', () => {
    const needsInstallationMetadataRefresh = loadNeedsInstallationMetadataRefresh();

    expect(
      needsInstallationMetadataRefresh({
        installation: {
          authUrl: 'https://ezkey.example.com',
          name: 'Ezkey System',
          description: 'Ezkey MFA instance for your organization',
        },
      }),
    ).toBe(false);
  });
});