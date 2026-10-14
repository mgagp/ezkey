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

/** How the job was initiated (Integrity cut 3 OQ4 A′). */
export type IntegrityAsyncJobTrigger = 'MANUAL' | 'AUTO' | 'NIGHTLY';

/** Structured chain verdict status (D3 / P1). */
export type IntegrityChainStatus =
  | 'OK'
  | 'UNDECLARED_GAP_DETECTED'
  | 'CHAIN_INTEGRITY_VIOLATION_DETECTED'
  | 'NOT_VERIFIED';

/** Who holds the heavy-crypto path (process-local or ShedLock-derived). */
export type IntegrityBusyHolder = 'NIGHTLY' | 'REPORT_GET' | 'OPERATOR_JOB';

export interface IntegrityAsyncJobResponse {
  jobId: string;
  type: IntegrityAsyncJobType;
  status: IntegrityAsyncJobStatus;
  trigger?: IntegrityAsyncJobTrigger;
  startedByUsername?: string;
  startedByAdminId?: number | null;
  startedAt?: string;
  heartbeatAt?: string;
  finishedAt?: string;
  scopeFrom?: string;
  scopeTo?: string;
  resultSummary?: string;
  errorSummary?: string;
  intact?: boolean;
  alertId?: number;
  chainStatus?: IntegrityChainStatus | null;
  totalCheckpoints?: number | null;
  validCheckpoints?: number | null;
  invalidCheckpoints?: number | null;
  archivedCheckpoints?: number | null;
  undeclaredGaps?: number | null;
  entryHmacViolations?: number | null;
  abandonedAt?: string;
  resumeOneLiner?: string;
  busyHolder?: IntegrityBusyHolder | null;
}

export interface IntegrityAsyncJobAcceptedResponse {
  jobId: string;
}

export interface IntegrityAsyncJobStartRequest {
  type: IntegrityAsyncJobType;
  from: string;
  to: string;
  raiseAlert?: boolean;
  /** Client-declared metadata: MANUAL (default) or AUTO. NIGHTLY is rejected on POST. */
  trigger?: 'MANUAL' | 'AUTO';
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

/** Returns null on 204 (no verdict yet). */
export async function getLastIntegrityVerdict(): Promise<IntegrityAsyncJobResponse | null> {
  const result = await fetchApi<IntegrityAsyncJobResponse | undefined>(`${BASE}/last-verdict`);
  return result ?? null;
}

export async function abandonIntegrityAsyncJob(): Promise<IntegrityAsyncJobResponse> {
  return fetchApi<IntegrityAsyncJobResponse>(`${BASE}/current/abandon`, {
    method: 'POST',
  });
}

/**
 * Sticky Escape statuses — Abandon and restart is available (TTL EXPIRED or explicit CANCELLED).
 * Crash/restart INTERRUPTED is auto-abandoned and is not Escape-sticky.
 */
export function isIntegrityAsyncEscapeStatus(
  status: IntegrityAsyncJobStatus | undefined | null,
): boolean {
  return status === 'EXPIRED' || status === 'CANCELLED';
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
