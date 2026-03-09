/**
 * Canonical query key prefixes for list and entity caches.
 *
 * Use these when calling invalidateQueries so the key matches the query key used by
 * the list hook (usePaginatedFromOrval, useQuery, or Orval-generated hooks). For
 * Orval-generated list hooks (e.g. tenants), use the generated query key factory
 * (getListTenantsQueryKey) instead of a string prefix — see AGENTS.md "List refresh
 * after mutations".
 */
export const queryKeys = {
  /** Integrations list (paginated). */
  integrations: ['integrations'] as const,
  /** Full integration list (e.g. useIntegrations lookup). */
  integrationsAll: ['integrations-all'] as const,
  /** Enrollments list (paginated). */
  enrollments: ['enrollments'] as const,
  /** Admins list (paginated). */
  admins: ['admins'] as const,
  /** API keys list. */
  apiKeys: ['api-keys'] as const,
  /** Single API key detail. */
  apiKey: (id: string | number) => ['api-key', id] as const,
  /** Encryption keys and reencryption batches. */
  encryptionKeys: ['encryption-keys'] as const,
  reencryptionBatches: ['reencryption-batches'] as const,
  /** Audit logs list (paginated). */
  auditLogs: ['audit-logs'] as const,
  /** Audit chain checkpoints (Global Admin, paginated). */
  auditChainCheckpoints: ['audit-logs', 'chain-checkpoints'] as const,
  /** Dashboard / stats aggregates. */
  stats: ['stats'] as const,
  /** Single integration detail. */
  integration: (id: string | number) => ['integration', id] as const,
  /** Single enrollment detail. */
  enrollment: (id: number) => ['enrollment', id] as const,
  /** Single admin detail. */
  adminDetail: (id: number) => ['admin-detail', id] as const,
} as const;
