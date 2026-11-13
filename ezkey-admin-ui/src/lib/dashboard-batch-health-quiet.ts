/**
 * When a dashboard batch-health card may collapse: nothing for the operator to do.
 * Success is quiet-healthy. Never-run is quiet-idle only when those jobs are off
 * by config (base / monitoring disabled) — not on integrity warmup.
 */

export type BatchHealthJobStatus = 'SUCCESS' | 'FAILED' | 'NEVER_RUN';

export type BatchHealthJobLike = {
  lastStatus?: BatchHealthJobStatus;
};

export type BatchHealthQuietReason = 'success' | 'never-run' | null;

export function resolveBatchHealthQuietReason(
  jobs: BatchHealthJobLike[],
  jobsExpectedIdle: boolean,
): BatchHealthQuietReason {
  if (jobs.length === 0) {
    return null;
  }
  const statuses = jobs.map((row) => row.lastStatus ?? 'NEVER_RUN');
  if (statuses.every((status) => status === 'SUCCESS')) {
    return 'success';
  }
  if (jobsExpectedIdle && statuses.every((status) => status === 'NEVER_RUN')) {
    return 'never-run';
  }
  return null;
}
