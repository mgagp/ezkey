import {parseQrPayload} from '../qrPayload';

describe('parseQrPayload', () => {
  // ─── Empty / blank input ──────────────────────────────────────────────────

  it('throws on empty string', () => {
    expect(() => parseQrPayload('')).toThrow('Empty payload');
  });

  it('throws on whitespace-only string', () => {
    expect(() => parseQrPayload('   ')).toThrow('Empty payload');
  });

  // ─── JSON format — happy paths ────────────────────────────────────────────

  it('parses JSON with string enrollmentId and no authUrl', () => {
    const result = parseQrPayload(
      JSON.stringify({enrollmentId: '123', enrollmentProofToken: 'token-abc'}),
    );
    expect(result).toEqual({
      enrollmentId: '123',
      enrollmentProofToken: 'token-abc',
      authUrl: undefined,
    });
  });

  it('parses JSON with numeric enrollmentId (coerced to string)', () => {
    const result = parseQrPayload(
      JSON.stringify({enrollmentId: 456, enrollmentProofToken: 'token-def'}),
    );
    expect(result.enrollmentId).toBe('456');
  });

  it('parses JSON with a valid HTTPS authUrl', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentId: '789',
        enrollmentProofToken: 'token-ghi',
        authUrl: 'https://ezkey.example.com',
      }),
    );
    expect(result).toEqual({
      enrollmentId: '789',
      enrollmentProofToken: 'token-ghi',
      authUrl: 'https://ezkey.example.com',
    });
  });

  it('normalizes HTTPS authUrl by stripping trailing slash', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentId: '1',
        enrollmentProofToken: 'tok',
        authUrl: 'https://ezkey.example.com/',
      }),
    );
    expect(result.authUrl).toBe('https://ezkey.example.com');
  });

  it('normalizes HTTPS authUrl by stripping default port 443', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentId: '1',
        enrollmentProofToken: 'tok',
        authUrl: 'https://ezkey.example.com:443/',
      }),
    );
    expect(result.authUrl).toBe('https://ezkey.example.com');
  });

  it('preserves non-default HTTPS port', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentId: '1',
        enrollmentProofToken: 'tok',
        authUrl: 'https://ezkey.example.com:8443',
      }),
    );
    expect(result.authUrl).toBe('https://ezkey.example.com:8443');
  });

  it('accepts HTTP on localhost (dev loopback)', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentId: '1',
        enrollmentProofToken: 'tok',
        authUrl: 'http://localhost:8080',
      }),
    );
    expect(result.authUrl).toBe('http://localhost:8080');
  });

  it('accepts HTTP on 127.0.0.1 (dev loopback)', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentId: '1',
        enrollmentProofToken: 'tok',
        authUrl: 'http://127.0.0.1:8080',
      }),
    );
    expect(result.authUrl).toBe('http://127.0.0.1:8080');
  });

  it('accepts HTTP on 10.0.2.2 (Android emulator host alias)', () => {
    const result = parseQrPayload(
      JSON.stringify({
        enrollmentId: '1',
        enrollmentProofToken: 'tok',
        authUrl: 'http://10.0.2.2:8080',
      }),
    );
    expect(result.authUrl).toBe('http://10.0.2.2:8080');
  });

  it('parses JSON with leading/trailing whitespace around the payload', () => {
    const result = parseQrPayload(
      '  ' + JSON.stringify({enrollmentId: '1', enrollmentProofToken: 'tok'}) + '  ',
    );
    expect(result.enrollmentId).toBe('1');
  });

  it('returns authUrl as undefined when the JSON field is absent', () => {
    const result = parseQrPayload(
      JSON.stringify({enrollmentId: '1', enrollmentProofToken: 'tok'}),
    );
    expect(result.authUrl).toBeUndefined();
  });

  // ─── JSON format — security: invalid authUrl ──────────────────────────────

  it('throws on HTTP with a non-loopback host (production MITM risk)', () => {
    expect(() =>
      parseQrPayload(
        JSON.stringify({
          enrollmentId: '1',
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
          enrollmentId: '1',
          enrollmentProofToken: 'tok',
          authUrl: 'not-a-url',
        }),
      ),
    ).toThrow('Invalid Auth API URL in QR payload.');
  });

  it('throws on authUrl with no host', () => {
    expect(() =>
      parseQrPayload(
        JSON.stringify({
          enrollmentId: '1',
          enrollmentProofToken: 'tok',
          authUrl: 'https://',
        }),
      ),
    ).toThrow('Invalid Auth API URL in QR payload.');
  });

  // ─── JSON format — missing required fields ────────────────────────────────

  it('throws Unsupported QR format on JSON missing enrollmentId', () => {
    expect(() =>
      parseQrPayload(JSON.stringify({enrollmentProofToken: 'tok'})),
    ).toThrow('Unsupported QR format');
  });

  it('throws Unsupported QR format on JSON missing enrollmentProofToken', () => {
    expect(() =>
      parseQrPayload(JSON.stringify({enrollmentId: '1'})),
    ).toThrow('Unsupported QR format');
  });

  it('throws Unsupported QR format on empty JSON object', () => {
    expect(() => parseQrPayload('{}')).toThrow('Unsupported QR format');
  });

  // ─── Pipe-delimited format ────────────────────────────────────────────────

  it('parses pipe-delimited format id|token', () => {
    const result = parseQrPayload('enrollment-42|proof-token-xyz');
    expect(result).toEqual({
      enrollmentId: 'enrollment-42',
      enrollmentProofToken: 'proof-token-xyz',
      authUrl: undefined,
    });
  });

  it('parses pipe-delimited format when token itself contains a pipe', () => {
    // Tokens with pipes are joined back — only first segment is the id
    const result = parseQrPayload('enrollment-1|part1|part2');
    expect(result.enrollmentId).toBe('enrollment-1');
    expect(result.enrollmentProofToken).toBe('part1|part2');
  });

  it('pipe-delimited result has no authUrl', () => {
    const result = parseQrPayload('id|token');
    expect(result.authUrl).toBeUndefined();
  });

  // ─── Unsupported formats ──────────────────────────────────────────────────

  it('throws Unsupported QR format on a plain string without pipe', () => {
    expect(() => parseQrPayload('justaplainstring')).toThrow('Unsupported QR format');
  });

  it('throws Unsupported QR format on a number string', () => {
    expect(() => parseQrPayload('12345')).toThrow('Unsupported QR format');
  });
});
