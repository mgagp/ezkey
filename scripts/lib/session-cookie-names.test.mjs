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
  parsePropertyLine,
  readPropertyLastWins,
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
      assert.notEqual(name.toUpperCase(), 'JSESSIONID');
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

  it('fails when two modules share a cookie name', () => {
    const result = assertDistinctServletSessionCookieNames(join(FIXTURES, 'red-duplicate'));
    assert.equal(result.ok, false);
    assert.match(result.errors.join('\n'), /duplicate/);
  });
});

describe('GREEN fixtures (#807)', () => {
  it('accepts spaced = and : separators', () => {
    const result = assertDistinctServletSessionCookieNames(join(FIXTURES, 'green-separator'));
    assert.equal(result.ok, true, result.ok ? '' : result.errors.join('\n'));
    const names = result.rows.map((r) => r.cookieName).sort();
    assert.deepEqual(names, ['EZKEY_A_SESSION', 'EZKEY_B_SESSION']);
  });

  it('profile overlay last-wins over base JSESSIONID', () => {
    const result = assertDistinctServletSessionCookieNames(join(FIXTURES, 'green-profile-last'));
    assert.equal(result.ok, true, result.ok ? '' : result.errors.join('\n'));
    const byModule = Object.fromEntries(result.rows.map((r) => [r.module, r]));
    assert.equal(byModule['mod-a'].cookieName, 'EZKEY_A_SESSION');
    assert.match(byModule['mod-a'].propertiesPath, /application-docker\.properties$/);
  });

  it('same-file later assignment wins over earlier JSESSIONID', () => {
    const result = assertDistinctServletSessionCookieNames(
      join(FIXTURES, 'green-last-wins-file'),
    );
    assert.equal(result.ok, true, result.ok ? '' : result.errors.join('\n'));
    const byModule = Object.fromEntries(result.rows.map((r) => [r.module, r]));
    assert.equal(byModule['mod-a'].cookieName, 'EZKEY_A_SESSION');
  });
});
