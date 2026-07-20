import {normalizeInstallationId, validateAuthUrl} from '../urlValidation';

describe('validateAuthUrl', () => {
  it('returns undefined for null/undefined/empty input', () => {
    expect(validateAuthUrl(null)).toBeUndefined();
    expect(validateAuthUrl(undefined)).toBeUndefined();
    expect(validateAuthUrl('')).toBeUndefined();
    expect(validateAuthUrl('   ')).toBeUndefined();
  });

  it('accepts a valid HTTPS URL', () => {
    expect(validateAuthUrl('https://ezkey.acme.com')).toBe('https://ezkey.acme.com');
  });

  it('accepts HTTPS URL with a port', () => {
    expect(validateAuthUrl('https://ezkey.acme.com:8443')).toBe('https://ezkey.acme.com:8443');
  });

  it('collapses implicit HTTPS port 443', () => {
    expect(validateAuthUrl('https://ezkey.acme.com:443')).toBe('https://ezkey.acme.com');
  });

  it('accepts HTTPS URL with a path and strips trailing slash', () => {
    expect(validateAuthUrl('https://ezkey.acme.com/api/')).toBe('https://ezkey.acme.com/api');
  });

  it('strips trailing slash on origin-only URL', () => {
    expect(validateAuthUrl('https://ezkey.acme.com/')).toBe('https://ezkey.acme.com');
  });

  it('rejects plain HTTP on public hosts', () => {
    expect(validateAuthUrl('http://ezkey.acme.com')).toBeUndefined();
  });

  it('allows HTTP on localhost for development', () => {
    expect(validateAuthUrl('http://localhost:8080')).toBe('http://localhost:8080');
  });

  it('allows HTTP on 127.0.0.1 for development', () => {
    expect(validateAuthUrl('http://127.0.0.1:8080')).toBe('http://127.0.0.1:8080');
  });

  it('allows HTTP on 10.0.2.2 (Android emulator loopback)', () => {
    expect(validateAuthUrl('http://10.0.2.2:8080')).toBe('http://10.0.2.2:8080');
  });

  it('rejects malformed URLs', () => {
    expect(validateAuthUrl('not-a-url')).toBeUndefined();
    expect(validateAuthUrl('ftp://files.example.com')).toBeUndefined();
    expect(validateAuthUrl('://missing-scheme.com')).toBeUndefined();
  });

  it('trims whitespace before parsing', () => {
    expect(validateAuthUrl('  https://ezkey.acme.com  ')).toBe('https://ezkey.acme.com');
  });

  it('lowercases scheme and host while preserving path casing', () => {
    expect(validateAuthUrl('HTTPS://EZKEY.Acme.COM/Auth/API/')).toBe(
      'https://ezkey.acme.com/Auth/API',
    );
  });

  it('returns undefined for non-string input', () => {
    // @ts-expect-error testing runtime safety
    expect(validateAuthUrl(123)).toBeUndefined();
    // @ts-expect-error testing runtime safety
    expect(validateAuthUrl({})).toBeUndefined();
  });
});

describe('normalizeInstallationId', () => {
  it('matches normalized Auth API URL semantics', () => {
    expect(normalizeInstallationId('https://EZKEY.Acme.COM:443/')).toBe(
      'https://ezkey.acme.com',
    );
  });

  it('treats case, trailing slash, and :443 as the same trust zone', () => {
    const canon = 'https://ezkey.acme.com';
    expect(normalizeInstallationId('https://ezkey.acme.com')).toBe(canon);
    expect(normalizeInstallationId('https://ezkey.acme.com/')).toBe(canon);
    expect(normalizeInstallationId('https://EZKEY.ACME.COM:443')).toBe(canon);
    expect(normalizeInstallationId('HTTPS://ezkey.acme.com:443/')).toBe(canon);
  });

  it('preserves distinct non-empty paths as distinct trust zones', () => {
    expect(normalizeInstallationId('https://ezkey.acme.com/auth')).toBe(
      'https://ezkey.acme.com/auth',
    );
    expect(normalizeInstallationId('https://ezkey.acme.com/auth/')).toBe(
      'https://ezkey.acme.com/auth',
    );
    expect(normalizeInstallationId('https://ezkey.acme.com')).not.toBe(
      normalizeInstallationId('https://ezkey.acme.com/auth'),
    );
  });

  it('preserves non-default ports as distinct trust zones', () => {
    expect(normalizeInstallationId('https://ezkey.acme.com:8443')).toBe(
      'https://ezkey.acme.com:8443',
    );
    expect(normalizeInstallationId('https://ezkey.acme.com:8443')).not.toBe(
      normalizeInstallationId('https://ezkey.acme.com'),
    );
  });

  it('returns undefined for invalid Auth URLs (no fake installation id)', () => {
    expect(normalizeInstallationId('http://ezkey.acme.com')).toBeUndefined();
    expect(normalizeInstallationId('not-a-url')).toBeUndefined();
    expect(normalizeInstallationId(null)).toBeUndefined();
  });
});
