import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  Key,
  RefreshCw,
  Play,
  RotateCcw,
  ChevronDown,
  ChevronUp,
  AlertTriangle,
  ListPlus,
  Loader2,
} from 'lucide-react';
import { AppShell } from '@/components/layout/app-shell';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ContextHelp } from '@/components/ui/context-help';
import { Dialog } from '@/components/ui/dialog';
import { Tooltip } from '@/components/ui/tooltip';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { getApiErrorMessage } from '@/lib/api-client';
import { ENCRYPTION_KEYS_SECTION_HELP } from '@/lib/help-text';
import { formatDate, formatRelativeTime } from '@/lib/utils';
import { useToast } from '@/context/toast-context';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import {
  listKeys,
  useCreateBatches,
  useGetKey,
  useListBatches,
  useResumeBatch,
  useRotateKey,
  useTriggerFullReencryption,
  useTriggerReencryptionForKey,
} from '@/generated/admin-api/encryption-keys/encryption-keys';
import type {
  EncryptionKeyResponse,
  KeyRotationResponse,
  PagedModelEncryptionKeyResponse,
  ReencryptionBatchResponse,
  ReencryptionTriggerResponse,
  ReencryptionKeyResponse,
  BatchResumeResponse,
  BatchCreationResponse,
} from '@/generated/admin-api/model';

// ── Helpers ───────────────────────────────────────────────────────────────────

function KeyStatusBadge({ status }: { status?: string }) {
  const { t } = useTranslation('encryption-keys');
  if (status === 'PRIMARY') return <Tooltip content={t('keyStatus.helpPrimary')}><Badge variant="success">{t('keyStatus.labelPrimary')}</Badge></Tooltip>;
  if (status === 'ENABLED') return <Tooltip content={t('keyStatus.helpEnabled')}><Badge variant="muted">{t('keyStatus.labelEnabled')}</Badge></Tooltip>;
  if (status === 'DISABLED') return <Tooltip content={t('keyStatus.helpDisabled')}><Badge variant="error">{t('keyStatus.labelDisabled')}</Badge></Tooltip>;
  if (status === 'PENDING') return <Tooltip content={t('keyStatus.helpPending')}><Badge variant="muted">{t('keyStatus.labelPending')}</Badge></Tooltip>;
  return <Badge variant="muted">{status ?? '—'}</Badge>;
}

function BatchStatusBadge({ status }: { status?: string }) {
  const { t } = useTranslation('encryption-keys');
  if (status === 'COMPLETED') return <Tooltip content={t('batchStatus.helpCompleted')}><Badge variant="success">{t('batchStatus.labelCompleted')}</Badge></Tooltip>;
  if (status === 'IN_PROGRESS' || status === 'PROCESSING') return <Tooltip content={t('batchStatus.helpInProgress')}><Badge variant="warning">{t('batchStatus.labelInProgress')}</Badge></Tooltip>;
  if (status === 'FAILED') return <Tooltip content={t('batchStatus.helpFailed')}><Badge variant="error">{t('batchStatus.labelFailed')}</Badge></Tooltip>;
  if (status === 'PENDING') return <Tooltip content={t('batchStatus.helpPending')}><Badge variant="muted">{t('batchStatus.labelPending')}</Badge></Tooltip>;
  return <Badge variant="muted">{status ?? '—'}</Badge>;
}

function ProgressBar({ pct }: { pct?: number }) {
  const value = pct ?? 0;
  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-2 bg-fg/10 border border-fg/20">
        <div
          className="h-full bg-accent transition-all"
          style={{ width: `${Math.min(value, 100)}%` }}
        />
      </div>
      <span className="text-xs font-mono w-10 text-right">{value}%</span>
    </div>
  );
}

// ── Re-encrypt Key Dialog ─────────────────────────────────────────────────────

