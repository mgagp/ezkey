/**
 * Hand-written types for dashboard and other models that mirror the Admin API DTOs.
 * Used when generated types are not yet available or for stable dashboard contract.
 */

/** Integration counts for dashboard overview. */
export interface DashboardIntegrationStats {
  total: number;
  active: number;
  retired: number;
  inactive?: number;
}

/** Active enrollment counts (grouped buckets) for dashboard overview. */
export interface DashboardEnrollmentStats {
  total: number;
  verified: number;
  inProgress: number;
  expired: number;
  unavailable: number;
}

/** Auth attempt counts and terminal-outcome rates in the last 24 hours (dashboard). */
export interface DashboardAuth24hStats {
  total: number;
  pending?: number;
  readCount?: number;
  accepted: number;
  rejected: number;
  invalid: number;
  expired: number;
  terminalTotal: number;
  successRatePct: number | null;
  invalidRatePct: number | null;
  expiredRatePct: number | null;
  rejectedRatePct: number | null;
}

/** Single recent audit log entry for dashboard recent activity. */
export interface DashboardRecentActivityItem {
  auditLogId?: number;
  eventType?: string;
  eventStatus?: string;
  eventAction?: string;
  apiName?: string;
  adminId?: number;
  createdAt?: string;
}

/** Structured details for AUDIT_CHAIN_GAP_PENDING alert. */
export interface DashboardGapPendingDetails {
  gapStart?: string;
  estimatedGapEnd?: string;
  estimatedGapMinutes?: number;
  anchorCheckpointId?: number;
  message?: string;
}

/** Single admin-console alert (e.g. audit chain gap pending). Populated only for Global Admin. */
export interface DashboardAlertItem {
  auditLogId?: number;
  eventType?: string;
  eventStatus?: string;
  createdAt?: string;
  eventDetails?: DashboardGapPendingDetails;
}

/** Aggregated dashboard overview response (stats, recent activity, optional alerts). */
export interface DashboardOverview {
  integrations: DashboardIntegrationStats;
  enrollments: DashboardEnrollmentStats;
  auth24h: DashboardAuth24hStats;
  recentActivity: DashboardRecentActivityItem[];
  /** Instance-level alerts (e.g. GAP_PENDING). Populated only for Global Admin. */
  alerts?: DashboardAlertItem[] | null;
}
