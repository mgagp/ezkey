/**
 * Integrity checkpoint timeline: quiet-window collapse and related helpers.
 *
 * Canon: product-docs/global/backlog/TB-2026-10-06-integrity-checkpoint-quiet-window-collapse.md
 *
 * Quiet REGULAR windows (entry_count = 0) may collapse into an expandable grey summary.
 * Non-REGULAR types (GAP_DECLARATION, MANIPULATION_CONCILIATION, ARCHIVE_SEAL, …) never
 * collapse. Collapse must not invent orange undeclared-gap rows — gap detection always
 * runs on the full chronological checkpoint sequence before collapse.
 */

import type { AuditChainCheckpointResponseDto } from '@/generated/admin-api/model';

/** URL query key: compress quiet REGULAR windows (`1` = on, `0` = off). */
export const CHECKPOINT_QUIET_COMPRESS_PARAM = 'cpQuiet';

/** URL query keys for checkpoint timeline date range (YYYY-MM-DD). */
export const CHECKPOINT_RANGE_FROM_PARAM = 'cpFrom';
export const CHECKPOINT_RANGE_TO_PARAM = 'cpTo';

/** URL query key for checkpoint type filter. */
export const CHECKPOINT_TYPE_PARAM = 'cpType';

/**
 * Grace period after window end before a checkpoint is "late-written".
 * Absorbs normal scheduler lag / clock skew without badge noise.
 */
export const LATE_WRITTEN_GRACE_MS = 60_000;

/** Minimum consecutive quiet REGULAR windows required to form a collapse group. */
export const QUIET_COLLAPSE_MIN_WINDOWS = 2;

export type CheckpointTimelineGapRow = {
  kind: 'gap';
  gapEnd: string;
  gapStart: string;
  durationMin: number;
};

export type CheckpointTimelineCheckpointRow = {
  kind: 'checkpoint';
  row: AuditChainCheckpointResponseDto;
  lateWritten: boolean;
};

export type CheckpointTimelineQuietSummaryRow = {
  kind: 'quiet-summary';
  /** Stable id for expand state (first checkpoint id or windowStart). */
  summaryId: string;
  windowStart: string;
  windowEnd: string;
  windowCount: number;
  /** Always 0 for quiet REGULAR stretches. */
  entryCount: number;
  /** True when any collapsed window is late-written (badge on grey summary). */
  lateWritten: boolean;
  windows: AuditChainCheckpointResponseDto[];
};

export type CheckpointTimelineDisplayRow =
  | CheckpointTimelineCheckpointRow
  | CheckpointTimelineGapRow
  | CheckpointTimelineQuietSummaryRow;

function isQuietRegular(cp: AuditChainCheckpointResponseDto): boolean {
  return (cp.checkpointType ?? 'REGULAR') === 'REGULAR' && (cp.entryCount ?? 0) === 0;
}

/**
 * Returns true when createdAt is clearly after windowEnd (beyond grace).
 *
 * @param windowEnd ISO window end
 * @param createdAt ISO created timestamp
 * @param graceMs tolerance after window end
 */
export function isLateWrittenCheckpoint(
  windowEnd: string | undefined,
  createdAt: string | undefined,
  graceMs: number = LATE_WRITTEN_GRACE_MS,
): boolean {
  if (!windowEnd || !createdAt) {
    return false;
  }
  const endMs = new Date(windowEnd).getTime();
  const createdMs = new Date(createdAt).getTime();
  if (Number.isNaN(endMs) || Number.isNaN(createdMs)) {
    return false;
  }
  return createdMs > endMs + graceMs;
}

/**
 * Reads compress preference from URL. Default is compress ON when the param is absent
 * (quiet scan is the useful default). Explicit `0` turns compress off. No localStorage.
 *
 * @param searchParams current URL search params
 */
export function readQuietCompressFromSearchParams(
  searchParams: Pick<URLSearchParams, 'get'>,
): boolean {
  const raw = searchParams.get(CHECKPOINT_QUIET_COMPRESS_PARAM);
  if (raw === '0' || raw === 'false') {
    return false;
  }
  if (raw === '1' || raw === 'true') {
    return true;
  }
  return true;
}

/**
 * Writes compress preference onto URL params (shareable). Removes the key when value
 * matches the default (compress on) so a bare Integrity URL stays clean.
 *
 * @param searchParams mutable params to update
 * @param compress whether quiet collapse is enabled
 */
export function writeQuietCompressToSearchParams(
  searchParams: URLSearchParams,
  compress: boolean,
): void {
  if (compress) {
    searchParams.delete(CHECKPOINT_QUIET_COMPRESS_PARAM);
  } else {
    searchParams.set(CHECKPOINT_QUIET_COMPRESS_PARAM, '0');
  }
}

/**
 * Default ~last 24h calendar window for the checkpoint timeline (YYYY-MM-DD).
 * Uses yesterday→today so a cold open covers a rolling day without oldest-history page 0.
 *
 * @param now reference instant
 */
export function defaultCheckpointRecentRange(now: Date = new Date()): {
  from: string;
  to: string;
} {
  const to = toYYYYMMDD(now);
  const past = new Date(now);
  past.setDate(past.getDate() - 1);
  return { from: toYYYYMMDD(past), to };
}

