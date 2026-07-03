/**
 * Shared date-range preset definitions and helpers for admin UI temporal filters.
 * When no IANA time zone is passed, semantics use the browser local calendar; when a zone is
 * passed (tenant display mode), presets use that zone's calendar. API params use start/end of day
 * in the active zone and convert to ISO UTC for the backend.
 */

import {
  addDaysYmd,
  endOfDayInTimeZone,
  formatYmdInTimeZone,
  startOfDayInTimeZone,
  weekdaySun0InZone,
} from '@/lib/timezone-calendar';

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

function getPresetDateRangeLocal(presetId: string): { from: string; to: string } | null {
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

function getPresetDateRangeInZone(
  presetId: string,
  timeZone: string,
): { from: string; to: string } | null {
  const todayYmd = formatYmdInTimeZone(new Date(), timeZone);

  switch (presetId) {
    case 'today':
      return { from: todayYmd, to: todayYmd };
    case 'yesterday': {
      const y = addDaysYmd(todayYmd, -1);
      return { from: y, to: y };
    }
    case 'last-7d': {
      const from = addDaysYmd(todayYmd, -7);
      return { from, to: todayYmd };
    }
    case 'last-30d': {
      const from = addDaysYmd(todayYmd, -30);
      return { from, to: todayYmd };
    }
    case 'last-week': {
      const wd = weekdaySun0InZone(todayYmd, timeZone);
      const lastMonday = addDaysYmd(todayYmd, -(wd + 6));
      const lastSunday = addDaysYmd(lastMonday, 6);
      return { from: lastMonday, to: lastSunday };
    }
    case 'last-month': {
      const [y, m] = todayYmd.split('-').map(Number);
      const firstOfThisMonth = new Date(Date.UTC(y, m - 1, 1));
      const lastOfPrev = new Date(firstOfThisMonth.getTime() - 86400000);
      const fy = lastOfPrev.getUTCFullYear();
      const fm = lastOfPrev.getUTCMonth();
      const firstOfLastMonth = new Date(Date.UTC(fy, fm, 1));
      const from = `${firstOfLastMonth.getUTCFullYear()}-${String(firstOfLastMonth.getUTCMonth() + 1).padStart(2, '0')}-01`;
      const to = `${fy}-${String(fm + 1).padStart(2, '0')}-${String(lastOfPrev.getUTCDate()).padStart(2, '0')}`;
      return { from, to };
    }
    case 'last-quarter': {
      const [y, m] = todayYmd.split('-').map(Number);
      const month0 = m - 1;
      const currentQ = Math.floor(month0 / 3);
      let year = y;
      let prevQ = currentQ - 1;
      if (prevQ < 0) {
        prevQ = 3;
        year -= 1;
      }
      const qStartMonth = prevQ * 3;
      const qStart = new Date(Date.UTC(year, qStartMonth, 1));
      const qEnd = new Date(Date.UTC(year, qStartMonth + 3, 0));
      const from = `${qStart.getUTCFullYear()}-${String(qStart.getUTCMonth() + 1).padStart(2, '0')}-01`;
      const to = `${qEnd.getUTCFullYear()}-${String(qEnd.getUTCMonth() + 1).padStart(2, '0')}-${String(qEnd.getUTCDate()).padStart(2, '0')}`;
      return { from, to };
    }
    default:
      return null;
  }
}

/**
 * Returns the date range for a preset id.
 * - Without {@code timeZone}: browser local calendar (legacy behavior).
 * - With {@code timeZone}: that IANA zone's calendar (tenant display mode).
 */
export function getPresetDateRange(
  presetId: string,
  timeZone?: string,
): { from: string; to: string } | null {
  if (!presetId || presetId === 'full') return null;
  if (!timeZone) {
    return getPresetDateRangeLocal(presetId);
  }
  return getPresetDateRangeInZone(presetId, timeZone);
}

/**
 * Converts two YYYY-MM-DD strings to API params.
 * Without {@code timeZone}: start/end of day in the browser local zone.
 * With {@code timeZone}: start/end of day in that IANA zone.
 */
/**
 * Converts calendar YYYY-MM-DD bounds to integrity API params with an exclusive `to`.
 *
 * Integrity verify and validation endpoints treat `to` as exclusive (`created_at < to`). List
 * endpoints use inclusive `createdBefore`; do not use this helper for list filters.
 */
export function integrityExclusiveDateRangeToApiParams(
  from: string,
  to: string,
  timeZone?: string,
): { createdAfter: string; createdBefore: string } {
  if (!timeZone) {
    const [yFrom, mFrom, dFrom] = from.split('-').map(Number);
    const [yTo, mTo, dTo] = to.split('-').map(Number);
    const startLocal = new Date(yFrom, mFrom - 1, dFrom, 0, 0, 0, 0);
    const endExclusiveLocal = new Date(yTo, mTo - 1, dTo + 1, 0, 0, 0, 0);
    return {
      createdAfter: startLocal.toISOString(),
      createdBefore: endExclusiveLocal.toISOString(),
    };
  }
  const start = startOfDayInTimeZone(from, timeZone);
  const endExclusive = startOfDayInTimeZone(addDaysYmd(to, 1), timeZone);
  return {
    createdAfter: start.toISOString(),
    createdBefore: endExclusive.toISOString(),
  };
}

/**
 * Estimates the wall-clock hours between integrity API bounds for a calendar date range.
 */
export function estimateIntegrityWindowHours(
  from: string,
  to: string,
  timeZone?: string,
): number {
  const { createdAfter, createdBefore } = integrityExclusiveDateRangeToApiParams(
    from,
    to,
    timeZone,
  );
  const ms = new Date(createdBefore).getTime() - new Date(createdAfter).getTime();
  return ms / 3_600_000;
}

/**
 * Rolling window ending at now — matches nightly batch semantics (`window-hours`).
 */
export function getRollingHoursWindow(hours: number): {
  createdAfter: string;
  createdBefore: string;
} {
  const now = new Date();
  return {
    createdAfter: new Date(now.getTime() - hours * 3_600_000).toISOString(),
    createdBefore: now.toISOString(),
  };
}

export function dateRangeToApiParams(
  from: string,
  to: string,
  timeZone?: string,
): { createdAfter: string; createdBefore: string } {
  if (!timeZone) {
    const [yFrom, mFrom, dFrom] = from.split('-').map(Number);
    const [yTo, mTo, dTo] = to.split('-').map(Number);
    const startLocal = new Date(yFrom, mFrom - 1, dFrom, 0, 0, 0, 0);
    const endLocal = new Date(yTo, mTo - 1, dTo, 23, 59, 59, 999);
    return {
      createdAfter: startLocal.toISOString(),
      createdBefore: endLocal.toISOString(),
    };
  }
  const start = startOfDayInTimeZone(from, timeZone);
  const end = endOfDayInTimeZone(to, timeZone);
  return {
    createdAfter: start.toISOString(),
    createdBefore: end.toISOString(),
  };
}
