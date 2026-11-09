import {parseQrPayload} from '../qrPayload';

describe('parseQrPayload', () => {
  it('throws on empty string', () => {
    expect(() => parseQrPayload('')).toThrow('Empty payload');
  });

  it('throws on whitespace-only string', () => {
    expect(() => parseQrPayload('   ')).toThrow('Empty payload');
  });

  it('parses a proof token and ignores a leftover enrollmentId', () => {
    const result = parseQrPayload(
      JSON.stringify({enrollmentId: '123', enrollmentProofToken: 'token-abc'}),
    );
    expect(result).toEqual({
      enrollmentProofToken: 'token-abc',
      authUrl: undefined,
    });
  });

  it('parses JSON with a valid HTTPS authUrl', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentProofToken: 'token-ghi',
        authUrl: 'https://ezkey.example.com',
      }),
    );
    expect(result).toEqual({
      enrollmentProofToken: 'token-ghi',
      authUrl: 'https://ezkey.example.com',
    });
  });

  it('normalizes HTTPS authUrl by stripping trailing slash', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentProofToken: 'tok',
        authUrl: 'https://ezkey.example.com/',
      }),
    );
    expect(result.authUrl).toBe('https://ezkey.example.com');
  });

  it('normalizes HTTPS authUrl by stripping default port 443', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentProofToken: 'tok',
        authUrl: 'https://ezkey.example.com:443/',
      }),
    );
    expect(result.authUrl).toBe('https://ezkey.example.com');
  });

  it('preserves non-default HTTPS port', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentProofToken: 'tok',
        authUrl: 'https://ezkey.example.com:8443',
      }),
    );
    expect(result.authUrl).toBe('https://ezkey.example.com:8443');
  });

  it('accepts HTTP on localhost (dev loopback)', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentProofToken: 'tok',
        authUrl: 'http://localhost:8080',
      }),
    );
    expect(result.authUrl).toBe('http://localhost:8080');
  });

  it('accepts HTTP on 127.0.0.1 (dev loopback)', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentProofToken: 'tok',
        authUrl: 'http://127.0.0.1:8080',
      }),
    );
    expect(result.authUrl).toBe('http://127.0.0.1:8080');
  });

  it('accepts HTTP on 10.0.2.2 (Android emulator host alias)', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentProofToken: 'tok',
        authUrl: 'http://10.0.2.2:8080',
      }),
    );
    expect(result.authUrl).toBe('http://10.0.2.2:8080');
  });

  it('throws on HTTP with a non-loopback host (production MITM risk)', () => {
    expect(() =>
      parseQrPayload(
        JSON.stringify({
          enrollmentProofToken: 'tok',
          authUrl: 'http://evil.example.com',
        }),
      ),
    ).toThrow('Invalid Auth API URL in QR payload.');
  });

  it('throws on malformed authUrl', () => {
    expect(() =>
      parseQrPayload(
        JSON.stringify({
          enrollmentProofToken: 'tok',
          authUrl: 'not-a-url',
        }),
      ),
    ).toThrow('Invalid Auth API URL in QR payload.');
  });

  it('throws Unsupported QR format when the proof token is missing', () => {
    expect(() => parseQrPayload(JSON.stringify({enrollmentId: '1'}))).toThrow(
      'Unsupported QR format',
    );
  });

  it('throws Unsupported QR format on empty JSON object', () => {
    expect(() => parseQrPayload('{}')).toThrow('Unsupported QR format');
  });

  it('throws Unsupported QR format on the legacy id|token paste', () => {
    expect(() => parseQrPayload('enrollment-42|proof-token-xyz')).toThrow(
      'Unsupported QR format',
    );
  });

  it('throws Unsupported QR format on a plain string', () => {
    expect(() => parseQrPayload('justaplainstring')).toThrow('Unsupported QR format');
  });
});
