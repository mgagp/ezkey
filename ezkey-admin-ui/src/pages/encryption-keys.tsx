import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { DataTable, type ColumnDef } from '@/components/data-table/data-table';
import { api, ApiError } from '@/lib/api-client';
import { formatDate, formatRelativeTime } from '@/lib/utils';
import { useToast } from '@/context/toast-context';
import type {
  EncryptionKeyResponse,
  KeyRotationResponse,
  ReencryptionBatchResponse,
  ReencryptionTriggerResponse,
  ReencryptionKeyResponse,
  BatchResumeResponse,
  BatchCreationResponse,
} from '@/generated/admin-api/model';

// ── Helpers ───────────────────────────────────────────────────────────────────

function KeyStatusBadge({ status }: { status?: string }) {
  if (status === 'PRIMARY') return <Badge variant="success">Primary</Badge>;
  if (status === 'ENABLED') return <Badge variant="muted">Enabled</Badge>;
  if (status === 'DISABLED') return <Badge variant="error">Disabled</Badge>;
  return <Badge variant="muted">{status ?? '—'}</Badge>;
}

function BatchStatusBadge({ status }: { status?: string }) {
  if (status === 'COMPLETED') return <Badge variant="success">Completed</Badge>;
  if (status === 'IN_PROGRESS' || status === 'PROCESSING') return <Badge variant="warning">In Progress</Badge>;
  if (status === 'FAILED') return <Badge variant="error">Failed</Badge>;
  if (status === 'PENDING') return <Badge variant="muted">Pending</Badge>;
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
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [result, setResult] = useState<ReencryptionKeyResponse | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      api.post<ReencryptionKeyResponse>(
        `/api/v1/encryption-keys/${keyData!.keyId}/reencrypt`,
        {},
      ),
    onSuccess: (data) => {
      setResult(data);
      toast(data.message ?? `Re-encryption triggered for key #${keyData!.keyId}`, 'success');
      queryClient.invalidateQueries({ queryKey: ['encryption-keys'] });
      queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
    },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'Re-encryption failed', 'error'),
  });

  function handleClose() {
    setResult(null);
    onClose();
  }

  if (!keyData) return null;

  return (
    <Dialog open={keyData !== null} onClose={handleClose} title={`Re-encrypt Key #${keyData.keyId}`} size="md">
      {result ? (
        <div className="space-y-3">
          <div className="flex items-center gap-2 text-success">
            <RotateCcw className="size-5" />
            <span className="font-bold">Re-encryption triggered</span>
          </div>
          <p className="text-sm">{result.message}</p>
          <div className="grid grid-cols-3 gap-3 text-center">
            <div className="border-2 border-fg/20 p-2">
              <p className="text-lg font-mono font-bold">{result.batchesCreated ?? 0}</p>
              <p className="text-[10px] font-black uppercase tracking-wider text-fg-muted">Created</p>
            </div>
            <div className="border-2 border-fg/20 p-2">
              <p className="text-lg font-mono font-bold">{result.batchesProcessed ?? 0}</p>
              <p className="text-[10px] font-black uppercase tracking-wider text-fg-muted">Processed</p>
            </div>
            <div className="border-2 border-fg/20 p-2">
              <p className="text-lg font-mono font-bold text-error">{result.batchesFailed ?? 0}</p>
              <p className="text-[10px] font-black uppercase tracking-wider text-fg-muted">Failed</p>
            </div>
          </div>
          <div className="flex justify-end pt-2">
            <Button onClick={handleClose}>Done</Button>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <div className="flex items-start gap-2 p-3 border-2 border-warning/40 bg-warning/5">
            <AlertTriangle className="size-4 text-warning mt-0.5 shrink-0" />
            <p className="text-xs text-fg-muted">
              This will create and process re-encryption batches to migrate all records
              from key <span className="font-mono font-bold">#{keyData.keyId}</span> to the current primary key.
              This may take time depending on the number of records.
            </p>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={handleClose}>Cancel</Button>
            <Button
              onClick={() => mutation.mutate()}
              disabled={mutation.isPending}
              className="gap-1.5"
            >
              <RotateCcw className="size-3.5" />
              {mutation.isPending ? 'Re-encrypting…' : 'Re-encrypt'}
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
  const { data: keyData, isLoading } = useQuery({
    queryKey: ['encryption-key', keyId],
    queryFn: () => api.get<EncryptionKeyResponse>(`/api/v1/encryption-keys/${keyId}`),
    enabled: keyId !== null,
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
    <Dialog open={keyId !== null} onClose={onClose} title={`Key #${keyId}`} size="md">
      {isLoading || !keyData ? (
        <div className="flex items-center justify-center py-8">
          <Loader2 className="size-5 animate-spin text-fg-muted" />
        </div>
      ) : (
        <>
          <dl className="space-y-2.5">
            <Row label="Key ID"><span className="font-mono">{keyData.keyId}</span></Row>
            <Row label="Status"><KeyStatusBadge status={keyData.keyStatus} /></Row>
            <Row label="Algorithm"><span className="font-mono text-xs">{keyData.algorithm ?? '—'}</span></Row>
            <Row label="Introduced"><span className="text-fg-muted">{keyData.introducedAt ? formatDate(keyData.introducedAt) : '—'}</span></Row>
            <Row label="Promoted primary">{keyData.promotedPrimaryAt ? formatDate(keyData.promotedPrimaryAt) : '—'}</Row>
            <Row label="Disabled">{keyData.disabledAt ? formatDate(keyData.disabledAt) : '—'}</Row>
            <Row label="Records encrypted"><span className="font-mono">{keyData.recordsEncrypted ?? 0}</span></Row>
            <Row label="Records re-encrypted"><span className="font-mono">{keyData.recordsReencrypted ?? 0}</span></Row>
            <Row label="Created by">{keyData.createdBy ?? '—'}</Row>
            {keyData.notes && <Row label="Notes"><span className="text-xs text-fg-muted">{keyData.notes}</span></Row>}
          </dl>
          <div className="flex justify-end gap-2 pt-4">
            {keyData.keyStatus !== 'PRIMARY' && keyData.keyStatus !== 'DISABLED' && (
              <Button
                variant="secondary"
                className="gap-1.5"
                onClick={() => { onClose(); onReencrypt(keyData); }}
              >
                <RotateCcw className="size-3.5" />
                Re-encrypt
              </Button>
            )}
            <Button onClick={onClose}>Close</Button>
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
    <Dialog open={batch !== null} onClose={onClose} title={`Batch #${batch.batchId}`} size="md">
      <dl className="space-y-2.5">
        <Row label="Batch ID"><span className="font-mono">{batch.batchId}</span></Row>
        <Row label="Status"><BatchStatusBadge status={batch.status} /></Row>
        <Row label="Target table"><span className="font-mono text-xs">{batch.targetTable}</span></Row>
        <Row label="Target column"><span className="font-mono text-xs">{batch.targetColumn}</span></Row>
        <Row label="Old key"><span className="font-mono">#{batch.oldKeyId}</span></Row>
        <Row label="New key"><span className="font-mono">#{batch.newKeyId}</span></Row>
        <Row label="Progress"><ProgressBar pct={batch.progressPct} /></Row>
        <Row label="Records total"><span className="font-mono">{batch.recordsTotal ?? 0}</span></Row>
        <Row label="Records done"><span className="font-mono">{batch.recordsDone ?? 0}</span></Row>
        <Row label="Records failed">
          <span className={`font-mono ${(batch.recordsFailed ?? 0) > 0 ? 'text-error font-bold' : ''}`}>
            {batch.recordsFailed ?? 0}
          </span>
        </Row>
        <Row label="Records skipped"><span className="font-mono">{batch.recordsSkipped ?? 0}</span></Row>
        <Row label="Retry count"><span className="font-mono">{batch.retryCount ?? 0}</span></Row>
        <Row label="Started">{batch.startedAt ? formatDate(batch.startedAt) : '—'}</Row>
        <Row label="Completed">{batch.completedAt ? formatDate(batch.completedAt) : '—'}</Row>
        {batch.errorMessage && (
          <Row label="Error">
            <span className="text-xs text-error font-mono">{batch.errorMessage}</span>
          </Row>
        )}
      </dl>
      <div className="flex justify-end pt-4">
        <Button onClick={onClose}>Close</Button>
      </div>
    </Dialog>
  );
}

// ── Rotate Key Dialog ─────────────────────────────────────────────────────────

function RotateKeyDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [reason, setReason] = useState('');
  const [result, setResult] = useState<KeyRotationResponse | null>(null);

  const mutation = useMutation({
    mutationFn: () => {
      const p = new URLSearchParams();
      if (reason.trim()) p.set('reason', reason.trim());
      const qs = p.toString();
      return api.post<KeyRotationResponse>(
        `/api/v1/encryption-keys/rotate${qs ? `?${qs}` : ''}`,
        {},
      );
    },
    onSuccess: (data) => {
      setResult(data);
      toast(`Key rotated — new primary: #${data.newPrimaryKeyId}`, 'success');
      queryClient.invalidateQueries({ queryKey: ['encryption-keys'] });
      queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
    },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'Rotation failed', 'error'),
  });

  function handleClose() {
    setReason('');
    setResult(null);
    onClose();
  }

  return (
    <Dialog open={open} onClose={handleClose} title="Rotate Encryption Key" size="md">
      {result ? (
        <div className="space-y-3">
          <div className="flex items-center gap-2 text-success">
            <Key className="size-5" />
            <span className="font-bold">Key rotated successfully</span>
          </div>
          <p className="text-sm">{result.message}</p>
          <p className="text-sm font-mono">New primary key: <strong>#{result.newPrimaryKeyId}</strong></p>
          <div className="flex justify-end pt-2">
            <Button onClick={handleClose}>Done</Button>
          </div>
        </div>
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }} className="space-y-4">
          <div className="flex items-start gap-2 p-3 border-2 border-warning/40 bg-warning/5">
            <AlertTriangle className="size-4 text-warning mt-0.5 shrink-0" />
            <p className="text-xs text-fg-muted">
              This will generate a new AES encryption key and promote it as the primary.
              A background re-encryption job will start to migrate existing records.
            </p>
          </div>
          <div className="space-y-1">
            <Label htmlFor="rotate-reason">Reason (min 10 chars)</Label>
            <Input
              id="rotate-reason"
              placeholder="Scheduled quarterly key rotation"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={handleClose}>Cancel</Button>
            <Button type="submit" disabled={mutation.isPending || reason.trim().length < 10}>
              {mutation.isPending ? 'Rotating…' : 'Rotate Key'}
            </Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

// ── Re-encryption Batches Section ─────────────────────────────────────────────

function ReencryptionBatchesSection() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const [expanded, setExpanded] = useState(false);
  const [statusFilter, setStatusFilter] = useState('');
  const [selectedBatch, setSelectedBatch] = useState<ReencryptionBatchResponse | null>(null);

  const { data: batches = [], isLoading } = useQuery({
    queryKey: ['reencryption-batches'],
    queryFn: () => api.get<ReencryptionBatchResponse[]>('/api/v1/encryption-keys/reencryption-batches'),
    enabled: expanded,
  });

  const filteredBatches = useMemo(() => {
    if (!statusFilter) return batches;
    return batches.filter((b) => b.status === statusFilter);
  }, [batches, statusFilter]);

  const triggerMutation = useMutation({
    mutationFn: () => api.post<ReencryptionTriggerResponse>('/api/v1/encryption-keys/reencrypt/trigger', {}),
    onSuccess: (data) => {
      toast(data.message ?? `Triggered — ${data.batchesCreated} batches`, 'success');
      queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
    },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'Trigger failed', 'error'),
  });

  const createBatchesMutation = useMutation({
    mutationFn: () => api.post<BatchCreationResponse>('/api/v1/encryption-keys/reencrypt/create-batches', {}),
    onSuccess: (data) => {
      toast(data.message ?? `Created ${data.batchesCreated} batches`, 'success');
      queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
    },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'Batch creation failed', 'error'),
  });

  const resumeMutation = useMutation({
    mutationFn: (batchId: number) =>
      api.post<BatchResumeResponse>(`/api/v1/encryption-keys/reencryption-batches/${batchId}/resume`, {}),
    onSuccess: (data) => {
      toast(data.message ?? `Batch #${data.batchId} resumed`, 'success');
      queryClient.invalidateQueries({ queryKey: ['reencryption-batches'] });
    },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'Resume failed', 'error'),
  });

  const batchColumns: ColumnDef<ReencryptionBatchResponse>[] = [
    { header: 'ID', key: 'batchId', className: 'w-14', render: (r) => <span className="font-mono text-xs">{r.batchId}</span> },
    { header: 'Table', key: 'targetTable', render: (r) => <span className="font-mono text-xs">{r.targetTable}</span> },
    { header: 'Column', key: 'targetColumn', render: (r) => <span className="font-mono text-xs">{r.targetColumn}</span> },
    {
      header: 'Keys',
      key: 'keys',
      render: (r) => (
        <span className="text-xs">
          #{r.oldKeyId} → #{r.newKeyId}
        </span>
      ),
    },
    { header: 'Status', key: 'status', render: (r) => <BatchStatusBadge status={r.status} /> },
    {
      header: 'Progress',
      key: 'progress',
      className: 'w-32',
      render: (r) => <ProgressBar pct={r.progressPct} />,
    },
    {
      header: 'Records',
      key: 'records',
      render: (r) => (
        <span className="text-xs font-mono">
          {r.recordsDone ?? 0}/{r.recordsTotal ?? 0}
          {(r.recordsFailed ?? 0) > 0 && <span className="text-error ml-1">({r.recordsFailed} fail)</span>}
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
            onClick={(e) => { e.stopPropagation(); resumeMutation.mutate(r.batchId!); }}
            disabled={resumeMutation.isPending}
          >
            <Play className="size-3" />
            Resume
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
          <h2 className="font-black text-sm uppercase tracking-wider">Re-encryption Batches</h2>
          {batches.length > 0 && <Badge variant="muted">{batches.length}</Badge>}
        </div>
        {expanded ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
      </button>

      {expanded && (
        <div className="border-t-2 border-fg/20 p-4 space-y-3">
          <div className="flex items-center gap-2 flex-wrap">
            <div className="w-40">
              <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                <option value="">All statuses</option>
                <option value="PENDING">Pending</option>
                <option value="IN_PROGRESS">In Progress</option>
                <option value="COMPLETED">Completed</option>
                <option value="FAILED">Failed</option>
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
                {createBatchesMutation.isPending ? 'Creating…' : 'Create Batches'}
              </Button>
              <Button
                size="sm"
                variant="secondary"
                className="gap-1.5"
                onClick={() => triggerMutation.mutate()}
                disabled={triggerMutation.isPending}
              >
                <RefreshCw className="size-3.5" />
                {triggerMutation.isPending ? 'Triggering…' : 'Trigger Full Re-encryption'}
              </Button>
            </div>
          </div>

          <DataTable
            columns={batchColumns}
            data={filteredBatches}
            isLoading={isLoading}
            onRowClick={(row) => setSelectedBatch(row)}
            keyExtractor={(r, i) => r.batchId ?? i}
            emptyMessage={statusFilter ? 'No batches match the selected filter.' : 'No re-encryption batches found.'}
          />
          {!isLoading && filteredBatches.length > 0 && (
            <p className="text-xs text-fg-muted">
              {filteredBatches.length} batch{filteredBatches.length !== 1 ? 'es' : ''}
              {statusFilter ? ` (filtered from ${batches.length})` : ''}
            </p>
          )}
        </div>
      )}

      <BatchDetailDialog batch={selectedBatch} onClose={() => setSelectedBatch(null)} />
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function EncryptionKeysPage() {
  const [selectedKeyId, setSelectedKeyId] = useState<number | null>(null);
  const [rotateOpen, setRotateOpen] = useState(false);
  const [reencryptTarget, setReencryptTarget] = useState<EncryptionKeyResponse | null>(null);

  const { data: keys = [], isLoading, refetch } = useQuery({
    queryKey: ['encryption-keys'],
    queryFn: () => api.get<EncryptionKeyResponse[]>('/api/v1/encryption-keys'),
  });

  const columns: ColumnDef<EncryptionKeyResponse>[] = [
    {
      header: 'ID',
      key: 'keyId',
      className: 'w-14',
      render: (r) => (
        <span className="font-mono text-xs inline-flex items-center gap-1">
          {r.keyStatus === 'PRIMARY' && <Key className="size-3 text-accent" />}
          {r.keyId}
        </span>
      ),
    },
    { header: 'Status', key: 'keyStatus', render: (r) => <KeyStatusBadge status={r.keyStatus} /> },
    { header: 'Algorithm', key: 'algorithm', render: (r) => <span className="font-mono text-xs">{r.algorithm ?? '—'}</span> },
    {
      header: 'Records',
      key: 'recordsEncrypted',
      render: (r) => <span className="font-mono text-xs">{r.recordsEncrypted ?? 0}</span>,
    },
    {
      header: 'Introduced',
      key: 'introducedAt',
      render: (r) => (
        <span className="text-xs text-fg-muted">{r.introducedAt ? formatRelativeTime(r.introducedAt) : '—'}</span>
      ),
    },
    {
      header: 'Primary since',
      key: 'promotedPrimaryAt',
      render: (r) => (
        <span className="text-xs text-fg-muted">{r.promotedPrimaryAt ? formatDate(r.promotedPrimaryAt) : '—'}</span>
      ),
    },
    { header: 'Created by', key: 'createdBy', render: (r) => <span className="text-xs">{r.createdBy ?? '—'}</span> },
    {
      header: '',
      key: 'actions',
      render: (r) =>
        r.keyStatus === 'ENABLED' ? (
          <Button
            size="sm"
            variant="secondary"
            className="gap-1 py-0.5 px-2"
            onClick={(e) => { e.stopPropagation(); setReencryptTarget(r); }}
          >
            <RotateCcw className="size-3" />
            Re-encrypt
          </Button>
        ) : null,
    },
  ];

  return (
    <AppShell title="Encryption Keys">
      <div className="space-y-4">
        {/* Actions bar */}
        <div className="flex items-center justify-between">
          <p className="text-xs text-fg-muted">
            AES encryption keys protecting sensitive data at rest. Rotate periodically for compliance.
          </p>
          <div className="flex gap-2">
            <Button variant="secondary" size="sm" onClick={() => refetch()} className="gap-1.5">
              <RefreshCw className="size-3.5" />
              Refresh
            </Button>
            <Button size="sm" onClick={() => setRotateOpen(true)} className="gap-1.5">
              <Key className="size-3.5" />
              Rotate Key
            </Button>
          </div>
        </div>

        {/* Keys table */}
        <DataTable
          columns={columns}
          data={keys}
          isLoading={isLoading}
          onRowClick={(row) => setSelectedKeyId(row.keyId ?? null)}
          keyExtractor={(r, i) => r.keyId ?? i}
          emptyMessage="No encryption keys found."
        />

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
