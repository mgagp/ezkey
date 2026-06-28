import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { Check, Copy, Pencil, Shield, ShieldOff } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import { AppShell } from '@/components/layout/app-shell';
import { OperationalWarning } from '@/components/feature/operational-warning';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Tooltip } from '@/components/ui/tooltip';
import { ContextHelp } from '@/components/ui/context-help';
import { useToast } from '@/context/use-toast';
import { useListDetailPageNavigation } from '@/hooks/use-list-detail-page-navigation';
import { DetailPageNav } from '@/components/ui/detail-page-nav';
import { useIntegrations } from '@/hooks/use-integrations';
import { ApiError } from '@/lib/api-client';
import { parseAndValidateIpWhitelist } from '@/lib/ip-whitelist-validation';
import { formatDate } from '@/lib/utils';
import { RevokeApiKeyDialog } from '@/pages/api-keys';
import { getGetApiKeyQueryKey, useGetApiKey, useUpdateApiKey } from '@/generated/admin-api/api-keys/api-keys';
import type { ApiKeyResponseDto, ApiKeyUpdateRequestDto } from '@/generated/admin-api/model';

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

// ── Status badge ─────────────────────────────────────────────────────────────

function StatusBadge({ apiKey }: { apiKey: ApiKeyResponseDto }) {
  const { t } = useTranslation('api-keys');
  const [now] = useState(() => Date.now());
  if (apiKey.revokedAt) return <Badge variant="error">{t('status.labelRevoked')}</Badge>;
  if (apiKey.expiresAt && new Date(apiKey.expiresAt).getTime() < now) return <Badge variant="muted">{t('status.labelExpired')}</Badge>;
  if (apiKey.active && apiKey.expiresAt) {
    const daysLeft = (new Date(apiKey.expiresAt).getTime() - now) / (1000 * 60 * 60 * 24);
    if (daysLeft <= 7) return <Badge variant="warning">{t('status.labelExpiringSoon')}</Badge>;
  }
  if (apiKey.active && apiKey.operational === false) return <Badge variant="warning">{t('status.labelBlockedByParent')}</Badge>;
  return <Badge variant={apiKey.active ? 'success' : 'muted'}>{apiKey.active ? t('status.labelActive') : t('status.labelInactive')}</Badge>;
}

// ── Edit dialog ──────────────────────────────────────────────────────────────

type EditFormValues = {
  description?: string;
  ipWhitelist?: string;
};

