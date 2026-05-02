import { createContext } from 'react';
import type { DisplayTimezoneMode } from '@/lib/display-timezone-pref';

export interface DisplayTimezoneContextValue {
  /** User preference: browser local vs tenant business timezone. */
  mode: DisplayTimezoneMode;
  setMode: (mode: DisplayTimezoneMode) => void;
  /** Resolved IANA id passed to Intl when in tenant mode; undefined means browser local. */
  effectiveTimeZoneId: string | undefined;
  /** Tenant record timezone string when loaded (may be empty). */
  tenantTimeZoneRaw: string | undefined;
}

export const DisplayTimezoneContext = createContext<DisplayTimezoneContextValue | null>(null);
