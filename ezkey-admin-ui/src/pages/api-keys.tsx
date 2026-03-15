import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Check, Copy, Key, Plus, RefreshCw, Search, Shield, ShieldOff } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { z } from 'zod';
import { AppShell } from '@/components/layout/app-shell';
import { type ColumnDef } from '@/components/data-table/data-table';
import { PaginatedTable } from '@/components/data-table/paginated-table';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Tooltip } from '@/components/ui/tooltip';
import { DemoReasonBadges } from '@/components/feature/demo-reason-badges';
import { getIntegrationName, useIntegrations } from '@/hooks/use-integrations';
import { useDebounce } from '@/hooks/use-debounce';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { ApiError } from '@/lib/api-client';
import { parseAndValidateIpWhitelist } from '@/lib/ip-whitelist-validation';
import { formatDate } from '@/lib/utils';
import {
  getGetApiKeyQueryKey,
  listAllApiKeys,
  listApiKeys,
  useCreateApiKey,
  useRevokeApiKey,
} from '@/generated/admin-api/api-keys/api-keys';
import type {
  ApiKeyCreateResponseDto,
  ApiKeyResponseDto,
  PagedModelApiKeyResponseDto,
} from '@/generated/admin-api/model';

// ── Helpers ────────────────────────────────────────────────────────────────────

type KeyStatus = 'active' | 'expiring-soon' | 'expired' | 'revoked' | 'inactive';

const EXPIRING_SOON_DAYS = 7;

function getKeyStatus(key: ApiKeyResponseDto): KeyStatus {
  if (key.revokedAt) return 'revoked';
  if (key.expiresAt && new Date(key.expiresAt) < new Date()) return 'expired';
  if (key.active && key.expiresAt) {
    const daysLeft = (new Date(key.expiresAt).getTime() - Date.now()) / (1000 * 60 * 60 * 24);
    if (daysLeft <= EXPIRING_SOON_DAYS) return 'expiring-soon';
  }
  return key.active ? 'active' : 'inactive';
}

function KeyStatusBadge({ apiKey }: { apiKey: ApiKeyResponseDto }) {
  const { t } = useTranslation('api-keys');
  const status = getKeyStatus(apiKey);
  const hasIpRestriction = apiKey.ipWhitelist && apiKey.ipWhitelist.length > 0;

  const badge = (() => {
    switch (status) {
      case 'revoked': return <Tooltip content={t('status.helpRevoked')}><Badge variant="error">{t('status.labelRevoked')}</Badge></Tooltip>;
      case 'expired': return <Badge variant="muted">{t('status.labelExpired')}</Badge>;
      case 'expiring-soon': return <Tooltip content={t('status.helpExpiringSoon')}><Badge variant="warning">{t('status.labelExpiringSoon')}</Badge></Tooltip>;
      case 'active': return <Badge variant="success">{t('status.labelActive')}</Badge>;
      default: return <Badge variant="muted">{t('status.labelInactive')}</Badge>;
    }
  })();

  return (
    <span className="inline-flex items-center gap-1.5">
      {badge}
      {hasIpRestriction && (
        <Tooltip content={t('status.helpIpWhitelist')}>
          <span className="inline-flex"><Shield className="size-3 text-fg-muted" aria-hidden /></span>
        </Tooltip>
      )}
    </span>
  );
}

// ── Create API key dialog ──────────────────────────────────────────────────────

type ApiKeyFormValues = {
  integrationId: string;
  description?: string;
  expiresAt?: string;
  ipWhitelist?: string;
};

