/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * RFC 9457 Problem Details parser tests.
 */

import {parseAuthApiProblemDetail} from '../authApiProblem';

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
