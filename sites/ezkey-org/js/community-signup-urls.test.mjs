/**
 * Runtime proof for community-signup URL allowlisting (Lot B / CodeQL #114 #115).
 * Run: node --test sites/ezkey-org/js/community-signup-urls.test.mjs
 */
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULT_API_URL,
  resolveEvaluatorSignupApiUrl,
  resolveNavigationUrl,
  isAllowedHttpUrl,
  parseAllowedHttpUrl,
  API_HOST_ALLOWLIST,
  LINK_HOST_ALLOWLIST,
} from './community-signup-urls.mjs';

describe('resolveEvaluatorSignupApiUrl (?api= allowlist)', () => {
  it('keeps the default when ?api= points at a foreign host', () => {
    assert.equal(resolveEvaluatorSignupApiUrl('https://evil.example'), DEFAULT_API_URL);
    assert.equal(resolveEvaluatorSignupApiUrl('https://evil.example/'), DEFAULT_API_URL);
  });

  it('honors allowlisted Admin API bases (incl. localhost http) as normalized URLs', () => {
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://admin-api.ezkey.online'),
      'https://admin-api.ezkey.online/api/v1/public/evaluator-signup'
    );
    assert.equal(
      resolveEvaluatorSignupApiUrl('http://localhost:9080/'),
      'http://localhost:9080/api/v1/public/evaluator-signup'
    );
    assert.equal(
      resolveEvaluatorSignupApiUrl('http://127.0.0.1:9080'),
      'http://127.0.0.1:9080/api/v1/public/evaluator-signup'
    );
  });

  it('rejects javascript: and non-http schemes for ?api=', () => {
    assert.equal(resolveEvaluatorSignupApiUrl('javascript:alert(1)'), DEFAULT_API_URL);
    assert.equal(resolveEvaluatorSignupApiUrl('data:text/html,hi'), DEFAULT_API_URL);
  });
});

describe('resolveNavigationUrl (adminUiUrl / guidedTourUrl)', () => {
  const defaultAdmin = 'https://admin-ui.ezkey.online';
  const defaultTour = '/community-guided-tour.html';

  it('ignores javascript: adminUiUrl and keeps the default', () => {
    assert.equal(resolveNavigationUrl('javascript:alert(1)', defaultAdmin), defaultAdmin);
  });

  it('ignores foreign https hosts', () => {
    assert.equal(
      resolveNavigationUrl('https://evil.example/phish', defaultAdmin),
      defaultAdmin
    );
  });

  it('accepts allowlisted https community hosts as normalized href', () => {
    assert.equal(
      resolveNavigationUrl('https://admin-ui.ezkey.online/login', defaultAdmin),
      'https://admin-ui.ezkey.online/login'
    );
    assert.equal(
      resolveNavigationUrl('https://ezkey.org/community-guided-tour.html', defaultTour),
      'https://ezkey.org/community-guided-tour.html'
    );
  });

  it('allows http only on loopback', () => {
    assert.equal(
      resolveNavigationUrl('http://localhost:4173/', defaultAdmin),
      'http://localhost:4173/'
    );
    assert.equal(resolveNavigationUrl('http://evil.example/', defaultAdmin), defaultAdmin);
  });
});

describe('allowlist tables', () => {
  it('exposes the hard-coded API hosts from the Lot B brief', () => {
    assert.deepEqual([...API_HOST_ALLOWLIST].sort(), [
      '127.0.0.1',
      'admin-api.ezkey.online',
      'localhost',
    ]);
  });

  it('rejects protocol-relative and empty candidates', () => {
    assert.equal(isAllowedHttpUrl('//evil.example', LINK_HOST_ALLOWLIST), false);
    assert.equal(isAllowedHttpUrl('', LINK_HOST_ALLOWLIST), false);
    assert.equal(isAllowedHttpUrl(null, LINK_HOST_ALLOWLIST), false);
  });
});

