import { describe, expect, it } from 'vitest';
import {
  AUDIT_LOG_URL_ONLY_PARAMS,
  copyUrlOnlyAuditLogParams,
} from '@/lib/audit-log-list-url';

describe('copyUrlOnlyAuditLogParams', () => {
  it('copies integrity, highlight, and focus through a state rebuild', () => {
    const from = new URLSearchParams(
      'source=integrity-alert&integrity=1&highlightAuditLogIds=86&focusCheckpointId=12&createdAfter=2026-07-31T02:00Z',
    );
    const to = new URLSearchParams('createdAfter=2026-07-31T02:00Z');

    copyUrlOnlyAuditLogParams(from, to);

    expect(to.get('integrity')).toBe('1');
    expect(to.get('highlightAuditLogIds')).toBe('86');
    expect(to.get('focusCheckpointId')).toBe('12');
  });

  it('does not copy source — that param is owned by contextSource state', () => {
    const from = new URLSearchParams('source=integrity-alert&integrity=1');
    const to = new URLSearchParams();

    copyUrlOnlyAuditLogParams(from, to);

    expect(to.get('source')).toBeNull();
    expect(AUDIT_LOG_URL_ONLY_PARAMS).not.toContain('source');
  });
});
