import { useState } from 'react';
import { ShieldCheck, Info } from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { usePaginatedQuery } from '@/hooks/use-paginated-query';
import { api } from '@/lib/api-client';
import { formatDate, formatRelativeTime } from '@/lib/utils';
import type { PageResponse } from '@/types/api';
import type { AuditLog } from '@/types/models';

// ── Event type options (from EventType.java enum) ─────────────────────────────

const EVENT_TYPES = [
  ['ADMIN_LOGIN', 'Admin Login'],
  ['ADMIN_LOGOUT', 'Admin Logout'],
  ['ADMIN_PASSWORD_CHANGE', 'Admin Password Change'],
  ['ADMIN_RECOVERY_USE', 'Admin Recovery Use'],
  ['ENROLLMENT_CREATED', 'Enrollment Created'],
  ['ENROLLMENT_DELETED', 'Enrollment Deleted'],
  ['ENROLLMENT_BIND', 'Enrollment Bind'],
  ['ENROLLMENT_VERIFY', 'Enrollment Verify'],
  ['AUTH_ATTEMPT_CREATED', 'Auth Attempt Created'],
  ['AUTH_ATTEMPT_PENDING', 'Auth Attempt Pending'],
  ['AUTH_ATTEMPT_RESPOND', 'Auth Attempt Respond'],
  ['AUTH_ATTEMPT_CANCELLED', 'Auth Attempt Cancelled'],
  ['API_KEY_CREATED', 'API Key Created'],
  ['API_KEY_REVOKED', 'API Key Revoked'],
  ['API_KEY_EXPIRED', 'API Key Expired'],
  ['API_KEY_AUTH_SUCCESS', 'API Key Auth Success'],
  ['API_KEY_AUTH_FAILED', 'API Key Auth Failed'],
  ['API_KEY_IP_BLOCKED', 'API Key IP Blocked'],
  ['SYSTEM_ERROR', 'System Error'],
] as const;

function formatEventType(et: string): string {
  return et.split('_').map((w) => w.charAt(0) + w.slice(1).toLowerCase()).join(' ');
}

// ── Event status badge ────────────────────────────────────────────────────────

function EventStatusBadge({ status }: { status: AuditLog['eventStatus'] }) {
  if (status === 'SUCCESS') return <Badge variant="success">Success</Badge>;
  if (status === 'FAILURE') return <Badge variant="error">Failure</Badge>;
  return <Badge variant="error">Error</Badge>;
}

// ── Detail dialog ─────────────────────────────────────────────────────────────

