import { describe, expect, it } from 'vitest';
import type { AuditChainCheckpointResponseDto } from '@/generated/admin-api/model';
import {
  CHECKPOINT_QUIET_COMPRESS_PARAM,
  buildCheckpointRowsWithGaps,
  buildCheckpointTimelineDisplayRows,
  collapseQuietRegularWindows,
  defaultCheckpointRecentRange,
  isLateWrittenCheckpoint,
  readQuietCompressFromSearchParams,
  writeQuietCompressToSearchParams,
} from './checkpoint-quiet-collapse';

function cp(
  partial: Partial<AuditChainCheckpointResponseDto> & {
    checkpointId: number;
    windowStart: string;
    windowEnd: string;
  },
): AuditChainCheckpointResponseDto {
  return {
    checkpointType: 'REGULAR',
    entryCount: 0,
    ...partial,
  };
}

describe('isLateWrittenCheckpoint', () => {
  it('returns false when createdAt is within grace after window end', () => {
    expect(
      isLateWrittenCheckpoint('2026-10-06T14:05:00Z', '2026-10-06T14:05:30Z', 60_000),
    ).toBe(false);
  });

  it('returns true when createdAt is clearly after window end', () => {
    expect(
      isLateWrittenCheckpoint('2026-10-06T14:05:00Z', '2026-10-06T14:10:00Z', 60_000),
    ).toBe(true);
  });

  it('returns false when timestamps are missing', () => {
    expect(isLateWrittenCheckpoint(undefined, '2026-10-06T14:10:00Z')).toBe(false);
    expect(isLateWrittenCheckpoint('2026-10-06T14:05:00Z', undefined)).toBe(false);
  });
});

describe('URL quiet compress round-trip', () => {
  it('defaults to compress ON when param absent', () => {
    expect(readQuietCompressFromSearchParams(new URLSearchParams())).toBe(true);
  });

  it('reads explicit off and on', () => {
    expect(
      readQuietCompressFromSearchParams(new URLSearchParams(`${CHECKPOINT_QUIET_COMPRESS_PARAM}=0`)),
    ).toBe(false);
    expect(
      readQuietCompressFromSearchParams(new URLSearchParams(`${CHECKPOINT_QUIET_COMPRESS_PARAM}=1`)),
    ).toBe(true);
  });

  it('round-trips compress off via URL only (no sticky storage)', () => {
    const params = new URLSearchParams('mode=verify');
    writeQuietCompressToSearchParams(params, false);
    expect(params.get(CHECKPOINT_QUIET_COMPRESS_PARAM)).toBe('0');
    expect(readQuietCompressFromSearchParams(params)).toBe(false);

    writeQuietCompressToSearchParams(params, true);
    expect(params.has(CHECKPOINT_QUIET_COMPRESS_PARAM)).toBe(false);
    expect(readQuietCompressFromSearchParams(params)).toBe(true);
  });
});

describe('defaultCheckpointRecentRange', () => {
  it('covers yesterday through today (~last 24h calendar)', () => {
    const range = defaultCheckpointRecentRange(new Date('2026-10-07T12:00:00Z'));
    expect(range).toEqual({ from: '2026-10-06', to: '2026-10-07' });
  });
});

describe('buildCheckpointRowsWithGaps', () => {
  it('inserts orange gap between chronological ASC neighbors with a hole', () => {
    const rows = buildCheckpointRowsWithGaps(
      [
        cp({
          checkpointId: 1,
          windowStart: '2026-10-06T14:00:00Z',
          windowEnd: '2026-10-06T14:05:00Z',
          entryCount: 3,
        }),
        cp({
          checkpointId: 2,
          windowStart: '2026-10-06T14:20:00Z',
          windowEnd: '2026-10-06T14:25:00Z',
          entryCount: 1,
        }),
      ],
      'windowStart,ASC',
    );
    expect(rows.map((r) => r.kind)).toEqual(['checkpoint', 'gap', 'checkpoint']);
    if (rows[1].kind === 'gap') {
      expect(rows[1].durationMin).toBe(15);
    }
  });

  it('inserts gap correctly for newest-first DESC sort', () => {
    const rows = buildCheckpointRowsWithGaps(
      [
        cp({
          checkpointId: 2,
          windowStart: '2026-10-06T14:20:00Z',
          windowEnd: '2026-10-06T14:25:00Z',
          entryCount: 1,
        }),
        cp({
          checkpointId: 1,
          windowStart: '2026-10-06T14:00:00Z',
          windowEnd: '2026-10-06T14:05:00Z',
          entryCount: 3,
        }),
      ],
      'windowStart,DESC',
    );
    expect(rows.map((r) => r.kind)).toEqual(['checkpoint', 'gap', 'checkpoint']);
  });
});

