/**
 * Unit tests for quality-gate secret detection / redaction.
 * Run: node --test scripts/lib/quality-gate-secrets.test.mjs
 */
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  assertAllowlistRegexAnchored,
  assertKnownAllowlistPhase,
  detectSecrets,
  extractLogger,
  isEmittedInPhaseWindows,
  isMaskedOrNonSecretValue,
  isSecretChannelAllowlisted,
  parseLogTimestamp,
  parsePhaseWindows,
  sanitize,
  signature,
  splitDockerLogLine,
  ID_LABEL_VALUE_RE,
} from './quality-gate-secrets.mjs';

describe('isMaskedOrNonSecretValue', () => {
  it('treats redaction placeholders as masked', () => {
    assert.equal(isMaskedOrNonSecretValue('***REDACTED***'), true);
    assert.equal(isMaskedOrNonSecretValue('[REDACTED]'), true);
    assert.equal(isMaskedOrNonSecretValue('<redacted>'), true);
    assert.equal(isMaskedOrNonSecretValue('****'), true);
  });
  it('treats booleans and anchored ID= labels as non-secret', () => {
    assert.equal(isMaskedOrNonSecretValue('true'), true);
    assert.equal(isMaskedOrNonSecretValue('false'), true);
    assert.equal(isMaskedOrNonSecretValue('ID=1'), true);
    assert.equal(ID_LABEL_VALUE_RE.test('ID=5abc'), false);
    assert.equal(isMaskedOrNonSecretValue('ID=5abc'), false);
  });
  it('treats real values as present', () => {
    assert.equal(isMaskedOrNonSecretValue('ABCD1234'), false);
    assert.equal(isMaskedOrNonSecretValue('46'), false);
  });
});

describe('Christophe replay inputs', () => {
  const HEX32 = '0123456789abcdef0123456789abcdef';
  const TOKEN32 = 'abcdefghijklmnopqrstuvwxyz012345';
  /** @type {Array<[string, string, 'RED'|'AMBER', string]>} line, kind, severity, rawMustVanish */
  const cases = [
    ['challenge: 42', 'kv-secret', 'RED', '42'],
    ['accessCode=XyZ987', 'kv-secret', 'RED', 'XyZ987'],
    ['2001:db8::1', 'ip', 'AMBER', '2001:db8::1'],
    [`/t/${HEX32}`, 'path-token', 'RED', HEX32],
    ['Authorization: Bearer xyz', 'bearer', 'RED', 'xyz'],
    [`token=${TOKEN32}`, 'kv-secret', 'RED', TOKEN32],
    ['authorization: Basic dXNlcjpwYXNz', 'basic', 'RED', 'dXNlcjpwYXNz'],
    ['password="a b"', 'kv-secret', 'RED', 'a b'],
    ['admin@ezkey.local', 'email', 'AMBER', 'admin@ezkey.local'],
    ['connected from 192.168.1.42', 'ip', 'AMBER', '192.168.1.42'],
    [
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.sig',
      'jwt',
      'RED',
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9',
    ],
    ['{"password":"hunter2secret"}', 'json-secret', 'RED', 'hunter2secret'],
  ];
  for (const [line, kind, severity, raw] of cases) {
    it(`detects ${kind} (${severity}) and clears raw: ${line.slice(0, 48)}`, () => {
      const r = detectSecrets(line);
      assert.ok(r.kinds.length, `expected kinds for: ${line}`);
      assert.ok(r.kinds.includes(kind), `expected ${kind} in ${r.kinds} for: ${line}`);
      assert.equal(r.severity, severity);
      const redacted = sanitize(line);
      assert.equal(
        redacted.includes(raw),
        false,
        `sanitize must clear ${JSON.stringify(raw)}; got ${JSON.stringify(redacted)}`,
      );
    });
  }

  it('redacts passphrase / privateKey / otp / pin / JSESSIONID', () => {
    const samples = [
      ['passphrase=s3cret-phrase', 's3cret-phrase'],
      ['privateKey=MEECAQAwEwYHKoZIzj0CAQY', 'MEECAQAwEwYHKoZIzj0CAQY'],
      ['otp=123456', '123456'],
      ['pin=9876', '9876'],
      ['Cookie: JSESSIONID=ABC123session', 'ABC123session'],
    ];
    for (const [line, raw] of samples) {
      const r = detectSecrets(line);
      assert.equal(r.severity, 'RED', line);
      assert.equal(sanitize(line).includes(raw), false, line);
    }
  });

  it('username / user are AMBER (not RED)', () => {
    const r1 = detectSecrets('username=alice');
    assert.equal(r1.severity, 'AMBER');
    assert.ok(r1.kinds.includes('kv-username'));
    assert.equal(sanitize('username=alice').includes('alice'), false);
    const r2 = detectSecrets('{"user":"bob"}');
    assert.equal(r2.severity, 'AMBER');
    assert.ok(r2.kinds.includes('json-username'));
  });
});