function toYYYYMMDD(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * Inserts orange undeclared-gap rows between consecutive checkpoints when sorted by
 * windowStart (ASC or DESC). Gap detection uses the full sequence — never the collapsed view.
 *
 * @param checkpoints page content in API sort order
 * @param sort current sort string (e.g. windowStart,DESC)
 */
export function buildCheckpointRowsWithGaps(
  checkpoints: AuditChainCheckpointResponseDto[],
  sort: string,
): Array<CheckpointTimelineCheckpointRow | CheckpointTimelineGapRow> {
  const sortField = sort.split(',')[0] ?? '';
  const sortDir = (sort.split(',')[1] ?? 'ASC').toUpperCase();
  const isChronologicalSort = sortField === 'windowStart';
  const out: Array<CheckpointTimelineCheckpointRow | CheckpointTimelineGapRow> = [];

  for (let i = 0; i < checkpoints.length; i++) {
    const curr = checkpoints[i];
    out.push({
      kind: 'checkpoint',
      row: curr,
      lateWritten: isLateWrittenCheckpoint(curr.windowEnd, curr.createdAt),
    });
    if (!isChronologicalSort) {
      continue;
    }
    const next = checkpoints[i + 1];
    if (!next) {
      continue;
    }
    // ASC: older → newer; gap when curr.windowEnd < next.windowStart
    // DESC: newer → older; gap when next.windowEnd < curr.windowStart
    if (sortDir === 'DESC') {
      if (next.windowEnd && curr.windowStart) {
        const olderEnd = new Date(next.windowEnd).getTime();
        const newerStart = new Date(curr.windowStart).getTime();
        if (olderEnd < newerStart) {
          out.push({
            kind: 'gap',
            gapEnd: next.windowEnd,
            gapStart: curr.windowStart,
            durationMin: Math.round((newerStart - olderEnd) / 60000),
          });
        }
      }
    } else if (curr.windowEnd && next.windowStart) {
      const end = new Date(curr.windowEnd).getTime();
      const start = new Date(next.windowStart).getTime();
      if (end < start) {
        out.push({
          kind: 'gap',
          gapEnd: curr.windowEnd,
          gapStart: next.windowStart,
          durationMin: Math.round((start - end) / 60000),
        });
      }
    }
  }
  return out;
}

function quietSummaryId(windows: AuditChainCheckpointResponseDto[]): string {
  const first = windows[0];
  if (first?.checkpointId != null) {
    return `quiet-${first.checkpointId}`;
  }
  return `quiet-${first?.windowStart ?? 'unknown'}`;
}

/**
 * Collapses consecutive quiet REGULAR checkpoint rows into grey summary rows.
 * Gap rows and non-quiet / non-REGULAR checkpoints stay as first-class rows.
 * When compress is false, returns the input unchanged (still with lateWritten flags).
 *
 * @param rows gap-aware rows from {@link buildCheckpointRowsWithGaps}
 * @param compress whether to collapse quiet stretches
 * @param expandedSummaryIds summary ids currently expanded by the operator
 */
export function collapseQuietRegularWindows(
  rows: Array<CheckpointTimelineCheckpointRow | CheckpointTimelineGapRow>,
  compress: boolean,
  expandedSummaryIds: ReadonlySet<string> = new Set(),
): CheckpointTimelineDisplayRow[] {
  if (!compress) {
    return rows;
  }

  const out: CheckpointTimelineDisplayRow[] = [];
  let i = 0;
  while (i < rows.length) {
    const item = rows[i];
    if (item.kind !== 'checkpoint' || !isQuietRegular(item.row)) {
      out.push(item);
      i += 1;
      continue;
    }

    const quietRun: CheckpointTimelineCheckpointRow[] = [];
    while (
      i < rows.length
      && rows[i].kind === 'checkpoint'
      && isQuietRegular((rows[i] as CheckpointTimelineCheckpointRow).row)
    ) {
      quietRun.push(rows[i] as CheckpointTimelineCheckpointRow);
      i += 1;
    }

    if (quietRun.length < QUIET_COLLAPSE_MIN_WINDOWS) {
      out.push(...quietRun);
      continue;
    }

    const windows = quietRun.map((r) => r.row);
    // Chronological bounds: earliest windowStart → latest windowEnd (works for ASC and DESC lists)
    const starts = windows
      .map((w) => w.windowStart)
      .filter((s): s is string => Boolean(s))
      .sort();
    const ends = windows
      .map((w) => w.windowEnd)
      .filter((s): s is string => Boolean(s))
      .sort();
    const windowStart = starts[0] ?? '';
    const windowEnd = ends[ends.length - 1] ?? '';
    const summaryId = quietSummaryId(windows);
    const lateWritten = quietRun.some((r) => r.lateWritten);

    if (expandedSummaryIds.has(summaryId)) {
      out.push({
        kind: 'quiet-summary',
        summaryId,
        windowStart,
        windowEnd,
        windowCount: windows.length,
        entryCount: 0,
        lateWritten,
        windows,
      });
      out.push(...quietRun);
    } else {
      out.push({
        kind: 'quiet-summary',
        summaryId,
        windowStart,
        windowEnd,
        windowCount: windows.length,
        entryCount: 0,
        lateWritten,
        windows,
      });
    }
  }
  return out;
}

/**
 * Builds display rows: gap detection on full sequence, then optional quiet collapse.
 *
 * @param checkpoints page content
 * @param sort current sort
 * @param compress quiet collapse enabled
 * @param expandedSummaryIds expanded grey summaries
 */
export function buildCheckpointTimelineDisplayRows(
  checkpoints: AuditChainCheckpointResponseDto[],
  sort: string,
  compress: boolean,
  expandedSummaryIds: ReadonlySet<string> = new Set(),
): CheckpointTimelineDisplayRow[] {
  const withGaps = buildCheckpointRowsWithGaps(checkpoints, sort);
  return collapseQuietRegularWindows(withGaps, compress, expandedSummaryIds);
}
