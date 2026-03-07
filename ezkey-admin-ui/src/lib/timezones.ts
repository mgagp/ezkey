/**
 * Time zone options for tenant forms (IANA time zone identifiers).
 * Uses Intl.supportedValuesOf('timeZone') so the list matches the runtime and stays up to date.
 */

let cachedZones: string[] | null = null;

/**
 * Returns sorted IANA time zone identifiers for dropdowns.
 * Uses the browser's Intl implementation (same identifiers as Java ZoneId, etc.).
 */
export function getTimeZoneOptions(): string[] {
  if (cachedZones !== null) return cachedZones;
  try {
    const zones = Intl.supportedValuesOf('timeZone') as string[];
    cachedZones = [...zones].sort((a, b) => a.localeCompare(b));
  } catch {
    // Fallback for very old runtimes that don't support supportedValuesOf
    cachedZones = [
      'Africa/Cairo', 'Africa/Johannesburg', 'America/Los_Angeles', 'America/New_York',
      'America/Chicago', 'America/Denver', 'America/Toronto', 'Asia/Dubai', 'Asia/Tokyo',
      'Asia/Shanghai', 'Asia/Kolkata', 'Australia/Sydney', 'Europe/London', 'Europe/Paris',
      'Europe/Berlin', 'Europe/Moscow', 'Pacific/Auckland', 'UTC',
    ].sort((a, b) => a.localeCompare(b));
  }
  return cachedZones;
}

/** Quick-access time zones: North America (reduced list). */
const QUICK_NA_ZONES = [
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/Toronto',
  'America/Montreal',
] as const;

/** Quick-access time zones: Europe (France & nearby), reduced list. */
const QUICK_EUROPE_ZONES = [
  'Europe/Paris',
  'Europe/Brussels',
  'Europe/Luxembourg',
  'Europe/Zurich',
  'Europe/Berlin',
  'Europe/London',
] as const;

export type TimeZoneOptionsGrouped = {
  quickNorthAmerica: string[];
  quickEurope: string[];
  all: string[];
};

/**
 * Returns time zone options grouped for quick-access optgroups (North America, Europe) then all.
 */
export function getTimeZoneOptionsGrouped(): TimeZoneOptionsGrouped {
  const all = getTimeZoneOptions();
  return {
    quickNorthAmerica: [...QUICK_NA_ZONES],
    quickEurope: [...QUICK_EUROPE_ZONES],
    all,
  };
}
