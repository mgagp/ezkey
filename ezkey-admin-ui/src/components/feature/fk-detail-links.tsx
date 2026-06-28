import { Link } from 'react-router-dom';
import { adminListDetailHref } from '@/lib/list-detail-navigation';

interface AdminFkLinkProps {
  adminId: number;
  username?: string | null;
  fallbackLabel: string;
  /** When true, link using fallbackLabel if username is absent (entity likely exists). Default: plain #id (deleted admin). */
  linkWhenNameMissing?: boolean;
}

/**
 * Operator-facing link to an administrator detail (list dialog via {@code ?adminId=}).
 * Uses username when the API enriched the FK; otherwise plain {@code #id} when the admin row is
 * gone, or {@code fallbackLabel} as link when {@code linkWhenNameMissing} is set.
 */
export function AdminFkLink({
  adminId,
  username,
  fallbackLabel,
  linkWhenNameMissing = false,
}: AdminFkLinkProps) {
  const label = username?.trim();
  if (label) {
    return (
      <Link
        to={adminListDetailHref(adminId)}
        className="font-medium text-accent hover:underline"
      >
        {label}
        <span className="ml-1.5 font-mono text-xs text-fg-muted">(ID {adminId})</span>
      </Link>
    );
  }
  if (linkWhenNameMissing) {
    return (
      <Link
        to={adminListDetailHref(adminId)}
        className="font-medium text-accent hover:underline"
      >
        {fallbackLabel}
      </Link>
    );
  }
  return <span className="font-mono text-fg-muted">#{adminId}</span>;
}

interface EnrollmentFkLinkProps {
  enrollmentId: number;
  enrollmentName?: string | null;
  fallbackLabel: string;
}

/** Link to enrollment detail page with name + ID when enriched, or fallback label only. */
export function EnrollmentFkLink({
  enrollmentId,
  enrollmentName,
  fallbackLabel,
}: EnrollmentFkLinkProps) {
  const trimmed = enrollmentName?.trim();
  return (
    <Link
      to={`/enrollments/${enrollmentId}`}
      className="font-medium text-accent hover:underline"
    >
      {trimmed ? (
        <>
          {trimmed}
          <span className="ml-1.5 font-mono text-xs text-fg-muted">(ID {enrollmentId})</span>
        </>
      ) : (
        fallbackLabel
      )}
    </Link>
  );
}
