/**
 * Integrity atelier in-page mode resolution (cut-2 phase B).
 *
 * Modes are jobs inside the single `/integrity` page — not routes or nav items.
 * Phase A progressive disclosure remains authoritative for open/closed density
 * inside each mode.
 *
 * @since 2026
 */

import type {
  IntegrityDisclosureQuery,
  IntegrityDisclosureState,
} from './integrity-progressive-disclosure';

/** In-page atelier job modes (Observe | Verify | Remediate). */
export type IntegrityAtelierMode = 'observe' | 'verify' | 'remediate';

/** Stable ordered list for mode chrome rendering. */
export const INTEGRITY_ATELIER_MODES: readonly IntegrityAtelierMode[] = [
  'observe',
  'verify',
  'remediate',
] as const;

/**
 * Parse an optional `?mode=` query value.
 *
 * @param raw raw search-param value
 * @return a valid mode, or null when absent / unknown
 */
export function parseIntegrityModeParam(
  raw: string | null | undefined,
): IntegrityAtelierMode | null {
  if (raw === 'observe' || raw === 'verify' || raw === 'remediate') {
    return raw;
  }
  return null;
}

/** Inputs for default mode resolution from deep-links and non-green signals. */
export type IntegrityModeResolutionInput = {
  /** Optional explicit `?mode=` deep-link. */
  modeParam?: string | null;
  /** Deep-link / investigation query signals. */
  query: IntegrityDisclosureQuery;
  /** Server / UI non-green signals. */
  state: IntegrityDisclosureState;
  /** True when the operator (or gap locate) focused an undeclared gap. */
  hasFocusedGap?: boolean;
};

/**
 * Resolve the default atelier mode from URL + non-green signals.
 *
 * Priority (highest first):
 * 1. Valid `mode` query param
 * 2. Investigation / locate (`source=integrity-alert`, `focusCheckpointId`, gap focus) → Verify
 * 3. `action=reconcile` → Remediate
 * 4. Non-green server/UI state → Remediate
 * 5. Healthy bare → Observe
 *
 * @param input resolution inputs
 * @return default mode for the atelier
 */
export function resolveIntegrityAtelierMode(
  input: IntegrityModeResolutionInput,
): IntegrityAtelierMode {
  const explicit = parseIntegrityModeParam(input.modeParam);
  if (explicit) {
    return explicit;
  }

  const { query, state, hasFocusedGap = false } = input;

  if (
    query.focusCheckpointId != null
    || query.source === 'integrity-alert'
    || hasFocusedGap
  ) {
    return 'verify';
  }

  if (query.action === 'reconcile') {
    return 'remediate';
  }

  if (
    state.undeclaredGapCount > 0
    || state.hasActionableIncident
    || state.awaitingConfirmTranche
    || state.chainNonGreen
  ) {
    return 'remediate';
  }

  return 'observe';
}
