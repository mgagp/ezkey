import { describe, expect, it } from 'vitest';
import {
  AUDIT_LOG_URL_ONLY_PARAMS,
  copyUrlOnlyAuditLogParams,
} from '@/lib/audit-log-list-url';

describe('copyUrlOnlyAuditLogParams', () => {
  it('does not copy integrity deep-link params — those belong on /integrity', () => {
    const from = new URLSearchParams(
      'source=integrity-alert&integrity=1&highlightAuditLogIds=86&focusCheckpointId=12&createdAfter=2026-07-31T02:00Z',
    );
    const to = new URLSearchParams('createdAfter=2026-07-31T02:00Z');

    copyUrlOnlyAuditLogParams(from, to);

    expect(to.get('integrity')).toBeNull();
    expect(to.get('highlightAuditLogIds')).toBeNull();
    expect(to.get('focusCheckpointId')).toBeNull();
    expect(AUDIT_LOG_URL_ONLY_PARAMS).not.toContain('integrity');
    expect(AUDIT_LOG_URL_ONLY_PARAMS).not.toContain('highlightAuditLogIds');
    expect(AUDIT_LOG_URL_ONLY_PARAMS).not.toContain('focusCheckpointId');
  });

  it('does not copy source — that param is owned by contextSource state', () => {
    const from = new URLSearchParams('source=enrollment-detail&enrollmentId=7');
    const to = new URLSearchParams();

    copyUrlOnlyAuditLogParams(from, to);

    expect(to.get('source')).toBeNull();
    expect(AUDIT_LOG_URL_ONLY_PARAMS).not.toContain('source');
  });
});
