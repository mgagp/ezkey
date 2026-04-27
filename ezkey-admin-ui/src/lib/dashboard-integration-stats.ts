/**
 * Dashboard integration lifecycle helpers.
 */

export interface DashboardIntegrationStatsLike {
  total?: number;
  active?: number;
  retired?: number;
  inactive?: number;
}

/**
 * Returns the retired integration count while remaining tolerant to the legacy inactive alias
 * during the API transition.
 */
export function getRetiredIntegrationCount(
  stats?: DashboardIntegrationStatsLike,
): number | undefined {
  return stats?.retired ?? stats?.inactive;
}
