import { cn } from '@/lib/utils';
import { X } from 'lucide-react';
import { type ReactNode, useEffect } from 'react';

interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  /** `lg-wide` ≈ 126.5% of `lg` (max-w-2xl): 115% then +10% for content-heavy read-only modals. */
  size?: 'sm' | 'md' | 'lg' | 'lg-wide';
  /** When false, backdrop click and Escape key will not close the dialog. Defaults to true. */
  dismissible?: boolean;
  /** Optional actions (e.g. prev/next) rendered in the header between the title and the close button. */
  headerActions?: ReactNode;
}

const sizeClasses: Record<NonNullable<DialogProps['size']>, string> = {
  sm: 'max-w-sm',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
  /** 42rem × 1.15 × 1.10 */
  'lg-wide': 'max-w-[53.13rem]',
};

/**
 * Modal dialog with neo-brutalism styling.
 * Closes on backdrop click or Escape key unless `dismissible` is set to false.
 */
export function Dialog({
  open,
  onClose,
  title,
  children,
  size = 'md',
  dismissible = true,
  headerActions,
}: DialogProps) {
  useEffect(() => {
    if (!open || !dismissible) return;
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [open, onClose, dismissible]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-fg/40"
        onClick={dismissible ? onClose : undefined}
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
        <div className="flex items-center justify-between gap-2 px-5 py-3 border-b-2 border-fg shrink-0">
          <h2 className="text-xs font-black uppercase tracking-widest text-fg min-w-0 flex-1 truncate">{title}</h2>
          {headerActions != null ? <div className="flex items-center gap-1 shrink-0">{headerActions}</div> : null}
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
