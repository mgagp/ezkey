/**
 * Deep-link builders for dashboard stat badges → list screens with filters aligned to dashboard
 * semantics (entity rows / rolling 24h windows).
 */

export const DASHBOARD_DRILLDOWN_SOURCE = 'dashboard';

/** Query param: reuse rolling last-24h window on each fetch (matches dashboard auth-24h stats). */
export const ROLLING_24H_PRESET_PARAM = 'preset';
export const ROLLING_24H_PRESET_VALUE = 'rolling24h';

/** Enrollment list: combined status buckets without a single API status filter. */
export const ENROLLMENT_BUCKET_PARAM = 'bucket';
export type EnrollmentDrilldownBucket = 'inProgress' | 'unavailable' | 'incidents';

/** Integration list filter key (matches {@link IntegrationListFilter} in integrations page). */
export const INTEGRATION_LIFECYCLE_FILTER_PARAM = 'lifecycleFilter';

export type IntegrationLifecycleFilterParam = 'active' | 'retired' | 'all';

/**
 * Rolling window aligned with {@code OffsetDateTime.now().minusHours(24)} to {@code now} on the
 * server (dashboard auth-24h aggregate).
 */
export function getRolling24HoursWindowIso(): { createdAfter: string; createdBefore: string } {
  const now = new Date();
  return {
    createdAfter: new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString(),
    createdBefore: now.toISOString(),
  };
}

function appendIfDefined(params: URLSearchParams, key: string, value: string | undefined | null): void {
  if (value != null && value !== '') {
    params.set(key, value);
  }
}

export function buildAuthAttemptsDrilldownUrl(options: {
  status?: string;
  /** When true, add rolling 24h preset (recommended for dashboard auth badges). */
  rolling24h?: boolean;
}): string {
  const params = new URLSearchParams();
  appendIfDefined(params, 'status', options.status);
  if (options.rolling24h) {
    params.set(ROLLING_24H_PRESET_PARAM, ROLLING_24H_PRESET_VALUE);
  }
  params.set('source', DASHBOARD_DRILLDOWN_SOURCE);
  const q = params.toString();
  return q ? `/auth-attempts?${q}` : '/auth-attempts';
}

export function buildEnrollmentsDrilldownUrl(options: {
  status?: string;
  /** Explicit active flag — use false for suspended (VERIFIED + inactive) drilldown. */
  active?: boolean;
  bucket?: EnrollmentDrilldownBucket;
}): string {
  const params = new URLSearchParams();
  appendIfDefined(params, 'status', options.status);
  if (options.active === false) {
    params.set('active', 'false');
  }
  if (options.bucket) {
    params.set(ENROLLMENT_BUCKET_PARAM, options.bucket);
  }
  params.set('source', DASHBOARD_DRILLDOWN_SOURCE);
  const q = params.toString();
  return q ? `/enrollments?${q}` : '/enrollments';
}

export function buildIntegrationsDrilldownUrl(lifecycleFilter: IntegrationLifecycleFilterParam): string {
  const params = new URLSearchParams();
  params.set(INTEGRATION_LIFECYCLE_FILTER_PARAM, lifecycleFilter);
  params.set('source', DASHBOARD_DRILLDOWN_SOURCE);
  return `/integrations?${params.toString()}`;
}

/**
 * Audit log trail for auth-attempt events (not 1:1 with auth-attempt row counts). Uses the same
 * rolling 24h window as dashboard when {@link ROLLING_24H_PRESET_PARAM} is set.
 */
export function buildAuthAttemptAuditTrailUrl(): string {
  const { createdAfter, createdBefore } = getRolling24HoursWindowIso();
  const params = new URLSearchParams();
  params.set('eventType', 'AUTH_ATTEMPT');
  params.set('createdAfter', createdAfter);
  params.set('createdBefore', createdBefore);
  params.set('source', DASHBOARD_DRILLDOWN_SOURCE);
  return `/audit-logs?${params.toString()}`;
}
