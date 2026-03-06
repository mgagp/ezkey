import { AppShell } from '@/components/layout/app-shell';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

// ── Types ─────────────────────────────────────────────────────────────────────

interface KeyOperation {
  name: string;
  method: 'GET' | 'POST';
  path: string;
}

// ── Data ──────────────────────────────────────────────────────────────────────

const operations: KeyOperation[] = [
  { name: 'List keys',                    method: 'GET',  path: '/api/v1/encryption-keys' },
  { name: 'Get primary key',              method: 'GET',  path: '/api/v1/encryption-keys/primary' },
  { name: 'Get key by ID',                method: 'GET',  path: '/api/v1/encryption-keys/:keyId' },
  { name: 'Rotate key',                   method: 'POST', path: '/api/v1/encryption-keys/rotate' },
  { name: 'List re-encryption batches',   method: 'GET',  path: '/api/v1/encryption-keys/reencryption-batches' },
  { name: 'Resume batch',                 method: 'POST', path: '/api/v1/encryption-keys/reencryption-batches/:batchId/resume' },
  { name: 'Trigger full re-encryption',   method: 'POST', path: '/api/v1/encryption-keys/reencrypt/trigger' },
  { name: 'Trigger re-encryption for key', method: 'POST', path: '/api/v1/encryption-keys/:keyId/reencrypt' },
  { name: 'Create re-encryption batches', method: 'POST', path: '/api/v1/encryption-keys/reencrypt/create-batches' },
];

// ── Component ─────────────────────────────────────────────────────────────────

export default function EncryptionKeysPage() {
  return (
    <AppShell title="Encryption Keys">
      <div className="space-y-4">
        <Alert variant="info" title="Feature coming soon">
          Encryption key management is available via the Admin API. Full UI support will be added
          in a future release.
        </Alert>

        <Card>
          <CardHeader>
            <CardTitle>Available Operations</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-fg/60 mb-4">
              Manage AES encryption keys used to protect sensitive data at rest. Key rotation
              triggers a background re-encryption job that processes data in batches without
              downtime.
            </p>
            <table className="w-full text-sm border-2 border-fg">
              <thead>
                <tr className="bg-fg text-surface">
                  <th className="px-3 py-2 text-left font-bold uppercase tracking-wide text-xs w-8">
                    Method
                  </th>
                  <th className="px-3 py-2 text-left font-bold uppercase tracking-wide text-xs">
                    Operation
                  </th>
                  <th className="px-3 py-2 text-left font-bold uppercase tracking-wide text-xs">
                    Endpoint
                  </th>
                </tr>
              </thead>
              <tbody>
                {operations.map((op) => (
                  <tr key={op.path + op.method} className="border-t-2 border-fg/10">
                    <td className="px-3 py-2.5">
                      <Badge variant={op.method === 'GET' ? 'success' : 'warning'}>
                        {op.method}
                      </Badge>
                    </td>
                    <td className="px-3 py-2.5 font-medium">{op.name}</td>
                    <td className="px-3 py-2.5 font-mono text-xs text-fg/70">{op.path}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent>
        </Card>
      </div>
    </AppShell>
  );
}
