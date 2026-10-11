/**
 * Unit tests for quality-gate secret detection / redaction.
 * Run: node --test scripts/lib/quality-gate-secrets.test.mjs
 */
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  assertAllowlistRegexAnchored,
  assertKnownAllowlistPhase,
  assertKnownAllowlistRequires,
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
    ['Authorization: Bearer xyzTOKEN9', 'bearer', 'RED', 'xyzTOKEN9'],
    [`token=${TOKEN32}`, 'kv-secret', 'RED', TOKEN32],
    ['Authorization: Basic dXNlcjpwYXNz', 'basic', 'RED', 'dXNlcjpwYXNz'],
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

describe('Basic header form (case-sensitive)', () => {
  it('redacts Authorization: Basic <b64>', () => {
    const line = 'Authorization: Basic dXNlcjpwYXNz';
    assert.equal(detectSecrets(line).severity, 'RED');
    assert.ok(detectSecrets(line).kinds.includes('basic'));
    assert.equal(sanitize(line).includes('dXNlcjpwYXNz'), false);
  });

  it('does not flag prose basic / Basic authentication (Patrick + Christophe)', () => {
    assert.equal(detectSecrets('Using basic configuration').kinds.length, 0);
    assert.equal(
      detectSecrets('HTTP Basic authentication is not configured').kinds.length,
      0,
    );
    assert.equal(detectSecrets('Basic authentication enabled').kinds.length, 0);
  });

  it('does not treat lowercase authorization: Basic as header Basic', () => {
    const r = detectSecrets('authorization: Basic dXNlcjpwYXNz');
    assert.equal(r.kinds.includes('basic'), false);
  });

  it('does not treat HTTP status code=200 as a secret', () => {
    assert.equal(detectSecrets('response code=200').kinds.length, 0);
    assert.equal(detectSecrets('status code: 404').kinds.length, 0);
    assert.equal(detectSecrets('code=200').kinds.length, 0);
  });

  it('does not treat #751 challenge: returning prose as a secret', () => {
    const line =
      '📋 Passwordless with challenge: returning auth attempt info (ID: 42)';
    assert.equal(detectSecrets(line).kinds.length, 0);
    // Still catches real challenge codes.
    assert.equal(detectSecrets('challenge: 42').severity, 'RED');
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

describe('TOKEN / Java class names / UUID', () => {
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

  it('does not score canonical UUID as opaque-token', () => {
    const uuid = '550e8400-e29b-41d4-a716-446655440000';
    const line = `authAttemptId=${uuid} processed`;
    const r = detectSecrets(line);
    assert.equal(r.kinds.includes('opaque-token'), false, `kinds=${r.kinds}`);
    assert.match(sanitize(line), /<UUID>/);
    assert.equal(sanitize(line).includes(uuid), false);
  });

  it('does not score jar path stems as opaque-token', () => {
    const lines = [
      'Running from: /tmp/jetty-9f3a2b1c0d8e7f6a5b4c.jar',
      'location=/tmp/root/winstone/jenkins-abc123def4567890abcd.jar/WEB-INF/classes/log4j2.xml',
      'at org.springframework.data.repository.core.support.RepositoryMethodInvoker.doInvoke(RepositoryMethodInvoker.java:169) ~[spring-data-commons-4.1.1.jar!/:4.1.1]',
    ];
    for (const line of lines) {
      const r = detectSecrets(line);
      assert.equal(
        r.kinds.includes('opaque-token'),
        false,
        `opaque on jar line kinds=${r.kinds} line=${line.slice(0, 80)}`,
      );
      assert.equal(sanitize(line).includes('<TOKEN>'), false, line.slice(0, 80));
    }
  });

  it('does not score git: hex SHA as opaque-token', () => {
    const line =
      'jetty-12.1.8; built: 2026-04-01T01:09:18.991Z; git: a1b2c3d4e5f6789012345678abcdef01; jvm 25.0.1';
    const r = detectSecrets(line);
    assert.equal(r.kinds.includes('opaque-token'), false, `kinds=${r.kinds}`);
    assert.match(sanitize(line), /git: a1b2c3d4e5f6789012345678abcdef01/);
  });

  it('does not score uname arch triples as opaque-token', () => {
    const line =
      'LOG:  starting PostgreSQL 18.6 on x86_64-pc-linux-musl, compiled by gcc (Alpine 15.2.0)';
    const r = detectSecrets(line);
    assert.equal(r.kinds.includes('opaque-token'), false, `kinds=${r.kinds}`);
    assert.match(sanitize(line), /x86_64-pc-linux-musl/);
  });

  it('still scores real opaque secrets after jar/git/arch negatives', () => {
    const line = 'proof=AbCdEfGhIjKlMnOpQr123456 beside /tmp/leave-me-alone.jar';
    const r = detectSecrets(line);
    assert.equal(r.kinds.includes('opaque-token'), true, `kinds=${r.kinds}`);
    assert.match(sanitize(line), /proof=<TOKEN>/);
    assert.match(sanitize(line), /leave-me-alone\.jar/);
  });

  it('scores legit: <hex32> as opaque-token (git: word-boundary only)', () => {
    const hex = 'a1b2c3d4e5f6789012345678abcdef01';
    const line = `legit: ${hex}`;
    const r = detectSecrets(line);
    assert.equal(r.kinds.includes('opaque-token'), true, `kinds=${r.kinds}`);
    assert.equal(r.severity, 'RED');
  });

  it('scores x86_64-<opaque> as opaque-token (arch triple tightened)', () => {
    const line = 'arch=x86_64-Zk3p9QmR7vN2wXyL4bHt leaked';
    const r = detectSecrets(line);
    assert.equal(r.kinds.includes('opaque-token'), true, `kinds=${r.kinds}`);
    assert.equal(r.severity, 'RED');
  });
});

describe('admin username opaque → AMBER; keyset-backup only', () => {
  // Test shape: ^[a-z][a-z0-9-]*-(?:[0-9a-f]{8,20}|\d{10,13})$, ≤32, ≥20 for opaque
  const ADMIN = 'admin-a1b2c3d4e5f67890';

  it('scores for/by/tenant admin: and successful for: as opaque-username AMBER', () => {
    const lines = [
      `Passwordless auth (challenge flow) successful for admin: ${ADMIN} after 0s`,
      `Creating integration by admin: ${ADMIN} (type: TENANT_ADMIN)`,
      `Creating tenant admin: ${ADMIN} for tenant 24`,
      `Authentication successful for: ${ADMIN}`,
    ];
    for (const line of lines) {
      const r = detectSecrets(line);
      assert.equal(r.kinds.includes('opaque-username'), true, `kinds=${r.kinds} line=${line}`);
      assert.equal(r.kinds.includes('opaque-token'), false, `no RED opaque: ${line}`);
      assert.equal(r.severity, 'AMBER', line);
      assert.match(sanitize(line), /<USER>/);
    }
  });

  it('keeps for admin: <random 40-char token> RED (adversarial)', () => {
    const tok = 'a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8s9T0'; // 40 mixed
    const line = `Passwordless auth successful for admin: ${tok} after 0s`;
    const r = detectSecrets(line);
    assert.equal(r.kinds.includes('opaque-token'), true, `kinds=${r.kinds}`);
    assert.equal(r.severity, 'RED');
    assert.equal(r.kinds.includes('opaque-username'), false);
  });

  it('ignores only keyset-backup-<ts>.json.encrypted', () => {
    const line =
      'Set file permissions to 600: /etc/ezkey/keysets/keyset-backup-20261011T014310Z.json.encrypted';
    const r = detectSecrets(line);
    assert.equal(r.kinds.includes('opaque-token'), false, `kinds=${r.kinds}`);
    assert.match(sanitize(line), /keyset-backup-20261011T014310Z\.json\.encrypted/);
  });

  it('keeps <token>.json.encrypted / .pem / .key RED (adversarial)', () => {
    const tok = 'AbCdEfGhIjKlMnOpQr123456';
    for (const ext of ['.json.encrypted', '.pem', '.key']) {
      const line = `path=/tmp/${tok}${ext}`;
      const r = detectSecrets(line);
      assert.equal(r.kinds.includes('opaque-token'), true, `kinds=${r.kinds} ext=${ext}`);
      assert.equal(r.severity, 'RED', ext);
    }
  });

  it('keeps proof=<token> for admin: alice RED (secret before username glue)', () => {
    const tok = 'AbCdEfGhIjKlMnOpQr123456';
    const line = `proof=${tok} for admin: alice`;
    const r = detectSecrets(line);
    assert.equal(r.severity, 'RED');
    assert.ok(
      r.kinds.includes('opaque-token') || r.kinds.includes('kv-secret'),
      `kinds=${r.kinds}`,
    );
    assert.equal(sanitize(line).includes(tok), false);
  });

  it('keeps /t/<hex32> and token=<32> RED', () => {
    const hex = '0123456789abcdef0123456789abcdef';
    const tok = 'abcdefghijklmnopqrstuvwxyz012345';
    const pathR = detectSecrets(`/t/${hex}`);
    assert.equal(pathR.kinds.includes('path-token'), true);
    assert.equal(pathR.severity, 'RED');
    const tokR = detectSecrets(`token=${tok}`);
    assert.equal(tokR.kinds.includes('kv-secret'), true);
    assert.equal(tokR.severity, 'RED');
  });
});

describe('PENDING_*_DIAG Sha256Hex and Bearer prose', () => {
  const HEX64 =
    '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';

  it('does not score payloadSha256Utf8Hex=<64 hex> as opaque', () => {
    const line = `PENDING_PAYLOAD_DIAG enrollmentId=1 authAttemptId=2 payloadSha256Utf8Hex=${HEX64}`;
    const r = detectSecrets(line);
    assert.equal(r.kinds.includes('opaque-token'), false, `kinds=${r.kinds}`);
    assert.match(sanitize(line), new RegExp(HEX64));
  });

  it('keeps proofTokenSha256Hex=<40-char non-hex token> RED (adversarial)', () => {
    const tok = 'a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8s9'; // 40 mixed, not 64 hex
    const line = `proofTokenSha256Hex=${tok}`;
    const r = detectSecrets(line);
    assert.equal(r.severity, 'RED', `kinds=${r.kinds}`);
    assert.ok(
      r.kinds.includes('opaque-token') || r.kinds.includes('kv-secret'),
      `kinds=${r.kinds}`,
    );
    assert.equal(sanitize(line).includes(tok), false);
  });

  it('keeps proofToken=<same 64 hex> RED', () => {
    const line = `proofToken=${HEX64}`;
    const r = detectSecrets(line);
    assert.equal(r.kinds.includes('kv-secret'), true, `kinds=${r.kinds}`);
    assert.equal(r.severity, 'RED');
    assert.equal(sanitize(line).includes(HEX64), false);
  });

  it('does not score prose "got Bearer token" / lowercase bearer as bearer RED', () => {
    for (const line of [
      'Invalid token type - expected recovery token, got Bearer token',
      'Invalid token type - expected recovery token, got bearer token',
    ]) {
      const r = detectSecrets(line);
      assert.equal(r.kinds.includes('bearer'), false, `kinds=${r.kinds} line=${line}`);
    }
  });

  it('scores authorization: bearer abc12345 RED (adversarial)', () => {
    const line = 'authorization: bearer abc12345';
    const r = detectSecrets(line);
    assert.equal(r.kinds.includes('bearer'), true, `kinds=${r.kinds}`);
    assert.equal(r.severity, 'RED');
    assert.equal(sanitize(line).includes('abc12345'), false);
  });

  it('still scores Authorization: Bearer <8+ chars> RED', () => {
    const tok = 'abcdefgh';
    const line = `Authorization: Bearer ${tok}`;
    const r = detectSecrets(line);
    assert.equal(r.kinds.includes('bearer'), true, `kinds=${r.kinds}`);
    assert.equal(r.severity, 'RED');
  });

  it('scores challenge response: digits RED (#753)', () => {
    const line = 'Including challenge response: 482913';
    const r = detectSecrets(line);
    assert.equal(r.kinds.includes('challenge-response'), true, `kinds=${r.kinds}`);
    assert.equal(r.severity, 'RED');
    assert.equal(sanitize(line).includes('482913'), false);
  });
});

describe('allowlist regex anchoring', () => {
  it('accepts full ^…$ without ^.* pseudo-anchor', () => {
    assert.doesNotThrow(() =>
      assertAllowlistRegexAnchored('^<TS> ERROR .*ezkey_encryption_key_pkey.*$'),
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

  it('rejects unknown requires= values', () => {
    assert.doesNotThrow(() => assertKnownAllowlistRequires('COMPOSE_RETRY_FIRED'));
    assert.doesNotThrow(() => assertKnownAllowlistRequires(null));
    assert.throws(
      () => assertKnownAllowlistRequires('COMPOSE_RETY_FIRED'),
      /unknown requires/,
    );
    assert.throws(
      () => assertKnownAllowlistRequires('COMPOSE_RETRY_FIRED,TYPO'),
      /unknown requires/,
    );
  });
});

describe('allowlist signature match', () => {
  it('#747 regex matches signature of a full Spring ERROR line', () => {
    const springLine =
      '2026-10-09T20:38:05.123Z ERROR 1 --- [ezkey-admin-api] [           main] o.h.engine.jdbc.spi.SqlExceptionHelper   : ERROR: duplicate key value violates unique constraint "ezkey_encryption_key_pkey"';
    const sig = signature(springLine);
    assert.match(sig, /ezkey_encryption_key_pkey/);
    assert.match(sig, /^<TS> ERROR /);
    const re = /^<TS> ERROR .*ezkey_encryption_key_pkey.*$/;
    assert.ok(re.test(sig), `sig=${sig}`);
    // Bare ERROR: line is NOT what the pipeline sees — document the footgun.
    const bare =
      'ERROR: duplicate key value violates unique constraint "ezkey_encryption_key_pkey"';
    assert.equal(re.test(signature(bare)), false);
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

  /** Maintainer ruling: emission window = clean-start only. */
  const allowlist = [
    {
      service: 'ezkey-admin-api',
      phases: ['clean-start'],
      re: /^o\.e\.admin\.service\.AdminBootstrapService .*Enrollment Proof Token=.*$/,
      kind: 'secret-channel',
      hygieneBad: false,
    },
  ];

  const dockerLineCleanStart =
    '2026-10-09T20:38:19.219626050Z 2026-10-09T20:38:19.219Z  WARN 1 --- [ezkey-admin-api] [           main] o.e.admin.service.AdminBootstrapService  :    Enrollment Proof Token: f7zMmUzbJSV1WPjXD_jLuoDNucM559Qr7MWIo_MC0yo.0KvCfXqW9ydHGisXjxMz2w';
  const dockerLinePostClean =
    '2026-10-09T20:39:06.000000000Z 2026-10-09T20:39:06.000Z  WARN 1 --- [ezkey-admin-api] [           main] o.e.admin.service.AdminBootstrapService  :    Enrollment Proof Token: f7zMmUzbJSV1WPjXD_jLuoDNucM559Qr7MWIo_MC0yo.0KvCfXqW9ydHGisXjxMz2w';
  const dockerLineOutside =
    '2026-10-09T20:45:00.000000000Z 2026-10-09T20:45:00.000Z  WARN 1 --- [ezkey-admin-api] [           main] o.e.admin.service.AdminBootstrapService  :    Enrollment Proof Token: f7zMmUzbJSV1WPjXD_jLuoDNucM559Qr7MWIo_MC0yo.0KvCfXqW9ydHGisXjxMz2w';

  it('parses docker --timestamps prefix', () => {
    const { body, emittedAt } = splitDockerLogLine(dockerLineCleanStart);
    assert.match(body, /Enrollment Proof Token/);
    assert.equal(emittedAt.toISOString(), '2026-10-09T20:38:19.219Z');
    assert.equal(parseLogTimestamp(dockerLineCleanStart).toISOString(), '2026-10-09T20:38:19.219Z');
  });

  it('isEmittedInPhaseWindows true inside clean-start', () => {
    const ts = parseLogTimestamp(dockerLineCleanStart);
    assert.equal(isEmittedInPhaseWindows(ts, ['clean-start'], windows), true);
    assert.equal(isEmittedInPhaseWindows(ts, ['health'], windows), false);
  });

  it('allowlists bootstrap proof token emitted in clean-start when scanned in post-clean-health', () => {
    const { body, emittedAt } = splitDockerLogLine(dockerLineCleanStart);
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
      'clean-start emission must stay allowlisted when scanned in post-clean-health/health',
    );
  });

  it('keeps same bootstrap proof token RED when emitted in post-clean-health', () => {
    const { body, emittedAt } = splitDockerLogLine(dockerLinePostClean);
    assert.equal(
      isEmittedInPhaseWindows(emittedAt, ['post-clean-health'], windows),
      true,
    );
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
      'post-clean-health emission is outside #750 clean-start-only window → RED',
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
