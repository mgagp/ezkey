/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * RFC 9457 Problem Details parser and user-facing Auth API error tests.
 */

import {
  AUTH_ATTEMPT_RESPOND_BEFORE_READ_TYPE,
  AUTH_ATTEMPT_STATE_CONFLICT_TYPE,
  INTEGRATION_SIGNING_UNAVAILABLE_TYPE,
  RESOURCE_NOT_FOUND_TYPE,
  catalogKeyForAuthApiProblemType,
  isAuthAttemptStateConflict,
  isEzkeyProblemType,
  parseAuthApiProblemDetail,
  resolveAuthApiUserFacingOutcome,
  userFacingAuthApiError,
} from '../authApiProblem';

const FALLBACK = 'Request failed.';
const STATE_CONFLICT_MSG =
  'This request is no longer active. No action is required.';
const RESPOND_BEFORE_READ_MSG =
  'This request is not ready yet. Tap Try again after the device has claimed it.';
const SIGNING_UNAVAILABLE_MSG =
  'Authentication signing is temporarily unavailable. Try again later.';
const RESOURCE_NOT_FOUND_MSG = 'This request could not be found.';

const CATALOG = {
  fallback: FALLBACK,
  stateConflict: STATE_CONFLICT_MSG,
  respondBeforeRead: RESPOND_BEFORE_READ_MSG,
  integrationSigningUnavailable: SIGNING_UNAVAILABLE_MSG,
  resourceNotFound: RESOURCE_NOT_FOUND_MSG,
};

const CLOUDFLARE_1020_BODY = {
  type: 'https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-1xxx-errors/error-1020/',
  title: 'Error 1020',
  status: 403,
  detail: 'Access denied. Contact the site owner if you believe this is a mistake.',
  cloudflare_error: true,
  retryable: false,
  ray_id: '0123456789abcdef',
  what_you_should_do: 'Do not retry. Contact the site owner.',
};

function httpErrorWithData(
  data: unknown,
  status = 400,
): {response: {status: number; data: unknown}} {
  return {response: {status, data}};
}

describe('parseAuthApiProblemDetail', () => {
  it('parses type, title, detail, and status', () => {
    expect(
      parseAuthApiProblemDetail({
        type: 'https://ezkey.example/problems/enrollment-not-found',
        title: 'Enrollment not found',
        status: 404,
        detail: 'No enrollment matches the given id.',
      }),
    ).toEqual({
      type: 'https://ezkey.example/problems/enrollment-not-found',
      title: 'Enrollment not found',
      status: 404,
      detail: 'No enrollment matches the given id.',
    });
  });

  it('accepts a body that has title without type', () => {
    expect(parseAuthApiProblemDetail({title: 'Bad Request', status: 400})).toEqual({
      title: 'Bad Request',
      status: 400,
    });
  });

  it('accepts a body that has type without title', () => {
    expect(parseAuthApiProblemDetail({type: 'about:blank'})).toEqual({
      type: 'about:blank',
    });
  });

  it('returns null for non-objects', () => {
    expect(parseAuthApiProblemDetail(null)).toBeNull();
    expect(parseAuthApiProblemDetail('not-json')).toBeNull();
    expect(parseAuthApiProblemDetail(404)).toBeNull();
  });

  it('returns null when neither type nor title is a string', () => {
    expect(parseAuthApiProblemDetail({status: 500, detail: 'oops'})).toBeNull();
    expect(parseAuthApiProblemDetail({})).toBeNull();
  });
});

