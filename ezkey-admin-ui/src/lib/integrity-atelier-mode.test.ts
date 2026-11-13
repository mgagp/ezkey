import { describe, expect, it } from 'vitest';
import {
  parseIntegrityModeParam,
  resolveIntegrityAtelierMode,
} from './integrity-atelier-mode';

const healthyState = {
  undeclaredGapCount: 0,
  hasActionableIncident: false,
  awaitingConfirmTranche: false,
  chainNonGreen: false,
};

describe('parseIntegrityModeParam', () => {
  it('accepts the three product modes', () => {
    expect(parseIntegrityModeParam('observe')).toBe('observe');
    expect(parseIntegrityModeParam('verify')).toBe('verify');
    expect(parseIntegrityModeParam('remediate')).toBe('remediate');
  });

  it('rejects absent or unknown values', () => {
    expect(parseIntegrityModeParam(null)).toBe(null);
    expect(parseIntegrityModeParam(undefined)).toBe(null);
    expect(parseIntegrityModeParam('')).toBe(null);
    expect(parseIntegrityModeParam('Observe')).toBe(null);
    expect(parseIntegrityModeParam('other')).toBe(null);
  });
});

describe('resolveIntegrityAtelierMode', () => {
  it('defaults healthy bare open to Observe', () => {
    expect(
      resolveIntegrityAtelierMode({ query: {}, state: healthyState }),
    ).toBe('observe');
  });

  it('honors an explicit mode param over other signals', () => {
    expect(
      resolveIntegrityAtelierMode({
        modeParam: 'observe',
        query: { action: 'reconcile', source: 'integrity-alert' },
        state: { ...healthyState, chainNonGreen: true },
      }),
    ).toBe('observe');
    expect(
      resolveIntegrityAtelierMode({
        modeParam: 'verify',
        query: { action: 'reconcile' },
        state: healthyState,
      }),
    ).toBe('verify');
  });

  it('maps investigation / locate deep-links to Verify', () => {
    expect(
      resolveIntegrityAtelierMode({
        query: { source: 'integrity-alert' },
        state: healthyState,
      }),
    ).toBe('verify');
    expect(
      resolveIntegrityAtelierMode({
        query: { focusCheckpointId: 12 },
        state: healthyState,
      }),
    ).toBe('verify');
    expect(
      resolveIntegrityAtelierMode({
        query: {},
        state: healthyState,
        hasFocusedGap: true,
      }),
    ).toBe('verify');
  });

  it('prefers Verify over Remediate when investigation and non-green both apply', () => {
    expect(
      resolveIntegrityAtelierMode({
        query: { source: 'integrity-alert' },
        state: { ...healthyState, undeclaredGapCount: 1, chainNonGreen: true },
      }),
    ).toBe('verify');
  });

  it('maps action=reconcile to Remediate without requiring non-green state', () => {
    expect(
      resolveIntegrityAtelierMode({
        query: { action: 'reconcile' },
        state: healthyState,
      }),
    ).toBe('remediate');
  });

  it('maps non-green signals to Remediate', () => {
    expect(
      resolveIntegrityAtelierMode({
        query: {},
        state: { ...healthyState, undeclaredGapCount: 2 },
      }),
    ).toBe('remediate');
    expect(
      resolveIntegrityAtelierMode({
        query: {},
        state: { ...healthyState, hasActionableIncident: true },
      }),
    ).toBe('remediate');
    expect(
      resolveIntegrityAtelierMode({
        query: {},
        state: { ...healthyState, awaitingConfirmTranche: true },
      }),
    ).toBe('remediate');
    expect(
      resolveIntegrityAtelierMode({
        query: {},
        state: { ...healthyState, chainNonGreen: true },
      }),
    ).toBe('remediate');
  });
});
