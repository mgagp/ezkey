import { cn } from '@/lib/utils';
import { forwardRef, type ButtonHTMLAttributes } from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'destructive';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  isLoading?: boolean;
}

const variantClasses: Record<ButtonVariant, string> = {
  primary: [
    'bg-accent text-white border-2 border-fg',
    'shadow-brutal hover:shadow-brutal-lg hover:-translate-y-px',
    'active:shadow-none active:translate-y-0',
  ].join(' '),
  secondary: [
    'bg-surface text-fg border-2 border-fg',
    'shadow-brutal hover:shadow-brutal-lg hover:-translate-y-px',
    'active:shadow-none active:translate-y-0',
  ].join(' '),
  ghost: 'bg-transparent text-fg border-2 border-transparent hover:border-fg/20 hover:bg-fg/5',
  destructive: [
    'bg-error text-white border-2 border-error',
    'shadow-[3px_3px_0_#dc2626] hover:shadow-[5px_5px_0_#dc2626] hover:-translate-y-px',
    'active:shadow-none active:translate-y-0',
  ].join(' '),
};

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'px-3 py-1.5 text-xs',
  md: 'px-4 py-2 text-sm',
  lg: 'px-6 py-3 text-base',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', isLoading, disabled, children, ...props }, ref) => (
    <button
      ref={ref}
      disabled={disabled ?? isLoading}
      className={cn(
        'inline-flex items-center justify-center gap-2 font-bold tracking-wide cursor-pointer',
        'transition-all duration-100',
        'disabled:opacity-50 disabled:cursor-not-allowed disabled:shadow-none disabled:translate-y-0',
        variantClasses[variant],
        sizeClasses[size],
        className,
      )}
      {...props}
    >
      {isLoading && (
        <span className="size-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
      )}
      {children}
    </button>
  ),
);
Button.displayName = 'Button';