function ReencryptKeyDialog({
  keyData,
  onClose,
}: {
  keyData: EncryptionKeyResponse | null;
  onClose: () => void;
}) {
  const { t } = useTranslation('encryption-keys');
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [result, setResult] = useState<ReencryptionKeyResponse | null>(null);

  const mutation = useTriggerReencryptionForKey({
    mutation: {
      onSuccess: (data) => {
        const res = data as unknown as ReencryptionKeyResponse;
        setResult(res);
        toast(res.message ?? t('reencryptDialog.toastSuccess', { id: keyData!.keyId }), 'success');
        queryClient.invalidateQueries({ queryKey: ['encryption-keys'] });
        queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
      },
      onError: (e) => toast(getApiErrorMessage(e, t('reencryptDialog.errorTrigger')), 'error'),
    },
  });

  function handleClose() {
    setResult(null);
    onClose();
  }

  if (!keyData) return null;

  function handleTrigger() {
    const keyId = keyData?.keyId;
    if (keyId != null) mutation.mutate({ keyId });
  }

  return (
    <Dialog open={keyData !== null} onClose={handleClose} title={t('reencryptDialog.title', { id: keyData.keyId })} size="md">
      {result ? (
        <div className="space-y-3">
          <div className="flex items-center gap-2 text-success">
            <RotateCcw className="size-5" />
            <span className="font-bold">{t('reencryptDialog.triggered')}</span>
          </div>
          <p className="text-sm">{result.message}</p>
          <div className="grid grid-cols-3 gap-3 text-center">
            <div className="border-2 border-fg/20 p-2">
              <p className="text-lg font-mono font-bold">{result.batchesCreated ?? 0}</p>
              <p className="text-[10px] font-black uppercase tracking-wider text-fg-muted">{t('reencryptDialog.created')}</p>
            </div>
            <div className="border-2 border-fg/20 p-2">
              <p className="text-lg font-mono font-bold">{result.batchesProcessed ?? 0}</p>
              <p className="text-[10px] font-black uppercase tracking-wider text-fg-muted">{t('reencryptDialog.processed')}</p>
            </div>
            <div className="border-2 border-fg/20 p-2">
              <p className="text-lg font-mono font-bold text-error">{result.batchesFailed ?? 0}</p>
              <p className="text-[10px] font-black uppercase tracking-wider text-fg-muted">{t('reencryptDialog.failed')}</p>
            </div>
          </div>
          <div className="flex justify-end pt-2">
            <Button onClick={handleClose}>{t('reencryptDialog.done')}</Button>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <div className="flex items-start gap-2 p-3 border-2 border-warning/40 bg-warning/5">
            <AlertTriangle className="size-4 text-warning mt-0.5 shrink-0" />
            <p className="text-xs text-fg-muted">
              {t('reencryptDialog.warning', { id: keyData.keyId })}
            </p>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={handleClose}>{t('reencryptDialog.cancel')}</Button>
            <Button
              onClick={handleTrigger}
              disabled={mutation.isPending}
              className="gap-1.5"
            >
              <RotateCcw className="size-3.5" />
              {mutation.isPending ? t('reencryptDialog.submitting') : t('reencryptDialog.submit')}
            </Button>
          </div>
        </div>
      )}
    </Dialog>
  );
}

// ── Key Detail Dialog ─────────────────────────────────────────────────────────

