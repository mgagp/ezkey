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

describe('needsInstallationMetadataRefresh', () => {
  const loadNeedsInstallationMetadataRefresh = () => {
    jest.resetModules();

    return require('../installationMetadata')
      .needsInstallationMetadataRefresh as (record: {
      authUrl?: string;
      installationHost?: string;
      installationName?: string;
      installationDescription?: string;
    }) => boolean;
  };

  it('refreshes host-only fallback metadata without waiting for staleness', () => {
    const needsInstallationMetadataRefresh = loadNeedsInstallationMetadataRefresh();

    expect(
      needsInstallationMetadataRefresh({
        authUrl: 'https://ezkey.example.com',
        installationName: 'ezkey.example.com',
      }),
    ).toBe(true);
  });

  it('does not refresh when branded metadata is already present', () => {
    const needsInstallationMetadataRefresh = loadNeedsInstallationMetadataRefresh();

    expect(
      needsInstallationMetadataRefresh({
        authUrl: 'https://ezkey.example.com',
        installationName: 'Ezkey System',
        installationDescription: 'Ezkey MFA instance for your organization',
      }),
    ).toBe(false);
  });
});