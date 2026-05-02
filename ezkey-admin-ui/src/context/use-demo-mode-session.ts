import { useContext } from 'react';
import { DemoModeContext } from '@/context/demo-mode-context-value';
import type { DemoModeContextValue } from '@/context/demo-mode-context-value';

export function useDemoModeSession(): DemoModeContextValue {
  return useContext(DemoModeContext);
}