function CreateApiKeyDialog({
  open,
  onClose,
  defaultIntegrationId,
}: {
  open: boolean;
  onClose: () => void;
  defaultIntegrationId?: number;
}) {
  const { t } = useTranslation('api-keys');
  const queryClient = useQueryClient();
  const { list: integrations, isLoading: loadingIntegrations } = useIntegrations();

  const apiKeySchema = useMemo(
    () =>
      z
        .object({
          integrationId: z.string().min(1, t('validation.selectIntegration')),
          description: z.string().max(255).optional().or(z.literal('')),
          expiresAt: z.string().optional().or(z.literal('')),
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
  const [createdKey, setCreatedKey] = useState<ApiKeyCreateResponseDto | null>(null);
  const [integrationKeyCopied, setIntegrationKeyCopied] = useState(false);
  const [secretCopied, setSecretCopied] = useState(false);
  const [savedConfirmed, setSavedConfirmed] = useState(false);

  const { register, handleSubmit, reset, formState: { errors } } = useForm<ApiKeyFormValues>({
    resolver: zodResolver(apiKeySchema) as never,
    defaultValues: {
      integrationId: defaultIntegrationId ? String(defaultIntegrationId) : '',
      description: '',
      expiresAt: '',
      ipWhitelist: '',
    },
  });

  // When opening from integration detail (defaultIntegrationId), preselect that integration.
  // Run after integrations load so the <option value="..."> exists in the DOM.
  useEffect(() => {
    if (open && defaultIntegrationId !== undefined && !loadingIntegrations) {
      reset({
        integrationId: String(defaultIntegrationId),
        description: '',
        expiresAt: '',
        ipWhitelist: '',
      });
    }
  }, [open, defaultIntegrationId, loadingIntegrations, reset]);

  const createMutation = useCreateApiKey({
    mutation: {
      onSuccess: (key) => {
        setCreatedKey(key as unknown as ApiKeyCreateResponseDto);
        void queryClient.invalidateQueries({ queryKey: ['api-keys'] });
      },
    },
  });

  const handleClose = () => {
    if (createdKey && !savedConfirmed) return; // block until confirmed
    reset();
    setCreatedKey(null);
    setIntegrationKeyCopied(false);
    setSecretCopied(false);
    setSavedConfirmed(false);
    createMutation.reset();
    onClose();
  };

  const handleCopyIntegrationKey = async () => {
    if (!createdKey?.integrationKey) return;
    try {
      await navigator.clipboard.writeText(createdKey.integrationKey);
      setIntegrationKeyCopied(true);
      setTimeout(() => setIntegrationKeyCopied(false), 2000);
    } catch { /* ignore */ }
  };

  const onSubmit = (values: ApiKeyFormValues) => {
    const parseResult = parseAndValidateIpWhitelist(values.ipWhitelist);
    const ipLines = parseResult.success && parseResult.entries.length > 0
      ? parseResult.entries
      : undefined;

    createMutation.mutate({
      data: {
        integrationId: parseInt(values.integrationId, 10),
        description: values.description || undefined,
        expiresAt: values.expiresAt ? new Date(values.expiresAt).toISOString() : undefined,
        ipWhitelist: ipLines?.length ? ipLines : undefined,
      },
    });
  };

  const handleCopySecret = async () => {
    if (!createdKey?.secretKey) return;
    try {
      await navigator.clipboard.writeText(createdKey.secretKey);
      setSecretCopied(true);
      setTimeout(() => setSecretCopied(false), 2000);
    } catch { /* ignore */ }
  };

  return (
    <Dialog open={open} onClose={handleClose} title={t('create.title')} size="md" dismissible={false}>
      {createdKey ? (
        /* ── Secret key — SHOWN ONCE ────────────────────── */
        <div className="space-y-4">
          <div className="border-2 border-error bg-error/5 p-3 flex gap-2">
            <AlertTriangle className="size-4 text-error shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-black text-error">{t('create.warningTitle')}</p>
              <p className="text-xs text-error/80 mt-0.5">{t('create.warningBody')}</p>
            </div>
          </div>

          <div className="space-y-1.5">
            <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">{t('create.integrationKeyLabel')}</p>
            <div className="border-2 border-fg/30 p-2.5 font-mono text-xs bg-bg break-all select-all">
              {createdKey.integrationKey}
            </div>
            <Button variant="secondary" size="sm" onClick={handleCopyIntegrationKey} className="gap-1.5">
              {integrationKeyCopied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
              {integrationKeyCopied ? t('create.copied') : t('create.copyIntegrationKey')}
            </Button>
          </div>

          <div className="space-y-1.5">
            <p className="text-[10px] font-black uppercase tracking-widest text-error">{t('create.secretKeyLabel')}</p>
            <div className="border-2 border-error p-2.5 font-mono text-xs bg-error/5 break-all select-all">
              {createdKey.secretKey}
            </div>
            <Button variant="secondary" size="sm" onClick={handleCopySecret} className="gap-1.5">
              {secretCopied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
              {secretCopied ? t('create.copied') : t('create.copySecretKey')}
            </Button>
          </div>

          <div className="flex items-center gap-2.5 p-3 border-2 border-fg/20 bg-bg">
            <input
              id="key-saved"
              type="checkbox"
              className="size-4 border-2 border-fg accent-accent"
              checked={savedConfirmed}
              onChange={(e) => setSavedConfirmed(e.target.checked)}
            />
            <label htmlFor="key-saved" className="text-sm font-bold cursor-pointer select-none">
              {t('create.savedConfirmLabel')}
            </label>
          </div>

          <div className="flex justify-end pt-2">
            <Button onClick={handleClose} disabled={!savedConfirmed}>
              {t('create.done')}
            </Button>
          </div>
        </div>
      ) : !loadingIntegrations && integrations.length === 0 ? (
        <Alert variant="warning">
          {t('create.noIntegration')} <Link to="/integrations" className="font-medium text-accent underline">{t('create.createIntegrationFirst')}</Link>.
        </Alert>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-1">
            <Label htmlFor="key-integration">{t('create.integration')}</Label>
            <Select
              id="key-integration"
              disabled={loadingIntegrations}
              error={errors.integrationId?.message}
              {...register('integrationId')}
            >
              <option value="">{t('create.integrationPlaceholder')}</option>
              {integrations.map((i) => (
                <option key={i.id} value={i.id}>
                  {i.code} — {getIntegrationName(i)}
                </option>
              ))}
            </Select>
          </div>

          <div className="space-y-1">
            <Label htmlFor="key-desc">{t('create.description')} <span className="text-fg-muted font-normal">{t('create.descriptionOptional')}</span></Label>
            <Input
              id="key-desc"
              placeholder={t('create.descriptionPlaceholder')}
              error={errors.description?.message}
              {...register('description')}
            />
          </div>

          <div className="space-y-1">
            <Label htmlFor="key-expires">{t('create.expiresAt')} <span className="text-fg-muted font-normal">{t('create.expiresAtHint')}</span></Label>
            <Input
              id="key-expires"
              type="datetime-local"
              error={errors.expiresAt?.message}
              {...register('expiresAt')}
            />
          </div>

          <div className="space-y-1">
            <Label htmlFor="key-ips">{t('create.ipWhitelist')} <span className="text-fg-muted font-normal">{t('create.ipWhitelistHint')}</span></Label>
            <Textarea
              id="key-ips"
              placeholder={t('create.ipWhitelistPlaceholder')}
              rows={3}
              error={errors.ipWhitelist?.message}
              {...register('ipWhitelist')}
            />
            <p className="text-xs text-fg-muted">{t('create.ipWhitelistHelp')}</p>
          </div>

          {createMutation.isError && (
            <Alert variant="error">
              {createMutation.error instanceof ApiError ? createMutation.error.message : t('create.errorCreate')}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={onClose}>{t('create.cancel')}</Button>
            <Button type="submit" isLoading={createMutation.isPending}>{t('create.submit')}</Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

// ── Revoke confirmation dialog ─────────────────────────────────────────────────

export function RevokeApiKeyDialog({
  apiKey,
  onClose,
  onRevoked,
}: {
  apiKey: ApiKeyResponseDto | null;
  onClose: () => void;
  onRevoked?: () => void;
}) {
  const { t } = useTranslation('api-keys');
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');

  const revokeMutation = useRevokeApiKey({
    mutation: {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: ['api-keys'] });
        if (apiKey?.apiKeyId != null) {
          void queryClient.invalidateQueries({ queryKey: getGetApiKeyQueryKey(apiKey.apiKeyId) });
        }
        setReason('');
        onRevoked?.();
        onClose();
      },
    },
  });

  const handleClose = () => {
    setReason('');
    revokeMutation.reset();
    onClose();
  };

  if (!apiKey) return null;

  const reasonTooShort = reason.trim().length > 0 && reason.trim().length < 10;

  return (
    <Dialog open={apiKey !== null} onClose={handleClose} title={t('revokeDialog.title')} size="sm">
      <div className="space-y-4">
        <p className="text-sm text-fg">
          {t('revokeDialog.confirm')}{' '}
          <span className="font-mono text-xs">{(apiKey.integrationKey ?? '').slice(0, 18)}…</span>?
        </p>
        <p className="text-xs text-fg-muted">
          {t('revokeDialog.permanent')}
        </p>
        <div className="space-y-1">
          <Label htmlFor="revoke-reason">
            {t('revokeDialog.reasonLabel')} <span className="text-fg-muted font-normal">{t('revokeDialog.reasonHint')}</span>
          </Label>
          <Input
            id="revoke-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder={t('revokeDialog.reasonPlaceholder')}
          />
          <DemoReasonBadges onSelect={setReason} />
          {reasonTooShort && (
            <p className="text-xs text-error">{t('revokeDialog.reasonMinError')}</p>
          )}
        </div>
        {revokeMutation.isError && (
          <Alert variant="error">
            {revokeMutation.error instanceof ApiError ? revokeMutation.error.message : t('revokeDialog.errorRevoke')}
          </Alert>
        )}
        <div className="flex gap-2 justify-end">
          <Button variant="ghost" size="sm" onClick={handleClose}>{t('revokeDialog.cancel')}</Button>
          <Button
            variant="destructive"
            size="sm"
            isLoading={revokeMutation.isPending}
            disabled={reasonTooShort}
            onClick={() => revokeMutation.mutate({ keyId: apiKey!.apiKeyId!, params: { reason: reason.trim() || undefined } })}
            className="gap-1.5"
          >
            <ShieldOff className="size-3.5" />
            {t('revokeDialog.revoke')}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

// ── Status filter → API param ─────────────────────────────────────────────────

type StatusFilter = '' | 'active' | 'revoked' | 'expired';

function statusFilterToActive(value: StatusFilter): boolean | undefined {
  if (value === '') return undefined;
  if (value === 'active') return true;
  return false; // revoked | expired → inactive
}

// ── Main page ─────────────────────────────────────────────────────────────────

type BaseParams = {
  integrationId?: number;
  active?: boolean;
  description?: string;
};

export default function ApiKeysPage() {
  const { t } = useTranslation('api-keys');
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [createOpen, setCreateOpen] = useState(false);
  const [prefillIntegrationId, setPrefillIntegrationId] = useState<number | undefined>(undefined);
  const [revokeTarget, setRevokeTarget] = useState<ApiKeyResponseDto | null>(null);
  const [integrationFilter, setIntegrationFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('');
  const [descriptionInput, setDescriptionInput] = useState('');
  const debouncedDescription = useDebounce(descriptionInput, 300);

  useEffect(() => {
    const create = searchParams.get('create');
    const idParam = searchParams.get('integrationId');
    if (create === '1' && idParam) {
      const id = Number(idParam);
      if (!Number.isNaN(id)) {
        setCreateOpen(true);
        setPrefillIntegrationId(id);
        setSearchParams({}, { replace: true });
      }
    }
  }, [searchParams, setSearchParams]);

  const { list: integrations, lookup } = useIntegrations();

  const baseParams: BaseParams = {
    integrationId: integrationFilter ? Number(integrationFilter) : undefined,
    active: statusFilterToActive(statusFilter),
    description: debouncedDescription || undefined,
  };

  const { data, pagination, isLoading, refetch } = usePaginatedFromOrval<
    ApiKeyResponseDto,
    BaseParams
  >({
    queryKey: ['api-keys', integrationFilter, statusFilter, debouncedDescription],
    baseParams,
    fetchPage: (params) => {
      if (integrationFilter) {
        return listApiKeys(Number(integrationFilter), {
          page: params.page,
          size: params.size,
          sort: params.sort,
          active: params.active,
        }) as Promise<PagedModelApiKeyResponseDto>;
      }
      return listAllApiKeys({
        ...params,
        integrationId: params.integrationId,
        active: params.active,
        description: params.description,
      }) as Promise<PagedModelApiKeyResponseDto>;
    },
    defaultSort: 'createdAt,DESC',
  });

  const columns: ColumnDef<ApiKeyResponseDto>[] = [
    {
      header: t('list.columns.id'),
      key: 'apiKeyId',
      className: 'w-14',
      sortKey: 'apiKeyId',
      render: (r) => <span className="font-mono text-xs">{r.apiKeyId}</span>,
    },
    {
      header: t('list.columns.integration'),
      key: 'integrationId',
      sortKey: 'integrationKey',
      render: (r) => (
        <span className="text-xs font-medium">{lookup.get(r.integrationId!) ?? `#${r.integrationId ?? '?'}`}</span>
      ),
    },
    {
      header: t('list.columns.description'),
      key: 'description',
      sortKey: 'description',
      render: (r) => (
        <span className="text-xs text-fg-muted">{r.description ?? '—'}</span>
      ),
    },
    {
      header: t('list.columns.status'),
      key: 'active',
      sortKey: 'active',
      render: (r) => <KeyStatusBadge apiKey={r} />,
    },
    {
      header: t('list.columns.created'),
      key: 'createdAt',
      sortKey: 'createdAt',
      render: (r) => (
        <span className="text-xs text-fg-muted">{r.createdAt ? formatDate(r.createdAt) : '—'}</span>
      ),
    },
    {
      header: t('list.columns.expires'),
      key: 'expiresAt',
      sortKey: 'expiresAt',
      render: (r) => (
        <span className="text-xs text-fg-muted">{r.expiresAt ? formatDate(r.expiresAt) : '—'}</span>
      ),
    },
    {
      header: t('list.columns.lastUsed'),
      key: 'lastUsedAt',
      sortKey: 'lastUsedAt',
      render: (r) => (
        <span className="text-xs text-fg-muted">{r.lastUsedAt ? formatDate(r.lastUsedAt) : '—'}</span>
      ),
    },
    {
      header: '',
      key: 'actions',
      className: 'w-10',
      render: (r) =>
        r.active && !r.revokedAt ? (
          <Tooltip content={t('list.revokeTooltip')}>
            <Button
              variant="destructive"
              size="sm"
              onClick={(e) => { e.stopPropagation(); setRevokeTarget(r); }}
              className="gap-1 px-2"
            >
              <ShieldOff className="size-3" />
            </Button>
          </Tooltip>
        ) : null,
    },
  ];

  return (
    <AppShell title={t('list.title')}>
      <div className="space-y-4">
        <div className="flex gap-3 items-center flex-wrap">
          <div className="flex-1 min-w-52 relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 size-3.5 text-fg-muted pointer-events-none" />
            <Input
              placeholder={t('list.searchPlaceholder')}
              value={descriptionInput}
              onChange={(e) => setDescriptionInput(e.target.value)}
              className="pl-8"
            />
          </div>
          <div className="w-52">
            <Select value={integrationFilter} onChange={(e) => setIntegrationFilter(e.target.value)}>
              <option value="">{t('list.filterIntegrationAll')}</option>
              {integrations.map((i) => (
                <option key={i.id} value={String(i.id)}>{i.code}</option>
              ))}
            </Select>
          </div>
          <div className="w-36">
            <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as StatusFilter)}>
              <option value="">{t('list.filterStatusAll')}</option>
              <option value="active">{t('list.filterStatusActive')}</option>
              <option value="revoked">{t('list.filterStatusRevoked')}</option>
              <option value="expired">{t('list.filterStatusExpired')}</option>
            </Select>
          </div>
          <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5">
            <RefreshCw className="size-3.5" />
            {t('list.refresh')}
          </Button>
          <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-1.5 ml-auto">
            <Plus className="size-3.5" />
            {t('list.newApiKey')}
          </Button>
        </div>

        <div className="flex items-center gap-2">
          <Key className="size-3.5 text-fg-muted" />
          <p className="text-xs text-fg-muted italic">{t('list.hint')}</p>
        </div>

        <div>
          <PaginatedTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            keyExtractor={(r, i) => r.apiKeyId ?? i}
            emptyMessage={t('list.emptyMessage')}
            onRowClick={(row) => navigate(`/api-keys/${row.apiKeyId}`)}
            currentSort={pagination.sort}
            onSort={pagination.setSort}
            pagination={pagination}
          />
        </div>
      </div>

      <CreateApiKeyDialog
        key={prefillIntegrationId ?? 'new'}
        open={createOpen}
        onClose={() => {
          setCreateOpen(false);
          setPrefillIntegrationId(undefined);
        }}
        defaultIntegrationId={prefillIntegrationId}
      />
      <RevokeApiKeyDialog apiKey={revokeTarget} onClose={() => setRevokeTarget(null)} />
    </AppShell>
  );
}
