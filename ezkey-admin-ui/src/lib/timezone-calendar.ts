/**
 * Calendar helpers for a specific IANA time zone (business / tenant view).
 * Used when the display timezone preference is "tenant" so presets align with tenant-local dates.
 */

const ymdFormatters = new Map<string, Intl.DateTimeFormat>();
const weekdayFormatters = new Map<string, Intl.DateTimeFormat>();

function getYmdFormatter(timeZone: string): Intl.DateTimeFormat {
  const cached = ymdFormatters.get(timeZone);
  if (cached) return cached;
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  ymdFormatters.set(timeZone, formatter);
  return formatter;
}

function getWeekdayFormatter(timeZone: string): Intl.DateTimeFormat {
  const cached = weekdayFormatters.get(timeZone);
  if (cached) return cached;
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone,
    weekday: 'short',
  });
  weekdayFormatters.set(timeZone, formatter);
  return formatter;
}

/** Format an instant as YYYY-MM-DD in the given IANA time zone. */
export function formatYmdInTimeZone(date: Date, timeZone: string): string {
  const parts = getYmdFormatter(timeZone).formatToParts(date);
  const y = parts.find((p) => p.type === 'year')?.value;
  const m = parts.find((p) => p.type === 'month')?.value;
  const d = parts.find((p) => p.type === 'day')?.value;
  return `${y}-${m}-${d}`;
}

/** Add calendar days to a YYYY-MM-DD string (Gregorian, no TZ conversion). */
export function addDaysYmd(ymd: string, deltaDays: number): string {
  const [y, m, d] = ymd.split('-').map(Number);
  const utc = Date.UTC(y, m - 1, d + deltaDays);
  const dt = new Date(utc);
  return `${dt.getUTCFullYear()}-${String(dt.getUTCMonth() + 1).padStart(2, '0')}-${String(dt.getUTCDate()).padStart(2, '0')}`;
}

/**
 * Smallest UTC instant whose calendar date in {@code timeZone} equals {@code ymd}.
 * Uses binary search on UTC milliseconds.
 */
export function startOfDayInTimeZone(ymd: string, timeZone: string): Date {
  const [y, m, d] = ymd.split('-').map(Number);
  let lo = Date.UTC(y, m - 1, d - 1, 0, 0, 0);
  let hi = Date.UTC(y, m - 1, d + 1, 23, 59, 59);
  while (lo < hi) {
    const mid = Math.floor((lo + hi) / 2);
    const midYmd = formatYmdInTimeZone(new Date(mid), timeZone);
    if (midYmd < ymd) {
      lo = mid + 1;
    } else {
      hi = mid;
    }
  }
  return new Date(lo);
}

/** Last millisecond of {@code ymd} in {@code timeZone}. */
export function endOfDayInTimeZone(ymd: string, timeZone: string): Date {
  const next = addDaysYmd(ymd, 1);
  const startNext = startOfDayInTimeZone(next, timeZone);
  return new Date(startNext.getTime() - 1);
}

/** Weekday 0=Sunday … 6=Saturday for {@code ymd} interpreted in {@code timeZone}. */
export function weekdaySun0InZone(ymd: string, timeZone: string): number {
  const t = startOfDayInTimeZone(ymd, timeZone);
  const w = getWeekdayFormatter(timeZone).format(t);
  const map: Record<string, number> = {
    Sun: 0,
    Mon: 1,
    Tue: 2,
    Wed: 3,
    Thu: 4,
    Fri: 5,
    Sat: 6,
  };
  return map[w] ?? 0;
}
