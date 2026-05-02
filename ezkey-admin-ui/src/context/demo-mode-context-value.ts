import { createContext } from 'react';

export interface DemoModeContextValue {
  sessionDemoOn: boolean;
  toggleSessionDemo: () => void;
}

export const defaultDemoModeContextValue: DemoModeContextValue = {
  sessionDemoOn: false,
  toggleSessionDemo: () => {},
};

export const DemoModeContext = createContext<DemoModeContextValue>(defaultDemoModeContextValue);
