import { useQuery } from '@tanstack/react-query';
import { fetchApi } from '@/lib/api-client';
import { queryKeys } from '@/lib/query-keys';

/**
 * Thin encryption lifecycle flags from GET /api/v1/encryption-keys/lifecycle-config.
 * Dual source: live config flags, not the product runtime profile name.
 */
export type EncryptionLifecycleConfig = {
  rotationEnabled: boolean;
  reencryptionEnabled: boolean;
};

/**
 * Fetches whether rotation and re-encryption mutations are enabled on this instance.
 *
 * @returns enable flags for Admin UI honesty chrome
 */
export function fetchEncryptionLifecycleConfig(): Promise<EncryptionLifecycleConfig> {
  return fetchApi<EncryptionLifecycleConfig>('/api/v1/encryption-keys/lifecycle-config');
}

/**
 * Query hook for encryption lifecycle enable flags (Global Admin Encryption Keys page).
 */
export function useEncryptionLifecycleConfig() {
  return useQuery({
    queryKey: queryKeys.encryptionLifecycleConfig,
    queryFn: fetchEncryptionLifecycleConfig,
    staleTime: 30_000,
    retry: 1,
  });
}
