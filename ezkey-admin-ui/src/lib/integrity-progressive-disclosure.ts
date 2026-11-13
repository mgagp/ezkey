/**
 * Progressive disclosure rules for Integrity cut-2 phase A.
 *
 * Healthy open: Remediate cluster (maintenance + incidents + gaps list) and
 * checkpoint timeline stay collapsed. Non-green server/UI state or remediation
 * deep-links auto-open the Remediate cluster. Timeline opens only when the
 * query or operator needs investigation context.
 *
 * @since 2026
 */

/** Query signals that affect Integrity panel expansion. */
export type IntegrityDisclosureQuery = {
  /** Deep-link `action` (e.g. `reconcile`). */
  action?: string | null;
  /** Deep-link `source` (e.g. `integrity-alert`). */
  source?: string | null;
  /** Deep-link `focusCheckpointId`. */
  focusCheckpointId?: number | null;
};

/** Runtime / server signals that make the atelier non-green. */
export type IntegrityDisclosureState = {
  undeclaredGapCount: number;
  /** True when any incident is IN_PROGRESS or RECOVERED_PENDING_DECLARATION. */
  hasActionableIncident: boolean;
  /** Sealed tranche awaiting Confirm archived. */
  awaitingConfirmTranche: boolean;
  /** Chain report not intact / has violations or undeclared gaps. */
  chainNonGreen: boolean;
};

/**
 * Whether the checkpoint timeline must open from the URL / investigation context.
 *
 * Does <strong>not</strong> open solely for `action=reconcile` — that is Remediate only.
 *
 * @param query deep-link / investigation signals
 * @param hasFocusedGap true when the operator (or gap locate) focused an undeclared gap
 * @return true when the timeline should force-open
 */
export function shouldForceTimelineOpen(
  query: IntegrityDisclosureQuery,
  hasFocusedGap = false,
): boolean {
  if (query.focusCheckpointId != null) {
    return true;
  }
  if (hasFocusedGap) {
    return true;
  }
  if (query.source === 'integrity-alert') {
    return true;
  }
  return false;
}

/**
 * Whether Exceptional Maintenance, Operational incidents, and (when present)
 * the undeclared-gaps list should auto-expand.
 *
 * @param query deep-link signals
 * @param state server / UI non-green signals
 * @return true when the Remediate cluster should open
 */
export function shouldAutoOpenRemediateCluster(
  query: IntegrityDisclosureQuery,
  state: IntegrityDisclosureState,
): boolean {
  if (query.action === 'reconcile') {
    return true;
  }
  if (query.source === 'integrity-alert') {
    return true;
  }
  if (query.focusCheckpointId != null) {
    return true;
  }
  if (state.undeclaredGapCount > 0) {
    return true;
  }
  if (state.hasActionableIncident) {
    return true;
  }
  if (state.awaitingConfirmTranche) {
    return true;
  }
  if (state.chainNonGreen) {
    return true;
  }
  return false;
}

/**
 * Heartbeat incident statuses that deserve Remediate attention.
 *
 * @param status incident status string
 * @return true when the incident is actionable (not merely historical CLOSED)
 */
export function isActionableIncidentStatus(status: string | null | undefined): boolean {
  return status === 'RECOVERED_PENDING_DECLARATION' || status === 'IN_PROGRESS';
}

/**
 * Derive non-green from the last chain verification report.
 *
 * @param report chain verification payload (or null before first run)
 * @return true when the report indicates work for the operator
 */
export function isChainReportNonGreen(
  report: {
    intact?: boolean;
    status?: string;
    undeclaredGaps?: unknown[] | null;
    invalidCheckpoints?: number;
  } | null,
): boolean {
  if (!report) {
    return false;
  }
  if (report.intact === false) {
    return true;
  }
  if ((report.undeclaredGaps?.length ?? 0) > 0) {
    return true;
  }
  if ((report.invalidCheckpoints ?? 0) > 0) {
    return true;
  }
  if (
    report.status === 'UNDECLARED_GAP_DETECTED'
    || report.status === 'CHAIN_INTEGRITY_VIOLATION_DETECTED'
  ) {
    return true;
  }
  return false;
}

/**
 * Count undeclared gaps on a chain report.
 *
 * @param report chain verification payload
 * @return gap count (0 when absent)
 */
export function undeclaredGapCountFromReport(
  report: { undeclaredGaps?: unknown[] | null } | null,
): number {
  return report?.undeclaredGaps?.length ?? 0;
}
