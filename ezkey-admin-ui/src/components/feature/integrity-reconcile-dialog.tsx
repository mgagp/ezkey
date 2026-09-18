import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { CheckCircle, Loader2 } from 'lucide-react';
import { useQueryClient } from '@tanstack/react-query';
import { Alert as UiAlert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { ReasonFieldRow } from '@/components/feature/reason-field-row';
import { EntryHmacBadge } from '@/components/feature/entry-hmac-badge';
import { useToast } from '@/context/use-toast';
import {
  checkIntegrity,
  useReconcileIntegrityRupture,
} from '@/generated/admin-api/audit-logs/audit-logs';
import type {
  EntryIntegrityViolation,
  IntegrityReport,
  IntegrityRuptureReconciliationRequestCategory,
  IntegrityRuptureReconciliationResult,
} from '@/generated/admin-api/model';
import { IntegrityRuptureReconciliationRequestCategory as ReconcileCategory } from '@/generated/admin-api/model';
import { getTranslatedApiError } from '@/lib/api-error-i18n';
import {
  clearIntegrityInvestigationSession,
  entryViolationDisplayState,
  isEntryReconcileAckRequired,
  listReconcileRequiredEntryViolations,
} from '@/lib/integrity-investigation-session';
import { formatDate } from '@/lib/utils';

const RECONCILE_CATEGORIES = [
  ReconcileCategory.ACCIDENTAL_DBA_EDIT,
  ReconcileCategory.CORRUPTION,
  ReconcileCategory.INVESTIGATED_BENIGN,
  ReconcileCategory.OTHER,
] as const satisfies readonly IntegrityRuptureReconciliationRequestCategory[];

function InfoPair({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="flex gap-2 text-sm">
      <span className="text-fg-muted shrink-0">{label}:</span>
      <span className={mono ? 'font-mono text-xs break-all' : ''}>{value}</span>
    </div>
  );
}

function EntryViolationContent({ violation }: { violation: EntryIntegrityViolation }) {
  const { t } = useTranslation('audit-logs');
  const displayState = entryViolationDisplayState(violation);
  const status = violation.conciliationStatus ?? 'NONE';
  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs">
      <EntryHmacBadge state={displayState} />
      <span className="font-mono">#{violation.auditLogId}</span>
      <span className="text-fg-muted">— {violation.reason ?? '—'}</span>
      {status !== 'NONE' && (
        <span className="text-[10px] font-bold uppercase tracking-wide text-fg-muted">
          {t(`integrity.reconcileDialog.conciliationStatus.${status}`)}
        </span>
      )}
    </div>
  );
}

/**
 * Integrity-atelier dialog for {@code POST .../lifecycle/reconcile-integrity-rupture}.
 * Global Admin only; opened from the Integrity route (not Alerts).
 */
