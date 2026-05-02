import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { search } from '@/generated/admin-api/integrations/integrations';
import type { IntegrationResponseDto, PagedModelIntegrationResponseDto } from '@/generated/admin-api/model';

/** Returns the best display name for an integration, trying English first, then French, then code. */
export function getIntegrationName(integration: IntegrationResponseDto): string {
  return integration.name ?? integration.code ?? '';
}

/**
 * Loads all integrations (up to 100) and returns a list and a name lookup Map.
 * Results are cached by TanStack Query (60s stale). Shared across all consumers.
 */
export function useIntegrations(): {
  list: IntegrationResponseDto[];
  lookup: Map<number, string>;
  isLoading: boolean;
} {
  const { data, isLoading } = useQuery({
    queryKey: ['integrations-all'],
    queryFn: () => search({ size: 100 }) as Promise<PagedModelIntegrationResponseDto>,
    staleTime: 60_000,
  });

  const list = useMemo(() => data?.content ?? [], [data?.content]);

  const lookup = useMemo(() => {
    const map = new Map<number, string>();
    list.forEach((integration) => {
      if (integration.id !== undefined) {
        map.set(integration.id, getIntegrationName(integration));
      }
    });
    return map;
  }, [list]);

  return { list, lookup, isLoading };
}
