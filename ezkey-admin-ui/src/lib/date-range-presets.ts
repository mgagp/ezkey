/**
 * Shared date-range preset definitions and helpers for admin UI temporal filters.
 * All semantics use the user's local timezone; API params are built from local
 * start/end of day and converted to ISO for the backend.
 */

export const DATE_RANGE_PRESET_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: 'Full range' },
  { value: 'today', label: 'Today' },
  { value: 'yesterday', label: 'Yesterday' },
  { value: 'last-7d', label: 'Last 7 days' },
  { value: 'last-30d', label: 'Last 30 days' },
  { value: 'last-week', label: 'Last week (Mon–Sun)' },
  { value: 'last-month', label: 'Last month' },
  { value: 'last-quarter', label: 'Last quarter' },
];

/** Format a local Date to YYYY-MM-DD. */
function toYYYYMMDD(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * Returns the date range for a preset id using local calendar semantics.
 * - Today / Yesterday: that calendar day.
 * - Last 7 days / Last 30 days: rolling from (now − N days) to now (as calendar dates).
 * - Last week: previous Monday–Sunday.
 * - Last month: previous calendar month.
 * - Last quarter: previous calendar quarter.
 * Returns null for empty/full range.
 */
export function getPresetDateRange(presetId: string): { from: string; to: string } | null {
  if (!presetId || presetId === 'full') return null;
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());

  switch (presetId) {
    case 'today':
      return { from: toYYYYMMDD(today), to: toYYYYMMDD(today) };
    case 'yesterday': {
      const y = new Date(today);
      y.setDate(y.getDate() - 1);
      return { from: toYYYYMMDD(y), to: toYYYYMMDD(y) };
    }
    case 'last-7d': {
      const d7 = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
      const from = toYYYYMMDD(d7);
      const to = toYYYYMMDD(today);
      return { from, to };
    }
    case 'last-30d': {
      const d30 = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
      const from = toYYYYMMDD(d30);
      const to = toYYYYMMDD(today);
      return { from, to };
    }
    case 'last-week': {
      const dayOfWeek = today.getDay();
      const lastMonday = new Date(today);
      lastMonday.setDate(today.getDate() - (dayOfWeek + 6));
      const lastSunday = new Date(lastMonday);
      lastSunday.setDate(lastSunday.getDate() + 6);
      return { from: toYYYYMMDD(lastMonday), to: toYYYYMMDD(lastSunday) };
    }
    case 'last-month': {
      const firstOfLastMonth = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      const lastOfLastMonth = new Date(today.getFullYear(), today.getMonth(), 0);
      return { from: toYYYYMMDD(firstOfLastMonth), to: toYYYYMMDD(lastOfLastMonth) };
    }
    case 'last-quarter': {
      const currentQ = Math.floor(today.getMonth() / 3);
      const qStart = new Date(today.getFullYear(), (currentQ - 1) * 3, 1);
      const qEnd = new Date(today.getFullYear(), currentQ * 3, 0);
      return { from: toYYYYMMDD(qStart), to: toYYYYMMDD(qEnd) };
    }
    default:
      return null;
  }
}

/**
 * Converts two YYYY-MM-DD strings (local calendar dates) to API params.
 * Uses start-of-day for from and end-of-day for to in the user's local timezone,
 * then converts to ISO so "Today" and "Yesterday" match the admin's calendar.
 */
export function dateRangeToApiParams(
  from: string,
  to: string,
): { createdAfter: string; createdBefore: string } {
  const [yFrom, mFrom, dFrom] = from.split('-').map(Number);
  const [yTo, mTo, dTo] = to.split('-').map(Number);
  const startLocal = new Date(yFrom, mFrom - 1, dFrom, 0, 0, 0, 0);
  const endLocal = new Date(yTo, mTo - 1, dTo, 23, 59, 59, 999);
  return {
    createdAfter: startLocal.toISOString(),
    createdBefore: endLocal.toISOString(),
  };
}
