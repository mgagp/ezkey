import { type ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface IntegratedDeliveryNoticeProps {
  /** Short collapsed label (e.g. "Integrated delivery"). */
  summary: string;
  /** Full educational body shown when expanded. */
  children: ReactNode;
  className?: string;
}

/**
 * Collapsed-by-default educational note for the integrated-delivery posture.
 * Keeps operator dialogs light while preserving full copy on expand.
 */
export function IntegratedDeliveryNotice({
  summary,
  children,
  className,
}: IntegratedDeliveryNoticeProps) {
  return (
    <details
      className={cn('text-xs text-fg-muted border-l-2 border-fg/25 pl-3 py-0.5', className)}
    >
      <summary className="cursor-pointer select-none font-semibold text-fg outline-none marker:text-fg-muted">
        {summary}
      </summary>
      <p className="mt-1.5 leading-relaxed">{children}</p>
    </details>
  );
}
