import { describe, expect, it } from 'vitest';
import {
  isActionableIncidentStatus,
  isChainReportNonGreen,
  shouldAutoOpenRemediateCluster,
  shouldForceTimelineOpen,
  undeclaredGapCountFromReport,
} from './integrity-progressive-disclosure';

describe('shouldForceTimelineOpen', () => {
  it('stays closed on healthy open with no query', () => {
    expect(shouldForceTimelineOpen({})).toBe(false);
  });

  it('does not open for action=reconcile alone', () => {
    expect(shouldForceTimelineOpen({ action: 'reconcile' })).toBe(false);
  });

  it('opens for focusCheckpointId', () => {
    expect(shouldForceTimelineOpen({ focusCheckpointId: 42 })).toBe(true);
  });

  it('opens for integrity-alert investigation source', () => {
    expect(shouldForceTimelineOpen({ source: 'integrity-alert' })).toBe(true);
  });

  it('opens when a gap is focused (locate)', () => {
    expect(shouldForceTimelineOpen({}, true)).toBe(true);
  });
});

describe('shouldAutoOpenRemediateCluster', () => {
  const healthyState = {
    undeclaredGapCount: 0,
    hasActionableIncident: false,
    awaitingConfirmTranche: false,
    chainNonGreen: false,
  };

  it('stays closed when healthy and no deep-link', () => {
    expect(shouldAutoOpenRemediateCluster({}, healthyState)).toBe(false);
  });

  it('opens for action=reconcile', () => {
    expect(
      shouldAutoOpenRemediateCluster({ action: 'reconcile' }, healthyState),
    ).toBe(true);
  });

  it('opens for source=integrity-alert', () => {
    expect(
      shouldAutoOpenRemediateCluster({ source: 'integrity-alert' }, healthyState),
    ).toBe(true);
  });

  it('opens when undeclared gaps are present', () => {
    expect(
      shouldAutoOpenRemediateCluster({}, { ...healthyState, undeclaredGapCount: 2 }),
    ).toBe(true);
  });

  it('opens for actionable incidents', () => {
    expect(
      shouldAutoOpenRemediateCluster(
        {},
        { ...healthyState, hasActionableIncident: true },
      ),
    ).toBe(true);
  });

  it('opens when sealed tranche awaits confirm', () => {
    expect(
      shouldAutoOpenRemediateCluster(
        {},
        { ...healthyState, awaitingConfirmTranche: true },
      ),
    ).toBe(true);
  });

  it('opens when chain report is non-green', () => {
    expect(
      shouldAutoOpenRemediateCluster({}, { ...healthyState, chainNonGreen: true }),
    ).toBe(true);
  });
});

describe('isActionableIncidentStatus', () => {
  it('treats pending and in-progress as actionable', () => {
    expect(isActionableIncidentStatus('RECOVERED_PENDING_DECLARATION')).toBe(true);
    expect(isActionableIncidentStatus('IN_PROGRESS')).toBe(true);
  });

  it('treats CLOSED as non-actionable', () => {
    expect(isActionableIncidentStatus('CLOSED')).toBe(false);
    expect(isActionableIncidentStatus(null)).toBe(false);
  });
});

describe('isChainReportNonGreen', () => {
  it('is green when intact with no gaps', () => {
    expect(isChainReportNonGreen({ intact: true, status: 'OK', undeclaredGaps: [] })).toBe(
      false,
    );
  });

  it('is non-green for undeclared gaps or intact=false', () => {
    expect(
      isChainReportNonGreen({
        intact: true,
        status: 'UNDECLARED_GAP_DETECTED',
        undeclaredGaps: [{ gapStart: 'a' }],
      }),
    ).toBe(true);
    expect(isChainReportNonGreen({ intact: false, undeclaredGaps: [] })).toBe(true);
  });

  it('is green before first report', () => {
    expect(isChainReportNonGreen(null)).toBe(false);
  });
});

describe('undeclaredGapCountFromReport', () => {
  it('counts gaps safely', () => {
    expect(undeclaredGapCountFromReport(null)).toBe(0);
    expect(undeclaredGapCountFromReport({ undeclaredGaps: [1, 2] })).toBe(2);
  });
});
