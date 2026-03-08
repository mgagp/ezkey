import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Power, PowerOff, ShieldOff, Trash2, Users } from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { EnrollmentStatusBadge } from '@/components/feature/enrollment-status-badge';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useToast } from '@/context/toast-context';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { getIntegrationName } from '@/hooks/use-integrations';
import { getApiErrorMessage } from '@/lib/api-client';
import { formatDate } from '@/lib/utils';
import {
  deactivateAllEnrollments,
  delete1,
  getById1,
  reactivateAllEnrollments,
  revokeAllEnrollments,
} from '@/generated/admin-api/integrations/integrations';
import { search1 } from '@/generated/admin-api/enrollments/enrollments';
import type { EnrollmentResponseDto, IntegrationResponseDto, PagedModelEnrollmentResponseDto } from '@/generated/admin-api/model';

// ── Info row helper ──────────────────────────────────────────────────────────

function InfoRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-4">
      <dt className="w-32 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">
        {label}
      </dt>
      <dd className="text-sm">{children}</dd>
    </div>
  );
}

// ── Danger Zone Confirm Dialog ───────────────────────────────────────────────

function DangerConfirmDialog({
  open,
  onClose,
  title,
  description,
  confirmLabel,
  requireReason,
  optionalReason,
  isPending,
  isError,
  errorMessage,
  onConfirm,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description: string;
  confirmLabel: string;
  requireReason?: boolean;
  /** When true, reason field is shown but empty is allowed; if provided, min 10 chars. */
  optionalReason?: boolean;
  isPending: boolean;
  isError: boolean;
  errorMessage: string;
  onConfirm: (reason: string) => void;
}) {
  const [reason, setReason] = useState('');

  const showReason = requireReason || optionalReason;
  const reasonInvalid = optionalReason && reason.length > 0 && reason.length < 10;
  const disabled = requireReason ? reason.length < 10 : reasonInvalid;

  const handleClose = () => { setReason(''); onClose(); };

  return (
    <Dialog open={open} onClose={handleClose} title={title} size="sm">
      <div className="space-y-4">
        <div className="flex items-start gap-3">
          <AlertTriangle className="size-5 text-error shrink-0 mt-0.5" />
          <p className="text-sm">{description}</p>
        </div>
        {showReason && (
          <div className="space-y-1.5">
            <Label htmlFor="danger-reason">
              Reason {requireReason ? '(min 10 chars, required for audit)' : '(min 10 chars, for audit trail)'}
            </Label>
            <Input
              id="danger-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Justification..."
            />
          </div>
        )}
        {isError && <Alert variant="error">{errorMessage}</Alert>}
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={handleClose}>Cancel</Button>
          <Button
            variant="destructive"
            isLoading={isPending}
            disabled={disabled}
            onClick={() => onConfirm(reason)}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────

export default function IntegrationDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const integrationId = Number(id);

  const [dangerAction, setDangerAction] = useState<'revoke-all' | 'deactivate-all' | 'reactivate-all' | 'delete' | null>(null);

  const { data: integration, isLoading } = useQuery({
    queryKey: ['integration', integrationId],
    queryFn: () => getById1(integrationId) as Promise<IntegrationResponseDto>,
    enabled: !isNaN(integrationId),
  });

  const { data: enrollments, pagination: enrPagination, isLoading: loadingEnr } = usePaginatedFromOrval<EnrollmentResponseDto, { integrationId?: number }>({
    queryKey: ['enrollments', 'for-integration', integrationId],
    baseParams: { integrationId: isNaN(integrationId) ? undefined : integrationId },
    defaultSize: 10,
    fetchPage: (params) => search1(params) as Promise<PagedModelEnrollmentResponseDto>,
  });

  const enrollmentColumns: ColumnDef<EnrollmentResponseDto>[] = [
    { header: 'ID', key: 'enrollmentId', className: 'w-14', sortKey: 'enrollmentId', render: (r) => <span className="font-mono text-xs">{r.enrollmentId}</span> },
    { header: 'Name', key: 'enrollmentName', sortKey: 'enrollmentName', render: (r) => <span className="font-medium">{r.enrollmentName}</span> },
    { header: 'Status', key: 'enrollmentStatus', sortKey: 'status', render: (r) => <EnrollmentStatusBadge status={r.enrollmentStatus} /> },
    { header: 'Active', key: 'enrollmentActive', render: (r) => <Badge variant={r.enrollmentActive ? 'success' : 'muted'}>{r.enrollmentActive ? 'Yes' : 'No'}</Badge> },
    { header: 'Verified', key: 'verifiedAt', sortKey: 'verifiedAt', render: (r) => <span className="text-xs text-fg-muted">{r.verifiedAt ? formatDate(r.verifiedAt) : '—'}</span> },
  ];

  const name = integration ? getIntegrationName(integration) : '...';
  const isSystemIntegration = (integration as { isSystemIntegration?: boolean } | undefined)?.isSystemIntegration === true;

  return (
    <AppShell
      title={isLoading ? 'Integration' : name}
      breadcrumb={[{ label: 'Integrations', path: '/integrations' }]}
    >
      <div className="space-y-6">

        {integration && isSystemIntegration && (
          <Alert variant="info">
            This is the Ezkey system integration used for admin MFA. Admin enrollments are managed via the dedicated admin provisioning flow (Admins screen). Bulk actions and integration deletion are not available for this integration.
          </Alert>
        )}

        {/* Details */}
        {integration && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            <Card className="lg:col-span-2">
              <CardHeader><CardTitle>Integration Details</CardTitle></CardHeader>
              <CardContent>
                <dl className="space-y-3">
                  <InfoRow label="ID"><span className="font-mono">{integration.id}</span></InfoRow>
                  <InfoRow label="Code"><span className="font-mono">{integration.code}</span></InfoRow>
                  {integration.tenantId != null && (
                    <InfoRow label="Tenant ID"><span className="font-mono">{integration.tenantId}</span></InfoRow>
                  )}
                  <InfoRow label="Name">
                    <span className="font-medium">{integration.name ?? integration.code}</span>
                  </InfoRow>
                  {integration.description && (
                    <InfoRow label="Description">
                      <span className="text-fg-muted">{integration.description}</span>
                    </InfoRow>
                  )}
                  <InfoRow label="Status">
                    <Badge variant={integration.active ? 'success' : 'muted'}>
                      {integration.active ? 'Active' : 'Inactive'}
                    </Badge>
                  </InfoRow>
                  <InfoRow label="Created">
                    <span className="text-fg-muted">{formatDate(integration.createdAt ?? '')}</span>
                  </InfoRow>
                </dl>
              </CardContent>
            </Card>

            <Card>
              <CardHeader><CardTitle>Actions</CardTitle></CardHeader>
              <CardContent>
                <div className="space-y-2">
                  <Button
                    variant="secondary"
                    size="sm"
                    className="w-full justify-start gap-2"
                    onClick={() => navigate(`/enrollments?integrationId=${integration.id}`)}
                  >
                    <Users className="size-3.5" />
                    View All Enrollments
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        )}

        {/* Enrollments section */}
        <div className="border-2 border-fg shadow-brutal bg-surface">
          <div className="flex items-center justify-between px-4 py-3 border-b-2 border-fg bg-bg">
            <h3 className="text-xs font-black uppercase tracking-widest text-fg-muted">
              Enrollments for this Integration
            </h3>
            {!isSystemIntegration && (
              <Button
                size="sm"
                onClick={() => navigate(`/enrollments?integrationId=${integrationId}`)}
              >
                <Users className="size-3.5" />
                New Enrollment
              </Button>
            )}
          </div>
          <DataTable
            columns={enrollmentColumns}
            data={enrollments}
            isLoading={loadingEnr}
            onRowClick={(row) => navigate(`/enrollments/${row.enrollmentId}`)}
            keyExtractor={(row, i) => row.enrollmentId ?? i}
            emptyMessage="No enrollments for this integration yet."
            currentSort={enrPagination.sort}
            onSort={enrPagination.setSort}
          />
          <Pagination
            page={enrPagination.page}
            totalPages={enrPagination.totalPages}
            totalElements={enrPagination.totalElements}
            isFirst={enrPagination.isFirst}
            isLast={enrPagination.isLast}
            onPrevPage={enrPagination.prevPage}
            onNextPage={enrPagination.nextPage}
            pageSize={enrPagination.size}
            onPageSizeChange={enrPagination.setPageSize}
          />
        </div>

        {/* Danger Zone — hidden for system integration */}
        {integration && !isSystemIntegration && (
          <Card className="border-error">
            <CardHeader><CardTitle>Danger Zone</CardTitle></CardHeader>
            <CardContent>
              <div className="space-y-3">
                <p className="text-xs text-fg-muted">
                  Mass operations affect <strong>all enrollments</strong> under this integration. Use with extreme caution.
                </p>
                <div className="flex flex-wrap gap-2">
                  <Button variant="destructive" size="sm" className="gap-1.5" onClick={() => setDangerAction('deactivate-all')}>
                    <PowerOff className="size-3.5" />
                    Deactivate All
                  </Button>
                  <Button variant="secondary" size="sm" className="gap-1.5" onClick={() => setDangerAction('reactivate-all')}>
                    <Power className="size-3.5" />
                    Reactivate All
                  </Button>
                  <Button variant="destructive" size="sm" className="gap-1.5" onClick={() => setDangerAction('revoke-all')}>
                    <ShieldOff className="size-3.5" />
                    Revoke All
                  </Button>
                </div>
                <div className="border-t-2 border-error/20 pt-3">
                  <p className="text-xs text-fg-muted mb-2">
                    Deleting this integration will remove it permanently. All associated enrollments must be handled first.
                  </p>
                  <Button variant="destructive" size="sm" className="gap-1.5" onClick={() => setDangerAction('delete')}>
                    <Trash2 className="size-3.5" />
                    Delete Integration
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>
        )}
      </div>

      {/* Danger confirm dialogs */}
      <DangerConfirmDialog
        open={dangerAction === 'deactivate-all'}
        onClose={() => setDangerAction(null)}
        title="Deactivate All Enrollments"
        description="All active enrollments under this integration will be deactivated. Users will no longer be able to authenticate until reactivated."
        confirmLabel="Deactivate All"
        optionalReason
        isPending={false}
        isError={false}
        errorMessage=""
        onConfirm={(reason) => {
          const params = reason.trim().length >= 10 ? { reason: reason.trim() } : {};
          deactivateAllEnrollments(Number(integrationId), params)
            .then(() => {
              void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
              toast('All enrollments deactivated.');
              setDangerAction(null);
            })
            .catch((e) => toast(getApiErrorMessage(e, 'Failed.'), 'error'));
        }}
      />
      <DangerConfirmDialog
        open={dangerAction === 'reactivate-all'}
        onClose={() => setDangerAction(null)}
        title="Reactivate All Enrollments"
        description="All deactivated enrollments under this integration will be reactivated."
        confirmLabel="Reactivate All"
        optionalReason
        isPending={false}
        isError={false}
        errorMessage=""
        onConfirm={(reason) => {
          const params = reason.trim().length >= 10 ? { reason: reason.trim() } : {};
          reactivateAllEnrollments(Number(integrationId), params)
            .then(() => {
              void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
              toast('All enrollments reactivated.');
              setDangerAction(null);
            })
            .catch((e) => toast(getApiErrorMessage(e, 'Failed.'), 'error'));
        }}
      />
      <DangerConfirmDialog
        open={dangerAction === 'revoke-all'}
        onClose={() => setDangerAction(null)}
        title="Revoke All Enrollments"
        description="All enrollments will be PERMANENTLY and IRREVERSIBLY revoked. All users under this integration will lose authentication access. New enrollments must be created for re-enrolment."
        confirmLabel="Revoke All Permanently"
        optionalReason
        isPending={false}
        isError={false}
        errorMessage=""
        onConfirm={(reason) => {
          const params = reason.trim().length >= 10 ? { reason: reason.trim() } : undefined;
          revokeAllEnrollments(Number(integrationId), params)
            .then(() => {
              void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
              toast('All enrollments permanently revoked.', 'error');
              setDangerAction(null);
            })
            .catch((e) => toast(getApiErrorMessage(e, 'Failed.'), 'error'));
        }}
      />
      <DangerConfirmDialog
        open={dangerAction === 'delete'}
        onClose={() => setDangerAction(null)}
        title="Delete Integration"
        description={`Are you sure you want to permanently delete integration "${integration?.name ?? integration?.code}"? This action cannot be undone.`}
        confirmLabel="Yes, Delete Integration"
        optionalReason
        isPending={false}
        isError={false}
        errorMessage=""
        onConfirm={(reason) => {
          const params = reason.trim().length >= 10 ? { reason: reason.trim() } : undefined;
          delete1(Number(integrationId), params)
            .then(() => {
              void queryClient.invalidateQueries({ queryKey: ['integrations'] });
              toast('Integration deleted.');
              navigate('/integrations');
            })
            .catch((e) => toast(getApiErrorMessage(e, 'Failed to delete.'), 'error'));
        }}
      />
    </AppShell>
  );
}
