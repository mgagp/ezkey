import {groupEnrollmentsByTenant} from '../tenantGrouping';
import type {StoredEnrollment} from '../../services/storage/enrollmentStorage';

function makeEnrollment(overrides: Partial<StoredEnrollment> = {}): StoredEnrollment {
  return {
    id: '1',
    integrationId: '1',
    integrationName: 'Integration',
    tenantName: 'Tenant A',
    createdAt: '2025-01-01T00:00:00.000Z',
    lastActivityAt: '2025-01-01T00:00:00.000Z',
    status: 'active',
    enrollmentProofToken: 'token',
    authUrl: 'https://tenant-a.example.com',
    installationId: 'https://tenant-a.example.com',
    installationHost: 'tenant-a.example.com',
    installationName: 'Tenant A Ezkey',
    ...overrides,
  };
}

describe('groupEnrollmentsByTenant', () => {
  it('returns empty array when enrollments is empty', () => {
    expect(groupEnrollmentsByTenant([])).toEqual([]);
  });

  it('returns one installation group for a single installation', () => {
    const enrollments = [
      makeEnrollment({id: '1', tenantName: 'Acme Corp', tenantId: 1}),
      makeEnrollment({id: '2', tenantName: 'Acme Corp', tenantId: 1}),
    ];
    const result = groupEnrollmentsByTenant(enrollments);
    expect(result).toHaveLength(1);
    expect(result[0].installationName).toBe('Tenant A Ezkey');
    expect(result[0].tenantGroups).toHaveLength(1);
    expect(result[0].tenantGroups[0].tenantName).toBe('Acme Corp');
    expect(result[0].tenantGroups[0].enrollments).toHaveLength(2);
  });

  it('separates enrollments by installation before tenant grouping', () => {
    const enrollments = [
      makeEnrollment({id: '1', tenantName: 'Acme Corp', tenantId: 1}),
      makeEnrollment({
        id: '2',
        tenantName: 'IT Dept',
        tenantId: 2,
        authUrl: 'https://ops.example.com',
        installationId: 'https://ops.example.com',
        installationHost: 'ops.example.com',
        installationName: 'Ops Ezkey',
      }),
      makeEnrollment({id: '3', tenantName: 'Acme Corp', tenantId: 1}),
    ];
    const result = groupEnrollmentsByTenant(enrollments);
    expect(result).toHaveLength(2);
    expect(result[0].installationName).toBe('Ops Ezkey');
    expect(result[1].installationName).toBe('Tenant A Ezkey');
    expect(result[1].tenantGroups[0].enrollments).toHaveLength(2);
  });

  it('renders missing tenant metadata as ungrouped enrollments', () => {
    const enrollments = [
      makeEnrollment({id: '1', tenantName: '', tenantId: 1}),
      makeEnrollment({id: '2', tenantName: null as unknown as string}),
      makeEnrollment({id: '3', tenantName: '   '}),
    ];
    const result = groupEnrollmentsByTenant(enrollments);
    expect(result).toHaveLength(1);
    expect(result[0].tenantGroups).toHaveLength(0);
    expect(result[0].ungroupedEnrollments).toHaveLength(3);
  });

  it('sorts tenant groups alphabetically inside an installation', () => {
    const enrollments = [
      makeEnrollment({id: '2', tenantName: 'Acme Corp'}),
      makeEnrollment({id: '3', tenantName: 'Zebra Inc'}),
      makeEnrollment({id: '4', tenantName: 'IT Department'}),
    ];
    const result = groupEnrollmentsByTenant(enrollments);
    expect(result[0].tenantGroups.map(group => group.tenantName)).toEqual([
      'Acme Corp',
      'IT Department',
      'Zebra Inc',
    ]);
  });

  it('sorts enrollments within groups: favorites first, then by createdAt descending', () => {
    const enrollments = [
      makeEnrollment({
        id: '1',
        tenantName: 'Acme',
        createdAt: '2025-01-01T00:00:00.000Z',
        favorited: false,
      }),
      makeEnrollment({
        id: '2',
        tenantName: 'Acme',
        createdAt: '2025-01-03T00:00:00.000Z',
        favorited: true,
      }),
      makeEnrollment({
        id: '3',
        tenantName: 'Acme',
        createdAt: '2025-01-02T00:00:00.000Z',
        favorited: false,
      }),
    ];
    const result = groupEnrollmentsByTenant(enrollments);
    expect(result[0].tenantGroups[0].enrollments.map(e => e.id)).toEqual(['2', '3', '1']);
  });

  it('uses installation host as fallback display name when branding is missing', () => {
    const enrollments = [
      makeEnrollment({
        id: '1',
        tenantName: 'Acme Corp',
        installationName: undefined,
        authUrl: 'https://fallback.example.com:443/',
        installationId: undefined,
        installationHost: undefined,
      }),
    ];
    const result = groupEnrollmentsByTenant(enrollments);
    expect(result[0].installationName).toBe('fallback.example.com');
    expect(result[0].showHostHint).toBe(true);
  });
});
