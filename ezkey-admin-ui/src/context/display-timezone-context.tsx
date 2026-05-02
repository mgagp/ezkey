import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useGetTenant } from '@/generated/admin-api/tenants/tenants';
import type { TenantResponseDto } from '@/generated/admin-api/model';
import { useAuth } from '@/context/use-auth';
import {
  defaultDisplayTimezoneMode,
  type DisplayTimezoneMode,
  readDisplayTimezoneMode,
  writeDisplayTimezoneMode,
} from '@/lib/display-timezone-pref';
import { setDisplayTimeZoneResolver } from '@/lib/display-timezone-resolver';
import { DisplayTimezoneContext } from '@/context/display-timezone-context-value';
import type { DisplayTimezoneContextValue } from '@/context/display-timezone-context-value';

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

  const [modeOverride, setModeState] = useState<DisplayTimezoneMode | null>(() =>
    readDisplayTimezoneMode(),
  );
  const mode = session
    ? modeOverride ?? defaultDisplayTimezoneMode(session.adminType)
    : 'local';

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

