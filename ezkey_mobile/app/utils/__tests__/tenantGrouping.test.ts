import {groupEnrollmentsByInstallation} from '../tenantGrouping';
import type {StoredEnrollment} from '../../services/storage/enrollmentStorage';

function makeEnrollment(overrides: Partial<StoredEnrollment> = {}): StoredEnrollment {
  return {
    id: '1',
    integrationId: '1',
    integrationName: 'Integration',
    tenantName: 'Tenant A',
    createdAt: '2025-01-01T00:00:00.000Z',
    lastActivityAt: '2025-01-01T00:00:00.000Z',
    enrollmentProofToken: 'token',
    installation: {
      id: 'https://tenant-a.example.com',
      authUrl: 'https://tenant-a.example.com',
      host: 'tenant-a.example.com',
      name: 'Tenant A Ezkey',
    },
    ...overrides,
  };
}

describe('groupEnrollmentsByInstallation', () => {
  it('returns empty array when enrollments is empty', () => {
    expect(groupEnrollmentsByInstallation([])).toEqual([]);
  });

  it('returns one installation group for a single installation', () => {
    const enrollments = [
      makeEnrollment({id: '1', tenantName: 'Acme Corp', tenantId: 1}),
      makeEnrollment({id: '2', tenantName: 'Acme Corp', tenantId: 1}),
    ];
    const result = groupEnrollmentsByInstallation(enrollments);
    expect(result).toHaveLength(1);
    expect(result[0].installation.name).toBe('Tenant A Ezkey');
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
        installation: {
          id: 'https://ops.example.com',
          authUrl: 'https://ops.example.com',
          host: 'ops.example.com',
          name: 'Ops Ezkey',
        },
      }),
      makeEnrollment({id: '3', tenantName: 'Acme Corp', tenantId: 1}),
    ];
    const result = groupEnrollmentsByInstallation(enrollments);
    expect(result).toHaveLength(2);
    expect(result[0].installation.name).toBe('Ops Ezkey');
    expect(result[1].installation.name).toBe('Tenant A Ezkey');
    expect(result[1].tenantGroups[0].enrollments).toHaveLength(2);
  });

  it('renders missing tenant metadata as ungrouped enrollments', () => {
    const enrollments = [
      makeEnrollment({id: '1', tenantName: '', tenantId: 1}),
      makeEnrollment({id: '2', tenantName: null as unknown as string}),
      makeEnrollment({id: '3', tenantName: '   '}),
    ];
    const result = groupEnrollmentsByInstallation(enrollments);
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
    const result = groupEnrollmentsByInstallation(enrollments);
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
    const result = groupEnrollmentsByInstallation(enrollments);
    expect(result[0].tenantGroups[0].enrollments.map(e => e.id)).toEqual(['2', '3', '1']);
  });

  it('uses installation host as fallback display name when branding is missing', () => {
    const enrollments = [
      makeEnrollment({
        id: '1',
        tenantName: 'Acme Corp',
        installation: {
          id: 'https://fallback.example.com',
          authUrl: 'https://fallback.example.com:443/',
          name: '',
        } as unknown as StoredEnrollment['installation'],
      }),
    ];
    const result = groupEnrollmentsByInstallation(enrollments);
    expect(result[0].installation.name).toBe('fallback.example.com');
    expect(result[0].showHostHint).toBe(true);
  });

  it('groups equivalent normalized auth URLs under the same installation', () => {
    const enrollments = [
      makeEnrollment({
        id: '1',
        tenantName: 'Acme Corp',
        installation: undefined,
        authUrl: 'https://EZKEY.Acme.COM:443/',
      } as Partial<StoredEnrollment>),
      makeEnrollment({
        id: '2',
        tenantName: 'Acme Corp',
        installation: {
          id: 'https://ezkey.acme.com',
          authUrl: 'https://ezkey.acme.com',
          host: 'ezkey.acme.com',
          name: 'Acme Ezkey',
        },
      }),
    ];

    const result = groupEnrollmentsByInstallation(enrollments);

    expect(result).toHaveLength(1);
    expect(result[0].installation.id).toBe('https://ezkey.acme.com');
    expect(result[0].tenantGroups[0].enrollments).toHaveLength(2);
  });
});
