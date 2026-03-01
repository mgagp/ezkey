import { cn } from '@/lib/utils';
import { X } from 'lucide-react';
import { type ReactNode, useEffect } from 'react';

interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  size?: 'sm' | 'md' | 'lg';
}

const sizeClasses: Record<NonNullable<DialogProps['size']>, string> = {
  sm: 'max-w-sm',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
};

/**
 * Modal dialog with neo-brutalism styling.
 * Closes on backdrop click or Escape key.
 */
export function Dialog({ open, onClose, title, children, size = 'md' }: DialogProps) {
  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-fg/40"
        onClick={onClose}
        aria-hidden="true"
      />
      {/* Panel */}
      <div
        role="dialog"
        aria-modal="true"
        className={cn(
          'relative z-10 w-full bg-surface border-2 border-fg shadow-brutal-lg max-h-[90vh] flex flex-col',
          sizeClasses[size],
        )}
      >
        <div className="flex items-center justify-between px-5 py-3 border-b-2 border-fg shrink-0">
          <h2 className="text-xs font-black uppercase tracking-widest text-fg">{title}</h2>
          <button
            onClick={onClose}
            className="p-1 hover:bg-fg/10 transition-colors"
            aria-label="Close dialog"
          >
            <X className="size-4" />
          </button>
        </div>
        <div className="p-5 overflow-y-auto">{children}</div>
      </div>
    </div>
  );
}