function KeyDetailDialog({
  keyId,
  onClose,
  onReencrypt,
}: {
  keyId: number | null;
  onClose: () => void;
  onReencrypt: (key: EncryptionKeyResponse) => void;
}) {
  const { t } = useTranslation('encryption-keys');
  const { data: rawKey, isLoading } = useGetKey(keyId ?? 0, {
    query: { enabled: keyId != null },
  });
  const keyData = rawKey as EncryptionKeyResponse | undefined;

  if (keyId === null) return null;

  function Row({ label, children }: { label: string; children: React.ReactNode }) {
    return (
      <div className="flex gap-4">
        <dt className="w-40 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">{label}</dt>
        <dd className="text-sm break-all">{children}</dd>
      </div>
    );
  }

  return (
    <Dialog open={keyId !== null} onClose={onClose} title={t('keyDetail.title', { id: keyId })} size="md">
      {isLoading || !keyData ? (
        <div className="flex items-center justify-center py-8">
          <Loader2 className="size-5 animate-spin text-fg-muted" />
        </div>
      ) : (
        <>
          <dl className="space-y-2.5">
            <Row label={t('keyDetail.labelKeyId')}><span className="font-mono">{keyData.keyId}</span></Row>
            <Row label={t('keyDetail.labelStatus')}><KeyStatusBadge status={keyData.keyStatus} /></Row>
            <Row label={t('keyDetail.labelAlgorithm')}><span className="font-mono text-xs">{keyData.algorithm ?? '—'}</span></Row>
            <Row label={t('keyDetail.labelIntroduced')}><span className="text-fg-muted">{keyData.introducedAt ? formatDate(keyData.introducedAt) : '—'}</span></Row>
            <Row label={t('keyDetail.labelPromotedPrimary')}>{keyData.promotedPrimaryAt ? formatDate(keyData.promotedPrimaryAt) : '—'}</Row>
            <Row label={t('keyDetail.labelDisabled')}>{keyData.disabledAt ? formatDate(keyData.disabledAt) : '—'}</Row>
            <Row label={t('keyDetail.labelRecordsEncrypted')}><span className="font-mono">{keyData.recordsEncrypted ?? 0}</span></Row>
            <Row label={t('keyDetail.labelRecordsReencrypted')}><span className="font-mono">{keyData.recordsReencrypted ?? 0}</span></Row>
            <Row label={t('keyDetail.labelCreatedBy')}>{keyData.createdBy ?? '—'}</Row>
            {keyData.notes && <Row label={t('keyDetail.labelNotes')}><span className="text-xs text-fg-muted">{keyData.notes}</span></Row>}
          </dl>
          <div className="flex justify-end gap-2 pt-4">
            {keyData.keyStatus !== 'PRIMARY' && keyData.keyStatus !== 'DISABLED' && (
              <Button
                variant="secondary"
                className="gap-1.5"
                onClick={() => { onClose(); onReencrypt(keyData); }}
              >
                <RotateCcw className="size-3.5" />
                {t('list.reencryptButton')}
              </Button>
            )}
            <Button onClick={onClose}>{t('keyDetail.close')}</Button>
          </div>
        </>
      )}
    </Dialog>
  );
}

// ── Batch Detail Dialog ───────────────────────────────────────────────────────

function BatchDetailDialog({
  batch,
  onClose,
}: {
  batch: ReencryptionBatchResponse | null;
  onClose: () => void;
}) {
  const { t } = useTranslation('encryption-keys');
  if (!batch) return null;

  function Row({ label, children }: { label: string; children: React.ReactNode }) {
    return (
      <div className="flex gap-4">
        <dt className="w-40 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">{label}</dt>
        <dd className="text-sm break-all">{children}</dd>
      </div>
    );
  }

  return (
    <Dialog open={batch !== null} onClose={onClose} title={t('batchDetail.title', { id: batch.batchId })} size="md">
      <dl className="space-y-2.5">
        <Row label={t('batchDetail.labelBatchId')}><span className="font-mono">{batch.batchId}</span></Row>
        <Row label={t('batchDetail.labelStatus')}><BatchStatusBadge status={batch.status} /></Row>
        <Row label={t('batchDetail.labelTargetTable')}><span className="font-mono text-xs">{batch.targetTable}</span></Row>
        <Row label={t('batchDetail.labelTargetColumn')}><span className="font-mono text-xs">{batch.targetColumn}</span></Row>
        <Row label={t('batchDetail.labelOldKey')}><span className="font-mono">#{batch.oldKeyId}</span></Row>
        <Row label={t('batchDetail.labelNewKey')}><span className="font-mono">#{batch.newKeyId}</span></Row>
        <Row label={t('batchDetail.labelProgress')}><ProgressBar pct={batch.progressPct} /></Row>
        <Row label={t('batchDetail.labelRecordsTotal')}><span className="font-mono">{batch.recordsTotal ?? 0}</span></Row>
        <Row label={t('batchDetail.labelRecordsDone')}><span className="font-mono">{batch.recordsDone ?? 0}</span></Row>
        <Row label={t('batchDetail.labelRecordsFailed')}>
          <span className={`font-mono ${(batch.recordsFailed ?? 0) > 0 ? 'text-error font-bold' : ''}`}>
            {batch.recordsFailed ?? 0}
          </span>
        </Row>
        <Row label={t('batchDetail.labelRecordsSkipped')}><span className="font-mono">{batch.recordsSkipped ?? 0}</span></Row>
        <Row label={t('batchDetail.labelRetryCount')}><span className="font-mono">{batch.retryCount ?? 0}</span></Row>
        <Row label={t('batchDetail.labelStarted')}>{batch.startedAt ? formatDate(batch.startedAt) : '—'}</Row>
        <Row label={t('batchDetail.labelCompleted')}>{batch.completedAt ? formatDate(batch.completedAt) : '—'}</Row>
        {batch.errorMessage && (
          <Row label={t('batchDetail.labelError')}>
            <span className="text-xs text-error font-mono">{batch.errorMessage}</span>
          </Row>
        )}
      </dl>
      <div className="flex justify-end pt-4">
        <Button onClick={onClose}>{t('batchDetail.close')}</Button>
      </div>
    </Dialog>
  );
}

