import {EnrollmentSummary} from '../types';

export const MOCK_ENROLLMENTS: EnrollmentSummary[] = [
  {
    id: 'enr_001',
    integrationId: 'int-banking',
    integrationName: 'Acme Bank',
    tenantName: 'Retail Banking',
    createdAt: '2025-10-05T15:12:22.000Z',
    lastActivityAt: '2025-10-21T13:44:02.000Z',
    status: 'active',
    favorited: true,
    logoUri: 'https://placehold.co/128x128?text=AB',
  },
  {
    id: 'enr_002',
    integrationId: 'int-cloud',
    integrationName: 'Nimbus Cloud',
    tenantName: 'Corporate Admin',
    createdAt: '2025-09-12T09:01:44.000Z',
    lastActivityAt: '2025-10-18T10:02:01.000Z',
    status: 'active',
    logoUri: 'https://placehold.co/128x128?text=NC',
  },
  {
    id: 'enr_003',
    integrationId: 'int-hr',
    integrationName: 'Atlas HR Suite',
    tenantName: 'People Ops',
    createdAt: '2025-10-01T17:45:00.000Z',
    lastActivityAt: '2025-10-15T08:31:09.000Z',
    status: 'pending',
  },
];
