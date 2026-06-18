import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { isDemoMode } from '@/lib/demo-mode';
import { DemoModeContext } from '@/context/demo-mode-context-value';

interface DemoModeProviderProps {
  children: ReactNode;
}

/**
 * When VITE_DEMO_MODE is true, provides session-level toggle for demo mode (Ctrl+click on sidebar brand).
 * When false, children render unchanged and consumers get default (sessionDemoOn: false).
 * Demo-mode code is stripped in production builds.
 */
export function DemoModeProvider({ children }: DemoModeProviderProps) {
  if (!isDemoMode) {
    return <>{children}</>;
  }
  return <DemoModeProviderInner>{children}</DemoModeProviderInner>;
}

function DemoModeProviderInner({ children }: DemoModeProviderProps) {
  const [sessionDemoOn, setSessionDemoOn] = useState(false);
  const toggleSessionDemo = useCallback(() => {
    setSessionDemoOn((prev) => !prev);
  }, []);
  const value = useMemo(
    () => ({ sessionDemoOn, toggleSessionDemo }),
    [sessionDemoOn, toggleSessionDemo],
  );
  return (
    <DemoModeContext.Provider value={value}>
      {children}
    </DemoModeContext.Provider>
  );
}
