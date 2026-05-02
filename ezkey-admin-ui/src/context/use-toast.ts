import { useContext } from 'react';
import { ToastContext } from '@/context/toast-context-value';
import type { ToastContextValue } from '@/context/toast-context-value';

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside ToastProvider');
  return ctx;
}
