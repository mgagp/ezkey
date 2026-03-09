import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { isDemoMode } from '@/lib/demo-mode';

interface DemoModeContextValue {
  sessionDemoOn: boolean;
  toggleSessionDemo: () => void;
}

const defaultValue: DemoModeContextValue = {
  sessionDemoOn: false,
  toggleSessionDemo: () => {},
};

const DemoModeContext = createContext<DemoModeContextValue>(defaultValue);

export function useDemoModeSession(): DemoModeContextValue {
  return useContext(DemoModeContext);
}

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
  return (
    <DemoModeContext.Provider value={{ sessionDemoOn, toggleSessionDemo }}>
      {children}
    </DemoModeContext.Provider>
  );
}
