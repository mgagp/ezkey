/**
 * Hand client for Integrity async jobs (single global slot).
 * Kept until Orval regen after OpenAPI refresh; shapes match Admin API DTOs.
 */

import { fetchApi } from '@/lib/api-client';
import { ApiError } from '@/lib/api-client';

export type IntegrityAsyncJobType =
  | 'VERIFY_CHAIN_RANGE'
  | 'VERIFY_ENTRY_HMAC_RANGE'
  | 'RUN_VALIDATION';

export type IntegrityAsyncJobStatus =
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'INTERRUPTED';

export interface IntegrityAsyncJobResponse {
  jobId: string;
  type: IntegrityAsyncJobType;
  status: IntegrityAsyncJobStatus;
  startedByUsername?: string;
  startedByAdminId?: number;
  startedAt?: string;
  heartbeatAt?: string;
  finishedAt?: string;
  scopeFrom?: string;
  scopeTo?: string;
  resultSummary?: string;
  errorSummary?: string;
  intact?: boolean;
  alertId?: number;
  abandonedAt?: string;
  resumeOneLiner?: string;
}

export interface IntegrityAsyncJobAcceptedResponse {
  jobId: string;
}

export interface IntegrityAsyncJobStartRequest {
  type: IntegrityAsyncJobType;
  from: string;
  to: string;
  raiseAlert?: boolean;
}

const BASE = '/api/v1/audit-logs/integrity/jobs';

export async function startIntegrityAsyncJob(
  body: IntegrityAsyncJobStartRequest,
): Promise<IntegrityAsyncJobAcceptedResponse> {
  return fetchApi<IntegrityAsyncJobAcceptedResponse>(BASE, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/** Returns null on 204 (idle). */
export async function getCurrentIntegrityAsyncJob(): Promise<IntegrityAsyncJobResponse | null> {
  const result = await fetchApi<IntegrityAsyncJobResponse | undefined>(`${BASE}/current`);
  return result ?? null;
}

export async function abandonIntegrityAsyncJob(): Promise<IntegrityAsyncJobResponse> {
  return fetchApi<IntegrityAsyncJobResponse>(`${BASE}/current/abandon`, {
    method: 'POST',
  });
}

export function integrityAsyncBusyResumeLine(error: unknown): string | null {
  if (!(error instanceof ApiError) || error.status !== 409) {
    return null;
  }
  const problem = error.problemDetail;
  if (!problem) {
    return error.message;
  }
  const resume = problem.resumeOneLiner;
  if (typeof resume === 'string' && resume.length > 0) {
    return resume;
  }
  const current = problem.currentJob;
  if (current && typeof current === 'object' && 'resumeOneLiner' in current) {
    const line = (current as { resumeOneLiner?: string }).resumeOneLiner;
    if (typeof line === 'string') {
      return line;
    }
  }
  return problem.detail ?? error.message;
}
