import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Check, Copy, Key, Plus, RefreshCw, Search, Shield, ShieldOff } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { AppShell } from '@/components/layout/app-shell';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { Pagination } from '@/components/data-table/pagination';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Tooltip } from '@/components/ui/tooltip';
import { getIntegrationName, useIntegrations } from '@/hooks/use-integrations';
import { useDebounce } from '@/hooks/use-debounce';
import { usePaginatedFromOrval } from '@/hooks/use-paginated-orval';
import { ApiError } from '@/lib/api-client';
import { API_KEY_HELP } from '@/lib/help-text';
import { formatDate } from '@/lib/utils';
import {
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
  const status = getKeyStatus(apiKey);
  const hasIpRestriction = apiKey.ipWhitelist && apiKey.ipWhitelist.length > 0;

  const badge = (() => {
    switch (status) {
      case 'revoked': return <Tooltip content={API_KEY_HELP.REVOKED}><Badge variant="error">Revoked</Badge></Tooltip>;
      case 'expired': return <Badge variant="muted">Expired</Badge>;
      case 'expiring-soon': return <Tooltip content={API_KEY_HELP.EXPIRING_SOON}><Badge variant="warning">Expiring Soon</Badge></Tooltip>;
      case 'active': return <Badge variant="success">Active</Badge>;
      default: return <Badge variant="muted">Inactive</Badge>;
    }
  })();

  return (
    <span className="inline-flex items-center gap-1.5">
      {badge}
      {hasIpRestriction && (
        <Tooltip content={API_KEY_HELP.IP_WHITELIST}>
          <span className="inline-flex"><Shield className="size-3 text-fg-muted" aria-hidden /></span>
        </Tooltip>
      )}
    </span>
  );
}

// ── Create API key dialog ──────────────────────────────────────────────────────

const apiKeySchema = z.object({
  integrationId: z.string().min(1, 'Select an integration'),
  description: z.string().max(255).optional().or(z.literal('')),
  expiresAt: z.string().optional().or(z.literal('')),
  ipWhitelist: z.string().optional().or(z.literal('')),
});
type ApiKeyFormValues = z.infer<typeof apiKeySchema>;

function CreateApiKeyDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const { list: integrations, isLoading: loadingIntegrations } = useIntegrations();
  const [createdKey, setCreatedKey] = useState<ApiKeyCreateResponseDto | null>(null);
  const [integrationKeyCopied, setIntegrationKeyCopied] = useState(false);
  const [secretCopied, setSecretCopied] = useState(false);
  const [savedConfirmed, setSavedConfirmed] = useState(false);

  const { register, handleSubmit, reset, formState: { errors } } = useForm<ApiKeyFormValues>({
    resolver: zodResolver(apiKeySchema),
  });

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
    const ipLines = values.ipWhitelist
      ? values.ipWhitelist.split('\n').map((s) => s.trim()).filter(Boolean)
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
    <Dialog open={open} onClose={handleClose} title="New API Key" size="md" dismissible={false}>
      {createdKey ? (
        /* ── Secret key — SHOWN ONCE ────────────────────── */
        <div className="space-y-4">
          {/* Warning banner */}
          <div className="border-2 border-error bg-error/5 p-3 flex gap-2">
            <AlertTriangle className="size-4 text-error shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-black text-error">This is the only time you will see the secret key.</p>
              <p className="text-xs text-error/80 mt-0.5">Copy it now and store it securely. It cannot be retrieved after this dialog is closed.</p>
            </div>
          </div>

          {/* Public key */}
          <div className="space-y-1.5">
            <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">Integration Key (public)</p>
            <div className="border-2 border-fg/30 p-2.5 font-mono text-xs bg-bg break-all select-all">
              {createdKey.integrationKey}
            </div>
            <Button variant="secondary" size="sm" onClick={handleCopyIntegrationKey} className="gap-1.5">
              {integrationKeyCopied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
              {integrationKeyCopied ? 'Copied!' : 'Copy Integration Key'}
            </Button>
          </div>

          {/* Secret key */}
          <div className="space-y-1.5">
            <p className="text-[10px] font-black uppercase tracking-widest text-error">Secret Key (save now!)</p>
            <div className="border-2 border-error p-2.5 font-mono text-xs bg-error/5 break-all select-all">
              {createdKey.secretKey}
            </div>
            <Button variant="secondary" size="sm" onClick={handleCopySecret} className="gap-1.5">
              {secretCopied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
              {secretCopied ? 'Copied!' : 'Copy Secret Key'}
            </Button>
          </div>

          {/* Confirmation checkbox */}
          <div className="flex items-center gap-2.5 p-3 border-2 border-fg/20 bg-bg">
            <input
              id="key-saved"
              type="checkbox"
              className="size-4 border-2 border-fg accent-accent"
              checked={savedConfirmed}
              onChange={(e) => setSavedConfirmed(e.target.checked)}
            />
            <label htmlFor="key-saved" className="text-sm font-bold cursor-pointer select-none">
              I have saved the secret key in a secure location
            </label>
          </div>

          <div className="flex justify-end pt-2">
            <Button onClick={handleClose} disabled={!savedConfirmed}>
              Done
            </Button>
          </div>
        </div>
      ) : !loadingIntegrations && integrations.length === 0 ? (
        <Alert variant="warning">
          No integration available. <Link to="/integrations" className="font-medium text-accent underline">Create an integration first</Link>.
        </Alert>
      ) : (
        /* ── Create form ─────────────────────────────────── */
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-1">
            <Label htmlFor="key-integration">Integration *</Label>
            <Select
              id="key-integration"
              disabled={loadingIntegrations}
              error={errors.integrationId?.message}
              {...register('integrationId')}
            >
              <option value="">Select an integration...</option>
              {integrations.map((i) => (
                <option key={i.id} value={i.id}>
                  {i.code} — {getIntegrationName(i)}
                </option>
              ))}
            </Select>
          </div>

          <div className="space-y-1">
            <Label htmlFor="key-desc">Description <span className="text-fg-muted font-normal">(optional)</span></Label>
            <Input
              id="key-desc"
              placeholder="e.g. Production server — CI/CD pipeline"
              error={errors.description?.message}
              {...register('description')}
            />
          </div>

          <div className="space-y-1">
            <Label htmlFor="key-expires">Expires At <span className="text-fg-muted font-normal">(optional — leave blank for no expiration)</span></Label>
            <Input
              id="key-expires"
              type="datetime-local"
              error={errors.expiresAt?.message}
              {...register('expiresAt')}
            />
          </div>

          <div className="space-y-1">
            <Label htmlFor="key-ips">IP Whitelist <span className="text-fg-muted font-normal">(optional — one IP or CIDR per line)</span></Label>
            <Textarea
              id="key-ips"
              placeholder={'192.168.1.0/24\n10.0.0.1'}
              rows={3}
              error={errors.ipWhitelist?.message}
              {...register('ipWhitelist')}
            />
          </div>

          {createMutation.isError && (
            <Alert variant="error">
              {createMutation.error instanceof ApiError ? createMutation.error.message : 'Failed to create API key.'}
            </Alert>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
            <Button type="submit" isLoading={createMutation.isPending}>Create API Key</Button>
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
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');

  const revokeMutation = useRevokeApiKey({
    mutation: {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: ['api-keys'] });
        void queryClient.invalidateQueries({ queryKey: ['api-key'] });
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
    <Dialog open={apiKey !== null} onClose={handleClose} title="Revoke API Key" size="sm">
      <div className="space-y-4">
        <p className="text-sm text-fg">
          Are you sure you want to revoke key{' '}
          <span className="font-mono text-xs">{(apiKey.integrationKey ?? '').slice(0, 18)}…</span>?
        </p>
        <p className="text-xs text-fg-muted">
          Revoking is permanent. Any service using this key will lose access immediately.
        </p>
        <div className="space-y-1">
          <Label htmlFor="revoke-reason">
            Reason <span className="text-fg-muted font-normal">(optional — min 10 chars for audit trail)</span>
          </Label>
          <Input
            id="revoke-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. Key compromised, rotating credentials"
          />
          {reasonTooShort && (
            <p className="text-xs text-error">Reason must be at least 10 characters.</p>
          )}
        </div>
        {revokeMutation.isError && (
          <Alert variant="error">
            {revokeMutation.error instanceof ApiError ? revokeMutation.error.message : 'Failed to revoke key.'}
          </Alert>
        )}
        <div className="flex gap-2 justify-end">
          <Button variant="ghost" size="sm" onClick={handleClose}>Cancel</Button>
          <Button
            variant="destructive"
            size="sm"
            isLoading={revokeMutation.isPending}
            disabled={reasonTooShort}
            onClick={() => revokeMutation.mutate({ keyId: apiKey!.apiKeyId!, params: { reason: reason.trim() || undefined } })}
            className="gap-1.5"
          >
            <ShieldOff className="size-3.5" />
            Revoke
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
  const navigate = useNavigate();
  const [createOpen, setCreateOpen] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<ApiKeyResponseDto | null>(null);
  const [integrationFilter, setIntegrationFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('');
  const [descriptionInput, setDescriptionInput] = useState('');
  const debouncedDescription = useDebounce(descriptionInput, 300);

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
      header: 'ID',
      key: 'apiKeyId',
      className: 'w-14',
      sortKey: 'apiKeyId',
      render: (r) => <span className="font-mono text-xs">{r.apiKeyId}</span>,
    },
    {
      header: 'Integration',
      key: 'integrationId',
      sortKey: 'integrationKey',
      render: (r) => (
        <span className="text-xs font-medium">{lookup.get(r.integrationId!) ?? `#${r.integrationId ?? '?'}`}</span>
      ),
    },
    {
      header: 'Description',
      key: 'description',
      sortKey: 'description',
      render: (r) => (
        <span className="text-xs text-fg-muted">{r.description ?? '—'}</span>
      ),
    },
    {
      header: 'Status',
      key: 'active',
      sortKey: 'active',
      render: (r) => <KeyStatusBadge apiKey={r} />,
    },
    {
      header: 'Created',
      key: 'createdAt',
      sortKey: 'createdAt',
      render: (r) => (
        <span className="text-xs text-fg-muted">{r.createdAt ? formatDate(r.createdAt) : '—'}</span>
      ),
    },
    {
      header: 'Expires',
      key: 'expiresAt',
      sortKey: 'expiresAt',
      render: (r) => (
        <span className="text-xs text-fg-muted">{r.expiresAt ? formatDate(r.expiresAt) : '—'}</span>
      ),
    },
    {
      header: 'Last Used',
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
          <Tooltip content="Revoke this key">
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
    <AppShell title="API Keys">
      <div className="space-y-4">
        {/* Toolbar */}
        <div className="flex gap-3 items-center flex-wrap">
          <div className="flex-1 min-w-52 relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 size-3.5 text-fg-muted pointer-events-none" />
            <Input
              placeholder="Search by description..."
              value={descriptionInput}
              onChange={(e) => setDescriptionInput(e.target.value)}
              className="pl-8"
            />
          </div>
          <div className="w-44">
            <Select value={integrationFilter} onChange={(e) => setIntegrationFilter(e.target.value)}>
              <option value="">All Integrations</option>
              {integrations.map((i) => (
                <option key={i.id} value={String(i.id)}>{i.code}</option>
              ))}
            </Select>
          </div>
          <div className="w-36">
            <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as StatusFilter)}>
              <option value="">All Statuses</option>
              <option value="active">Active</option>
              <option value="revoked">Revoked</option>
              <option value="expired">Expired</option>
            </Select>
          </div>
          <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5">
            <RefreshCw className="size-3.5" />
            Refresh
          </Button>
          <Button size="sm" onClick={() => setCreateOpen(true)} className="gap-1.5 ml-auto">
            <Plus className="size-3.5" />
            New API Key
          </Button>
        </div>

        {/* Info banner */}
        <div className="flex items-center gap-2">
          <Key className="size-3.5 text-fg-muted" />
          <p className="text-xs text-fg-muted italic">Max 5 active keys per integration. Secret keys are shown only at creation and cannot be retrieved later.</p>
        </div>

        {/* Table */}
        <div>
          <DataTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            keyExtractor={(r, i) => r.apiKeyId ?? i}
            emptyMessage="No API keys found. Create your first key to enable M2M access."
            onRowClick={(row) => navigate(`/api-keys/${row.apiKeyId}`)}
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
      </div>

      <CreateApiKeyDialog open={createOpen} onClose={() => setCreateOpen(false)} />
      <RevokeApiKeyDialog apiKey={revokeTarget} onClose={() => setRevokeTarget(null)} />
    </AppShell>
  );
}
