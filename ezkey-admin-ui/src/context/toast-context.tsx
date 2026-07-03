import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { CheckCircle, AlertCircle, Info, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { ToastContext, type Toast, type ToastVariant } from '@/context/toast-context-value';

// ── Types ─────────────────────────────────────────────────────────────────────

// ── Toast item ────────────────────────────────────────────────────────────────

const TOAST_DURATION_MS: Record<ToastVariant, number> = {
  success: 4000,
  error: 6000,
  info: 4000,
};

function ToastItem({ toast, onRemove }: { toast: Toast; onRemove: (id: string) => void }) {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const duration = TOAST_DURATION_MS[toast.variant];
    timerRef.current = setTimeout(() => onRemove(toast.id), duration);
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [toast.id, toast.variant, onRemove]);

  const Icon =
    toast.variant === 'success' ? CheckCircle :
    toast.variant === 'error'   ? AlertCircle :
    Info;

  const styles: Record<ToastVariant, string> = {
    success: 'border-success bg-surface text-fg',
    error: 'border-error bg-surface text-fg',
    info: 'border-accent bg-surface text-fg',
  };

  const iconStyles: Record<ToastVariant, string> = {
    success: 'text-success',
    error:   'text-error',
    info:    'text-accent',
  };

  return (
    <div
      className={cn(
        'flex items-start gap-3 px-4 py-3 border-2 shadow-brutal-lg min-w-72 max-w-96',
        'animate-[slideIn_200ms_ease-out]',
        styles[toast.variant],
      )}
      role="alert"
    >
      <Icon className={cn('size-4 shrink-0 mt-0.5', iconStyles[toast.variant])} />
      <span className="text-sm flex-1 leading-snug">{toast.message}</span>
      <button
        type="button"
        onClick={() => onRemove(toast.id)}
        className="shrink-0 text-fg/40 hover:text-fg transition-colors"
        aria-label="Dismiss"
      >
        <X className="size-3.5" />
      </button>
    </div>
  );
}

// ── Provider ──────────────────────────────────────────────────────────────────

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const remove = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const toast = useCallback((message: string, variant: ToastVariant = 'success') => {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    setToasts((prev) => [...prev, { id, message, variant }]);
  }, []);

  const value = useMemo(() => ({ toast }), [toast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      {/* Toast container — bottom-right */}
      <div className="fixed bottom-6 right-6 z-50 flex flex-col gap-2 items-end pointer-events-none">
        {toasts.map((t) => (
          <div key={t.id} className="pointer-events-auto">
            <ToastItem toast={t} onRemove={remove} />
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
