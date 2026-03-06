import { cn } from '@/lib/utils';
import { ChevronDown } from 'lucide-react';
import { forwardRef, type SelectHTMLAttributes } from 'react';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  error?: string;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, error, children, ...props }, ref) => (
    <div className="relative w-full">
      <select
        ref={ref}
        className={cn(
          'w-full px-3 py-2 text-sm bg-surface text-fg appearance-none',
          'border-2 border-fg outline-none pr-8',
          'focus:shadow-accent focus:border-accent',
          'transition-shadow duration-100',
          'disabled:opacity-50 disabled:cursor-not-allowed',
          error && 'border-error',
          className,
        )}
        {...props}
      >
        {children}
      </select>
      <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 size-4 pointer-events-none text-fg-muted" />
      {error && <p className="mt-1 text-xs text-error font-bold">{error}</p>}
    </div>
  ),
);
Select.displayName = 'Select';
