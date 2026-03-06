import { cn } from '@/lib/utils';
import { forwardRef, type InputHTMLAttributes } from 'react';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, error, ...props }, ref) => (
    <div className="w-full">
      <input
        ref={ref}
        className={cn(
          'w-full px-3 py-2 text-sm bg-surface text-fg',
          'border-2 border-fg outline-none',
          'placeholder:text-fg-muted font-medium',
          'focus:shadow-accent focus:border-accent',
          'transition-shadow duration-100',
          'disabled:opacity-50 disabled:cursor-not-allowed',
          error && 'border-error focus:shadow-[3px_3px_0_#dc2626]',
          className,
        )}
        {...props}
      />
      {error && <p className="mt-1 text-xs text-error font-bold">{error}</p>}
    </div>
  ),
);
Input.displayName = 'Input';
