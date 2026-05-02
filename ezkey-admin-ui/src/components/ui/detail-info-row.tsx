import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

export function DetailInfoRow({
  label,
  children,
  labelClassName,
  valueClassName,
  className,
}: {
  label: string;
  children: ReactNode;
  labelClassName?: string;
  valueClassName?: string;
  className?: string;
}) {
  return (
    <div className={cn('flex gap-4', className)}>
      <dt
        className={cn(
          'w-36 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0',
          labelClassName,
        )}
      >
        {label}
      </dt>
      <dd className={cn('text-sm', valueClassName)}>{children}</dd>
    </div>
  );
}
