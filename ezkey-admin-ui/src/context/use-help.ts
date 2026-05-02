import { useContext } from 'react';
import { HelpContext } from '@/context/help-context-value';
import type { HelpContextValue } from '@/context/help-context-value';

export function useHelp(): HelpContextValue {
  const ctx = useContext(HelpContext);
  if (!ctx) {
    throw new Error('useHelp must be used within HelpProvider');
  }
  return ctx;
}