describe('detectSecrets product cases', () => {
  it('ignores challenge=***REDACTED*** (already masked)', () => {
    const r = detectSecrets('Passwordless with challenge=***REDACTED*** for user');
    assert.equal(r.kinds.length, 0);
  });

  it('hits challenge=ABCD1234 (unmasked)', () => {
    const r = detectSecrets('Passwordless with challenge=ABCD1234 for user');
    assert.equal(r.severity, 'RED');
  });

  it('ignores challenge: true boolean', () => {
    const r = detectSecrets(
      '🔐 Passwordless auth attempt created (ID: 1, challenge: true)',
    );
    assert.equal(r.kinds.length, 0, `unexpected kinds: ${r.kinds}`);
  });

  it('hits Enrollment Challenge Code: 123456', () => {
    const r = detectSecrets('   Enrollment Challenge Code: 123456');
    assert.equal(r.severity, 'RED');
    assert.ok(r.kinds.includes('kv-secret'));
  });

  it('hits recovery code shape', () => {
    const r = detectSecrets('   1. 3711-0244-2145-1666-3366-1916-2965-8688');
    assert.equal(r.severity, 'RED');
    assert.ok(r.kinds.includes('recovery-code'));
  });

  it('hits --enrollment-proof-token CLI', () => {
    const r = detectSecrets(
      '       --enrollment-proof-token "f7zMmUzbJSV1WPjXD_jLuoDNucM559Qr7MWIo_MC0yo.0KvCfXqW9ydHGisXjxMz2w"',
    );
    assert.equal(r.severity, 'RED');
  });

  it('Bearer before kv — token not left in clear', () => {
    const line = 'Authorization: Bearer abcdefghijklmnopqrstuvwxyz012345';
    const out = sanitize(line);
    assert.match(out, /Bearer \*\*\*REDACTED\*\*\*/);
    assert.equal(out.includes('abcdefghijklmnopqrstuvwxyz012345'), false);
  });
});

describe('TOKEN / Java class names', () => {
  it('does NOT tokenize DataIntegrityViolationException', () => {
    const line =
      'org.springframework.dao.DataIntegrityViolationException: could not execute';
    const out = sanitize(line);
    assert.match(out, /DataIntegrityViolationException/);
    assert.equal(out.includes('<TOKEN>'), false);
  });

  it('does tokenize mixed alphanumeric opaque secrets ≥20', () => {
    const line = 'opaque=AbCdEfGhIjKlMnOpQr123456';
    const out = sanitize(line);
    assert.match(out, /<TOKEN>|REDACTED/);
  });
});

describe('allowlist regex anchoring', () => {
  it('accepts full ^…$ without ^.* pseudo-anchor', () => {
    assert.doesNotThrow(() =>
      assertAllowlistRegexAnchored('^ERROR:.*ezkey_encryption_key_pkey.*$'),
    );
  });
  it('rejects missing $', () => {
    assert.throws(() => assertAllowlistRegexAnchored('^ERROR:.*pkey'), /fully anchored/);
  });
  it('rejects ^.* pseudo-anchors', () => {
    assert.throws(
      () => assertAllowlistRegexAnchored('^.*ezkey_encryption_key_pkey.*$'),
      /pseudo-anchor/,
    );
  });

  it('rejects unknown phase names', () => {
    assert.doesNotThrow(() => assertKnownAllowlistPhase('clean-start'));
    assert.doesNotThrow(() => assertKnownAllowlistPhase('*'));
    assert.throws(() => assertKnownAllowlistPhase('bootstrap'), /unknown phase/);
    assert.throws(() => assertKnownAllowlistPhase('post-clean'), /unknown phase/);
  });
});

describe('allowlist signature match', () => {
  it('ezkey_encryption_key_pkey survives sanitize (no digit → not <TOKEN>)', () => {
    const line =
      'ERROR: duplicate key value violates unique constraint "ezkey_encryption_key_pkey"';
    const sig = signature(line);
    assert.match(sig, /ezkey_encryption_key_pkey/);
    assert.ok(new RegExp('^ERROR:.*ezkey_encryption_key_pkey.*$').test(sig));
  });

  it('bootstrap proof-token subject matches logger-anchored allowlist regex', () => {
    const line =
      '2026-10-09T19:58:56.790Z  WARN 1 --- [ezkey-admin-api] [           main] o.e.admin.service.AdminBootstrapService  :    Enrollment Proof Token: f7zMmUzbJSV1WPjXD_jLuoDNucM559Qr7MWIo_MC0yo.0KvCfXqW9ydHGisXjxMz2w';
    const logger = extractLogger(line);
    const subject = `${logger} ${signature(line)}`;
    const re =
      /^o\.e\.admin\.service\.AdminBootstrapService .*Enrollment Proof Token=.*$/;
    assert.ok(re.test(subject), `subject=${subject}`);
  });

  it('signature collapses thread names for dedup', () => {
    const a =
      '2026-10-09T20:39:30.927Z  INFO 1 --- [ezkey-admin-api] [nio-9080-exec-9] o.ezkey.admin.service.AdminAuthService   : Passwordless with challenge=ABCD1234';
    const b =
      '2026-10-09T20:39:31.001Z  INFO 1 --- [ezkey-admin-api] [nio-9080-exec-2] o.ezkey.admin.service.AdminAuthService   : Passwordless with challenge=ABCD1234';
    const truncated =
      '2026-10-09T20:39:20.055Z  INFO 1 --- [ezkey-admin-api] [io-9080-exec-10] o.ezkey.admin.service.AdminAuthService   : Passwordless with challenge=ABCD1234';
    assert.equal(signature(a), signature(b));
    assert.equal(signature(a), signature(truncated));
  });
});

