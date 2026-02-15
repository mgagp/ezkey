import {validateAuthUrl} from '../urlValidation';

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

  it('returns undefined for non-string input', () => {
    // @ts-expect-error testing runtime safety
    expect(validateAuthUrl(123)).toBeUndefined();
    // @ts-expect-error testing runtime safety
    expect(validateAuthUrl({})).toBeUndefined();
  });
});
