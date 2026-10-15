/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * RFC 9457 Problem Details parser and user-facing Auth API error tests.
 */

import {
  AUTH_ATTEMPT_STATE_CONFLICT_TYPE,
  isAuthAttemptStateConflict,
  isEzkeyProblemType,
  parseAuthApiProblemDetail,
  resolveAuthApiUserFacingOutcome,
  userFacingAuthApiError,
} from '../authApiProblem';

const FALLBACK = 'Request failed.';
const STATE_CONFLICT_MSG =
  'This request is no longer active. No action is required.';

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

describe('userFacingAuthApiError', () => {
  it('shows origin detail when type is under the Ezkey namespace', () => {
    const error = httpErrorWithData({
      type: 'https://ezkey.io/problems/auth/auth-attempt-binding-failed',
      title: 'Authentication request binding failed',
      status: 400,
      detail: 'The authentication request could not be processed.',
    });
    expect(userFacingAuthApiError(error, FALLBACK)).toBe(
      'The authentication request could not be processed.',
    );
  });

  it('returns the fallback for Cloudflare 1020 JSON and never leaks edge fields', () => {
    const error = httpErrorWithData(CLOUDFLARE_1020_BODY, 403);
    const message = userFacingAuthApiError(error, FALLBACK);
    expect(message).toBe(FALLBACK);
    expect(message).not.toContain('1020');
    expect(message).not.toContain('ray_id');
    expect(message).not.toContain('0123456789abcdef');
    expect(message).not.toContain('what_you_should_do');
    expect(message).not.toContain(CLOUDFLARE_1020_BODY.detail);
  });

  it('returns the fallback for title-only, about:blank, HTML, and non-object bodies', () => {
    expect(userFacingAuthApiError(httpErrorWithData({title: 'Bad Request', status: 400}), FALLBACK)).toBe(
      FALLBACK,
    );
    expect(
      userFacingAuthApiError(
        httpErrorWithData({type: 'about:blank', detail: 'Generic failure'}),
        FALLBACK,
      ),
    ).toBe(FALLBACK);
    expect(
      userFacingAuthApiError(httpErrorWithData('<html><body>Error 1020</body></html>', 403), FALLBACK),
    ).toBe(FALLBACK);
    expect(userFacingAuthApiError(httpErrorWithData(null, 502), FALLBACK)).toBe(FALLBACK);
    expect(userFacingAuthApiError(new Error('Network Error'), FALLBACK)).toBe(FALLBACK);
    expect(userFacingAuthApiError('not-json', FALLBACK)).toBe(FALLBACK);
  });

  it('returns the fallback when the Ezkey type has no usable detail', () => {
    expect(
      userFacingAuthApiError(
        httpErrorWithData({
          type: 'https://ezkey.io/problems/auth/auth-attempt-binding-failed',
          title: 'Authentication request binding failed',
        }),
        FALLBACK,
      ),
    ).toBe(FALLBACK);
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
    expect(userFacingAuthApiError(error, FALLBACK, STATE_CONFLICT_MSG)).toBe(STATE_CONFLICT_MSG);
    expect(userFacingAuthApiError(error, FALLBACK, STATE_CONFLICT_MSG)).not.toContain('READ');
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
    expect(resolveAuthApiUserFacingOutcome(error, FALLBACK, STATE_CONFLICT_MSG)).toEqual({
      message: STATE_CONFLICT_MSG,
      retryable: false,
    });
  });

  it('marks other Ezkey problems as retryable', () => {
    const error = httpErrorWithData({
      type: 'https://ezkey.io/problems/auth/auth-attempt-binding-failed',
      title: 'Authentication request binding failed',
      status: 400,
      detail: 'The authentication request could not be processed.',
    });
    expect(isAuthAttemptStateConflict(error)).toBe(false);
    expect(resolveAuthApiUserFacingOutcome(error, FALLBACK, STATE_CONFLICT_MSG)).toEqual({
      message: 'The authentication request could not be processed.',
      retryable: true,
    });
  });
});
