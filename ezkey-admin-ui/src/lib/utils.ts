import { type ClassValue, clsx } from 'clsx';
import i18n from 'i18next';
import { twMerge } from 'tailwind-merge';

/** Merge Tailwind classes safely — the standard shadcn/ui utility. */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

/**
 * Resolves the current UI locale and Intl date options for date/time formatting.
 * French (fr, fr-CA) uses 24-hour format; English uses default (12h for en-CA).
 */
function getDateLocaleAndOptions(): { locale: string; hour12: boolean } {
  const lang = i18n.language?.split('-')[0] ?? 'en';
  if (lang === 'fr') {
    return { locale: 'fr-CA', hour12: false };
  }
  return { locale: 'en-CA', hour12: true };
}

/** Format an ISO date string to a human-readable local date/time (locale-aware, 24h for French). */
export function formatDate(dateStr: string): string {
  const { locale, hour12 } = getDateLocaleAndOptions();
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12,
  }).format(new Date(dateStr));
}

/**
 * Format an ISO date string in local time with an explicit timezone label (e.g. "11 Mar 2025, 09:00 (EST)").
 * Use in audit/chain contexts so operators know whether they are looking at local time or UTC when
 * correlating with DB or logs (DB stores UTC). Locale-aware; 24h for French.
 */
export function formatDateWithTimezone(dateStr: string): string {
  const date = new Date(dateStr);
  const { locale, hour12 } = getDateLocaleAndOptions();
  const parts = new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12,
    timeZoneName: 'short',
  }).formatToParts(date);
  const dateTime = parts
    .filter((p) => p.type !== 'timeZoneName')
    .map((p) => p.value)
    .join('');
  const tzPart = parts.find((p) => p.type === 'timeZoneName');
  const tz = tzPart?.value ?? 'UTC';
  return `${dateTime.trim()} (${tz})`;
}

/** Format seconds remaining as MM:SS for countdown display. */
export function formatCountdown(seconds: number): string {
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

/** Format a 2-digit challenge code with zero-padding. */
export function formatChallengeCode(code: number): string {
  return String(code).padStart(2, '0');
}

/** Localized relative time (e.g. "5m ago" / "Il y a 5 min") using common:date keys. */
export function formatRelativeTime(dateStr: string): string {
  const diffMs = Date.now() - new Date(dateStr).getTime();
  const diffMins = Math.floor(diffMs / 60_000);
  if (diffMins < 1) return i18n.t('common:date.justNow');
  if (diffMins < 60) return i18n.t('common:date.minutesAgo', { count: diffMins });
  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return i18n.t('common:date.hoursAgo', { count: diffHours });
  const days = Math.floor(diffHours / 24);
  return i18n.t('common:date.daysAgo', { count: days });
}
