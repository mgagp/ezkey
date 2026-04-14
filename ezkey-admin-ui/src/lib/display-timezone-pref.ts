/**
 * Persisted preference for whether timestamps and date filters follow the browser or the tenant
 * business timezone (IANA from the tenant record).
 */

export type DisplayTimezoneMode = 'local' | 'tenant';

const STORAGE_KEY = 'ezkey_admin_display_tz_mode';

export function readDisplayTimezoneMode(): DisplayTimezoneMode | null {
  const v = window.localStorage.getItem(STORAGE_KEY);
  if (v === 'local' || v === 'tenant') {
    return v;
  }
  return null;
}

export function writeDisplayTimezoneMode(mode: DisplayTimezoneMode): void {
  window.localStorage.setItem(STORAGE_KEY, mode);
}

/** Default mode when no preference is stored yet. */
export function defaultDisplayTimezoneMode(adminType: string): DisplayTimezoneMode {
  if (adminType === 'TENANT_ADMIN' || adminType === 'INTEGRATION_ADMIN') {
    return 'tenant';
  }
  return 'local';
}
