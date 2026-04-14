import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

type BadgeVariant = 'default' | 'success' | 'error' | 'warning' | 'muted';

const variantClasses: Record<BadgeVariant, string> = {
  default: 'bg-fg text-surface',
  success: 'bg-success text-white',
  error: 'bg-error text-white',
  warning: 'bg-warning text-white',
  muted: 'bg-fg/10 text-fg-muted',
};

/**
 * Clickable dashboard stat badge: matches {@link Badge} visuals with link semantics and focus.
 */
export function DashboardStatBadgeLink({
  to,
  variant,
  children,
  ariaLabel,
  className,
}: {
  to: string;
  variant: BadgeVariant;
  children: ReactNode;
  ariaLabel: string;
  className?: string;
}) {
  return (
    <Link
      to={to}
      aria-label={ariaLabel}
      className={cn(
        'inline-flex items-center px-1.5 py-0.5 text-xs font-bold uppercase tracking-wide border border-black/10',
        'focus-visible:outline-offset-2 focus-visible:outline-2 focus-visible:outline-accent',
        'hover:opacity-90 hover:shadow-brutal transition cursor-pointer',
        variantClasses[variant],
        className,
      )}
    >
      {children}
    </Link>
  );
}
