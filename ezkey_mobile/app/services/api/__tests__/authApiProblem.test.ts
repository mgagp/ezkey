/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * RFC 9457 Problem Details parser and user-facing Auth API error tests.
 */

import {AxiosError} from 'axios';
import {
  isEzkeyProblemType,
  parseAuthApiProblemDetail,
  userFacingAuthApiError,
} from '../authApiProblem';

const FALLBACK = 'Request failed.';

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

function axiosErrorWithData(data: unknown, status = 400): AxiosError {
  return new AxiosError('Request failed with status code 400', AxiosError.ERR_BAD_REQUEST, undefined, undefined, {
    status,
    statusText: 'Bad Request',
    headers: {},
    config: {} as AxiosError['config'],
    data,
  });
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
    const error = axiosErrorWithData({
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
    const error = axiosErrorWithData(CLOUDFLARE_1020_BODY, 403);
    const message = userFacingAuthApiError(error, FALLBACK);
    expect(message).toBe(FALLBACK);
    expect(message).not.toContain('1020');
    expect(message).not.toContain('ray_id');
    expect(message).not.toContain('0123456789abcdef');
    expect(message).not.toContain('what_you_should_do');
    expect(message).not.toContain(CLOUDFLARE_1020_BODY.detail);
  });

  it('returns the fallback for title-only, about:blank, HTML, and non-object bodies', () => {
    expect(userFacingAuthApiError(axiosErrorWithData({title: 'Bad Request', status: 400}), FALLBACK)).toBe(
      FALLBACK,
    );
    expect(
      userFacingAuthApiError(
        axiosErrorWithData({type: 'about:blank', detail: 'Generic failure'}),
        FALLBACK,
      ),
    ).toBe(FALLBACK);
    expect(
      userFacingAuthApiError(axiosErrorWithData('<html><body>Error 1020</body></html>', 403), FALLBACK),
    ).toBe(FALLBACK);
    expect(userFacingAuthApiError(axiosErrorWithData(null, 502), FALLBACK)).toBe(FALLBACK);
    expect(userFacingAuthApiError(new Error('Network Error'), FALLBACK)).toBe(FALLBACK);
    expect(userFacingAuthApiError('not-json', FALLBACK)).toBe(FALLBACK);
  });

  it('returns the fallback when the Ezkey type has no usable detail', () => {
    expect(
      userFacingAuthApiError(
        axiosErrorWithData({
          type: 'https://ezkey.io/problems/auth/auth-attempt-binding-failed',
          title: 'Authentication request binding failed',
        }),
        FALLBACK,
      ),
    ).toBe(FALLBACK);
  });
});
