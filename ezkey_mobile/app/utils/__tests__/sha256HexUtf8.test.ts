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
});
