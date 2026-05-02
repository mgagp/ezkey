import { useState, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, Key, ListOrdered, Power, PowerOff, ShieldOff, Trash2, Users } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { DemoReasonBadges } from '@/components/feature/demo-reason-badges';
import { OperationalWarning } from '@/components/feature/operational-warning';
import { ReasonFieldRow } from '@/components/feature/reason-field-row';
import { AppShell } from '@/components/layout/app-shell';
import { ContextHelp } from '@/components/ui/context-help';
import { type ColumnDef } from '@/components/data-table/data-table';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { DevicePrivateKeyTierBadge } from '@/components/feature/device-private-key-tier-badge';
import { EnrollmentStatusBadge } from '@/components/feature/enrollment-status-badge';
import { RelatedDetailsButton } from '@/components/feature/related-details-button';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { useToast } from '@/context/use-toast';
import { useExpandableRelatedDetails } from '@/hooks/use-expandable-related-details';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { getIntegrationName } from '@/hooks/use-integrations';
import { useDemoModeSession } from '@/context/use-demo-mode-session';
import { api } from '@/lib/api-client';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import { isDemoMode } from '@/lib/demo-mode';
import { useListDetailPageNavigation } from '@/hooks/use-list-detail-page-navigation';
import { buildListDetailNavState } from '@/lib/list-detail-navigation';
import type { ReasonPresetGroupId } from '@/lib/reason-preset-groups';
import { formatDate } from '@/lib/utils';
import { DetailPageNav } from '@/components/ui/detail-page-nav';
import {
  delete1,
  getById1,
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

type BulkEnrollmentOperationResult = {
  affectedCount: number;
  skippedCount: number;
  noOp: boolean;
};

function buildBulkEnrollmentOperationPath(
  integrationId: number,
  action: 'deactivate-all' | 'reactivate-all' | 'revoke-all',
  reason: string,
): string {
  const params = new URLSearchParams();
  if (reason.trim().length >= 10) {
    params.set('reason', reason.trim());
  }

  const query = params.toString();
  const basePath = `/api/v1/integrations/${integrationId}/enrollments/${action}`;
  return query ? `${basePath}?${query}` : basePath;
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
  reasonLabel,
  reasonPlaceholder,
  cancelLabel,
  isPending,
  isError,
  errorMessage,
  onConfirm,
  renderReasonBadges,
  reasonPresetGroup,
  reasonFieldIdPrefix = 'danger-confirm',
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description: string;
  confirmLabel: string;
  requireReason?: boolean;
  /** When true, reason field is shown but empty is allowed; if provided, min 10 chars. */
  optionalReason?: boolean;
  reasonLabel?: string;
  reasonPlaceholder?: string;
  cancelLabel?: string;
  isPending: boolean;
  isError: boolean;
  errorMessage: string;
  onConfirm: (reason: string) => void;
  /** Demo mode: render quick-select reason badges (receives setReason). */
  renderReasonBadges?: (setReason: (value: string) => void) => ReactNode;
  /** When set, shows production quick-pick presets above the reason field. */
  reasonPresetGroup?: ReasonPresetGroupId;
  /** Stable prefix for reason input and quick-pick ids (unique per dialog instance). */
  reasonFieldIdPrefix?: string;
}) {
  const [reason, setReason] = useState('');

  const showReason = requireReason || optionalReason;
  const trimmed = reason.trim();
  const reasonInvalid = optionalReason && trimmed.length > 0 && trimmed.length < 10;
  const disabled = requireReason ? trimmed.length < 10 : reasonInvalid;

  const handleClose = () => { setReason(''); onClose(); };

  return (
    <Dialog open={open} onClose={handleClose} title={title} size="sm">
      <div className="space-y-4">
        <div className="flex items-start gap-3">
          <AlertTriangle className="size-5 text-error shrink-0 mt-0.5" />
          <p className="text-sm">{description}</p>
        </div>
        {showReason && (
          <ReasonFieldRow
            presetGroup={reasonPresetGroup}
            idPrefix={reasonFieldIdPrefix}
            inputId={`${reasonFieldIdPrefix}-reason`}
            value={reason}
            onChange={setReason}
            label={reasonLabel ?? (requireReason ? 'Reason (min 10 chars, required for audit)' : 'Reason (min 10 chars, for audit trail)')}
            placeholder={reasonPlaceholder ?? 'Justification...'}
            showMinLengthError={trimmed.length > 0 && trimmed.length < 10}
            childrenAfterInput={renderReasonBadges?.(setReason)}
          />
        )}
        {isError && <Alert variant="error">{errorMessage}</Alert>}
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={handleClose}>{cancelLabel ?? 'Cancel'}</Button>
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
  const { t } = useTranslation('integrations');
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { sessionDemoOn } = useDemoModeSession();
  const renderReasonBadges =
    isDemoMode && sessionDemoOn
      ? (setReason: (value: string) => void) => <DemoReasonBadges onSelect={setReason} />
      : undefined;
  const { toast } = useToast();
  const integrationId = Number(id);

  const { nav: integrationListNav, goPrev: goPrevIntegration, goNext: goNextIntegration, showEndOfPageHint: showIntegrationListEndHint } =
    useListDetailPageNavigation({
      currentId: integrationId,
      pathPrefix: '/integrations',
    });

  const [dangerAction, setDangerAction] = useState<'revoke-all' | 'deactivate-all' | 'reactivate-all' | 'retire' | 'delete' | null>(null);

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
    { header: t('detail.enrollmentColumns.id'), key: 'enrollmentId', className: 'w-14', sortKey: 'enrollmentId', render: (r) => <span className="font-mono text-xs">{r.enrollmentId}</span> },
    { header: t('detail.enrollmentColumns.name'), key: 'enrollmentName', sortKey: 'enrollmentName', render: (r) => <span className="font-medium">{r.enrollmentName}</span> },
    { header: t('detail.enrollmentColumns.status'), key: 'enrollmentStatus', sortKey: 'status', render: (r) => <EnrollmentStatusBadge status={r.enrollmentStatus} /> },
    { header: t('detail.enrollmentColumns.active'), key: 'enrollmentActive', render: (r) => <Badge variant={r.enrollmentActive ? 'success' : 'muted'}>{r.enrollmentActive ? t('detail.activeYes') : t('detail.activeNo')}</Badge> },
    { header: t('detail.enrollmentColumns.verified'), key: 'verifiedAt', sortKey: 'verifiedAt', render: (r) => <span className="text-xs text-fg-muted">{r.verifiedAt ? formatDate(r.verifiedAt) : '—'}</span> },
    {
      header: t('detail.enrollmentColumns.keyTier'),
      key: 'devicePrivateKeyStorageTier',
      render: (r) => <DevicePrivateKeyTierBadge tier={r.devicePrivateKeyStorageTier} />,
    },
  ];

  const name = integration ? getIntegrationName(integration) : '...';
  const isSystemIntegration = (integration as { isSystemIntegration?: boolean } | undefined)?.isSystemIntegration === true;
  const isRetired = integration?.lifecycleStatus === 'RETIRED';

  const relatedDetails = useExpandableRelatedDetails({
    tenantId: integration?.tenantId ?? undefined,
  });

  return (
    <AppShell
      title={isLoading ? t('detail.fallbackTitle') : name}
      detailNav={
        integrationListNav ? (
          <DetailPageNav
            hasPrev={integrationListNav.prevId !== undefined}
            hasNext={integrationListNav.nextId !== undefined}
            onPrev={goPrevIntegration}
            onNext={goNextIntegration}
            showEndOfPageHint={showIntegrationListEndHint}
          />
        ) : undefined
      }
      breadcrumb={[{ label: t('detail.breadcrumbIntegrations'), path: '/integrations' }]}
    >
      <div className="space-y-6">

        {integration && isSystemIntegration && (
          <Alert variant="info">
            {t('detail.systemIntegrationAlert')}
          </Alert>
        )}

        {/* Details */}
        {integration && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            <Card className="lg:col-span-2">
              <CardHeader className="flex flex-row items-center justify-between gap-2">
                <CardTitle>{t('detail.integrationDetails')}</CardTitle>
                {relatedDetails.hasAnyFk && (
                  <RelatedDetailsButton
                    onClick={relatedDetails.expand}
                    isExpanded={relatedDetails.isExpanded}
                    isLoading={relatedDetails.isLoading}
                  />
                )}
              </CardHeader>
              <CardContent>
                <dl className="space-y-3">
                  <InfoRow label={t('detail.infoId')}><span className="font-mono">{integration.id}</span></InfoRow>
                  <InfoRow label={t('detail.infoCode')}><span className="font-mono">{integration.code}</span></InfoRow>
                  {integration.tenantId != null && (
                    <InfoRow label={t('detail.infoTenantId')}><span className="font-mono">{integration.tenantId}</span></InfoRow>
                  )}
                  {relatedDetails.isExpanded && relatedDetails.tenant && (
                    <InfoRow label={t('common:detail.relatedTenant')}>
                      <Link
                        to={`/tenants/${relatedDetails.tenant.tenantId}`}
                        className="font-medium text-accent hover:underline"
                      >
                        {relatedDetails.tenant.tenantName ?? relatedDetails.tenant.tenantId} (ID {relatedDetails.tenant.tenantId})
                      </Link>
                    </InfoRow>
                  )}
                  <InfoRow label={t('detail.infoName')}>
                    <span className="font-medium">{integration.name ?? integration.code}</span>
                  </InfoRow>
                  {integration.description && (
                    <InfoRow label={t('detail.infoDescription')}>
                      <span className="text-fg-muted">{integration.description}</span>
                    </InfoRow>
                  )}
                  <InfoRow label={t('detail.infoStatus')}>
                    <Badge variant={integration.lifecycleStatus === 'ACTIVE' ? 'success' : 'muted'}>
                      {integration.lifecycleStatus === 'ACTIVE' ? t('list.statusActive') : t('list.statusRetired')}
                    </Badge>
                  </InfoRow>
                  <InfoRow label={t('detail.infoCreated')}>
                    <span className="text-fg-muted">{formatDate(integration.createdAt ?? '')}</span>
                  </InfoRow>
                </dl>
              </CardContent>
            </Card>

            <Card>
              <CardHeader><CardTitle>{t('detail.actions')}</CardTitle></CardHeader>
              <CardContent>
                <div className="space-y-2">
                  <Button
                    variant="secondary"
                    size="sm"
                    className="w-full justify-start gap-2"
                    onClick={() => navigate(`/enrollments?integrationId=${integration.id}`)}
                  >
                    <Users className="size-3.5" />
                    {t('detail.viewAllEnrollments')}
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    className="w-full justify-start gap-2"
                    onClick={() => navigate(`/audit-logs?integrationId=${integration.id}&source=integration-detail`)}
                  >
                    <ListOrdered className="size-3.5" />
                    {t('detail.viewRelatedAudits')}
                  </Button>
                  {!isRetired && (
                    <Button
                      variant="secondary"
                      size="sm"
                      className="w-full justify-start gap-2"
                      onClick={() => navigate(`/api-keys?create=1&integrationId=${integration.id}`)}
                    >
                      <Key className="size-3.5" />
                      {t('detail.createApiKey')}
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          </div>
        )}

        {integration && !isRetired && integration.operational === false && (
          <OperationalWarning message={t('detail.operationalWarning.tenantInactive')} />
        )}

        {/* Enrollments section */}
        <div className="border-2 border-fg shadow-brutal bg-surface">
          <div className="flex items-center justify-between px-4 py-3 border-b-2 border-fg bg-bg">
            <h3 className="text-xs font-black uppercase tracking-widest text-fg-muted">
              {t('detail.enrollmentsSectionTitle')}
            </h3>
            {integration && !isSystemIntegration && !isRetired && (
              <Button
                size="sm"
                onClick={() => navigate(`/enrollments?integrationId=${integrationId}`)}
              >
                <Users className="size-3.5" />
                {t('detail.newEnrollment')}
              </Button>
            )}
          </div>
          <PaginatedTable
            columns={enrollmentColumns}
            data={enrollments}
            isLoading={loadingEnr}
            onRowClick={(row) => {
              const st = buildListDetailNavState(enrollments, (r) => r.enrollmentId ?? 0, row, !enrPagination.isLast);
              navigate(`/enrollments/${row.enrollmentId}`, { state: st ?? undefined });
            }}
            keyExtractor={(row, i) => row.enrollmentId ?? i}
            emptyMessage={t('detail.enrollmentsEmpty')}
            currentSort={enrPagination.sort}
            onSort={enrPagination.setSort}
            pagination={enrPagination}
          />
        </div>

        {/* Danger Zone — hidden for system integration */}
        {integration && !isSystemIntegration && (
          <Card className="border-error">
            <CardHeader><CardTitle>{t('detail.dangerZone')}</CardTitle></CardHeader>
            <CardContent>
              <div className="space-y-3">
                <p className="text-xs text-fg-muted">
                  {isRetired ? t('detail.retiredIntro') : t('detail.dangerZoneIntro')}
                </p>
                {!isRetired && (
                  <>
                    <div className="flex flex-wrap gap-2">
                      <Button variant="destructive" size="sm" className="gap-1.5" onClick={() => setDangerAction('deactivate-all')}>
                        <PowerOff className="size-3.5" />
                        {t('detail.deactivateAll')}
                      </Button>
                      <Button variant="secondary" size="sm" className="gap-1.5" onClick={() => setDangerAction('reactivate-all')}>
                        <Power className="size-3.5" />
                        {t('detail.reactivateAll')}
                      </Button>
                      <Button variant="destructive" size="sm" className="gap-1.5" onClick={() => setDangerAction('revoke-all')}>
                        <ShieldOff className="size-3.5" />
                        {t('detail.revokeAll')}
                      </Button>
                    </div>
                    <div className="flex items-center gap-2">
                      <Button variant="destructive" size="sm" className="gap-1.5" onClick={() => setDangerAction('retire')}>
                        <Trash2 className="size-3.5" />
                        {t('detail.retireIntegration')}
                      </Button>
                      <ContextHelp
                        title={t('detail.contextHelp.retireTitle')}
                        content={t('detail.contextHelp.retireContent')}
                      />
                    </div>
                  </>
                )}
                <div className="border-t-2 border-error/20 pt-3">
                  <p className="text-xs text-fg-muted mb-2">
                    {t('detail.deleteIntro')}
                  </p>
                  {isRetired && (
                    <div className="flex items-center gap-2">
                      <Button variant="destructive" size="sm" className="gap-1.5" onClick={() => setDangerAction('delete')}>
                        <Trash2 className="size-3.5" />
                        {t('detail.deleteIntegration')}
                      </Button>
                      <ContextHelp
                        title={t('detail.contextHelp.deleteTitle')}
                        content={t('detail.contextHelp.deleteContent')}
                      />
                    </div>
                  )}
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
        title={t('detail.danger.deactivateAllTitle')}
        description={t('detail.danger.deactivateAllDescription')}
        confirmLabel={t('detail.danger.deactivateAllConfirm')}
        optionalReason
        reasonLabel={t('detail.danger.reasonLabelOptional')}
        reasonPlaceholder={t('detail.danger.reasonPlaceholder')}
        cancelLabel={t('detail.danger.cancel')}
        reasonPresetGroup="integration_bulk"
        reasonFieldIdPrefix="integration-danger-deactivate-all"
        isPending={false}
        isError={false}
        errorMessage=""
        renderReasonBadges={renderReasonBadges}
        onConfirm={(reason) => {
          api
            .post<BulkEnrollmentOperationResult>(
              buildBulkEnrollmentOperationPath(Number(integrationId), 'deactivate-all', reason),
              undefined,
            )
            .then((result) => {
              if (!result.noOp) {
                void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
              }
              toast(
                result.noOp
                  ? t('detail.toastDeactivatedNoOp')
                  : t('detail.toastDeactivated', { count: result.affectedCount }),
              );
              setDangerAction(null);
            })
            .catch((e) => toast(getTranslatedApiError(e, t, t('detail.errorFailed')), 'error'));
        }}
      />
      <DangerConfirmDialog
        open={dangerAction === 'reactivate-all'}
        onClose={() => setDangerAction(null)}
        title={t('detail.danger.reactivateAllTitle')}
        description={t('detail.danger.reactivateAllDescription')}
        confirmLabel={t('detail.danger.reactivateAllConfirm')}
        optionalReason
        reasonLabel={t('detail.danger.reasonLabelOptional')}
        reasonPlaceholder={t('detail.danger.reasonPlaceholder')}
        cancelLabel={t('detail.danger.cancel')}
        reasonPresetGroup="integration_bulk"
        reasonFieldIdPrefix="integration-danger-reactivate-all"
        isPending={false}
        isError={false}
        errorMessage=""
        renderReasonBadges={renderReasonBadges}
        onConfirm={(reason) => {
          api
            .post<BulkEnrollmentOperationResult>(
              buildBulkEnrollmentOperationPath(Number(integrationId), 'reactivate-all', reason),
              undefined,
            )
            .then((result) => {
              if (!result.noOp) {
                void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
              }
              toast(
                result.noOp
                  ? t('detail.toastReactivatedNoOp')
                  : t('detail.toastReactivated', { count: result.affectedCount }),
              );
              setDangerAction(null);
            })
            .catch((e) => toast(getTranslatedApiError(e, t, t('detail.errorFailed')), 'error'));
        }}
      />
      <DangerConfirmDialog
        open={dangerAction === 'revoke-all'}
        onClose={() => setDangerAction(null)}
        title={t('detail.danger.revokeAllTitle')}
        description={t('detail.danger.revokeAllDescription')}
        confirmLabel={t('detail.danger.revokeAllConfirm')}
        optionalReason
        reasonLabel={t('detail.danger.reasonLabelOptional')}
        reasonPlaceholder={t('detail.danger.reasonPlaceholder')}
        cancelLabel={t('detail.danger.cancel')}
        reasonPresetGroup="integration_bulk"
        reasonFieldIdPrefix="integration-danger-revoke-all"
        isPending={false}
        isError={false}
        errorMessage=""
        renderReasonBadges={renderReasonBadges}
        onConfirm={(reason) => {
          api
            .post<BulkEnrollmentOperationResult>(
              buildBulkEnrollmentOperationPath(Number(integrationId), 'revoke-all', reason),
              undefined,
            )
            .then((result) => {
              if (!result.noOp) {
                void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
              }
              toast(
                result.noOp
                  ? t('detail.toastRevokedNoOp')
                  : t('detail.toastRevoked', { count: result.affectedCount }),
                'error',
              );
              setDangerAction(null);
            })
            .catch((e) => toast(getTranslatedApiError(e, t, t('detail.errorFailed')), 'error'));
        }}
      />
      <DangerConfirmDialog
        open={dangerAction === 'retire'}
        onClose={() => setDangerAction(null)}
        title={t('detail.danger.retireTitle')}
        description={t('detail.danger.retireDescription')}
        confirmLabel={t('detail.danger.retireConfirm')}
        requireReason
        reasonLabel={t('detail.danger.reasonLabelRequired')}
        reasonPlaceholder={t('detail.danger.reasonPlaceholder')}
        cancelLabel={t('detail.danger.cancel')}
        reasonPresetGroup="integration_retire"
        reasonFieldIdPrefix="integration-danger-retire"
        isPending={false}
        isError={false}
        errorMessage=""
        renderReasonBadges={renderReasonBadges}
        onConfirm={(reason) => {
          const path = `/api/v1/integrations/${integrationId}/retire?reason=${encodeURIComponent(reason.trim())}`;
          api
            .post<void>(path, undefined)
            .then(() => {
              void queryClient.invalidateQueries({ queryKey: ['integration', integrationId] });
              void queryClient.invalidateQueries({ queryKey: ['integrations'] });
              void queryClient.invalidateQueries({ queryKey: ['integrations-all'] });
              void queryClient.invalidateQueries({ queryKey: ['enrollments'] });
              toast(t('detail.toastRetired'));
              setDangerAction(null);
            })
            .catch((e) => toast(getTranslatedApiError(e, t, t('detail.errorFailed')), 'error'));
        }}
      />
      <DangerConfirmDialog
        open={dangerAction === 'delete'}
        onClose={() => setDangerAction(null)}
        title={t('detail.danger.deleteTitle')}
        description={t('detail.danger.deleteDescription', { name: integration?.name ?? integration?.code ?? '' })}
        confirmLabel={t('detail.danger.deleteConfirm')}
        requireReason
        reasonLabel={t('detail.danger.reasonLabelRequired')}
        reasonPlaceholder={t('detail.danger.reasonPlaceholder')}
        cancelLabel={t('detail.danger.cancel')}
        reasonPresetGroup="integration_delete"
        reasonFieldIdPrefix="integration-danger-delete"
        isPending={false}
        isError={false}
        errorMessage=""
        renderReasonBadges={renderReasonBadges}
        onConfirm={(reason) => {
          delete1(Number(integrationId), { reason: reason.trim() })
            .then(() => {
              void queryClient.invalidateQueries({ queryKey: ['integrations'] });
              toast(t('detail.toastDeleted'));
              navigate('/integrations');
            })
            .catch((e) => toast(getTranslatedApiError(e, t, t('detail.errorDeleteFailed')), 'error'));
        }}
      />
    </AppShell>
  );
}