describe('collapseQuietRegularWindows', () => {
  it('collapses consecutive quiet REGULAR into one grey summary', () => {
    const withGaps = buildCheckpointRowsWithGaps(
      [
        cp({
          checkpointId: 10,
          windowStart: '2026-10-06T14:05:00Z',
          windowEnd: '2026-10-06T14:10:00Z',
        }),
        cp({
          checkpointId: 11,
          windowStart: '2026-10-06T14:10:00Z',
          windowEnd: '2026-10-06T14:15:00Z',
        }),
        cp({
          checkpointId: 12,
          windowStart: '2026-10-06T14:15:00Z',
          windowEnd: '2026-10-06T14:20:00Z',
        }),
      ],
      'windowStart,ASC',
    );
    const display = collapseQuietRegularWindows(withGaps, true);
    expect(display).toHaveLength(1);
    expect(display[0].kind).toBe('quiet-summary');
    if (display[0].kind === 'quiet-summary') {
      expect(display[0].windowCount).toBe(3);
      expect(display[0].entryCount).toBe(0);
      expect(display[0].windowStart).toBe('2026-10-06T14:05:00Z');
      expect(display[0].windowEnd).toBe('2026-10-06T14:20:00Z');
    }
  });

  it('never collapses GAP_DECLARATION or MANIPULATION_CONCILIATION', () => {
    const display = buildCheckpointTimelineDisplayRows(
      [
        cp({
          checkpointId: 1,
          windowStart: '2026-10-06T14:00:00Z',
          windowEnd: '2026-10-06T14:05:00Z',
        }),
        cp({
          checkpointId: 2,
          windowStart: '2026-10-06T14:05:00Z',
          windowEnd: '2026-10-06T14:10:00Z',
          checkpointType: 'GAP_DECLARATION',
          entryCount: 0,
        }),
        cp({
          checkpointId: 3,
          windowStart: '2026-10-06T14:10:00Z',
          windowEnd: '2026-10-06T14:15:00Z',
        }),
        cp({
          checkpointId: 4,
          windowStart: '2026-10-06T14:15:00Z',
          windowEnd: '2026-10-06T14:20:00Z',
          checkpointType: 'MANIPULATION_CONCILIATION',
          entryCount: 0,
        }),
        cp({
          checkpointId: 5,
          windowStart: '2026-10-06T14:20:00Z',
          windowEnd: '2026-10-06T14:25:00Z',
        }),
      ],
      'windowStart,ASC',
      true,
    );
    const kinds = display.map((r) => r.kind);
    expect(kinds).toEqual(['checkpoint', 'checkpoint', 'checkpoint', 'checkpoint', 'checkpoint']);
    const types = display
      .filter((r): r is Extract<typeof r, { kind: 'checkpoint' }> => r.kind === 'checkpoint')
      .map((r) => r.row.checkpointType);
    expect(types).toContain('GAP_DECLARATION');
    expect(types).toContain('MANIPULATION_CONCILIATION');
    expect(display.some((r) => r.kind === 'quiet-summary')).toBe(false);
  });

  it('never collapses ARCHIVE_SEAL', () => {
    const display = buildCheckpointTimelineDisplayRows(
      [
        cp({
          checkpointId: 1,
          windowStart: '2026-10-06T14:00:00Z',
          windowEnd: '2026-10-06T14:05:00Z',
          checkpointType: 'ARCHIVE_SEAL',
          entryCount: 0,
        }),
        cp({
          checkpointId: 2,
          windowStart: '2026-10-06T14:05:00Z',
          windowEnd: '2026-10-06T14:10:00Z',
          checkpointType: 'ARCHIVE_SEAL',
          entryCount: 0,
        }),
      ],
      'windowStart,ASC',
      true,
    );
    expect(display.every((r) => r.kind === 'checkpoint')).toBe(true);
  });

  it('does not invent false gap rows when quiet windows are collapsed', () => {
    // Contiguous quiet REGULAR windows — hide-empty would invent a gap between activity rows
    const display = buildCheckpointTimelineDisplayRows(
      [
        cp({
          checkpointId: 1,
          windowStart: '2026-10-06T13:55:00Z',
          windowEnd: '2026-10-06T14:00:00Z',
          entryCount: 5,
        }),
        cp({
          checkpointId: 2,
          windowStart: '2026-10-06T14:00:00Z',
          windowEnd: '2026-10-06T14:05:00Z',
        }),
        cp({
          checkpointId: 3,
          windowStart: '2026-10-06T14:05:00Z',
          windowEnd: '2026-10-06T14:10:00Z',
        }),
        cp({
          checkpointId: 4,
          windowStart: '2026-10-06T14:10:00Z',
          windowEnd: '2026-10-06T14:15:00Z',
        }),
        cp({
          checkpointId: 5,
          windowStart: '2026-10-06T14:15:00Z',
          windowEnd: '2026-10-06T14:20:00Z',
          entryCount: 2,
        }),
      ],
      'windowStart,ASC',
      true,
    );
    expect(display.some((r) => r.kind === 'gap')).toBe(false);
    expect(display.map((r) => r.kind)).toEqual(['checkpoint', 'quiet-summary', 'checkpoint']);
  });

  it('keeps a real undeclared gap as orange even when quiet stretches collapse around it', () => {
    const display = buildCheckpointTimelineDisplayRows(
      [
        cp({
          checkpointId: 1,
          windowStart: '2026-10-06T14:00:00Z',
          windowEnd: '2026-10-06T14:05:00Z',
        }),
        cp({
          checkpointId: 2,
          windowStart: '2026-10-06T14:05:00Z',
          windowEnd: '2026-10-06T14:10:00Z',
        }),
        // hole: 14:10 → 14:25
        cp({
          checkpointId: 3,
          windowStart: '2026-10-06T14:25:00Z',
          windowEnd: '2026-10-06T14:30:00Z',
        }),
        cp({
          checkpointId: 4,
          windowStart: '2026-10-06T14:30:00Z',
          windowEnd: '2026-10-06T14:35:00Z',
        }),
      ],
      'windowStart,ASC',
      true,
    );
    expect(display.map((r) => r.kind)).toEqual([
      'quiet-summary',
      'gap',
      'quiet-summary',
    ]);
  });

  it('shows late-written badge on grey summary when any collapsed window is late', () => {
    const display = buildCheckpointTimelineDisplayRows(
      [
        cp({
          checkpointId: 1,
          windowStart: '2026-10-06T14:00:00Z',
          windowEnd: '2026-10-06T14:05:00Z',
          createdAt: '2026-10-06T14:05:10Z',
        }),
        cp({
          checkpointId: 2,
          windowStart: '2026-10-06T14:05:00Z',
          windowEnd: '2026-10-06T14:10:00Z',
          createdAt: '2026-10-06T15:00:00Z',
        }),
      ],
      'windowStart,ASC',
      true,
    );
    expect(display).toHaveLength(1);
    expect(display[0].kind).toBe('quiet-summary');
    if (display[0].kind === 'quiet-summary') {
      expect(display[0].lateWritten).toBe(true);
    }
  });

  it('expands summary to restore individual REGULAR rows while keeping the summary header', () => {
    const withGaps = buildCheckpointRowsWithGaps(
      [
        cp({
          checkpointId: 10,
          windowStart: '2026-10-06T14:05:00Z',
          windowEnd: '2026-10-06T14:10:00Z',
        }),
        cp({
          checkpointId: 11,
          windowStart: '2026-10-06T14:10:00Z',
          windowEnd: '2026-10-06T14:15:00Z',
        }),
      ],
      'windowStart,ASC',
    );
    const collapsed = collapseQuietRegularWindows(withGaps, true);
    expect(collapsed[0].kind).toBe('quiet-summary');
    const summaryId =
      collapsed[0].kind === 'quiet-summary' ? collapsed[0].summaryId : '';
    const expanded = collapseQuietRegularWindows(withGaps, true, new Set([summaryId]));
    expect(expanded.map((r) => r.kind)).toEqual([
      'quiet-summary',
      'checkpoint',
      'checkpoint',
    ]);
  });

  it('does not collapse a single quiet window alone', () => {
    const display = buildCheckpointTimelineDisplayRows(
      [
        cp({
          checkpointId: 1,
          windowStart: '2026-10-06T14:00:00Z',
          windowEnd: '2026-10-06T14:05:00Z',
        }),
      ],
      'windowStart,ASC',
      true,
    );
    expect(display).toHaveLength(1);
    expect(display[0].kind).toBe('checkpoint');
  });
});
