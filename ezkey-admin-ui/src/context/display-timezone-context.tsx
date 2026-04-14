import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useGetTenant } from '@/generated/admin-api/tenants/tenants';
import type { TenantResponseDto } from '@/generated/admin-api/model';
import { useAuth } from '@/context/auth-context';
import {
  defaultDisplayTimezoneMode,
  type DisplayTimezoneMode,
  readDisplayTimezoneMode,
  writeDisplayTimezoneMode,
} from '@/lib/display-timezone-pref';
import { setDisplayTimeZoneResolver } from '@/lib/display-timezone-resolver';

export interface DisplayTimezoneContextValue {
  /** User preference: browser local vs tenant business timezone. */
  mode: DisplayTimezoneMode;
  setMode: (mode: DisplayTimezoneMode) => void;
  /** Resolved IANA id passed to Intl when in tenant mode; undefined means browser local. */
  effectiveTimeZoneId: string | undefined;
  /** Tenant record timezone string when loaded (may be empty). */
  tenantTimeZoneRaw: string | undefined;
}

const DisplayTimezoneContext = createContext<DisplayTimezoneContextValue | null>(null);

function isValidIanaZone(id: string): boolean {
  try {
    Intl.DateTimeFormat(undefined, { timeZone: id }).format(0);
    return true;
  } catch {
    return false;
  }
}

export function DisplayTimezoneProvider({ children }: { children: ReactNode }) {
  const { session } = useAuth();
  const tenantId = session?.tenantId;

  const [mode, setModeState] = useState<DisplayTimezoneMode>('local');

  useEffect(() => {
    if (!session) {
      setModeState('local');
      return;
    }
    const saved = readDisplayTimezoneMode();
    const def = defaultDisplayTimezoneMode(session.adminType);
    setModeState(saved ?? def);
  }, [session]);

  const { data: tenant } = useGetTenant<TenantResponseDto>(
    typeof tenantId === 'number' && !Number.isNaN(tenantId) ? tenantId : 0,
    {
      query: {
        enabled: typeof tenantId === 'number' && tenantId > 0 && mode === 'tenant',
      },
    },
  );

  const tenantTimeZoneRaw = tenant?.timezone?.trim() || undefined;

  const effectiveTimeZoneId = useMemo(() => {
    if (mode === 'local') {
      return undefined;
    }
    if (tenantTimeZoneRaw && isValidIanaZone(tenantTimeZoneRaw)) {
      return tenantTimeZoneRaw;
    }
    return undefined;
  }, [mode, tenantTimeZoneRaw]);

  useEffect(() => {
    setDisplayTimeZoneResolver(() => effectiveTimeZoneId);
    return () => {
      setDisplayTimeZoneResolver(() => undefined);
    };
  }, [effectiveTimeZoneId]);

  const setMode = useCallback((next: DisplayTimezoneMode) => {
    setModeState(next);
    writeDisplayTimezoneMode(next);
  }, []);

  const value = useMemo(
    () =>
      ({
        mode,
        setMode,
        effectiveTimeZoneId,
        tenantTimeZoneRaw,
      }) satisfies DisplayTimezoneContextValue,
    [mode, setMode, effectiveTimeZoneId, tenantTimeZoneRaw],
  );

  return (
    <DisplayTimezoneContext.Provider value={value}>{children}</DisplayTimezoneContext.Provider>
  );
}

export function useDisplayTimezone(): DisplayTimezoneContextValue {
  const ctx = useContext(DisplayTimezoneContext);
  if (!ctx) {
    throw new Error('useDisplayTimezone must be used within DisplayTimezoneProvider');
  }
  return ctx;
}
