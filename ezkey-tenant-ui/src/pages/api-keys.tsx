import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Check, Copy, Key, Plus, RefreshCw, ShieldOff } from 'lucide-react';
import { useForm } from 'react-hook-form';
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
import { useDebounce } from '@/hooks/use-debounce';
import { useIntegrations, getIntegrationName } from '@/hooks/use-integrations';
import { usePaginatedQuery } from '@/hooks/use-paginated-query';
import { ApiError, api } from '@/lib/api-client';
import { formatDate } from '@/lib/utils';
import type { ApiKeyCreateRequest, PageResponse } from '@/types/api';
import type { ApiKey, ApiKeyCreateResponse } from '@/types/models';

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
  const [createdKey, setCreatedKey] = useState<ApiKeyCreateResponse | null>(null);
  const [secretCopied, setSecretCopied] = useState(false);
  const [savedConfirmed, setSavedConfirmed] = useState(false);

  const { register, handleSubmit, reset, formState: { errors } } = useForm<ApiKeyFormValues>({
    resolver: zodResolver(apiKeySchema),
  });

  const createMutation = useMutation({
    mutationFn: (data: ApiKeyCreateRequest) =>
      api.post<ApiKeyCreateResponse>('/api/v1/api-keys', data),
    onSuccess: (key) => {
      setCreatedKey(key);
      void queryClient.invalidateQueries({ queryKey: ['api-keys'] });
    },
  });

  const handleClose = () => {
    if (createdKey && !savedConfirmed) return; // block until confirmed
    reset();
    setCreatedKey(null);
    setSecretCopied(false);
    setSavedConfirmed(false);
    createMutation.reset();
    onClose();
  };

  const onSubmit = (values: ApiKeyFormValues) => {
    const ipLines = values.ipWhitelist
      ? values.ipWhitelist.split('\n').map((s) => s.trim()).filter(Boolean)
      : undefined;

    createMutation.mutate({
      integrationId: parseInt(values.integrationId, 10),
      description: values.description || undefined,
      expiresAt: values.expiresAt ? new Date(values.expiresAt).toISOString() : undefined,
      ipWhitelist: ipLines?.length ? ipLines : undefined,
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
    <Dialog open={open} onClose={handleClose} title="New API Key" size="md">
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
          <div className="space-y-1">
            <p className="text-[10px] font-black uppercase tracking-widest text-fg-muted">Integration Key (public)</p>
            <div className="border-2 border-fg/30 p-2.5 font-mono text-xs bg-bg break-all">
              {createdKey.integrationKey}
            </div>
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

function RevokeDialog({
  apiKey,
  onClose,
}: {
  apiKey: ApiKey | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();

  const revokeMutation = useMutation({
    mutationFn: () => api.delete(`/api/v1/api-keys/${apiKey!.apiKeyId}`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['api-keys'] });
      onClose();
    },
  });

  if (!apiKey) return null;

  return (
    <Dialog open={apiKey !== null} onClose={onClose} title="Revoke API Key" size="sm">
      <div className="space-y-4">
        <p className="text-sm text-fg">
          Are you sure you want to revoke key <span className="font-mono text-xs">{apiKey.integrationKey.slice(0, 18)}…</span>?
        </p>
        <p className="text-xs text-fg-muted">
          Revoking is permanent. Any service using this key will lose access immediately.
        </p>
        {revokeMutation.isError && (
          <Alert variant="error">
            {revokeMutation.error instanceof ApiError ? revokeMutation.error.message : 'Failed to revoke key.'}
          </Alert>
        )}
        <div className="flex gap-2 justify-end">
          <Button variant="ghost" size="sm" onClick={onClose}>Cancel</Button>
          <Button
            variant="destructive"
            size="sm"
            isLoading={revokeMutation.isPending}
            onClick={() => revokeMutation.mutate()}
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

// ── Main page ─────────────────────────────────────────────────────────────────

export default function ApiKeysPage() {
  const [createOpen, setCreateOpen] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<ApiKey | null>(null);
  const [integrationFilter, setIntegrationFilter] = useState('');
  const debouncedFilter = useDebounce(integrationFilter, 0);

  const { list: integrations, lookup } = useIntegrations();

  const { data, pagination, isLoading, refetch } = usePaginatedQuery<ApiKey>({
    queryKey: ['api-keys', debouncedFilter],
    queryFn: ({ page, size, sort }) => {
      const base = debouncedFilter
        ? `/api/v1/api-keys/integration/${debouncedFilter}`
        : '/api/v1/api-keys';
      const p = new URLSearchParams({ page: String(page), size: String(size), sort });
      return api.get<PageResponse<ApiKey>>(`${base}?${p.toString()}`);
    },
  });

  const columns: ColumnDef<ApiKey>[] = [
    { header: 'ID', key: 'apiKeyId', className: 'w-12', render: (r) => <span className="font-mono text-xs">{r.apiKeyId}</span> },
    {
      header: 'Integration',
      key: 'integrationId',
      render: (r) => (
        <span className="text-xs">{lookup.get(r.integrationId) ?? `#${r.integrationId}`}</span>
      ),
    },
    {
      header: 'Key',
      key: 'integrationKey',
      render: (r) => (
        <span className="font-mono text-xs">
          {r.integrationKey.slice(0, 22)}<span className="text-fg-muted">…</span>
        </span>
      ),
    },
    { header: 'Description', key: 'description', render: (r) => <span className="text-xs text-fg-muted">{r.description ?? '—'}</span> },
    {
      header: 'Status',
      key: 'active',
      render: (r) => {
        if (r.revokedAt) return <Badge variant="error">Revoked</Badge>;
        if (r.expiresAt && new Date(r.expiresAt) < new Date()) return <Badge variant="muted">Expired</Badge>;
        return <Badge variant={r.active ? 'success' : 'muted'}>{r.active ? 'Active' : 'Inactive'}</Badge>;
      },
    },
    { header: 'Expires', key: 'expiresAt', render: (r) => <span className="text-xs text-fg-muted">{r.expiresAt ? formatDate(r.expiresAt) : '—'}</span> },
    { header: 'Last Used', key: 'lastUsedAt', render: (r) => <span className="text-xs text-fg-muted">{r.lastUsedAt ? formatDate(r.lastUsedAt) : '—'}</span> },
    {
      header: '',
      key: 'actions',
      render: (r) =>
        r.active && !r.revokedAt ? (
          <Button
            variant="destructive"
            size="sm"
            className="gap-1"
            onClick={(e) => { e.stopPropagation(); setRevokeTarget(r); }}
          >
            <ShieldOff className="size-3" />
            Revoke
          </Button>
        ) : (
          <span className="text-xs text-fg-muted italic">{r.revokedByUsername ?? '—'}</span>
        ),
    },
  ];

  return (
    <AppShell title="API Keys">
      <div className="space-y-4">
        <div className="flex gap-3 items-center flex-wrap">
          <div className="w-44">
            <Select value={integrationFilter} onChange={(e) => setIntegrationFilter(e.target.value)}>
              <option value="">All Integrations</option>
              {integrations.map((i) => (
                <option key={i.id} value={String(i.id)}>{i.code}</option>
              ))}
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

        <div className="flex items-center gap-2">
          <Key className="size-3.5 text-fg-muted" />
          <p className="text-xs text-fg-muted italic">Max 5 active keys per integration. Secret keys are shown only at creation and cannot be retrieved later.</p>
        </div>

        <div>
          <DataTable
            columns={columns}
            data={data}
            isLoading={isLoading}
            keyExtractor={(r) => r.apiKeyId}
            emptyMessage="No API keys found. Create your first key to enable M2M access."
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

      <CreateApiKeyDialog open={createOpen} onClose={() => setCreateOpen(false)} />
      <RevokeDialog apiKey={revokeTarget} onClose={() => setRevokeTarget(null)} />
    </AppShell>
  );
}