export function IntegrityReconcileDialog({
  open,
  onClose,
  alertId,
  failBoundary,
  resumeBoundary,
}: {
  open: boolean;
  onClose: () => void;
  alertId: number;
  failBoundary: string;
  resumeBoundary: string;
}) {
  const { t } = useTranslation('audit-logs');
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const [justification, setJustification] = useState('');
  const [category, setCategory] = useState<IntegrityRuptureReconciliationRequestCategory>(
    ReconcileCategory.INVESTIGATED_BENIGN,
  );
  const [ticketRef, setTicketRef] = useState('');
  const [result, setResult] = useState<IntegrityRuptureReconciliationResult | null>(null);
  const [liveEntries, setLiveEntries] = useState<EntryIntegrityViolation[]>([]);
  const [liveLoading, setLiveLoading] = useState(false);
  const [liveError, setLiveError] = useState<string | null>(null);
  const [checkedAckIds, setCheckedAckIds] = useState<number[]>([]);
  const [ackValidationError, setAckValidationError] = useState(false);

  const requiredAckViolations = useMemo(
    () => listReconcileRequiredEntryViolations(liveEntries),
    [liveEntries],
  );
  const requiredAckIds = useMemo(
    () =>
      requiredAckViolations
        .map((v) => v.auditLogId)
        .filter((id): id is number => id != null && id > 0)
        .sort((a, b) => a - b),
    [requiredAckViolations],
  );
  const allRequiredAckChecked =
    requiredAckIds.length === 0
    || requiredAckIds.every((id) => checkedAckIds.includes(id));

  useEffect(() => {
    if (!open || !failBoundary || !resumeBoundary) {
      return;
    }
    let cancelled = false;
    setLiveLoading(true);
    setLiveError(null);
    setCheckedAckIds([]);
    setAckValidationError(false);
    void (async () => {
      try {
        const report = (await checkIntegrity({
          from: failBoundary,
          to: resumeBoundary,
        })) as unknown as IntegrityReport;
        if (cancelled) {
          return;
        }
        setLiveEntries(report.entryViolations?.items ?? []);
      } catch (e) {
        if (!cancelled) {
          setLiveError(
            e instanceof Error ? e.message : t('integrity.reconcileDialog.liveVerifyFailed'),
          );
          setLiveEntries([]);
        }
      } finally {
        if (!cancelled) {
          setLiveLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open, failBoundary, resumeBoundary, t]);

  const reconcileMutation = useReconcileIntegrityRupture({
    mutation: {
      onSuccess: async (data) => {
        const next = data as unknown as IntegrityRuptureReconciliationResult;
        setResult(next);
        clearIntegrityInvestigationSession();
        toast(t('integrity.reconcileDialog.toastSuccess'), 'success');
        await queryClient.invalidateQueries({ queryKey: ['/api/v1/alerts'] });
        await queryClient.invalidateQueries({ queryKey: ['audit-archive-eligibility'] });
        await queryClient.invalidateQueries({ queryKey: ['audit-chain-incidents'] });
      },
      onError: (error) => {
        toast(
          getTranslatedApiError(error, t, t('integrity.reconcileDialog.errorFailed')),
          'error',
        );
      },
    },
  });

  const resetAndClose = () => {
    setJustification('');
    setCategory(ReconcileCategory.INVESTIGATED_BENIGN);
    setTicketRef('');
    setResult(null);
    setLiveEntries([]);
    setLiveError(null);
    setCheckedAckIds([]);
    setAckValidationError(false);
    reconcileMutation.reset();
    onClose();
  };

  const toggleAckEntry = (auditLogId: number, checked: boolean) => {
    setCheckedAckIds((prev) => {
      if (checked) {
        return prev.includes(auditLogId) ? prev : [...prev, auditLogId].sort((a, b) => a - b);
      }
      return prev.filter((id) => id !== auditLogId);
    });
    setAckValidationError(false);
  };

  return (
    <Dialog
      open={open}
      onClose={resetAndClose}
      title={t('integrity.reconcileDialog.title')}
      size="md"
      dismissible={false}
    >
      {result ? (
        <div className="space-y-3">
          <div className="flex items-center gap-2 text-success">
            <CheckCircle className="size-5" />
            <span className="font-bold">{t('integrity.reconcileDialog.successTitle')}</span>
          </div>
          <dl className="space-y-1.5 text-sm">
            <InfoPair
              label={t('integrity.reconcileDialog.resultBoundaries')}
              value={
                result.failBoundary && result.resumeBoundary
                  ? `${formatDate(result.failBoundary)} → ${formatDate(result.resumeBoundary)}`
                  : '—'
              }
            />
            <InfoPair
              label={t('integrity.reconcileDialog.resultCheckpointId')}
              value={String(result.conciliationCheckpointId ?? '—')}
            />
            <InfoPair
              label={t('integrity.reconcileDialog.resultChainHmac')}
              value={result.conciliationChainHmac ?? '—'}
              mono
            />
            <InfoPair
              label={t('integrity.reconcileDialog.resultAuditLogId')}
              value={String(result.auditLogId ?? '—')}
            />
            {(result.entryConciliationCount ?? 0) > 0 && (
              <>
                <InfoPair
                  label={t('integrity.reconcileDialog.resultEntryConciliationCount')}
                  value={String(result.entryConciliationCount ?? 0)}
                />
                <InfoPair
                  label={t('integrity.reconcileDialog.resultConciliatedEntries')}
                  value={
                    result.conciliatedAuditLogIds?.length
                      ? result.conciliatedAuditLogIds.map((entryId) => `#${entryId}`).join(', ')
                      : '—'
                  }
                  mono
                />
              </>
            )}
          </dl>
          <div className="flex justify-end pt-2">
            <Button type="button" onClick={resetAndClose}>
              {t('integrity.reconcileDialog.done')}
            </Button>
          </div>
        </div>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (!failBoundary || !resumeBoundary || Number.isNaN(alertId)) {
              return;
            }
            if (!allRequiredAckChecked) {
              setAckValidationError(true);
              return;
            }
            reconcileMutation.mutate({
              data: {
                alertId,
                failBoundary,
                resumeBoundary,
                justification: justification.trim(),
                category,
                externalTicketReference: ticketRef.trim() || undefined,
                acknowledgedAuditLogIds: requiredAckIds.length > 0 ? requiredAckIds : undefined,
              },
            });
          }}
          className="space-y-4"
        >
          <p className="text-xs text-fg-muted">{t('integrity.reconcileDialog.intro')}</p>
          <div className="border-2 border-warning/40 bg-warning/5 p-3 space-y-1">
            <p className="text-[10px] uppercase tracking-wider text-fg-muted font-bold">
              {t('integrity.reconcileDialog.boundariesHeading')}
            </p>
            <p className="text-sm font-bold">
              {formatDate(failBoundary)} → {formatDate(resumeBoundary)}
            </p>
            <p className="text-xs text-fg-muted font-mono">
              {t('integrity.reconcileDialog.alertRef', { id: alertId })}
            </p>
          </div>
          <div className="space-y-2 border-2 border-fg/10 p-3">
            <p className="text-[10px] uppercase tracking-wider text-fg-muted font-bold">
              {t('integrity.reconcileDialog.entryAckHeading')}
            </p>
            <p className="text-xs text-fg-muted">{t('integrity.reconcileDialog.entryAckHint')}</p>
            {liveLoading && (
              <p className="inline-flex items-center gap-1 text-xs text-fg-muted">
                <Loader2 className="size-3 animate-spin" />
                {t('integrity.reconcileDialog.liveLoading')}
              </p>
            )}
            {liveError && <UiAlert variant="error">{liveError}</UiAlert>}
            {!liveLoading && !liveError && liveEntries.length === 0 && (
              <p className="text-xs text-success">{t('integrity.reconcileDialog.entryAckNone')}</p>
            )}
            {!liveLoading && liveEntries.length > 0 && (
              <ul className="space-y-2">
                {liveEntries.map((violation) => {
                  const auditLogId = violation.auditLogId;
                  if (auditLogId == null) {
                    return null;
                  }
                  const needsAck = isEntryReconcileAckRequired(violation);
                  return (
                    <li
                      key={auditLogId}
                      className="flex items-start gap-2 border-b border-fg/10 pb-2 last:border-0 last:pb-0"
                    >
                      {needsAck ? (
                        <input
                          type="checkbox"
                          id={`integrity-reconcile-ack-${auditLogId}`}
                          checked={checkedAckIds.includes(auditLogId)}
                          onChange={(ev) => toggleAckEntry(auditLogId, ev.target.checked)}
                          className="mt-0.5 size-4 border-2 border-fg"
                        />
                      ) : (
                        <span className="mt-0.5 size-4 shrink-0" aria-hidden />
                      )}
                      <label
                        htmlFor={needsAck ? `integrity-reconcile-ack-${auditLogId}` : undefined}
                        className="flex-1 cursor-pointer"
                      >
                        <EntryViolationContent violation={violation} />
                        {!needsAck && (
                          <p className="text-[10px] text-fg-muted mt-0.5">
                            {t('integrity.reconcileDialog.entryAckExplainedNote')}
                          </p>
                        )}
                      </label>
                    </li>
                  );
                })}
              </ul>
            )}
            {ackValidationError && (
              <UiAlert variant="error">{t('integrity.reconcileDialog.entryAckValidation')}</UiAlert>
            )}
          </div>
          <div className="space-y-1">
            <Label htmlFor="integrity-reconcile-category" className="text-xs">
              {t('integrity.reconcileDialog.categoryLabel')}
            </Label>
            <Select
              id="integrity-reconcile-category"
              value={category}
              onChange={(e) =>
                setCategory(e.target.value as IntegrityRuptureReconciliationRequestCategory)}
            >
              {RECONCILE_CATEGORIES.map((item) => (
                <option key={item} value={item}>
                  {t(`integrity.reconcileDialog.category.${item}`)}
                </option>
              ))}
            </Select>
          </div>
          <div className="space-y-1">
            <Label htmlFor="integrity-reconcile-ticket" className="text-xs">
              {t('integrity.reconcileDialog.ticketLabel')}{' '}
              <span className="text-fg-muted font-normal">
                {t('integrity.reconcileDialog.ticketOptional')}
              </span>
            </Label>
            <Input
              id="integrity-reconcile-ticket"
              value={ticketRef}
              onChange={(e) => setTicketRef(e.target.value)}
              maxLength={128}
              placeholder={t('integrity.reconcileDialog.ticketPlaceholder')}
            />
          </div>
          <ReasonFieldRow
            presetGroup="audit_chain_justification"
            idPrefix="integrity-reconcile"
            inputId="integrity-reconcile-just"
            value={justification}
            onChange={setJustification}
            label={
              <>
                {t('integrity.reconcileDialog.justification')}{' '}
                <span className="text-fg-muted font-normal">
                  {t('integrity.reconcileDialog.justificationHint')}
                </span>
              </>
            }
            placeholder={t('integrity.reconcileDialog.justificationPlaceholder')}
            showMinLengthError={justification.trim().length > 0 && justification.trim().length < 10}
            minLengthErrorTone="justification"
          />
          {reconcileMutation.isError && (
            <UiAlert variant="error">
              {getTranslatedApiError(
                reconcileMutation.error,
                t,
                t('integrity.reconcileDialog.errorFailed'),
              )}
            </UiAlert>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={resetAndClose}>
              {t('integrity.reconcileDialog.cancel')}
            </Button>
            <Button
              type="submit"
              disabled={
                reconcileMutation.isPending
                || justification.trim().length < 10
                || liveLoading
                || Boolean(liveError)
              }
            >
              {reconcileMutation.isPending
                ? t('integrity.reconcileDialog.submitting')
                : t('integrity.reconcileDialog.submit')}
            </Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}
