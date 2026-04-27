import { describe, expect, it } from 'vitest';

import { getRetiredIntegrationCount } from './dashboard-integration-stats';

describe('getRetiredIntegrationCount', () => {
  it('prefers the explicit retired count', () => {
    expect(getRetiredIntegrationCount({ retired: 4, inactive: 1 })).toBe(4);
  });

  it('falls back to the legacy inactive alias during the API transition', () => {
    expect(getRetiredIntegrationCount({ inactive: 2 })).toBe(2);
  });
});
