import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { Check, Copy, Pencil, Shield, ShieldOff } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import { AppShell } from '@/components/layout/app-shell';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Tooltip } from '@/components/ui/tooltip';
import { useToast } from '@/context/toast-context';
import { useIntegrations } from '@/hooks/use-integrations';
import { ApiError } from '@/lib/api-client';
import { formatDate } from '@/lib/utils';
import { RevokeApiKeyDialog } from '@/pages/api-keys';
import { useGetApiKey, useUpdateApiKey } from '@/generated/admin-api/api-keys/api-keys';
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
  if (apiKey.revokedAt) return <Badge variant="error">Revoked</Badge>;
  if (apiKey.expiresAt && new Date(apiKey.expiresAt) < new Date()) return <Badge variant="muted">Expired</Badge>;
  if (apiKey.active && apiKey.expiresAt) {
    const daysLeft = (new Date(apiKey.expiresAt).getTime() - Date.now()) / (1000 * 60 * 60 * 24);
    if (daysLeft <= 7) return <Badge variant="warning">Expiring Soon</Badge>;
  }
  return <Badge variant={apiKey.active ? 'success' : 'muted'}>{apiKey.active ? 'Active' : 'Inactive'}</Badge>;
}

// ── Edit dialog ──────────────────────────────────────────────────────────────

const editSchema = z.object({
  description: z.string().max(255).optional().or(z.literal('')),
  ipWhitelist: z.string().optional().or(z.literal('')),
});
type EditFormValues = z.infer<typeof editSchema>;

function EditApiKeyDialog({
  apiKey,
  open,
  onClose,
}: {
  apiKey: ApiKeyResponseDto;
  open: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  const { register, handleSubmit, formState: { errors } } = useForm<EditFormValues>({
    resolver: zodResolver(editSchema),
    defaultValues: {
      description: apiKey.description ?? '',
      ipWhitelist: apiKey.ipWhitelist?.join('\n') ?? '',
    },
  });

  const updateMutation = useUpdateApiKey({
    mutation: {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: ['api-key', apiKey.apiKeyId] });
        void queryClient.invalidateQueries({ queryKey: ['api-keys'] });
        toast('API key updated.');
        onClose();
      },
    },
  });

  const onSubmit = (values: EditFormValues) => {
    const ipLines = values.ipWhitelist
      ? values.ipWhitelist.split('\n').map((s) => s.trim()).filter(Boolean)
      : [];

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
    <Dialog open={open} onClose={onClose} title="Edit API Key" size="md" dismissible={false}>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div className="space-y-1">
          <Label htmlFor="edit-desc">
            Description <span className="text-fg-muted font-normal">(optional)</span>
          </Label>
          <Input
            id="edit-desc"
            placeholder="e.g. Production server — CI/CD pipeline"
            error={errors.description?.message}
            {...register('description')}
          />
        </div>

        <div className="space-y-1">
          <Label htmlFor="edit-ips">
            IP Whitelist <span className="text-fg-muted font-normal">(one IP or CIDR per line — leave empty to remove restrictions)</span>
          </Label>
          <Textarea
            id="edit-ips"
            placeholder={'192.168.1.0/24\n10.0.0.1'}
            rows={3}
            error={errors.ipWhitelist?.message}
            {...register('ipWhitelist')}
          />
        </div>

        {updateMutation.isError && (
          <Alert variant="error">
            {is409
              ? 'This key was modified by another user. Please refresh and try again.'
              : updateMutation.error instanceof ApiError
                ? updateMutation.error.message
                : 'Failed to update API key.'}
          </Alert>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" isLoading={updateMutation.isPending}>Save Changes</Button>
        </div>
      </form>
    </Dialog>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────

export default function ApiKeyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const keyId = Number(id);

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

  return (
    <AppShell
      title={isLoading ? 'API Key' : `API Key #${apiKey?.apiKeyId ?? '?'}`}
      breadcrumb={[{ label: 'API Keys', path: '/api-keys' }]}
    >
      {isLoading && (
        <div className="flex items-center justify-center h-32">
          <span className="size-5 border-2 border-fg border-t-transparent rounded-full animate-spin" />
        </div>
      )}

      {apiKey && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            {/* ── Details card ──────────────────────────────── */}
            <Card className="lg:col-span-2">
              <CardHeader><CardTitle>API Key Details</CardTitle></CardHeader>
              <CardContent>
                <dl className="space-y-3">
                  <InfoRow label="ID">
                    <span className="font-mono">{apiKey.apiKeyId}</span>
                  </InfoRow>

                  <InfoRow label="Integration">
                    {apiKey.integrationId ? (
                      <Link
                        to={`/integrations/${apiKey.integrationId}`}
                        className="font-medium text-accent hover:underline"
                      >
                        {integrationName ?? `#${apiKey.integrationId}`}
                      </Link>
                    ) : (
                      <span className="text-fg-muted">—</span>
                    )}
                  </InfoRow>

                  <InfoRow label="Key">
                    <span className="inline-flex items-center gap-2">
                      <span className="font-mono text-xs break-all select-all">
                        {apiKey.integrationKey}
                      </span>
                      <Tooltip content="Copy integration key">
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

                  <InfoRow label="Description">
                    <span className="text-fg-muted">{apiKey.description || '—'}</span>
                  </InfoRow>

                  <InfoRow label="Status">
                    <StatusBadge apiKey={apiKey} />
                  </InfoRow>

                  <InfoRow label="Created">
                    <span className="text-fg-muted">
                      {apiKey.createdAt ? formatDate(apiKey.createdAt) : '—'}
                    </span>
                  </InfoRow>

                  <InfoRow label="Expires">
                    <span className="text-fg-muted">
                      {apiKey.expiresAt ? formatDate(apiKey.expiresAt) : 'Never'}
                    </span>
                  </InfoRow>

                  <InfoRow label="Last Used">
                    <span className="text-fg-muted">
                      {apiKey.lastUsedAt ? formatDate(apiKey.lastUsedAt) : 'Never'}
                    </span>
                  </InfoRow>

                  <InfoRow label="IP Whitelist">
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
                      <span className="text-fg-muted">No restrictions</span>
                    )}
                  </InfoRow>
                </dl>
              </CardContent>
            </Card>

            {/* ── Actions card ─────────────────────────────── */}
            <Card>
              <CardHeader><CardTitle>Actions</CardTitle></CardHeader>
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
                        Edit Key Config
                      </Button>
                      <Button
                        variant="destructive"
                        size="sm"
                        className="w-full justify-start gap-2"
                        onClick={() => setRevokeTarget(apiKey)}
                      >
                        <ShieldOff className="size-3.5" />
                        Revoke Key
                      </Button>
                    </>
                  )}

                  {!isActive && (
                    <p className="text-xs text-fg-muted italic">
                      This key is no longer active. No actions available.
                    </p>
                  )}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* ── Revocation info ────────────────────────────── */}
          {apiKey.revokedAt && (
            <Card className="border-error">
              <CardHeader><CardTitle>Revocation Info</CardTitle></CardHeader>
              <CardContent>
                <dl className="space-y-3">
                  <InfoRow label="Revoked At">
                    <span className="text-fg-muted">{formatDate(apiKey.revokedAt)}</span>
                  </InfoRow>
                  <InfoRow label="Revoked By">
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
