/**
 * Cross-cutting guard: every spring-boot-starter-webmvc module sets a unique
 * non-default server.servlet.session.cookie.name (#799 / #800 / #807).
 *
 * Run: node --test scripts/lib/session-cookie-names.test.mjs
 */
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  assertDistinctServletSessionCookieNames,
  collectServletSessionCookieNames,
  isDefaultServletSessionCookieName,
  joinContinuedLines,
  parsePropertyLine,
  readPropertyLastWins,
  unescapePropertyValue,
} from './session-cookie-names.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(HERE, '..', '..');
const FIXTURES = join(HERE, 'fixtures', 'session-cookie-names');

describe('servlet session cookie names (#799)', () => {
  it('every webmvc module sets a non-default unique cookie name', () => {
    const result = assertDistinctServletSessionCookieNames(REPO_ROOT);
    if (!result.ok) {
      assert.fail(result.errors.join('\n'));
    }
    const names = result.rows.map((r) => r.cookieName);
    assert.equal(new Set(names).size, names.length);
    for (const name of names) {
      assert.equal(isDefaultServletSessionCookieName(name), false);
      assert.match(name, /^EZKEY_[A-Z0-9_]+SESSION$/);
    }
  });

  it('covers Acme, demo-device, and API config/ properties', () => {
    const rows = collectServletSessionCookieNames(REPO_ROOT);
    const byModule = Object.fromEntries(rows.map((r) => [r.module, r]));

    assert.equal(byModule['ezkey-demo-app-acme']?.cookieName, 'EZKEY_ACME_SESSION');
    assert.equal(
      byModule['ezkey-demo-app-acme']?.propertiesPath,
      'ezkey-demo-app-acme/src/main/resources/application.properties',
    );

    assert.equal(byModule['ezkey-demo-device']?.cookieName, 'EZKEY_DEVICE_SESSION');
    assert.equal(
      byModule['ezkey-demo-device']?.propertiesPath,
      'ezkey-demo-device/src/main/resources/application.properties',
    );

    assert.equal(byModule['ezkey-admin-api']?.cookieName, 'EZKEY_ADMIN_API_SESSION');
    assert.equal(
      byModule['ezkey-admin-api']?.propertiesPath,
      'ezkey-admin-api/config/application.properties',
    );

    assert.equal(byModule['ezkey-auth-api']?.cookieName, 'EZKEY_AUTH_API_SESSION');
    assert.equal(byModule['ezkey-integration-api']?.cookieName, 'EZKEY_INTEGRATION_API_SESSION');
    assert.equal(byModule['ezkey-crypto-api']?.cookieName, 'EZKEY_CRYPTO_API_SESSION');

    assert.equal(rows.length, 6);
  });
});

describe('property line parsing (#807)', () => {
  it('accepts = and : with optional spaces', () => {
    assert.deepEqual(parsePropertyLine('server.servlet.session.cookie.name=EZKEY_A_SESSION'), {
      key: 'server.servlet.session.cookie.name',
      value: 'EZKEY_A_SESSION',
    });
    assert.deepEqual(parsePropertyLine('server.servlet.session.cookie.name = EZKEY_A_SESSION'), {
      key: 'server.servlet.session.cookie.name',
      value: 'EZKEY_A_SESSION',
    });
    assert.deepEqual(parsePropertyLine('server.servlet.session.cookie.name: EZKEY_A_SESSION'), {
      key: 'server.servlet.session.cookie.name',
      value: 'EZKEY_A_SESSION',
    });
  });

  it('last assignment in a file wins', () => {
    const file = join(
      FIXTURES,
      'green-last-wins-file',
      'mod-a',
      'config',
      'application.properties',
    );
    assert.equal(
      readPropertyLastWins(file, 'server.servlet.session.cookie.name'),
      'EZKEY_A_SESSION',
    );
  });

  it('unescapes \\: and \\= in values (#807 m5)', () => {
    assert.equal(unescapePropertyValue('prefix\\:suffix'), 'prefix:suffix');
    assert.equal(unescapePropertyValue('a\\=b'), 'a=b');
    assert.deepEqual(parsePropertyLine('other.key=prefix\\:suffix'), {
      key: 'other.key',
      value: 'prefix:suffix',
    });
  });

  it('joins backslash line continuations (#807 m5)', () => {
    assert.deepEqual(joinContinuedLines(['server.servlet.session.cookie.name=EZKEY_\\', 'A_SESSION']), [
      'server.servlet.session.cookie.name=EZKEY_A_SESSION',
    ]);
    const file = join(FIXTURES, 'parse-escapes', 'application.properties');
    assert.equal(
      readPropertyLastWins(file, 'server.servlet.session.cookie.name'),
      'EZKEY_A_SESSION',
    );
    assert.equal(readPropertyLastWins(file, 'other.key'), 'prefix:suffix');
  });

  it('strips leading whitespace on continuation lines like Java (#807 nit)', () => {
    assert.deepEqual(
      joinContinuedLines(['server.servlet.session.cookie.name=EZKEY_\\', '   A_SESSION']),
      ['server.servlet.session.cookie.name=EZKEY_A_SESSION'],
    );
    const file = join(FIXTURES, 'parse-continuation-ws', 'application.properties');
    assert.equal(
      readPropertyLastWins(file, 'server.servlet.session.cookie.name'),
      'EZKEY_A_SESSION',
    );
  });

  it('treats JSESSIONID and __Host-JSESSIONID as defaults (#807 nit)', () => {
    assert.equal(isDefaultServletSessionCookieName('JSESSIONID'), true);
    assert.equal(isDefaultServletSessionCookieName('jsessionid'), true);
    assert.equal(isDefaultServletSessionCookieName('__Host-JSESSIONID'), true);
    assert.equal(isDefaultServletSessionCookieName('__host-jsessionid'), true);
    assert.equal(isDefaultServletSessionCookieName('__Host-EZKEY_A_SESSION'), false);
    assert.equal(isDefaultServletSessionCookieName('EZKEY_A_SESSION'), false);
  });
});

