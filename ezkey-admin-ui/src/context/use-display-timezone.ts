import { useContext } from 'react';
import { DisplayTimezoneContext } from '@/context/display-timezone-context-value';
import type { DisplayTimezoneContextValue } from '@/context/display-timezone-context-value';

export function useDisplayTimezone(): DisplayTimezoneContextValue {
  const ctx = useContext(DisplayTimezoneContext);
  if (!ctx) {
    throw new Error('useDisplayTimezone must be used within DisplayTimezoneProvider');
  }
  return ctx;
}