describe('isEzkeyProblemType', () => {
  it('accepts Auth catalog and system types under https://ezkey.io/problems/', () => {
    expect(isEzkeyProblemType('https://ezkey.io/problems/auth/auth-attempt-binding-failed')).toBe(
      true,
    );
    expect(isEzkeyProblemType('https://ezkey.io/problems/system/audit-chain-heartbeat-degraded')).toBe(
      true,
    );
  });

  it('rejects Cloudflare, about:blank, example fixtures, and the bare namespace', () => {
    expect(isEzkeyProblemType(CLOUDFLARE_1020_BODY.type)).toBe(false);
    expect(isEzkeyProblemType('about:blank')).toBe(false);
    expect(isEzkeyProblemType('https://ezkey.example/problems/enrollment-not-found')).toBe(false);
    expect(isEzkeyProblemType('https://ezkey.io/problems')).toBe(false);
    expect(isEzkeyProblemType('https://ezkey.io/problems/')).toBe(false);
    expect(isEzkeyProblemType(undefined)).toBe(false);
  });
});

describe('catalogKeyForAuthApiProblemType', () => {
  it('maps known Auth types to catalog keys', () => {
    expect(catalogKeyForAuthApiProblemType(AUTH_ATTEMPT_STATE_CONFLICT_TYPE)).toBe('stateConflict');
    expect(catalogKeyForAuthApiProblemType(AUTH_ATTEMPT_RESPOND_BEFORE_READ_TYPE)).toBe(
      'respondBeforeRead',
    );
    expect(catalogKeyForAuthApiProblemType(INTEGRATION_SIGNING_UNAVAILABLE_TYPE)).toBe(
      'integrationSigningUnavailable',
    );
    expect(catalogKeyForAuthApiProblemType(RESOURCE_NOT_FOUND_TYPE)).toBe('resourceNotFound');
    expect(catalogKeyForAuthApiProblemType('https://ezkey.io/problems/auth/other')).toBe('generic');
  });
});

describe('userFacingAuthApiError', () => {
  it('never surfaces trapped server detail for Ezkey types — uses local catalog (#844)', () => {
    const trappedDetail = 'Cannot respond: status is no longer READ — internal id=999';
    const error = httpErrorWithData({
      type: 'https://ezkey.io/problems/auth/auth-attempt-binding-failed',
      title: 'Authentication request binding failed',
      status: 400,
      detail: trappedDetail,
    });
    const message = userFacingAuthApiError(error, CATALOG);
    expect(message).toBe(FALLBACK);
    expect(message).not.toContain(trappedDetail);
    expect(message).not.toContain('READ');
    expect(message).not.toContain('999');
  });

  it('returns the fallback for Cloudflare 1020 JSON and never leaks edge fields', () => {
    const error = httpErrorWithData(CLOUDFLARE_1020_BODY, 403);
    const message = userFacingAuthApiError(error, CATALOG);
    expect(message).toBe(FALLBACK);
    expect(message).not.toContain('1020');
    expect(message).not.toContain('ray_id');
    expect(message).not.toContain('0123456789abcdef');
    expect(message).not.toContain('what_you_should_do');
    expect(message).not.toContain(CLOUDFLARE_1020_BODY.detail);
  });

  it('returns the fallback for title-only, about:blank, HTML, and non-object bodies', () => {
    expect(userFacingAuthApiError(httpErrorWithData({title: 'Bad Request', status: 400}), CATALOG)).toBe(
      FALLBACK,
    );
    expect(
      userFacingAuthApiError(
        httpErrorWithData({type: 'about:blank', detail: 'Generic failure'}),
        CATALOG,
      ),
    ).toBe(FALLBACK);
    expect(
      userFacingAuthApiError(httpErrorWithData('<html><body>Error 1020</body></html>', 403), CATALOG),
    ).toBe(FALLBACK);
    expect(userFacingAuthApiError(httpErrorWithData(null, 502), CATALOG)).toBe(FALLBACK);
    expect(userFacingAuthApiError(new Error('Network Error'), CATALOG)).toBe(FALLBACK);
    expect(userFacingAuthApiError('not-json', CATALOG)).toBe(FALLBACK);
  });

  it('maps auth-attempt-state-conflict to the neutral inactive message (#833 Patrick)', () => {
    const error = httpErrorWithData(
      {
        type: AUTH_ATTEMPT_STATE_CONFLICT_TYPE,
        title: 'Authentication attempt state conflict',
        status: 409,
        detail: 'Cannot respond: status is no longer READ',
      },
      409,
    );
    expect(userFacingAuthApiError(error, CATALOG)).toBe(STATE_CONFLICT_MSG);
    expect(userFacingAuthApiError(error, CATALOG)).not.toContain('READ');
  });

  it('supports legacy (fallback, stateConflict) string overload', () => {
    const error = httpErrorWithData(
      {
        type: AUTH_ATTEMPT_STATE_CONFLICT_TYPE,
        status: 409,
        detail: 'must not surface',
      },
      409,
    );
    expect(userFacingAuthApiError(error, FALLBACK, STATE_CONFLICT_MSG)).toBe(STATE_CONFLICT_MSG);
  });
});