describe('emission-time allowlist windows (#750)', () => {
  const windows = parsePhaseWindows(
    [
      'phase\tstart_iso\tend_iso',
      'clean-start\t2026-10-09T20:37:37Z\t2026-10-09T20:39:05Z',
      'post-clean-health\t2026-10-09T20:39:05Z\t2026-10-09T20:39:07Z',
      'health\t2026-10-09T20:48:47Z\t2026-10-09T20:48:49Z',
    ].join('\n'),
  );

  const allowlist = [
    {
      service: 'ezkey-admin-api',
      phases: ['clean-start', 'post-clean-health'],
      re: /^o\.e\.admin\.service\.AdminBootstrapService .*Enrollment Proof Token=.*$/,
      kind: 'secret-channel',
      hygieneBad: false,
    },
  ];

  const dockerLineInWindow =
    '2026-10-09T20:38:19.219626050Z 2026-10-09T20:38:19.219Z  WARN 1 --- [ezkey-admin-api] [           main] o.e.admin.service.AdminBootstrapService  :    Enrollment Proof Token: f7zMmUzbJSV1WPjXD_jLuoDNucM559Qr7MWIo_MC0yo.0KvCfXqW9ydHGisXjxMz2w';
  const dockerLineOutside =
    '2026-10-09T20:45:00.000000000Z 2026-10-09T20:45:00.000Z  WARN 1 --- [ezkey-admin-api] [           main] o.e.admin.service.AdminBootstrapService  :    Enrollment Proof Token: f7zMmUzbJSV1WPjXD_jLuoDNucM559Qr7MWIo_MC0yo.0KvCfXqW9ydHGisXjxMz2w';

  it('parses docker --timestamps prefix', () => {
    const { body, emittedAt } = splitDockerLogLine(dockerLineInWindow);
    assert.match(body, /Enrollment Proof Token/);
    assert.equal(emittedAt.toISOString(), '2026-10-09T20:38:19.219Z');
    assert.equal(parseLogTimestamp(dockerLineInWindow).toISOString(), '2026-10-09T20:38:19.219Z');
  });

  it('isEmittedInPhaseWindows true inside clean-start', () => {
    const ts = parseLogTimestamp(dockerLineInWindow);
    assert.equal(isEmittedInPhaseWindows(ts, ['clean-start'], windows), true);
    assert.equal(isEmittedInPhaseWindows(ts, ['health'], windows), false);
  });

  it('allowlists bootstrap proof token emitted in clean-start window (even if scan is health)', () => {
    const { body, emittedAt } = splitDockerLogLine(dockerLineInWindow);
    const hit = {
      kinds: ['kv-secret'],
      logger: extractLogger(body),
      service: 'ezkey-admin-api-1',
      preview: sanitize(body),
      emittedAt,
    };
    assert.ok(/AdminBootstrapService/i.test(hit.logger));
    assert.equal(detectSecrets(body).severity, 'RED');
    assert.equal(
      isSecretChannelAllowlisted(hit, allowlist, windows),
      true,
      'in-window emission must stay allowlisted regardless of scan phase',
    );
  });

  it('keeps same bootstrap proof token RED when emitted outside allowlisted windows', () => {
    const { body, emittedAt } = splitDockerLogLine(dockerLineOutside);
    const hit = {
      kinds: ['kv-secret'],
      logger: extractLogger(body),
      service: 'ezkey-admin-api-1',
      preview: sanitize(body),
      emittedAt,
    };
    assert.equal(
      isSecretChannelAllowlisted(hit, allowlist, windows),
      false,
      'out-of-window emission (e.g. later restart) must stay RED',
    );
  });

  it('never allowlists recovery codes even inside clean-start window', () => {
    const line =
      '2026-10-09T20:38:19.273000000Z 2026-10-09T20:38:19.273Z  WARN 1 --- [ezkey-admin-api] [           main] o.e.admin.service.AdminBootstrapService  :    1. 3711-0244-2145-1666-3366-1916-2965-8688';
    const { body, emittedAt } = splitDockerLogLine(line);
    const hit = {
      kinds: ['recovery-code'],
      logger: extractLogger(body),
      service: 'ezkey-admin-api-1',
      preview: sanitize(body),
      emittedAt,
    };
    assert.equal(isSecretChannelAllowlisted(hit, allowlist, windows), false);
  });
});
