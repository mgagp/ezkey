import { cn } from '@/lib/utils';
import { AlertCircle, AlertTriangle, CheckCircle, Info } from 'lucide-react';
import { type HTMLAttributes } from 'react';

type AlertVariant = 'info' | 'success' | 'error' | 'warning';

interface AlertProps extends HTMLAttributes<HTMLDivElement> {
  variant?: AlertVariant;
  title?: string;
}

const config = {
  info: { icon: Info, classes: 'bg-blue-50 border-blue-500 text-blue-800' },
  success: { icon: CheckCircle, classes: 'bg-green-50 border-success text-green-800' },
  error: { icon: AlertCircle, classes: 'bg-red-50 border-error text-red-800' },
  warning: { icon: AlertTriangle, classes: 'bg-amber-50 border-warning text-amber-800' },
} satisfies Record<AlertVariant, { icon: typeof Info; classes: string }>;

export function Alert({ className, variant = 'info', title, children, ...props }: AlertProps) {
  const { icon: Icon, classes } = config[variant];
  return (
    <div
      role="alert"
      className={cn('flex gap-3 p-3 border-2 text-sm', classes, className)}
      {...props}
    >
      <Icon className="size-4 shrink-0 mt-0.5" />
      <div>
        {title && <p className="font-bold mb-0.5">{title}</p>}
        <p>{children}</p>
      </div>
    </div>
  );
}
