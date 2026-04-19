import { Alert } from '@/components/ui/alert';
import { cn } from '@/lib/utils';

interface OperationalWarningProps {
  /** Explanation message shown to the operator. */
  message: string;
  className?: string;
}

/**
 * Compact warning strip indicating that an entity's own status looks healthy but authentication
 * is currently blocked because a parent entity (tenant or integration) is inactive or retired.
 * Renders as a standard warning Alert. Only render this when operational === false while the
 * entity's own native status is healthy — never show it when the entity itself is the problem.
 */
export function OperationalWarning({ message, className }: OperationalWarningProps) {
  return (
    <Alert variant="warning" className={cn('text-xs py-2', className)}>
      {message}
    </Alert>
  );
}
