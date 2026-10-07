/**
 * SHA-256 hex must match Java MessageDigest over UTF-8 (Auth API pending payload diagnostic).
 */
import {sha256HexUtf8} from '../sha256HexUtf8';

describe('sha256HexUtf8', () => {
  it('matches known SHA-256 of UTF-8 string (cross-check with JVM)', () => {
    expect(sha256HexUtf8('abc')).toBe(
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
    );
  });

  it('handles empty string', () => {
    expect(sha256HexUtf8('')).toBe(
      'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    );
  });

  // These exact values must never change: they are part of the Keystore key-name
  // contract (MOB-011 / MOB-017). UTF-8 multi-byte input must stay stable across
  // js-sha256 majors used by deriveInstallationScopeId / sha256HexUtf8.
  it('pins known SHA-256 of UTF-8 multi-byte string (Keystore-adjacent contract)', () => {
    expect(sha256HexUtf8('é😀')).toBe(
      '1184d1f608158eea09d297565575892231550c403aaa913008d867a97cfd5c76',
    );
  });
});
