/**
 * Cross-cutting guard: every spring-boot-starter-webmvc module sets a unique
 * non-default server.servlet.session.cookie.name (#799 / #800).
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
} from './session-cookie-names.mjs';

const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

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