describe('resolveAuthApiUserFacingOutcome / isAuthAttemptStateConflict', () => {
  it('marks state conflict as non-retryable with the neutral message', () => {
    const error = httpErrorWithData(
      {
        type: AUTH_ATTEMPT_STATE_CONFLICT_TYPE,
        title: 'Authentication attempt state conflict',
        status: 409,
        detail: 'Server detail must not surface',
      },
      409,
    );
    expect(isAuthAttemptStateConflict(error)).toBe(true);
    expect(resolveAuthApiUserFacingOutcome(error, CATALOG)).toEqual({
      message: STATE_CONFLICT_MSG,
      retryable: false,
    });
  });

  it('marks respond-before-read as retryable with local catalog text (#844)', () => {
    const error = httpErrorWithData({
      type: AUTH_ATTEMPT_RESPOND_BEFORE_READ_TYPE,
      title: 'Request not acceptable',
      status: 400,
      detail: 'The authentication request has not been claimed by the device yet.',
    });
    expect(resolveAuthApiUserFacingOutcome(error, CATALOG)).toEqual({
      message: RESPOND_BEFORE_READ_MSG,
      retryable: true,
    });
    // Local catalog may say "claimed"; must not surface server problem detail verbatim.
    expect(resolveAuthApiUserFacingOutcome(error, CATALOG).message).not.toContain(
      'has not been claimed by the device yet',
    );
  });

  it('marks integration-signing-unavailable as retryable with local catalog text (#844)', () => {
    const error = httpErrorWithData(
      {
        type: INTEGRATION_SIGNING_UNAVAILABLE_TYPE,
        title: 'Service unavailable',
        status: 503,
        detail: 'Authentication signing is temporarily unavailable. Retry later.',
      },
      503,
    );
    expect(resolveAuthApiUserFacingOutcome(error, CATALOG)).toEqual({
      message: SIGNING_UNAVAILABLE_MSG,
      retryable: true,
    });
  });

  it('marks a 409 of another type as retryable (#844)', () => {
    const error = httpErrorWithData(
      {
        type: 'https://ezkey.io/problems/auth/enrollment-already-bound',
        title: 'Conflict',
        status: 409,
        detail: 'must not surface',
      },
      409,
    );
    expect(isAuthAttemptStateConflict(error)).toBe(false);
    expect(resolveAuthApiUserFacingOutcome(error, CATALOG)).toEqual({
      message: FALLBACK,
      retryable: true,
    });
    expect(resolveAuthApiUserFacingOutcome(error, CATALOG).message).not.toContain('must not');
  });

  it('marks other Ezkey problems as retryable with local fallback only', () => {
    const error = httpErrorWithData({
      type: 'https://ezkey.io/problems/auth/auth-attempt-binding-failed',
      title: 'Authentication request binding failed',
      status: 400,
      detail: 'The authentication request could not be processed.',
    });
    expect(isAuthAttemptStateConflict(error)).toBe(false);
    expect(resolveAuthApiUserFacingOutcome(error, CATALOG)).toEqual({
      message: FALLBACK,
      retryable: true,
    });
  });
});
