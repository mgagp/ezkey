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
  API_HOST_ALLOWLIST,
  LINK_HOST_ALLOWLIST,
} from './community-signup-urls.mjs';

describe('resolveEvaluatorSignupApiUrl (?api= allowlist)', () => {
  it('keeps the default when ?api= points at a foreign host', () => {
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://evil.example'),
      DEFAULT_API_URL
    );
    assert.equal(
      resolveEvaluatorSignupApiUrl('https://evil.example/'),
      DEFAULT_API_URL
    );
  });

  it('honors allowlisted Admin API bases (incl. localhost http)', () => {
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
    assert.equal(
      resolveNavigationUrl('javascript:alert(1)', defaultAdmin),
      defaultAdmin
    );
  });

  it('ignores foreign https hosts', () => {
    assert.equal(
      resolveNavigationUrl('https://evil.example/phish', defaultAdmin),
      defaultAdmin
    );
  });

  it('accepts allowlisted https community hosts', () => {
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
    assert.equal(
      resolveNavigationUrl('http://evil.example/', defaultAdmin),
      defaultAdmin
    );
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
