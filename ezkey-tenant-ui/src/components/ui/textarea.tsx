import { cn } from '@/lib/utils';
import { forwardRef, type TextareaHTMLAttributes } from 'react';

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  error?: string;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, error, ...props }, ref) => (
    <div className="w-full">
      <textarea
        ref={ref}
        className={cn(
          'w-full px-3 py-2 text-sm bg-surface text-fg',
          'border-2 border-fg outline-none resize-y min-h-24',
          'focus:shadow-accent focus:border-accent',
          'transition-shadow duration-100',
          'disabled:opacity-50 disabled:cursor-not-allowed',
          error && 'border-error',
          className,
        )}
        {...props}
      />
      {error && <p className="mt-1 text-xs text-error font-bold">{error}</p>}
    </div>
  ),
);
Textarea.displayName = 'Textarea';