function AuditLogDetailDialog({ log, onClose }: { log: AuditLog | null; onClose: () => void }) {
  if (!log) return null;

  function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
    return (
      <div className="flex gap-4">
        <dt className="w-36 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">{label}</dt>
        <dd className="text-sm break-all">{children}</dd>
      </div>
    );
  }

  return (
    <Dialog open={log !== null} onClose={onClose} title={`Log #${log.auditLogId}`} size="lg">
      <dl className="space-y-2.5">
        <InfoRow label="ID"><span className="font-mono">{log.auditLogId}</span></InfoRow>
        <InfoRow label="Event Type">
          <span className="font-mono text-xs bg-fg/5 px-1.5 py-0.5">{log.eventType}</span>
        </InfoRow>
        <InfoRow label="Status"><EventStatusBadge status={log.eventStatus} /></InfoRow>
        {log.apiName && <InfoRow label="API"><Badge variant="muted">{log.apiName}</Badge></InfoRow>}
        {log.adminId && <InfoRow label="Admin ID"><span className="font-mono">#{log.adminId}</span></InfoRow>}
        {log.integrationId && <InfoRow label="Integration"><span className="font-mono">#{log.integrationId}</span></InfoRow>}
        {log.enrollmentId && <InfoRow label="Enrollment"><span className="font-mono">#{log.enrollmentId}</span></InfoRow>}
        {log.authAttemptId && <InfoRow label="Auth Attempt"><span className="font-mono">#{log.authAttemptId}</span></InfoRow>}
        {log.ipAddress && <InfoRow label="IP Address"><span className="font-mono text-xs">{log.ipAddress}</span></InfoRow>}
        {log.userAgent && <InfoRow label="User Agent"><span className="text-xs text-fg-muted">{log.userAgent}</span></InfoRow>}
        {log.eventDetails && (
          <InfoRow label="Details">
            <pre className="text-xs bg-fg/5 p-2 overflow-auto max-h-32 whitespace-pre-wrap">{log.eventDetails}</pre>
          </InfoRow>
        )}
        {log.errorMessage && (
          <InfoRow label="Error">
            <span className="text-xs text-error">{log.errorMessage}</span>
          </InfoRow>
        )}
        <InfoRow label="Created"><span className="text-fg-muted">{formatDate(log.createdAt)}</span></InfoRow>
        <InfoRow label="HMAC Integrity">
          {log.entryHmac ? (
            <div className="flex items-center gap-1.5">
              <ShieldCheck className="size-3.5 text-success" />
              <span className="text-xs text-success font-bold">Chain intact</span>
            </div>
          ) : (
            <span className="text-xs text-fg-muted">Not available</span>
          )}
        </InfoRow>
        {log.instanceId && (
          <InfoRow label="Instance"><span className="font-mono text-xs text-fg-muted">{log.instanceId}</span></InfoRow>
        )}
      </dl>
      <div className="flex justify-end pt-4">
        <Button onClick={onClose}>Close</Button>
      </div>
    </Dialog>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function AuditLogsPage() {
  const [eventTypeFilter, setEventTypeFilter] = useState('');
  const [eventStatusFilter, setEventStatusFilter] = useState('');
  const [apiNameFilter, setApiNameFilter] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);

  const { data, pagination, isLoading, refetch } = usePaginatedQuery<AuditLog>({
    queryKey: ['audit-logs', eventTypeFilter, eventStatusFilter, apiNameFilter, dateFrom, dateTo],
    queryFn: ({ page, size, sort }) => {
      const p = new URLSearchParams({ page: String(page), size: String(size), sort });
      if (eventTypeFilter) p.set('eventType', eventTypeFilter);
      if (eventStatusFilter) p.set('eventStatus', eventStatusFilter);
      if (apiNameFilter) p.set('apiName', apiNameFilter);
      if (dateFrom) p.set('createdAfter', new Date(dateFrom).toISOString());
      if (dateTo) p.set('createdBefore', new Date(dateTo + 'T23:59:59').toISOString());
      return api.get<PageResponse<AuditLog>>(`/api/v1/audit-logs?${p.toString()}`);
    },
  });

  const columns: ColumnDef<AuditLog>[] = [
    { header: 'ID', key: 'auditLogId', className: 'w-14', render: (r) => <span className="font-mono text-xs">{r.auditLogId}</span> },
    {
      header: 'Event',
      key: 'eventType',
      render: (r) => (
        <span className="font-mono text-xs">{formatEventType(r.eventType)}</span>
      ),
    },
    { header: 'Status', key: 'eventStatus', render: (r) => <EventStatusBadge status={r.eventStatus} /> },
    { header: 'API', key: 'apiName', render: (r) => r.apiName ? <Badge variant="muted">{r.apiName}</Badge> : <span className="text-fg-muted">—</span> },
    {
      header: 'Admin',
      key: 'adminId',
      render: (r) => r.adminId ? <span className="font-mono text-xs">#{r.adminId}</span> : <span className="text-fg-muted">—</span>,
    },
    {
      header: 'HMAC',
      key: 'entryHmac',
      render: (r) =>
          r.entryHmac ? (
            <ShieldCheck className="size-3.5 text-success" />
          ) : (
          <span className="text-fg-muted text-xs">—</span>
        ),
    },
    { header: 'Time', key: 'createdAt', render: (r) => <span className="text-xs text-fg-muted">{formatRelativeTime(r.createdAt)}</span> },
    {
      header: '',
      key: 'detail',
      render: (r) => (
        <Button
          variant="ghost"
          size="sm"
          className="p-1"
          onClick={(e) => { e.stopPropagation(); setSelectedLog(r); }}
          title="View details"
        >
          <Info className="size-3.5" />
        </Button>
      ),
    },
  ];

  return (
    <AppShell title="Audit Logs">
      <div className="space-y-4">

        {/* Filter bar */}
        <div className="flex gap-3 items-center flex-wrap">
          <div className="w-52">
            <Select value={eventTypeFilter} onChange={(e) => setEventTypeFilter(e.target.value)}>
              <option value="">All Event Types</option>
              {EVENT_TYPES.map(([val, label]) => (
                <option key={val} value={val}>{label}</option>
              ))}
            </Select>
          </div>
          <div className="w-32">
            <Select value={eventStatusFilter} onChange={(e) => setEventStatusFilter(e.target.value)}>
              <option value="">All Status</option>
              <option value="SUCCESS">Success</option>
              <option value="FAILURE">Failure</option>
              <option value="ERROR">Error</option>
            </Select>
          </div>
          <div className="w-28">
            <Select value={apiNameFilter} onChange={(e) => setApiNameFilter(e.target.value)}>
              <option value="">All APIs</option>
              <option value="ADMIN">Admin</option>
              <option value="AUTH">Auth</option>
            </Select>
          </div>
          <div className="flex items-center gap-2">
            <Label className="text-xs shrink-0">From</Label>
            <Input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} className="w-36" />
          </div>
          <div className="flex items-center gap-2">
            <Label className="text-xs shrink-0">To</Label>
            <Input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} className="w-36" />
          </div>
          <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5 ml-auto">
            <ShieldCheck className="size-3.5" />
            Refresh
          </Button>
        </div>

        <p className="text-xs text-fg-muted italic">
          Read-only · Tenant-scoped · <span className="inline-flex items-center gap-1"><ShieldCheck className="size-3 text-success inline" /> HMAC</span> = tamper-evident chain intact for that entry.
        </p>

        <div>
          <DataTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => setSelectedLog(row)}
            keyExtractor={(r) => r.auditLogId}
            emptyMessage="No audit log entries found for the selected filters."
          />
          <Pagination
            page={pagination.page}
            totalPages={pagination.totalPages}
            totalElements={pagination.totalElements}
            isFirst={pagination.isFirst}
            isLast={pagination.isLast}
            onPrevPage={pagination.prevPage}
            onNextPage={pagination.nextPage}
          />
        </div>
      </div>

      <AuditLogDetailDialog log={selectedLog} onClose={() => setSelectedLog(null)} />
    </AppShell>
  );
}
