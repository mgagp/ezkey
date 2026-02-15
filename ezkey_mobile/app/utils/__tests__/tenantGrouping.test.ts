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
    ...overrides,
  };
}

describe('groupEnrollmentsByTenant', () => {
  it('returns empty array when enrollments is empty', () => {
    expect(groupEnrollmentsByTenant([])).toEqual([]);
  });

  it('returns one group for a single tenant', () => {
    const enrollments = [
      makeEnrollment({id: '1', tenantName: 'Acme Corp', tenantId: 1}),
      makeEnrollment({id: '2', tenantName: 'Acme Corp', tenantId: 1}),
    ];
    const result = groupEnrollmentsByTenant(enrollments);
    expect(result).toHaveLength(1);
    expect(result[0].tenantName).toBe('Acme Corp');
    expect(result[0].tenantId).toBe(1);
    expect(result[0].enrollments).toHaveLength(2);
  });

  it('separates enrollments by tenant', () => {
    const enrollments = [
      makeEnrollment({id: '1', tenantName: 'Acme Corp', tenantId: 1}),
      makeEnrollment({id: '2', tenantName: 'IT Dept', tenantId: 2}),
      makeEnrollment({id: '3', tenantName: 'Acme Corp', tenantId: 1}),
    ];
    const result = groupEnrollmentsByTenant(enrollments);
    expect(result).toHaveLength(2);
    expect(result[0].tenantName).toBe('Acme Corp');
    expect(result[0].enrollments).toHaveLength(2);
    expect(result[1].tenantName).toBe('IT Dept');
    expect(result[1].enrollments).toHaveLength(1);
  });

  it('normalizes null or blank tenantName to Unknown tenant', () => {
    const enrollments = [
      makeEnrollment({id: '1', tenantName: '', tenantId: 1}),
      makeEnrollment({id: '2', tenantName: null as unknown as string}),
      makeEnrollment({id: '3', tenantName: '   '}),
    ];
    const result = groupEnrollmentsByTenant(enrollments);
    expect(result).toHaveLength(1);
    expect(result[0].tenantName).toBe('Unknown tenant');
    expect(result[0].enrollments).toHaveLength(3);
  });

  it('sorts Unknown tenant last', () => {
    const enrollments = [
      makeEnrollment({id: '1', tenantName: ''}),
      makeEnrollment({id: '2', tenantName: 'Acme Corp'}),
      makeEnrollment({id: '3', tenantName: 'Zebra Inc'}),
    ];
    const result = groupEnrollmentsByTenant(enrollments);
    expect(result[0].tenantName).toBe('Acme Corp');
    expect(result[1].tenantName).toBe('Zebra Inc');
    expect(result[2].tenantName).toBe('Unknown tenant');
  });

  it('sorts tenant groups alphabetically by tenantName', () => {
    const enrollments = [
      makeEnrollment({id: '1', tenantName: 'Zebra Inc'}),
      makeEnrollment({id: '2', tenantName: 'Acme Corp'}),
      makeEnrollment({id: '3', tenantName: 'IT Department'}),
    ];
    const result = groupEnrollmentsByTenant(enrollments);
    expect(result[0].tenantName).toBe('Acme Corp');
    expect(result[1].tenantName).toBe('IT Department');
    expect(result[2].tenantName).toBe('Zebra Inc');
  });

  it('sorts enrollments within group: favorites first, then by createdAt descending', () => {
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
    expect(result[0].enrollments.map(e => e.id)).toEqual(['2', '3', '1']);
  });

  it('groups by tenantId when present for same tenantName', () => {
    const enrollments = [
      makeEnrollment({id: '1', tenantName: 'Acme', tenantId: 1}),
      makeEnrollment({id: '2', tenantName: 'Acme', tenantId: 2}),
    ];
    const result = groupEnrollmentsByTenant(enrollments);
    expect(result).toHaveLength(2);
    expect(result[0].tenantId).toBe(1);
    expect(result[1].tenantId).toBe(2);
  });

  it('includes tenantDescription when present', () => {
    const enrollments = [
      makeEnrollment({
        id: '1',
        tenantName: 'Acme Corp',
        tenantId: 1,
        tenantDescription: 'Main workspace',
      }),
    ];
    const result = groupEnrollmentsByTenant(enrollments);
    expect(result[0].tenantDescription).toBe('Main workspace');
  });
});