// ── Rotate Key Dialog ─────────────────────────────────────────────────────────

function RotateKeyDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation('encryption-keys');
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [reason, setReason] = useState('');
  const [result, setResult] = useState<KeyRotationResponse | null>(null);

  const mutation = useRotateKey({
    mutation: {
      onSuccess: (data) => {
        const res = data as unknown as KeyRotationResponse;
        setResult(res);
        toast(t('rotateDialog.toastSuccess', { id: res.newPrimaryKeyId }), 'success');
        queryClient.invalidateQueries({ queryKey: ['encryption-keys'] });
        queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
      },
      onError: (e) => toast(getApiErrorMessage(e, t('rotateDialog.errorRotate')), 'error'),
    },
  });

  function handleClose() {
    setReason('');
    setResult(null);
    onClose();
  }

  return (
    <Dialog open={open} onClose={handleClose} title={t('rotateDialog.title')} size="md" dismissible={false}>
      {result ? (
        <div className="space-y-3">
          <div className="flex items-center gap-2 text-success">
            <Key className="size-5" />
            <span className="font-bold">{t('rotateDialog.successTitle')}</span>
          </div>
          <p className="text-sm">{result.message}</p>
          <p className="text-sm font-mono">{t('rotateDialog.newPrimary')} <strong>#{result.newPrimaryKeyId}</strong></p>
          <div className="flex justify-end pt-2">
            <Button onClick={handleClose}>{t('rotateDialog.done')}</Button>
          </div>
        </div>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            mutation.mutate({ params: { reason: reason.trim() } });
          }}
          className="space-y-4"
        >
          <div className="flex items-start gap-2 p-3 border-2 border-warning/40 bg-warning/5">
            <AlertTriangle className="size-4 text-warning mt-0.5 shrink-0" />
            <p className="text-xs text-fg-muted">
              {t('rotateDialog.warning')}
            </p>
          </div>
          <div className="space-y-1">
            <Label htmlFor="rotate-reason">{t('rotateDialog.reasonLabel')}</Label>
            <Input
              id="rotate-reason"
              placeholder={t('rotateDialog.reasonPlaceholder')}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={handleClose}>{t('rotateDialog.cancel')}</Button>
            <Button type="submit" disabled={mutation.isPending || reason.trim().length < 10}>
              {mutation.isPending ? t('rotateDialog.submitting') : t('rotateDialog.submit')}
            </Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

// ── Re-encryption Batches Section ─────────────────────────────────────────────

function ReencryptionBatchesSection() {
  const { t } = useTranslation('encryption-keys');
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [expanded, setExpanded] = useState(false);
  const [statusFilter, setStatusFilter] = useState('');
  const [selectedBatch, setSelectedBatch] = useState<ReencryptionBatchResponse | null>(null);

  const { data: batchesData, isLoading } = useListBatches({
    query: { enabled: expanded },
  });
  const batches = (batchesData as ReencryptionBatchResponse[] | undefined) ?? [];

  const filteredBatches = useMemo(() => {
    if (!statusFilter) return batches;
    return batches.filter((b) => b.status === statusFilter);
  }, [batches, statusFilter]);

  const triggerMutation = useTriggerFullReencryption({
    mutation: {
      onSuccess: (data) => {
        const res = data as unknown as ReencryptionTriggerResponse;
        toast(res.message ?? t('batchesSection.toastTrigger', { count: res.batchesCreated }), 'success');
        queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
      },
      onError: (e) => toast(getApiErrorMessage(e, t('batchesSection.errorTrigger')), 'error'),
    },
  });

  const createBatchesMutation = useCreateBatches({
    mutation: {
      onSuccess: (data) => {
        const res = data as unknown as BatchCreationResponse;
        toast(res.message ?? t('batchesSection.toastCreate', { count: res.batchesCreated }), 'success');
        queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
      },
      onError: (e) => toast(getApiErrorMessage(e, t('batchesSection.errorCreate')), 'error'),
    },
  });

  const resumeMutation = useResumeBatch({
    mutation: {
      onSuccess: (data) => {
        const res = data as unknown as BatchResumeResponse;
        toast(res.message ?? t('batchesSection.toastResume', { id: res.batchId }), 'success');
        queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
      },
      onError: (e) => toast(getApiErrorMessage(e, t('batchesSection.errorResume')), 'error'),
    },
  });

  const batchColumns: ColumnDef<ReencryptionBatchResponse>[] = [
    { header: t('batchesSection.columnsId'), key: 'batchId', className: 'w-14', render: (r) => <span className="font-mono text-xs">{r.batchId}</span> },
    { header: t('batchesSection.columnsTable'), key: 'targetTable', render: (r) => <span className="font-mono text-xs">{r.targetTable}</span> },
    { header: t('batchesSection.columnsColumn'), key: 'targetColumn', render: (r) => <span className="font-mono text-xs">{r.targetColumn}</span> },
    {
      header: t('batchesSection.columnsKeys'),
      key: 'keys',
      render: (r) => (
        <span className="text-xs">
          #{r.oldKeyId} → #{r.newKeyId}
        </span>
      ),
    },
    { header: t('batchesSection.columnsStatus'), key: 'status', render: (r) => <BatchStatusBadge status={r.status} /> },
    {
      header: t('batchesSection.columnsProgress'),
      key: 'progress',
      className: 'w-32',
      render: (r) => <ProgressBar pct={r.progressPct} />,
    },
    {
      header: t('batchesSection.columnsRecords'),
      key: 'records',
      render: (r) => (
        <span className="text-xs font-mono">
          {r.recordsDone ?? 0}/{r.recordsTotal ?? 0}
          {(r.recordsFailed ?? 0) > 0 && <span className="text-error ml-1">({r.recordsFailed} {t('batchDetail.failSuffix')})</span>}
        </span>
      ),
    },
    {
      header: '',
      key: 'actions',
      render: (r) =>
        r.status === 'FAILED' || r.status === 'PENDING' ? (
          <Button
            size="sm"
            variant="secondary"
            className="gap-1 py-0.5 px-2"
            onClick={(e) => { e.stopPropagation(); resumeMutation.mutate({ batchId: r.batchId! }); }}
            disabled={resumeMutation.isPending}
          >
            <Play className="size-3" />
            {t('batchesSection.resume')}
          </Button>
        ) : null,
    },
  ];

  return (
    <div className="border-2 border-fg/20 bg-main shadow-brutal">
      <button
        type="button"
        className="w-full flex items-center justify-between p-4 text-left hover:bg-fg/5 transition-colors"
        onClick={() => setExpanded((v) => !v)}
      >
        <div className="flex items-center gap-2">
          <RotateCcw className="size-4 text-accent" />
          <h2 className="font-black text-sm uppercase tracking-wider">{t('batchesSection.title')}</h2>
          {batches.length > 0 && <Badge variant="muted">{batches.length}</Badge>}
        </div>
        {expanded ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
      </button>

      {expanded && (
        <div className="border-t-2 border-fg/20 p-4 space-y-3">
          <div className="flex items-center gap-2 flex-wrap">
            <div className="w-40">
              <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                <option value="">{t('batchesSection.filterAll')}</option>
                <option value="PENDING">{t('batchStatus.labelPending')}</option>
                <option value="IN_PROGRESS">{t('batchStatus.labelInProgress')}</option>
                <option value="COMPLETED">{t('batchStatus.labelCompleted')}</option>
                <option value="FAILED">{t('batchStatus.labelFailed')}</option>
              </Select>
            </div>
            <div className="flex gap-2 ml-auto">
              <Button
                size="sm"
                variant="secondary"
                className="gap-1.5"
                onClick={() => createBatchesMutation.mutate()}
                disabled={createBatchesMutation.isPending}
              >
                <ListPlus className="size-3.5" />
                {createBatchesMutation.isPending ? t('batchesSection.creating') : t('batchesSection.createBatches')}
              </Button>
              <Button
                size="sm"
                variant="secondary"
                className="gap-1.5"
                onClick={() => triggerMutation.mutate()}
                disabled={triggerMutation.isPending}
              >
                <RefreshCw className="size-3.5" />
                {triggerMutation.isPending ? t('batchesSection.triggering') : t('batchesSection.triggerFull')}
              </Button>
            </div>
          </div>

          <DataTable
            columns={batchColumns}
            data={filteredBatches}
            isLoading={isLoading}
            onRowClick={(row) => setSelectedBatch(row)}
            keyExtractor={(r, i) => r.batchId ?? i}
            emptyMessage={statusFilter ? t('batchesSection.emptyFiltered') : t('batchesSection.emptyAll')}
          />
          {!isLoading && filteredBatches.length > 0 && (
            <p className="text-xs text-fg-muted">
              {filteredBatches.length === 1
                ? t('batchesSection.batchCount', { count: filteredBatches.length })
                : t('batchesSection.batchCountPlural', { count: filteredBatches.length })}
              {statusFilter && ` ${t('batchesSection.filteredFrom', { total: batches.length })}`}
            </p>
          )}
        </div>
      )}

      <BatchDetailDialog batch={selectedBatch} onClose={() => setSelectedBatch(null)} />
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

type KeyStatusFilter = 'all' | 'PRIMARY' | 'ENABLED' | 'DISABLED' | 'PENDING';

export default function EncryptionKeysPage() {
  const { t } = useTranslation('encryption-keys');
  const [selectedKeyId, setSelectedKeyId] = useState<number | null>(null);
  const [rotateOpen, setRotateOpen] = useState(false);
  const [reencryptTarget, setReencryptTarget] = useState<EncryptionKeyResponse | null>(null);
  const [keyStatusFilter, setKeyStatusFilter] = useState<KeyStatusFilter>('all');

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<
    EncryptionKeyResponse,
    { keyStatus?: string }
  >({
    queryKey: ['encryption-keys', keyStatusFilter],
    baseParams: {
      keyStatus: keyStatusFilter === 'all' ? undefined : keyStatusFilter,
    },
    fetchPage: (params) =>
      listKeys(params) as Promise<PagedModelEncryptionKeyResponse>,
    defaultSort: 'introducedAt,DESC',
    defaultSize: 20,
  });

  const columns: ColumnDef<EncryptionKeyResponse>[] = [
    {
      header: t('list.columns.id'),
      key: 'keyId',
      className: 'w-14',
      sortKey: 'keyId',
      render: (r) => (
        <span className="font-mono text-xs inline-flex items-center gap-1">
          {r.keyStatus === 'PRIMARY' && <Key className="size-3 text-accent" />}
          {r.keyId}
        </span>
      ),
    },
    {
      header: t('list.columns.status'),
      key: 'keyStatus',
      sortKey: 'keyStatus',
      headerTooltip: t('list.headerTooltipStatus'),
      render: (r) => <KeyStatusBadge status={r.keyStatus} />,
    },
    {
      header: t('list.columns.algorithm'),
      key: 'algorithm',
      sortKey: 'algorithm',
      render: (r) => <span className="font-mono text-xs">{r.algorithm ?? '—'}</span>,
    },
    {
      header: t('list.columns.records'),
      key: 'recordsEncrypted',
      sortKey: 'recordsEncrypted',
      headerTooltip: t('list.headerTooltipRecords'),
      render: (r) => <span className="font-mono text-xs">{r.recordsEncrypted ?? 0}</span>,
    },
    {
      header: t('list.columns.introduced'),
      key: 'introducedAt',
      sortKey: 'introducedAt',
      render: (r) => (
        <span className="text-xs text-fg-muted">{r.introducedAt ? formatRelativeTime(r.introducedAt) : '—'}</span>
      ),
    },
    {
      header: t('list.columns.primarySince'),
      key: 'promotedPrimaryAt',
      sortKey: 'promotedPrimaryAt',
      headerTooltip: t('list.headerTooltipPrimarySince'),
      render: (r) => (
        <span className="text-xs text-fg-muted">{r.promotedPrimaryAt ? formatDate(r.promotedPrimaryAt) : '—'}</span>
      ),
    },
    {
      header: t('list.columns.createdBy'),
      key: 'createdBy',
      sortKey: 'createdBy',
      render: (r) => <span className="text-xs">{r.createdBy ?? '—'}</span>,
    },
    {
      header: '',
      key: 'actions',
      render: (r) =>
        r.keyStatus === 'ENABLED' ? (
          <Tooltip content={t('list.reencryptButtonTooltip')}>
            <Button
              size="sm"
              variant="secondary"
              className="gap-1 py-0.5 px-2"
              onClick={(e) => { e.stopPropagation(); setReencryptTarget(r); }}
            >
              <RotateCcw className="size-3" />
              {t('list.reencryptButton')}
            </Button>
          </Tooltip>
        ) : null,
    },
  ];

  return (
    <AppShell title={t('list.title')}>
      <div className="space-y-4">
        {/* Filter bar */}
        <div className="flex gap-3 items-center flex-wrap">
          <div className="flex items-center gap-1.5">
            <p className="text-xs text-fg-muted">
              {t('list.sectionIntro')}
            </p>
            <ContextHelp
              title={t('list.sectionHelpTitle')}
              content={ENCRYPTION_KEYS_SECTION_HELP.content}
              ariaLabel={`Help: ${t('list.sectionHelpTitle')}`}
            />
          </div>
          <div className="w-40">
            <Select
              value={keyStatusFilter}
              onChange={(e) => setKeyStatusFilter(e.target.value as KeyStatusFilter)}
            >
              <option value="all">{t('list.filterStatusAll')}</option>
              <option value="PRIMARY">{t('list.filterStatusPrimary')}</option>
              <option value="ENABLED">{t('list.filterStatusEnabled')}</option>
              <option value="DISABLED">{t('list.filterStatusDisabled')}</option>
              <option value="PENDING">{t('list.filterStatusPending')}</option>
            </Select>
          </div>
          <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5">
            <RefreshCw className="size-3.5" />
            {t('list.refresh')}
          </Button>
          <Button size="sm" onClick={() => setRotateOpen(true)} className="gap-1.5 ml-auto">
            <Key className="size-3.5" />
            {t('list.rotateKey')}
          </Button>
        </div>

        {/* Keys table */}
        <div>
          <DataTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => setSelectedKeyId(row.keyId ?? null)}
            keyExtractor={(r, i) => r.keyId ?? i}
            emptyMessage={t('list.emptyMessage')}
            currentSort={pagination.sort}
            onSort={pagination.setSort}
          />
          <Pagination
            page={pagination.page}
            totalPages={pagination.totalPages}
            totalElements={pagination.totalElements}
            isFirst={pagination.isFirst}
            isLast={pagination.isLast}
            onFirstPage={pagination.firstPage}
            onLastPage={pagination.lastPage}
            onPrevPage={pagination.prevPage}
            onNextPage={pagination.nextPage}
            pageSize={pagination.size}
            onPageSizeChange={pagination.setPageSize}
          />
        </div>

        {/* Re-encryption batches */}
        <ReencryptionBatchesSection />
      </div>

      <KeyDetailDialog
        keyId={selectedKeyId}
        onClose={() => setSelectedKeyId(null)}
        onReencrypt={(key) => setReencryptTarget(key)}
      />
      <RotateKeyDialog open={rotateOpen} onClose={() => setRotateOpen(false)} />
      <ReencryptKeyDialog keyData={reencryptTarget} onClose={() => setReencryptTarget(null)} />
    </AppShell>
  );
}