describe('m1 normalized return + userinfo rejection', () => {
  it('rejects userinfo in both directions (evil@allowlisted and allowlisted@evil)', () => {
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://evil.com@admin-api.ezkey.online/'),
      DEFAULT_API_URL
    );
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://admin-api.ezkey.online@evil.com/'),
      DEFAULT_API_URL
    );
    assert.equal(
      resolveNavigationUrl('https://evil.com@admin-ui.ezkey.online/', 'https://admin-ui.ezkey.online'),
      'https://admin-ui.ezkey.online'
    );
    assert.equal(
      resolveNavigationUrl('https://admin-ui.ezkey.online@evil.com/', 'https://admin-ui.ezkey.online'),
      'https://admin-ui.ezkey.online'
    );
    assert.equal(
      parseAllowedHttpUrl('https://user:pass@admin-api.ezkey.online/', API_HOST_ALLOWLIST),
      null
    );
  });

  it('returns normalized href (lowercase host, no trailing-dot host, no raw casing)', () => {
    assert.equal(
      resolveNavigationUrl(
        'https://ADMIN-UI.EZKEY.ONLINE/Login',
        'https://admin-ui.ezkey.online'
      ),
      'https://admin-ui.ezkey.online/Login'
    );
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://admin-api.ezkey.online./'),
      'https://admin-api.ezkey.online/api/v1/public/evaluator-signup'
    );
  });
});

describe('m3 adversarial allowlist cases', () => {
  const defaultAdmin = 'https://admin-ui.ezkey.online';

  it('rejects allowlisted-looking DNS suffix (admin-api.ezkey.online.evil.com)', () => {
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://admin-api.ezkey.online.evil.com/'),
      DEFAULT_API_URL
    );
    assert.equal(
      isAllowedHttpUrl('https://admin-api.ezkey.online.evil.com/', API_HOST_ALLOWLIST),
      false
    );
  });

  it('rejects when the allowlisted host appears only in a query parameter', () => {
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://evil.example/?api=https://admin-api.ezkey.online'),
      DEFAULT_API_URL
    );
    assert.equal(
      resolveNavigationUrl(
        'https://evil.example/?next=https://admin-ui.ezkey.online',
        defaultAdmin
      ),
      defaultAdmin
    );
  });

  it('accepts uppercase host via normalized hostname', () => {
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://ADMIN-API.EZKEY.ONLINE'),
      'https://admin-api.ezkey.online/api/v1/public/evaluator-signup'
    );
  });

  it('accepts trailing-dot host after normalization', () => {
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://admin-api.ezkey.online.'),
      'https://admin-api.ezkey.online/api/v1/public/evaluator-signup'
    );
  });

  it('rejects tab, NUL, and %0a in the candidate string', () => {
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://admin-api.ezkey.online/\t'),
      DEFAULT_API_URL
    );
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://admin-api.ezkey.online/\u0000'),
      DEFAULT_API_URL
    );
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://admin-api.ezkey.online/%0a'),
      DEFAULT_API_URL
    );
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://admin-api.ezkey.online%0a/'),
      DEFAULT_API_URL
    );
    assert.equal(
      resolveNavigationUrl('https://admin-ui.ezkey.online/\t', defaultAdmin),
      defaultAdmin
    );
  });

  it('rejects vbscript:', () => {
    assert.equal(resolveEvaluatorSignupApiUrl('vbscript:msgbox(1)'), DEFAULT_API_URL);
    assert.equal(resolveNavigationUrl('vbscript:msgbox(1)', defaultAdmin), defaultAdmin);
    assert.equal(isAllowedHttpUrl('vbscript:msgbox(1)', LINK_HOST_ALLOWLIST), false);
  });

  it('normalizes 0x7f000001 to loopback 127.0.0.1 (http allowlisted)', () => {
    // WHATWG URL maps 0x7f000001 → 127.0.0.1; after normalization it is an
    // allowlisted loopback host, returned as the canonical href form.
    assert.equal(
      resolveEvaluatorSignupApiUrl('http://0x7f000001:9080'),
      'http://127.0.0.1:9080/api/v1/public/evaluator-signup'
    );
    assert.equal(
      resolveNavigationUrl('http://0x7f000001:4173/app', defaultAdmin),
      'http://127.0.0.1:4173/app'
    );
  });
});