describe('RED fixtures (#807)', () => {
  it('fails when cookie property is missing', () => {
    assert.throws(
      () => collectServletSessionCookieNames(join(FIXTURES, 'red-missing')),
      /missing server\.servlet\.session\.cookie\.name/,
    );
  });

  it('fails when cookie name is JSESSIONID', () => {
    const result = assertDistinctServletSessionCookieNames(join(FIXTURES, 'red-jsessionid'));
    assert.equal(result.ok, false);
    assert.match(result.errors.join('\n'), /JSESSIONID/);
  });

  it('fails when cookie name is __Host-JSESSIONID', () => {
    const result = assertDistinctServletSessionCookieNames(
      join(FIXTURES, 'red-host-jsessionid'),
    );
    assert.equal(result.ok, false);
    assert.match(result.errors.join('\n'), /__Host-JSESSIONID/i);
  });

  it('fails when two modules share a cookie name', () => {
    const result = assertDistinctServletSessionCookieNames(join(FIXTURES, 'red-duplicate'));
    assert.equal(result.ok, false);
    assert.match(result.errors.join('\n'), /duplicate/);
  });

  it('fails when a profile reintroduces JSESSIONID (#807 m4)', () => {
    const result = assertDistinctServletSessionCookieNames(
      join(FIXTURES, 'red-profile-jsessionid'),
    );
    assert.equal(result.ok, false);
    assert.match(result.errors.join('\n'), /JSESSIONID/);
    assert.match(result.errors.join('\n'), /profile=docker/);
  });
});

describe('GREEN fixtures (#807)', () => {
  it('accepts spaced = and : separators', () => {
    const result = assertDistinctServletSessionCookieNames(join(FIXTURES, 'green-separator'));
    assert.equal(result.ok, true, result.ok ? '' : result.errors.join('\n'));
    const names = result.rows.map((r) => r.cookieName).sort();
    assert.deepEqual(names, ['EZKEY_A_SESSION', 'EZKEY_B_SESSION']);
  });

  it('checks each profile separately without stacking (#807 m4)', () => {
    const result = assertDistinctServletSessionCookieNames(join(FIXTURES, 'green-profile-last'));
    assert.equal(result.ok, true, result.ok ? '' : result.errors.join('\n'));
    const byModule = Object.fromEntries(result.rows.map((r) => [r.module, r]));
    assert.equal(byModule['mod-a'].cookieName, 'EZKEY_A_SESSION');
    assert.match(byModule['mod-a'].propertiesPath, /application\.properties$/);
  });

  it('same-file later assignment wins over earlier JSESSIONID', () => {
    const result = assertDistinctServletSessionCookieNames(
      join(FIXTURES, 'green-last-wins-file'),
    );
    assert.equal(result.ok, true, result.ok ? '' : result.errors.join('\n'));
    const byModule = Object.fromEntries(result.rows.map((r) => [r.module, r]));
    assert.equal(byModule['mod-a'].cookieName, 'EZKEY_A_SESSION');
  });

  it('accepts __Host- cookie name prefix (#807 m5)', () => {
    const result = assertDistinctServletSessionCookieNames(join(FIXTURES, 'green-host-prefix'));
    assert.equal(result.ok, true, result.ok ? '' : result.errors.join('\n'));
    const names = result.rows.map((r) => r.cookieName).sort();
    assert.deepEqual(names, ['__Host-EZKEY_A_SESSION', '__Host-EZKEY_B_SESSION']);
  });
});
