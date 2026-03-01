import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { api } from '@/lib/api-client';
import type { PageResponse } from '@/types/api';
import type { Integration } from '@/types/models';

/** Returns the best display name for an integration, trying English first, then French, then code. */
export function getIntegrationName(integration: Integration): string {
  return (
    integration.i18n.find((i) => i.language === 'en')?.name ??
    integration.i18n.find((i) => i.language === 'fr')?.name ??
    integration.i18n[0]?.name ??
    integration.code
  );
}

/**
 * Loads all integrations (up to 100) and returns a list and a name lookup Map.
 * Results are cached by TanStack Query (60s stale). Shared across all consumers.
 */
export function useIntegrations(): {
  list: Integration[];
  lookup: Map<number, string>;
  isLoading: boolean;
} {
  const { data, isLoading } = useQuery({
    queryKey: ['integrations-all'],
    queryFn: () => api.get<PageResponse<Integration>>('/api/v1/integrations?size=100'),
    staleTime: 60_000,
  });

  const list = data?.content ?? [];

  const lookup = useMemo(() => {
    const map = new Map<number, string>();
    list.forEach((integration) => map.set(integration.id, getIntegrationName(integration)));
    return map;
  }, [list]);

  return { list, lookup, isLoading };
}
