/**
 * Mock enrollment data for Phase 1 UI development.
 */

export type MockEnrollmentSummary = {
  id: string;
  integrationId: string;
  integrationName: string;
  tenantName: string;
  tenantId?: number;
  createdAt: string;
  lastActivityAt: string;
  status: 'active' | 'pending';
};

export const MOCK_ENROLLMENTS: MockEnrollmentSummary[] = [
  {
    id: '101',
    integrationId: '1',
    integrationName: 'Acme Corp Admin',
    tenantName: 'Acme Corp',
    tenantId: 1,
    createdAt: '2025-01-15T10:00:00.000Z',
    lastActivityAt: '2025-03-10T14:30:00.000Z',
    status: 'active',
  },
  {
    id: '102',
    integrationId: '2',
    integrationName: 'Internal Portal',
    tenantName: 'Acme Corp',
    tenantId: 1,
    createdAt: '2025-02-01T09:00:00.000Z',
    lastActivityAt: '2025-03-09T11:00:00.000Z',
    status: 'active',
  },
  {
    id: '103',
    integrationId: '3',
    integrationName: 'HR System',
    tenantName: 'HR Division',
    tenantId: 2,
    createdAt: '2025-02-20T16:00:00.000Z',
    lastActivityAt: '2025-03-08T08:45:00.000Z',
    status: 'pending',
  },
];

export function getMockEnrollmentById(id: string): MockEnrollmentSummary | undefined {
  return MOCK_ENROLLMENTS.find((e) => e.id === id);
}