function EditApiKeyDialog({
  apiKey,
  open,
  onClose,
}: {
  apiKey: ApiKeyResponseDto;
  open: boolean;
  onClose: () => void;
}) {
  const { t } = useTranslation('api-keys');
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const editSchema = useMemo(
    () =>
      z
        .object({
          description: z.string().max(255).optional().or(z.literal('')),
          ipWhitelist: z.string().optional().or(z.literal('')),
        })
        .superRefine((data, ctx) => {
          const result = parseAndValidateIpWhitelist(data.ipWhitelist);
          if (!result.success) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('validation.ipWhitelistInvalid', {
                line: result.lineNumber,
                value: result.value,
              }),
              path: ['ipWhitelist'],
            });
          }
        }),
    [t],
  );

  const { register, handleSubmit, formState: { errors } } = useForm<EditFormValues>({
    resolver: zodResolver(editSchema) as never,
    defaultValues: {
      description: apiKey.description ?? '',
      ipWhitelist: apiKey.ipWhitelist?.join('\n') ?? '',
    },
  });

  const updateMutation = useUpdateApiKey({
    mutation: {
      onSuccess: async () => {
        await queryClient.invalidateQueries({ queryKey: getGetApiKeyQueryKey(apiKey.apiKeyId!) });
        void queryClient.invalidateQueries({ queryKey: ['api-keys'] });
        toast(t('detail.toastUpdated'));
        onClose();
      },
    },
  });

  const onSubmit = (values: EditFormValues) => {
    const parseResult = parseAndValidateIpWhitelist(values.ipWhitelist);
    const ipLines = parseResult.success ? parseResult.entries : [];

    updateMutation.mutate({
      keyId: apiKey.apiKeyId!,
      data: {
        description: values.description || undefined,
        ipWhitelist: ipLines.length > 0 ? ipLines : null,
      } as ApiKeyUpdateRequestDto,
    });
  };

  const is409 = updateMutation.error instanceof ApiError && updateMutation.error.status === 409;

  return (
    <Dialog open={open} onClose={onClose} title={t('editDialog.title')} size="md" dismissible={false}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div className="space-y-1">
          <Label htmlFor="edit-desc">
            {t('editDialog.description')} <span className="text-fg-muted font-normal">{t('editDialog.descriptionOptional')}</span>
          </Label>
          <Input
            id="edit-desc"
            placeholder={t('editDialog.descriptionPlaceholder')}
            error={errors.description?.message}
            {...register('description')}
          />
        </div>

        <div className="space-y-1">
          <Label htmlFor="edit-ips">
            {t('editDialog.ipWhitelist')} <span className="text-fg-muted font-normal">{t('editDialog.ipWhitelistHint')}</span>
          </Label>
          <Textarea
            id="edit-ips"
            placeholder={t('editDialog.ipWhitelistPlaceholder')}
            rows={3}
            error={errors.ipWhitelist?.message}
            {...register('ipWhitelist')}
          />
          <p className="text-xs text-fg-muted">{t('editDialog.ipWhitelistHelp')}</p>
        </div>

        {updateMutation.isError && (
          <Alert variant="error">
            {is409
              ? t('editDialog.errorConflict')
              : updateMutation.error instanceof ApiError
                ? updateMutation.error.message
                : t('editDialog.errorUpdate')}
          </Alert>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>{t('editDialog.cancel')}</Button>
          <Button type="submit" isLoading={updateMutation.isPending}>{t('editDialog.save')}</Button>
        </div>
      </form>
    </Dialog>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────

export default function ApiKeyDetailPage() {
  const { t } = useTranslation('api-keys');
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const keyId = Number(id);

  const { nav: apiKeyListNav, goPrev: goPrevApiKey, goNext: goNextApiKey, showEndOfPageHint: showApiKeyListEndHint } =
    useListDetailPageNavigation({
      currentId: keyId,
      pathPrefix: '/api-keys',
    });

  const { lookup } = useIntegrations();

  const [editOpen, setEditOpen] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<ApiKeyResponseDto | null>(null);
  const [keyCopied, setKeyCopied] = useState(false);

  const { data: apiKey, isLoading } = useGetApiKey<ApiKeyResponseDto>(
    isNaN(keyId) ? 0 : keyId,
    { query: { enabled: !isNaN(keyId) } },
  );

  const handleCopyKey = async () => {
    if (!apiKey?.integrationKey) return;
    try {
      await navigator.clipboard.writeText(apiKey.integrationKey);
      setKeyCopied(true);
      setTimeout(() => setKeyCopied(false), 2000);
    } catch { /* ignore */ }
  };

  const isActive = apiKey?.active && !apiKey?.revokedAt;
  const isExpired = apiKey?.expiresAt ? new Date(apiKey.expiresAt) < new Date() : false;
  const integrationName = apiKey?.integrationId ? lookup.get(apiKey.integrationId) : undefined;
  const integrationDisplayName =
    apiKey?.integrationName != null && apiKey.integrationName.trim() !== ''
      ? apiKey.integrationName.trim()
      : integrationName ??
        (apiKey?.integrationId != null
          ? t('list.integrationFallback', { id: apiKey.integrationId })
          : undefined);

  return (
    <AppShell
      title={isLoading ? t('detail.fallbackTitle') : t('detail.title', { id: apiKey?.apiKeyId ?? '?' })}
      detailNav={
        apiKeyListNav ? (
          <DetailPageNav
            hasPrev={apiKeyListNav.prevId !== undefined}
            hasNext={apiKeyListNav.nextId !== undefined}
            onPrev={goPrevApiKey}
            onNext={goNextApiKey}
            showEndOfPageHint={showApiKeyListEndHint}
          />
        ) : undefined
      }
      breadcrumb={[{ label: t('detail.breadcrumb'), path: '/api-keys' }]}
    >
      {isLoading && (
        <div className="flex items-center justify-center h-32">
          <span className="size-5 border-2 border-fg border-t-transparent rounded-full animate-spin" />
        </div>
      )}

      {apiKey && (
        <div className="space-y-6">
          {apiKey.operational === false && isActive && !isExpired && (
            <OperationalWarning message={t('detail.operationalWarning.message')} />
          )}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            {/* ── Details card ──────────────────────────────── */}
            <Card className="lg:col-span-2">
              <CardHeader>
                <CardTitle>{t('detail.cardDetails')}</CardTitle>
              </CardHeader>
              <CardContent>
                <dl className="space-y-3">
                  <InfoRow label={t('detail.labelId')}>
                    <span className="font-mono">{apiKey.apiKeyId}</span>
                  </InfoRow>

                  <InfoRow label={t('detail.labelIntegration')}>
                    {apiKey.integrationId ? (
                      <Link
                        to={`/integrations/${apiKey.integrationId}`}
                        className="font-medium text-accent hover:underline"
                      >
                        {integrationDisplayName}
                        {apiKey.integrationName?.trim() ? (
                          <span className="ml-1.5 font-mono text-xs text-fg-muted">
                            (ID {apiKey.integrationId})
                          </span>
                        ) : null}
                      </Link>
                    ) : (
                      <span className="text-fg-muted">—</span>
                    )}
                  </InfoRow>

                  <InfoRow label={t('detail.labelKey')}>
                    <span className="inline-flex items-center gap-2">
                      <span className="font-mono text-xs break-all select-all">
                        {apiKey.integrationKey}
                      </span>
                      <Tooltip content={t('detail.copyKeyTooltip')}>
                        <button
                          type="button"
                          onClick={handleCopyKey}
                          className="shrink-0 text-fg-muted hover:text-fg transition-colors"
                        >
                          {keyCopied
                            ? <Check className="size-3.5 text-success" />
                            : <Copy className="size-3.5" />}
                        </button>
                      </Tooltip>
                    </span>
                  </InfoRow>

                  <InfoRow label={t('detail.labelDescription')}>
                    <span className="text-fg-muted">{apiKey.description || '—'}</span>
                  </InfoRow>

                  <InfoRow label={t('detail.labelStatus')}>
                    <StatusBadge apiKey={apiKey} />
                  </InfoRow>

                  <InfoRow label={t('detail.labelCreated')}>
                    <span className="text-fg-muted">
                      {apiKey.createdAt ? formatDate(apiKey.createdAt) : '—'}
                    </span>
                  </InfoRow>

                  <InfoRow label={t('detail.labelExpires')}>
                    <span className="text-fg-muted">
                      {apiKey.expiresAt ? formatDate(apiKey.expiresAt) : t('detail.never')}
                    </span>
                  </InfoRow>

                  <InfoRow label={t('detail.labelLastUsed')}>
                    <span className="text-fg-muted">
                      {apiKey.lastUsedAt ? formatDate(apiKey.lastUsedAt) : t('detail.never')}
                    </span>
                  </InfoRow>

                  <InfoRow label={t('detail.labelIpWhitelist')}>
                    {apiKey.ipWhitelist && apiKey.ipWhitelist.length > 0 ? (
                      <div className="flex flex-wrap gap-1.5">
                        {apiKey.ipWhitelist.map((ip) => (
                          <span
                            key={ip}
                            className="inline-flex items-center gap-1 font-mono text-xs px-1.5 py-0.5 border border-fg/20 bg-bg"
                          >
                            <Shield className="size-3 text-fg-muted" />
                            {ip}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <span className="text-fg-muted">{t('detail.noRestrictions')}</span>
                    )}
                  </InfoRow>
                </dl>
              </CardContent>
            </Card>

            <Card>
              <CardHeader><CardTitle>{t('detail.cardActions')}</CardTitle></CardHeader>
              <CardContent>
                <div className="space-y-2">
                  {isActive && !isExpired && (
                    <>
                      <Button
                        variant="secondary"
                        size="sm"
                        className="w-full justify-start gap-2"
                        onClick={() => setEditOpen(true)}
                      >
                        <Pencil className="size-3.5" />
                        {t('detail.editKeyConfig')}
                      </Button>
                      <div className="flex items-center gap-2">
                        <Button
                          variant="destructive"
                          size="sm"
                          className="flex-1 justify-start gap-2"
                          onClick={() => setRevokeTarget(apiKey)}
                        >
                          <ShieldOff className="size-3.5" />
                          {t('detail.revokeKey')}
                        </Button>
                        <ContextHelp title={t('detail.contextHelp.revokeTitle')} content={t('detail.contextHelp.revokeContent')} />
                      </div>
                    </>
                  )}

                  {!isActive && (
                    <p className="text-xs text-fg-muted italic">
                      {t('detail.noActions')}
                    </p>
                  )}
                </div>
              </CardContent>
            </Card>
          </div>

          {apiKey.revokedAt && (
            <Card className="border-error">
              <CardHeader><CardTitle>{t('detail.cardRevocation')}</CardTitle></CardHeader>
              <CardContent>
                <dl className="space-y-3">
                  <InfoRow label={t('detail.labelRevokedAt')}>
                    <span className="text-fg-muted">{formatDate(apiKey.revokedAt)}</span>
                  </InfoRow>
                  <InfoRow label={t('detail.labelRevokedBy')}>
                    <span className="text-fg-muted">{apiKey.revokedByUsername ?? '—'}</span>
                  </InfoRow>
                </dl>
              </CardContent>
            </Card>
          )}
        </div>
      )}

      {/* Dialogs */}
      {apiKey && editOpen && (
        <EditApiKeyDialog
          apiKey={apiKey}
          open={editOpen}
          onClose={() => setEditOpen(false)}
        />
      )}
      <RevokeApiKeyDialog
        apiKey={revokeTarget}
        onClose={() => setRevokeTarget(null)}
        onRevoked={() => navigate('/api-keys')}
      />
    </AppShell>
  );
}
