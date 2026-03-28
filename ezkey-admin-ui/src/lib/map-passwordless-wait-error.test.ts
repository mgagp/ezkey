import { describe, expect, it } from 'vitest';
import { ApiError } from './api-client';
import { mapPasswordlessWaitError } from './map-passwordless-wait-error';

const FALLBACK = 'fallback';
const CONNECTION_LOST = 'connection lost';

function apiError(
  status: number,
  body: Record<string, unknown>,
): ApiError {
  const detail = typeof body.detail === 'string' ? body.detail : undefined;
  const title = typeof body.title === 'string' ? body.title : undefined;
  const message = detail ?? title ?? `HTTP ${status}`;
  return new ApiError(status, body, message, body as import('./api-client').ProblemDetail);
}

describe('mapPasswordlessWaitError', () => {
  it('maps auth-rejected problem to rejected', () => {
    const err = apiError(400, {
      type: 'https://ezkey.io/problems/authentication/auth-rejected',
      title: 'Authentication Rejected',
      status: 400,
      detail: 'Device rejected the authentication request',
    });
    expect(mapPasswordlessWaitError(err, FALLBACK, CONNECTION_LOST)).toEqual({
      outcome: 'rejected',
    });
  });

  it('maps auth-expired problem to expired', () => {
    const err = apiError(400, {
      type: 'https://ezkey.io/problems/authentication/auth-expired',
      title: 'Authentication Expired',
      status: 400,
      detail: 'Authentication attempt expired - please try again',
    });
    expect(mapPasswordlessWaitError(err, FALLBACK, CONNECTION_LOST)).toEqual({
      outcome: 'expired',
    });
  });

  it('maps auth-timeout problem to error with API detail', () => {
    const err = apiError(408, {
      type: 'https://ezkey.io/problems/authentication/auth-timeout',
      title: 'Authentication Timeout',
      status: 408,
      detail: 'No device response within timeout period',
    });
    expect(mapPasswordlessWaitError(err, FALLBACK, CONNECTION_LOST)).toEqual({
      outcome: 'error',
      message: 'No device response within timeout period',
    });
  });

  it('maps HTTP 408 with unrelated type to error using getApiErrorMessage', () => {
    const err = apiError(408, {
      title: 'Timeout',
      status: 408,
      detail: 'Wait window ended',
    });
    expect(mapPasswordlessWaitError(err, FALLBACK, CONNECTION_LOST)).toEqual({
      outcome: 'error',
      message: 'Wait window ended',
    });
  });

  it('maps invalid-signature to error with detail', () => {
    const err = apiError(400, {
      type: 'https://ezkey.io/problems/authentication/invalid-signature',
      title: 'Invalid Signature',
      status: 400,
      detail: 'Device signature validation failed',
    });
    expect(mapPasswordlessWaitError(err, FALLBACK, CONNECTION_LOST)).toEqual({
      outcome: 'error',
      message: 'Device signature validation failed',
    });
  });

  it('maps generic ApiError to error with getApiErrorMessage', () => {
    const err = apiError(500, {
      title: 'Server Error',
      status: 500,
      detail: 'Unexpected',
    });
    expect(mapPasswordlessWaitError(err, FALLBACK, CONNECTION_LOST)).toEqual({
      outcome: 'error',
      message: 'Unexpected',
    });
  });

  it('maps TypeError to connectionLost', () => {
    expect(mapPasswordlessWaitError(new TypeError('Failed to fetch'), FALLBACK, CONNECTION_LOST)).toEqual({
      outcome: 'connectionLost',
      message: CONNECTION_LOST,
    });
  });

  it('maps unknown thrown value to connectionLost', () => {
    expect(mapPasswordlessWaitError(new Error('boom'), FALLBACK, CONNECTION_LOST)).toEqual({
      outcome: 'connectionLost',
      message: CONNECTION_LOST,
    });
  });
});
