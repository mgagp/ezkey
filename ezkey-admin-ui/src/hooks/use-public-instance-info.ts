import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import type { PublicInstanceInfo } from '@/types/public-instance-info';

const QUERY_KEY = ['public-instance-info'] as const;

export function usePublicInstanceInfo() {
  return useQuery({
    queryKey: QUERY_KEY,
    queryFn: () => api.getPublic<PublicInstanceInfo>('/api/v1/public/instance-info'),
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });
}
