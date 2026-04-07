import { useState, useEffect, useCallback } from 'react';
import type { TFunction } from 'i18next';
import { useTranslation, Trans } from 'react-i18next';
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
import { Select } from '@/components/ui/select';
import { type ColumnDef } from '@/components/data-table/data-table';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { DateRangeFilter } from '@/components/ui/date-range-filter';
import { DemoReasonBadges } from '@/components/feature/demo-reason-badges';
import { ReasonFieldRow } from '@/components/feature/reason-field-row';
import { HelpInlineButton } from '@/components/help/help-inline-button';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import { dateRangeToApiParams } from '@/lib/date-range-presets';
import { formatDate, formatRelativeTime } from '@/lib/utils';
import { useDetailNavigation } from '@/hooks/use-detail-navigation';
import { useDebounce } from '@/hooks/use-debounce';
import { DetailDialogHeaderNav } from '@/components/ui/detail-dialog-header-nav';
import { useToast } from '@/context/toast-context';
import { usePaginatedFromOrval, type PagedBody } from '@/hooks/use-paginated-orval';
import {
  listKeys,
  listBatches,
  useCreateBatches,
  useGetKey,
  useResumeBatch,
  useRotateKey,
  useTriggerFullReencryption,
  useTriggerReencryptionForKey,
} from '@/generated/admin-api/encryption-keys/encryption-keys';
import type {
  EncryptionKeyResponse,
  KeyRotationResponse,
  PagedModelEncryptionKeyResponse,
  PagedModelReencryptionBatchResponse,
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

/** Derived lifecycle stage from prefix counts + batch state (see KeyUsageVerificationService). */
function LifecycleStageBadge({ stage }: { stage?: string }) {
  const { t } = useTranslation('encryption-keys');
  if (!stage) return <Badge variant="muted">—</Badge>;
  const labelKey = `lifecycle.stage.${stage}`;
  const helpKey = `lifecycle.stageHelp.${stage}`;
  const label = t(labelKey, { defaultValue: stage });
  const help = t(helpKey, { defaultValue: '' });
  let variant: 'success' | 'muted' | 'warning' | 'error' = 'muted';
  if (stage === 'PRIMARY' || stage === 'DRAINED') variant = 'success';
  else if (stage === 'ENABLED_IN_USE') variant = 'warning';
  else if (stage === 'DISABLED') variant = 'error';
  return (
    <Tooltip content={help || label}>
      <Badge variant={variant}>{label}</Badge>
    </Tooltip>
  );
}

/**
 * Per-key re-encrypt migrates ciphertext from an old ENABLED key to the current PRIMARY. When the
 * lifecycle snapshot is DRAINED (no tracked ENC: rows and no incomplete migration batches for this
 * key), the action has no work to do — hide the button.
 */
function shouldShowReencryptButton(r: EncryptionKeyResponse): boolean {
  if (r.keyStatus !== 'ENABLED') return false;
  if (r.lifecycleStage === 'DRAINED') return false;
  return true;
}

function parseIsoMs(s?: string | null): number | null {
  if (!s) return null;
  const ms = new Date(s).getTime();
  return Number.isNaN(ms) ? null : ms;
}

/**
 * Wall-clock duration when both start and end timestamps exist (any terminal or paused run with
 * both set).
 */
function getReencryptionBatchDurationSeconds(
  startedAt?: string | null,
  completedAt?: string | null,
): number | null {
  const a = parseIsoMs(startedAt);
  const b = parseIsoMs(completedAt);
  if (a === null || b === null || b < a) return null;
  return (b - a) / 1000;
}

/** Elapsed seconds since start (in-progress batches). */
function getReencryptionBatchElapsedSeconds(startedAt?: string | null): number | null {
  const a = parseIsoMs(startedAt);
  if (a === null) return null;
  const elapsed = (Date.now() - a) / 1000;
  return elapsed >= 0 ? elapsed : null;
}

function formatRecordsPerSecond(rate: number): string {
  if (!Number.isFinite(rate) || rate < 0) return '0';
  if (rate >= 100) return String(Math.round(rate));
  if (rate >= 10) return rate.toFixed(1);
  return rate.toFixed(2);
}

/** List column: prefix-scan totals — PRIMARY (live volume) and ENABLED (migration backlog). */
function RecordsColumnValue({ row }: { row: EncryptionKeyResponse }) {
  const ks = row.keyStatus;
  if (ks === 'PENDING' || ks === 'DISABLED') {
    return <span className="font-mono text-xs text-fg-muted">—</span>;
  }
  if (ks === 'PRIMARY' || ks === 'ENABLED') {
    const v = row.remainingRecords;
    return (
      <span className="font-mono text-xs">{v != null ? v : '—'}</span>
    );
  }
  return <span className="font-mono text-xs text-fg-muted">—</span>;
}

function formatMigrationBaselineDetail(r: EncryptionKeyResponse): string {
  if (r.keyStatus === 'PRIMARY' || r.keyStatus === 'PENDING') {
    return '—';
  }
  return String(r.recordsEncrypted ?? 0);
}

function BatchStatusBadge({ status }: { status?: string }) {
  const { t } = useTranslation('encryption-keys');
  if (status === 'COMPLETED') return <Tooltip content={t('batchStatus.helpCompleted')}><Badge variant="success">{t('batchStatus.labelCompleted')}</Badge></Tooltip>;
  if (status === 'IN_PROGRESS' || status === 'PROCESSING') return <Tooltip content={t('batchStatus.helpInProgress')}><Badge variant="warning">{t('batchStatus.labelInProgress')}</Badge></Tooltip>;
  if (status === 'FAILED') return <Tooltip content={t('batchStatus.helpFailed')}><Badge variant="error">{t('batchStatus.labelFailed')}</Badge></Tooltip>;
  if (status === 'PENDING') return <Tooltip content={t('batchStatus.helpPending')}><Badge variant="muted">{t('batchStatus.labelPending')}</Badge></Tooltip>;
  if (status === 'PAUSED') return <Tooltip content={t('batchStatus.helpPaused')}><Badge variant="warning">{t('batchStatus.labelPaused')}</Badge></Tooltip>;
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
      onError: (e) => toast(getTranslatedApiError(e, t, t('reencryptDialog.errorTrigger')), 'error'),
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

function verificationStateLabel(state: string | undefined, t: TFunction) {
  if (!state) return '—';
  return t(`lifecycle.verification.${state}`, { defaultValue: state });
}

function KeyDetailDialog({
  keyId,
  onClose,
  onReencrypt,
  onPrev,
  onNext,
  hasPrev,
  hasNext,
  showNav,
  showEndOfPageHint,
}: {
  keyId: number | null;
  onClose: () => void;
  onReencrypt: (key: EncryptionKeyResponse) => void;
  onPrev: () => void;
  onNext: () => void;
  hasPrev: boolean;
  hasNext: boolean;
  showNav: boolean;
  showEndOfPageHint: boolean;
}) {
  const { t } = useTranslation('encryption-keys');
  const { t: tc } = useTranslation('common');
  const { data: rawKey, isLoading } = useGetKey(keyId ?? 0, {
    query: { enabled: keyId != null },
  });
  const keyData = rawKey as EncryptionKeyResponse | undefined;

  useDetailNavigation(keyId !== null && showNav, {
    hasPrev: hasPrev && showNav,
    hasNext: hasNext && showNav,
    onPrev,
    onNext,
  });

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
    <Dialog
      open={keyId !== null}
      onClose={onClose}
      title={t('keyDetail.title', { id: keyId })}
      size="md"
      headerActions={
        showNav ? (
          <DetailDialogHeaderNav
            hasPrev={hasPrev}
            hasNext={hasNext}
            onPrev={onPrev}
            onNext={onNext}
          />
        ) : undefined
      }
    >
      {showEndOfPageHint && (
        <p className="text-xs text-fg-muted italic border border-fg/20 bg-fg/[0.03] px-3 py-2 mb-4">
          {tc('detailNav.endOfPageMore')}
        </p>
      )}
      {isLoading || !keyData ? (
        <div className="flex items-center justify-center py-8">
          <Loader2 className="size-5 animate-spin text-fg-muted" />
        </div>
      ) : (
        <>
          <dl className="space-y-2.5">
            <Row label={t('keyDetail.labelKeyId')}><span className="font-mono">{keyData.keyId}</span></Row>
            <Row label={t('keyDetail.labelStatus')}><KeyStatusBadge status={keyData.keyStatus} /></Row>
            <Row label={t('keyDetail.labelLifecycleStage')}>
              <LifecycleStageBadge stage={keyData.lifecycleStage} />
            </Row>
            <Row label={t('keyDetail.labelRemainingRecords')}>
              <span className="font-mono text-xs">
                {keyData.remainingRecords != null ? keyData.remainingRecords : '—'}
              </span>
            </Row>
            <Row label={t('keyDetail.labelRemainingTargets')}>
              <span className="font-mono text-xs">
                {keyData.remainingTargets != null ? keyData.remainingTargets : '—'}
              </span>
            </Row>
            <Row label={t('keyDetail.labelVerificationState')}>
              <span className="text-xs">{verificationStateLabel(keyData.verificationState, t)}</span>
            </Row>
            <Row label={t('keyDetail.labelLastVerifiedAt')}>
              <span className="text-xs text-fg-muted">
                {keyData.lastVerifiedAt ? formatRelativeTime(keyData.lastVerifiedAt) : '—'}
              </span>
            </Row>
            <Row label={t('keyDetail.labelDecommissionEligible')}>
              {keyData.decommissionEligible ? t('keyDetail.yes') : t('keyDetail.no')}
            </Row>
            <Row label={t('keyDetail.labelIncompleteMigrationBatches')}>
              {keyData.incompleteMigrationBatches ? t('keyDetail.yes') : t('keyDetail.no')}
            </Row>
            <Row label={t('keyDetail.labelAlgorithm')}><span className="font-mono text-xs">{keyData.algorithm ?? '—'}</span></Row>
            <Row label={t('keyDetail.labelIntroduced')}><span className="text-fg-muted">{keyData.introducedAt ? formatDate(keyData.introducedAt) : '—'}</span></Row>
            <Row label={t('keyDetail.labelPromotedPrimary')}>{keyData.promotedPrimaryAt ? formatDate(keyData.promotedPrimaryAt) : '—'}</Row>
            <Row label={t('keyDetail.labelDisabled')}>{keyData.disabledAt ? formatDate(keyData.disabledAt) : '—'}</Row>
            <Row label={t('keyDetail.labelRecordsEncrypted')}><span className="font-mono">{formatMigrationBaselineDetail(keyData)}</span></Row>
            <Row label={t('keyDetail.labelRecordsReencrypted')}><span className="font-mono">{keyData.recordsReencrypted ?? 0}</span></Row>
            <Row label={t('keyDetail.labelCreatedBy')}>{keyData.createdBy ?? '—'}</Row>
            {keyData.notes && <Row label={t('keyDetail.labelNotes')}><span className="text-xs text-fg-muted">{keyData.notes}</span></Row>}
          </dl>
          <div className="flex justify-end gap-2 pt-4">
            {shouldShowReencryptButton(keyData) && (
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
  onPrev,
  onNext,
  hasPrev,
  hasNext,
  showNav,
  showEndOfPageHint,
}: {
  batch: ReencryptionBatchResponse | null;
  onClose: () => void;
  onPrev: () => void;
  onNext: () => void;
  hasPrev: boolean;
  hasNext: boolean;
  showNav: boolean;
  showEndOfPageHint: boolean;
}) {
  const { t } = useTranslation('encryption-keys');
  const { t: tc } = useTranslation('common');

  useDetailNavigation(batch !== null && showNav, {
    hasPrev: hasPrev && showNav,
    hasNext: hasNext && showNav,
    onPrev,
    onNext,
  });

  if (!batch) return null;

  const elapsedRunningSec =
    batch.status === 'IN_PROGRESS' && batch.startedAt && !batch.completedAt
      ? getReencryptionBatchElapsedSeconds(batch.startedAt)
      : null;
  const wallDurationSec = getReencryptionBatchDurationSeconds(batch.startedAt, batch.completedAt);
  const throughputRate =
    wallDurationSec != null && wallDurationSec > 0
      ? (batch.recordsDone ?? 0) / wallDurationSec
      : null;

  function Row({ label, children }: { label: string; children: React.ReactNode }) {
    return (
      <div className="flex gap-4">
        <dt className="w-40 font-black uppercase text-[10px] tracking-wider text-fg-muted pt-0.5 shrink-0">{label}</dt>
        <dd className="text-sm break-all">{children}</dd>
      </div>
    );
  }

  return (
    <Dialog
      open={batch !== null}
      onClose={onClose}
      title={t('batchDetail.title', { id: batch.batchId })}
      size="md"
      headerActions={
        showNav ? (
          <DetailDialogHeaderNav
            hasPrev={hasPrev}
            hasNext={hasNext}
            onPrev={onPrev}
            onNext={onNext}
          />
        ) : undefined
      }
    >
      {showEndOfPageHint && (
        <p className="text-xs text-fg-muted italic border border-fg/20 bg-fg/[0.03] px-3 py-2 mb-4">
          {tc('detailNav.endOfPageMore')}
        </p>
      )}
      <dl className="space-y-2.5">
        <Row label={t('batchDetail.labelBatchId')}><span className="font-mono">{batch.batchId}</span></Row>
        <Row label={t('batchDetail.labelStatus')}><BatchStatusBadge status={batch.status} /></Row>
        <Row label={t('batchDetail.labelTargetTable')}><span className="font-mono text-xs">{batch.targetTable}</span></Row>
        <Row label={t('batchDetail.labelTargetColumn')}><span className="font-mono text-xs">{batch.targetColumn}</span></Row>
        {batch.shardCount != null && batch.shardCount > 1 && batch.shardIndex != null && (
          <Row label={t('batchDetail.labelShard')}>
            <span className="font-mono text-xs">{batch.shardIndex} / {batch.shardCount}</span>
          </Row>
        )}
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
        {elapsedRunningSec != null && (
          <Row label={t('batchDetail.labelElapsed')}>
            <span className="font-mono text-xs">
              {t('batchDetail.durationSeconds', { seconds: Math.round(elapsedRunningSec) })}
            </span>
          </Row>
        )}
        {wallDurationSec != null && (
          <>
            <Row label={t('batchDetail.labelDuration')}>
              <span className="font-mono text-xs">
                {t('batchDetail.durationSeconds', { seconds: Math.round(wallDurationSec) })}
              </span>
            </Row>
            <Row label={t('batchDetail.labelThroughput')}>
              <span className="font-mono text-xs">
                {throughputRate != null
                  ? t('batchDetail.throughputRecordsPerSec', { rate: formatRecordsPerSecond(throughputRate) })
                  : '—'}
              </span>
            </Row>
          </>
        )}
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
      onError: (e) => toast(getTranslatedApiError(e, t, t('rotateDialog.errorRotate')), 'error'),
    },
  });

  function handleClose() {
    setReason('');
    setResult(null);
    onClose();
  }

  const reasonTooShort = reason.trim().length > 0 && reason.trim().length < 10;

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
          <ReasonFieldRow
            presetGroup="encryption_key_rotate"
            idPrefix="rotate-key"
            inputId="rotate-reason"
            value={reason}
            onChange={setReason}
            label={t('rotateDialog.reasonLabel')}
            placeholder={t('rotateDialog.reasonPlaceholder')}
            showMinLengthError={reasonTooShort}
            minLengthErrorTone="required"
            childrenAfterInput={<DemoReasonBadges onSelect={setReason} />}
          />
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
  const [targetTableInput, setTargetTableInput] = useState('');
  const [targetColumnInput, setTargetColumnInput] = useState('');
  const [oldKeyIdInput, setOldKeyIdInput] = useState('');
  const [newKeyIdInput, setNewKeyIdInput] = useState('');
  const [dateRange, setDateRange] = useState({ from: '', to: '' });
  const [selectedBatchIndex, setSelectedBatchIndex] = useState<number | null>(null);
  const [headerTotal, setHeaderTotal] = useState(0);

  const debouncedTable = useDebounce(targetTableInput, 400);
  const debouncedColumn = useDebounce(targetColumnInput, 400);
  const debouncedOldKey = useDebounce(oldKeyIdInput, 400);
  const debouncedNewKey = useDebounce(newKeyIdInput, 400);

  const apiDateParams =
    dateRange.from && dateRange.to
      ? dateRangeToApiParams(dateRange.from, dateRange.to)
      : { createdAfter: undefined as string | undefined, createdBefore: undefined as string | undefined };

  function parseKeyId(s: string): number | undefined {
    const t = s.trim();
    if (!t) return undefined;
    const n = parseInt(t, 10);
    return Number.isFinite(n) ? n : undefined;
  }

  const { data, pagination, isLoading } = usePaginatedFromOrval<
    ReencryptionBatchResponse,
    {
      status?: string;
      targetTable?: string;
      targetColumn?: string;
      oldKeyId?: number;
      newKeyId?: number;
      createdAfter?: string;
      createdBefore?: string;
    }
  >({
    queryKey: [
      'reencryption-batches',
      statusFilter,
      debouncedTable,
      debouncedColumn,
      debouncedOldKey,
      debouncedNewKey,
      dateRange.from,
      dateRange.to,
    ],
    baseParams: {
      status: statusFilter || undefined,
      targetTable: debouncedTable.trim() || undefined,
      targetColumn: debouncedColumn.trim() || undefined,
      oldKeyId: parseKeyId(debouncedOldKey),
      newKeyId: parseKeyId(debouncedNewKey),
      createdAfter: apiDateParams.createdAfter,
      createdBefore: apiDateParams.createdBefore,
    },
    fetchPage: (params) => listBatches(params) as Promise<PagedModelReencryptionBatchResponse>,
    defaultSort: 'createdAt,DESC',
    defaultSize: 20,
    enabled: expanded,
    refetchInterval: (query) => {
      const body = query.state.data as PagedBody<ReencryptionBatchResponse> | undefined;
      const rows = body?.content ?? [];
      const active = rows.some(
        (b) =>
          b.status === 'PENDING'
          || b.status === 'IN_PROGRESS'
          || b.status === 'PROCESSING',
      );
      return active ? 4000 : false;
    },
  });

  const selectedBatch = selectedBatchIndex !== null ? data[selectedBatchIndex] ?? null : null;
  const showBatchRowNav = data.length > 1;
  const batchHasPrev = selectedBatchIndex !== null && selectedBatchIndex > 0;
  const batchHasNext = selectedBatchIndex !== null && selectedBatchIndex < data.length - 1;
  const showBatchEndOfPageHint =
    selectedBatchIndex !== null &&
    data.length > 0 &&
    selectedBatchIndex === data.length - 1 &&
    !pagination.isLast;

  const goPrevBatch = useCallback(() => {
    setSelectedBatchIndex((i) => (i !== null && i > 0 ? i - 1 : i));
  }, []);

  const goNextBatch = useCallback(() => {
    setSelectedBatchIndex((i) => {
      if (i === null) return i;
      return i < data.length - 1 ? i + 1 : i;
    });
  }, [data.length]);

  useEffect(() => {
    if (selectedBatchIndex !== null && (selectedBatchIndex >= data.length || data.length === 0)) {
      setSelectedBatchIndex(null);
    }
  }, [selectedBatchIndex, data.length]);

  useEffect(() => {
    if (expanded) {
      setHeaderTotal(pagination.totalElements);
    }
  }, [expanded, pagination.totalElements]);

  const hasFilters = Boolean(
    statusFilter
    || debouncedTable.trim()
    || debouncedColumn.trim()
    || parseKeyId(debouncedOldKey) != null
    || parseKeyId(debouncedNewKey) != null
    || (dateRange.from && dateRange.to),
  );

  const triggerMutation = useTriggerFullReencryption({
    mutation: {
      onSuccess: (data) => {
        const res = data as unknown as ReencryptionTriggerResponse;
        toast(res.message ?? t('batchesSection.toastTrigger', { count: res.batchesCreated }), 'success');
        queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
      },
      onError: (e) => toast(getTranslatedApiError(e, t, t('batchesSection.errorTrigger')), 'error'),
    },
  });

  const createBatchesMutation = useCreateBatches({
    mutation: {
      onSuccess: (data) => {
        const res = data as unknown as BatchCreationResponse;
        toast(res.message ?? t('batchesSection.toastCreate', { count: res.batchesCreated }), 'success');
        queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
      },
      onError: (e) => toast(getTranslatedApiError(e, t, t('batchesSection.errorCreate')), 'error'),
    },
  });

  const resumeMutation = useResumeBatch({
    mutation: {
      onSuccess: (data) => {
        const res = data as unknown as BatchResumeResponse;
        toast(res.message ?? t('batchesSection.toastResume', { id: res.batchId }), 'success');
        queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
      },
      onError: (e) => toast(getTranslatedApiError(e, t, t('batchesSection.errorResume')), 'error'),
    },
  });

  const batchColumns: ColumnDef<ReencryptionBatchResponse>[] = [
    {
      header: t('batchesSection.columnsId'),
      key: 'batchId',
      className: 'w-14',
      sortKey: 'batchId',
      render: (r) => <span className="font-mono text-xs">{r.batchId}</span>,
    },
    {
      header: t('batchesSection.columnsTable'),
      key: 'targetTable',
      sortKey: 'targetTable',
      render: (r) => <span className="font-mono text-xs">{r.targetTable}</span>,
    },
    {
      header: t('batchesSection.columnsColumn'),
      key: 'targetColumn',
      sortKey: 'targetColumn',
      render: (r) => <span className="font-mono text-xs">{r.targetColumn}</span>,
    },
    {
      header: t('batchesSection.columnsShard'),
      key: 'shard',
      className: 'w-16',
      render: (r) =>
        r.shardCount != null && r.shardCount > 1 && r.shardIndex != null ? (
          <span className="font-mono text-xs">
            {r.shardIndex}/{r.shardCount}
          </span>
        ) : (
          <span className="text-fg-muted">—</span>
        ),
    },
    {
      header: t('batchesSection.columnsKeys'),
      key: 'keys',
      render: (r) => (
        <span className="text-xs">
          #{r.oldKeyId} → #{r.newKeyId}
        </span>
      ),
    },
    {
      header: t('batchesSection.columnsStatus'),
      key: 'status',
      sortKey: 'status',
      render: (r) => <BatchStatusBadge status={r.status} />,
    },
    {
      header: t('batchesSection.columnsProgress'),
      key: 'progress',
      className: 'w-32',
      sortKey: 'progressPct',
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
      header: t('batchesSection.columnsPerf'),
      headerTooltip: t('batchesSection.tooltipPerf'),
      key: 'perf',
      className: 'w-36',
      render: (r) => {
        const durSec = getReencryptionBatchDurationSeconds(r.startedAt, r.completedAt);
        if (durSec == null) {
          if (r.status === 'IN_PROGRESS' && r.startedAt) {
            const elapsed = getReencryptionBatchElapsedSeconds(r.startedAt);
            if (elapsed != null) {
              return (
                <span className="text-xs font-mono text-fg-muted">
                  {t('batchDetail.durationSeconds', { seconds: Math.round(elapsed) })}
                </span>
              );
            }
          }
          return <span className="text-fg-muted">—</span>;
        }
        const done = r.recordsDone ?? 0;
        const rate = durSec > 0 ? done / durSec : 0;
        return (
          <span className="text-xs font-mono text-fg-muted leading-tight">
            {t('batchDetail.durationSeconds', { seconds: Math.round(durSec) })}
            <span className="text-fg-muted/80"> · </span>
            {t('batchDetail.throughputRecordsPerSec', { rate: formatRecordsPerSecond(rate) })}
          </span>
        );
      },
    },
    {
      header: '',
      key: 'actions',
      render: (r) =>
        r.status === 'FAILED' || r.status === 'PENDING' || r.status === 'PAUSED' ? (
          <Tooltip content={t('batchesSection.helpResume')}>
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
          </Tooltip>
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
        <div className="flex items-center gap-2 min-w-0">
          <RotateCcw className="size-4 text-accent shrink-0" />
          <h2 className="font-black text-sm uppercase tracking-wider">{t('batchesSection.title')}</h2>
          {headerTotal > 0 && <Badge variant="muted">{headerTotal}</Badge>}
          <span
            className="inline-flex shrink-0"
            onClick={(e) => e.stopPropagation()}
            onKeyDown={(e) => e.stopPropagation()}
          >
            <ContextHelp
              title={t('batchesSection.sectionHelpTitle')}
              content={
                <div className="space-y-3">
                  <p>
                    <Trans
                      i18nKey="encryption-keys:batchesSection.sectionHelpContent"
                      components={{ strong: <strong /> }}
                    />
                  </p>
                  <p>
                    <Trans
                      i18nKey="encryption-keys:batchesSection.sectionHelpFilters"
                      components={{ strong: <strong /> }}
                    />
                  </p>
                </div>
              }
              ariaLabel={t('common:help.ariaLabel', { title: t('batchesSection.sectionHelpTitle') })}
            />
          </span>
        </div>
        {expanded ? <ChevronUp className="size-4 shrink-0" /> : <ChevronDown className="size-4 shrink-0" />}
      </button>

      {expanded && (
        <div className="border-t-2 border-fg/20 p-4 space-y-3">
          {/*
            Two rows: (1) common filters + primary actions stay on one band so buttons do not wrap
            under many fields; (2) precise filters (table/column/key ids) for power users.
          */}
          <div className="flex flex-col gap-3">
            <div className="flex flex-wrap items-end gap-2 justify-between gap-y-2">
              <div className="flex flex-wrap items-end gap-2 min-w-0">
                <div className="w-40">
                  <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                    <option value="">{t('batchesSection.filterAll')}</option>
                    <option value="PENDING">{t('batchStatus.labelPending')}</option>
                    <option value="IN_PROGRESS">{t('batchStatus.labelInProgress')}</option>
                    <option value="COMPLETED">{t('batchStatus.labelCompleted')}</option>
                    <option value="FAILED">{t('batchStatus.labelFailed')}</option>
                    <option value="PAUSED">{t('batchStatus.labelPaused')}</option>
                  </Select>
                </div>
                <DateRangeFilter
                  value={dateRange}
                  onChange={setDateRange}
                  showClear={true}
                  emptyOptionLabel={t('batchesSection.dateRangeFull')}
                />
              </div>
              <div className="flex flex-wrap gap-2 shrink-0">
                <Tooltip content={t('batchesSection.helpCreateBatches')}>
                  <Button
                    size="sm"
                    variant="primary"
                    className="gap-1.5"
                    onClick={() => createBatchesMutation.mutate()}
                    disabled={createBatchesMutation.isPending}
                  >
                    <ListPlus className="size-3.5" />
                    {createBatchesMutation.isPending ? t('batchesSection.creating') : t('batchesSection.createBatches')}
                  </Button>
                </Tooltip>
                <Tooltip content={t('batchesSection.helpTriggerFull')}>
                  <Button
                    size="sm"
                    variant="primary"
                    className="gap-1.5"
                    onClick={() => triggerMutation.mutate()}
                    disabled={triggerMutation.isPending}
                  >
                    <RefreshCw className="size-3.5" />
                    {triggerMutation.isPending ? t('batchesSection.triggering') : t('batchesSection.triggerFull')}
                  </Button>
                </Tooltip>
              </div>
            </div>

            <div className="flex flex-wrap items-end gap-2 pt-2 border-t border-fg/15">
              <div className="w-36">
                <Tooltip content={t('batchesSection.tooltipTargetTable')}>
                  <div className="w-full">
                    <Input
                      placeholder={t('batchesSection.placeholderTargetTable')}
                      value={targetTableInput}
                      onChange={(e) => setTargetTableInput(e.target.value)}
                      className="text-xs"
                    />
                  </div>
                </Tooltip>
              </div>
              <div className="w-36">
                <Tooltip content={t('batchesSection.tooltipTargetColumn')}>
                  <div className="w-full">
                    <Input
                      placeholder={t('batchesSection.placeholderTargetColumn')}
                      value={targetColumnInput}
                      onChange={(e) => setTargetColumnInput(e.target.value)}
                      className="text-xs"
                    />
                  </div>
                </Tooltip>
              </div>
              <div className="w-24">
                <Tooltip content={t('batchesSection.tooltipOldKeyId')}>
                  <div className="w-full">
                    <Input
                      placeholder={t('batchesSection.placeholderOldKeyId')}
                      value={oldKeyIdInput}
                      onChange={(e) => setOldKeyIdInput(e.target.value)}
                      type="number"
                      min={1}
                      className="text-xs"
                    />
                  </div>
                </Tooltip>
              </div>
              <div className="w-24">
                <Tooltip content={t('batchesSection.tooltipNewKeyId')}>
                  <div className="w-full">
                    <Input
                      placeholder={t('batchesSection.placeholderNewKeyId')}
                      value={newKeyIdInput}
                      onChange={(e) => setNewKeyIdInput(e.target.value)}
                      type="number"
                      min={1}
                      className="text-xs"
                    />
                  </div>
                </Tooltip>
              </div>
            </div>
          </div>

          <PaginatedTable
            columns={batchColumns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => {
              const idx = data.findIndex((r) => r.batchId === row.batchId);
              setSelectedBatchIndex(idx >= 0 ? idx : null);
            }}
            keyExtractor={(r, i) => r.batchId ?? i}
            emptyMessage={hasFilters ? t('batchesSection.emptyFiltered') : t('batchesSection.emptyAll')}
            currentSort={pagination.sort}
            onSort={pagination.setSort}
            pagination={pagination}
          />
        </div>
      )}

      <BatchDetailDialog
        batch={selectedBatch}
        onClose={() => setSelectedBatchIndex(null)}
        onPrev={goPrevBatch}
        onNext={goNextBatch}
        hasPrev={batchHasPrev}
        hasNext={batchHasNext}
        showNav={showBatchRowNav}
        showEndOfPageHint={showBatchEndOfPageHint}
      />
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

type KeyStatusFilter = 'all' | 'PRIMARY' | 'ENABLED' | 'DISABLED' | 'PENDING';

export default function EncryptionKeysPage() {
  const { t } = useTranslation('encryption-keys');
  const queryClient = useQueryClient();
  const [selectedKeyIndex, setSelectedKeyIndex] = useState<number | null>(null);
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

  const selectedKeyId = selectedKeyIndex !== null ? data[selectedKeyIndex]?.keyId ?? null : null;
  const showKeyRowNav = data.length > 1;
  const keyHasPrev = selectedKeyIndex !== null && selectedKeyIndex > 0;
  const keyHasNext = selectedKeyIndex !== null && selectedKeyIndex < data.length - 1;
  const showKeyEndOfPageHint =
    selectedKeyIndex !== null &&
    data.length > 0 &&
    selectedKeyIndex === data.length - 1 &&
    !pagination.isLast;

  const goPrevKey = useCallback(() => {
    setSelectedKeyIndex((i) => (i !== null && i > 0 ? i - 1 : i));
  }, []);

  const goNextKey = useCallback(() => {
    setSelectedKeyIndex((i) => {
      if (i === null) return i;
      return i < data.length - 1 ? i + 1 : i;
    });
  }, [data.length]);

  useEffect(() => {
    if (selectedKeyIndex !== null && (selectedKeyIndex >= data.length || data.length === 0)) {
      setSelectedKeyIndex(null);
    }
  }, [selectedKeyIndex, data.length]);

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
      header: t('list.columns.lifecycle'),
      key: 'lifecycleStage',
      headerTooltip: t('list.headerTooltipLifecycle'),
      render: (r) => <LifecycleStageBadge stage={r.lifecycleStage} />,
    },
    {
      header: t('list.columns.algorithm'),
      key: 'algorithm',
      sortKey: 'algorithm',
      render: (r) => <span className="font-mono text-xs">{r.algorithm ?? '—'}</span>,
    },
    {
      header: t('list.columns.records'),
      key: 'remainingRecords',
      headerTooltip: t('list.headerTooltipRecords'),
      render: (r) => <RecordsColumnValue row={r} />,
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
        shouldShowReencryptButton(r) ? (
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
          <HelpInlineButton className="p-1.5 hover:bg-fg/10 shrink-0 text-fg-muted hover:text-fg" />
          <div className="flex items-center gap-1.5">
            <p className="text-xs text-fg-muted">
              {t('list.sectionIntro')}
            </p>
            <ContextHelp
              title={t('list.sectionHelpTitle')}
              content={
                <Trans
                  i18nKey="encryption-keys:list.help.sectionContent"
                  components={{ strong: <strong /> }}
                />
              }
              ariaLabel={t('common:help.ariaLabel', { title: t('list.sectionHelpTitle') })}
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
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              void refetch();
              void queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
            }}
            className="gap-1.5"
          >
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
          <PaginatedTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            onRowClick={(row) => {
              const idx = data.findIndex((r) => r.keyId === row.keyId);
              setSelectedKeyIndex(idx >= 0 ? idx : null);
            }}
            keyExtractor={(r, i) => r.keyId ?? i}
            emptyMessage={t('list.emptyMessage')}
            currentSort={pagination.sort}
            onSort={pagination.setSort}
            pagination={pagination}
          />
        </div>

        {/* Re-encryption batches */}
        <ReencryptionBatchesSection />
      </div>

      <KeyDetailDialog
        keyId={selectedKeyId}
        onClose={() => setSelectedKeyIndex(null)}
        onReencrypt={(key) => setReencryptTarget(key)}
        onPrev={goPrevKey}
        onNext={goNextKey}
        hasPrev={keyHasPrev}
        hasNext={keyHasNext}
        showNav={showKeyRowNav}
        showEndOfPageHint={showKeyEndOfPageHint}
      />
      <RotateKeyDialog open={rotateOpen} onClose={() => setRotateOpen(false)} />
      <ReencryptKeyDialog keyData={reencryptTarget} onClose={() => setReencryptTarget(null)} />
    </AppShell>
  );
}
