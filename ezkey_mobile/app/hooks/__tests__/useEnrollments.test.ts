/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: useEnrollments hook tests
 * Description: Unit tests for the enrollment React Query hooks, covering query, mutation,
 *              cache update, and installation metadata refresh behaviours.
 * @since 2025
 */

jest.mock('../../services/storage/enrollmentStorage', () => ({
  enrollmentStorage: {
    listEnrollments: jest.fn(),
    listEnrollmentsDetailed: jest.fn(),
    saveEnrollment: jest.fn(),
    deleteEnrollment: jest.fn(),
    updateEnrollmentLastActivity: jest.fn(),
    updateInstallationMetadata: jest.fn(),
    replaceAll: jest.fn(),
  },
}));

jest.mock('../../services/api/instanceInfo', () => ({
  fetchVerifiedInstanceInfo: jest.fn(),
}));

jest.mock('../../utils/installationMetadata', () => ({
  isInstallationMetadataStale: jest.fn(),
  needsInstallationMetadataRefresh: jest.fn(),
  buildInstallation: jest.fn(),
  resolveEnrollmentAuthUrl: jest.fn(),
}));

import React from 'react';
import {act, create} from 'react-test-renderer';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {enrollmentStorage} from '../../services/storage/enrollmentStorage';
import {fetchVerifiedInstanceInfo} from '../../services/api/instanceInfo';
import {
  isInstallationMetadataStale,
  needsInstallationMetadataRefresh,
  buildInstallation,
  resolveEnrollmentAuthUrl,
} from '../../utils/installationMetadata';
import {
  useEnrollments,
  useEnrollmentById,
  useSaveEnrollment,
  useDeleteEnrollment,
  useMarkEnrollmentPendingChecked,
  useRefreshInstallationMetadata,
} from '../useEnrollments';
import type {
  EnrollmentListResult,
  StoredEnrollment,
} from '../../services/storage/enrollmentStorage';

const mockStorage = jest.mocked(enrollmentStorage);
const mockFetchVerifiedInstanceInfo = jest.mocked(fetchVerifiedInstanceInfo);
const mockIsStale = jest.mocked(isInstallationMetadataStale);
const mockNeedsRefresh = jest.mocked(needsInstallationMetadataRefresh);
const mockBuildInstallation = jest.mocked(buildInstallation);
const mockResolveAuthUrl = jest.mocked(resolveEnrollmentAuthUrl);

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {retry: false, gcTime: 0},
      mutations: {retry: false},
    },
  });
}

/**
 * Minimal renderHook wrapper backed by react-test-renderer and QueryClientProvider.
 * Allows synchronous setup; callers use `await act(async () => {})` to flush async work.
 */
function renderHook<T>(
  useHook: () => T,
  client?: QueryClient,
): {result: {current: T}; queryClient: QueryClient} {
  const queryClient = client ?? makeClient();
  const result = {current: null as unknown as T};

  function Probe() {
    result.current = useHook();
    return null;
  }

  act(() => {
    create(
      React.createElement(
        QueryClientProvider,
        {client: queryClient},
        React.createElement(Probe),
      ),
    );
  });

  return {result, queryClient};
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const baseInstallation = {
  id: 'https://ezkey.example.com',
  authUrl: 'https://ezkey.example.com',
  host: 'ezkey.example.com',
  name: 'Acme EU',
  lastRefreshedAt: '2026-05-08T00:00:00.000Z',
};

const sampleEnrollment: StoredEnrollment = {
  id: 'enr-1',
  integrationId: 'int-1',
  integrationName: 'Acme',
  createdAt: '2026-01-01T00:00:00.000Z',
  lastActivityAt: '2026-01-01T00:00:00.000Z',
  enrollmentProofToken: 'token-abc',
  integrationPublicKey: 'integ-pk',
  installation: baseInstallation,
};

const asListResult = (
  enrollments: StoredEnrollment[],
  broken: EnrollmentListResult['broken'] = [],
  collectionError = false,
): EnrollmentListResult => ({enrollments, broken, collectionError});

// ---------------------------------------------------------------------------
// useEnrollments
// ---------------------------------------------------------------------------

describe('useEnrollments', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('fetches the discriminated enrollment result from storage', async () => {
    mockStorage.listEnrollmentsDetailed.mockResolvedValue(asListResult([sampleEnrollment]));

    const {result} = renderHook(() => useEnrollments());
    let data: EnrollmentListResult | undefined;
    await act(async () => {
      ({data} = await result.current.refetch());
    });

    expect(mockStorage.listEnrollmentsDetailed).toHaveBeenCalled();
    expect(data).toEqual(asListResult([sampleEnrollment]));
  });

  it('exposes an empty result when storage returns no records', async () => {
    mockStorage.listEnrollmentsDetailed.mockResolvedValue(asListResult([]));

    const {result} = renderHook(() => useEnrollments());
    let data: EnrollmentListResult | undefined;
    await act(async () => {
      ({data} = await result.current.refetch());
    });

    expect(data).toEqual(asListResult([]));
  });

  it('surfaces broken rows and collection errors instead of a healthy empty list', async () => {
    const {enrollmentProofToken: _token, ...brokenMetadata} = sampleEnrollment;
    mockStorage.listEnrollmentsDetailed.mockResolvedValue(
      asListResult(
        [],
        [{id: 'enr-1', reason: 'secret_rehydration_failed', metadata: brokenMetadata}],
        true,
      ),
    );

    const {result} = renderHook(() => useEnrollments());
    let data: EnrollmentListResult | undefined;
    await act(async () => {
      ({data} = await result.current.refetch());
    });

    expect(data?.broken).toHaveLength(1);
    expect(data?.broken[0].reason).toBe('secret_rehydration_failed');
    expect(data?.collectionError).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// useEnrollmentById
// ---------------------------------------------------------------------------

describe('useEnrollmentById', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns the matching enrollment when the id is found', async () => {
    mockStorage.listEnrollmentsDetailed.mockResolvedValue(asListResult([sampleEnrollment]));

    const {result} = renderHook(() => useEnrollmentById('enr-1'));
    let data: StoredEnrollment | undefined;
    await act(async () => {
      ({data} = await result.current.refetch());
    });

    expect(data).toEqual(sampleEnrollment);
  });

  it('returns undefined when no enrollment matches the given id', async () => {
    mockStorage.listEnrollmentsDetailed.mockResolvedValue(asListResult([sampleEnrollment]));

    const {result} = renderHook(() => useEnrollmentById('unknown-id'));
    let data: StoredEnrollment | undefined;
    await act(async () => {
      ({data} = await result.current.refetch());
    });

    expect(data).toBeUndefined();
  });

  it('stays fail-closed: does not resolve an enrollment whose secrets are unusable', async () => {
    const {enrollmentProofToken: _token, ...brokenMetadata} = sampleEnrollment;
    mockStorage.listEnrollmentsDetailed.mockResolvedValue(
      asListResult([], [{id: 'enr-1', reason: 'missing_proof_token', metadata: brokenMetadata}]),
    );

    const {result} = renderHook(() => useEnrollmentById('enr-1'));
    let data: StoredEnrollment | undefined;
    await act(async () => {
      ({data} = await result.current.refetch());
    });

    expect(data).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// useSaveEnrollment
// ---------------------------------------------------------------------------

describe('useSaveEnrollment', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('delegates to enrollmentStorage.saveEnrollment', async () => {
    mockStorage.saveEnrollment.mockResolvedValue(undefined);

    const {result} = renderHook(() => useSaveEnrollment());
    await act(async () => {
      await result.current.mutateAsync(sampleEnrollment);
    });

    expect(mockStorage.saveEnrollment).toHaveBeenCalledWith(sampleEnrollment);
  });

  it('invalidates the enrollments cache on success', async () => {
    mockStorage.saveEnrollment.mockResolvedValue(undefined);

    const {result, queryClient} = renderHook(() => useSaveEnrollment());
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    await act(async () => {
      await result.current.mutateAsync(sampleEnrollment);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({queryKey: ['enrollments']});
  });
});

// ---------------------------------------------------------------------------
// useDeleteEnrollment
// ---------------------------------------------------------------------------

describe('useDeleteEnrollment', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('delegates to enrollmentStorage.deleteEnrollment with the given id', async () => {
    mockStorage.deleteEnrollment.mockResolvedValue(undefined);

    const {result} = renderHook(() => useDeleteEnrollment());
    await act(async () => {
      await result.current.mutateAsync('enr-1');
    });

    expect(mockStorage.deleteEnrollment).toHaveBeenCalledWith('enr-1');
  });

  it('invalidates the enrollments cache on success', async () => {
    mockStorage.deleteEnrollment.mockResolvedValue(undefined);

    const {result, queryClient} = renderHook(() => useDeleteEnrollment());
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    await act(async () => {
      await result.current.mutateAsync('enr-1');
    });

    expect(invalidateSpy).toHaveBeenCalledWith({queryKey: ['enrollments']});
  });
});

// ---------------------------------------------------------------------------
// useMarkEnrollmentPendingChecked
// ---------------------------------------------------------------------------

describe('useMarkEnrollmentPendingChecked', () => {
  const checkedAt = '2026-05-08T10:00:00.000Z';

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('calls updateEnrollmentLastActivity with the correct id and timestamp', async () => {
    mockStorage.updateEnrollmentLastActivity.mockResolvedValue({
      ...sampleEnrollment,
      lastActivityAt: checkedAt,
    });

    const {result} = renderHook(() => useMarkEnrollmentPendingChecked());
    await act(async () => {
      await result.current.mutateAsync({id: 'enr-1', checkedAt});
    });

    expect(mockStorage.updateEnrollmentLastActivity).toHaveBeenCalledWith('enr-1', checkedAt);
  });

  it('calls setQueryData for the list key and applies the timestamp update', async () => {
    mockStorage.updateEnrollmentLastActivity.mockResolvedValue({
      ...sampleEnrollment,
      lastActivityAt: checkedAt,
    });

    const queryClient = makeClient();
    const setQueryDataSpy = jest.spyOn(queryClient, 'setQueryData');

    const {result} = renderHook(() => useMarkEnrollmentPendingChecked(), queryClient);
    await act(async () => {
      await result.current.mutateAsync({id: 'enr-1', checkedAt});
    });

    // Verify the hook called setQueryData for the list key
    const listKeyCall = setQueryDataSpy.mock.calls.find(
      ([key]) => Array.isArray(key) && key.length === 1 && key[0] === 'enrollments',
    );
    expect(listKeyCall).toBeDefined();

    // Verify the captured updater correctly applies the new timestamp
    const updater = listKeyCall![1] as (
      current: EnrollmentListResult | undefined,
    ) => EnrollmentListResult | undefined;
    expect(updater(asListResult([sampleEnrollment]))?.enrollments[0].lastActivityAt).toBe(
      checkedAt,
    );

    // Verify the guard: do not update when the list key has no cached data
    expect(updater(undefined)).toBeUndefined();
  });

  it('calls setQueryData for the single-enrollment key and applies the timestamp update', async () => {
    mockStorage.updateEnrollmentLastActivity.mockResolvedValue({
      ...sampleEnrollment,
      lastActivityAt: checkedAt,
    });

    const queryClient = makeClient();
    const setQueryDataSpy = jest.spyOn(queryClient, 'setQueryData');

    const {result} = renderHook(() => useMarkEnrollmentPendingChecked(), queryClient);
    await act(async () => {
      await result.current.mutateAsync({id: 'enr-1', checkedAt});
    });

    // Verify the hook called setQueryData for the single-enrollment key
    const singleKeyCall = setQueryDataSpy.mock.calls.find(
      ([key]) => Array.isArray(key) && key.length === 2 && key[1] === 'enr-1',
    );
    expect(singleKeyCall).toBeDefined();

    // Verify the captured updater function applies the new timestamp
    const updater = singleKeyCall![1] as (
      current: StoredEnrollment | undefined,
    ) => StoredEnrollment | undefined;
    expect(updater(sampleEnrollment)?.lastActivityAt).toBe(checkedAt);

    // Verify the guard: do not update when the key has no cached data
    expect(updater(undefined)).toBeUndefined();
  });

  it('does not throw when storage returns undefined (enrollment not found)', async () => {
    mockStorage.updateEnrollmentLastActivity.mockResolvedValue(undefined);

    const {result} = renderHook(() => useMarkEnrollmentPendingChecked());
    await expect(
      act(async () => {
        await result.current.mutateAsync({id: 'missing', checkedAt});
      }),
    ).resolves.not.toThrow();
  });
});

// ---------------------------------------------------------------------------
// useRefreshInstallationMetadata
// ---------------------------------------------------------------------------

describe('useRefreshInstallationMetadata', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns false and skips all API calls when the enrollment list is empty', async () => {
    const {result} = renderHook(() => useRefreshInstallationMetadata());

    let returnValue: boolean | undefined;
    await act(async () => {
      returnValue = await result.current.mutateAsync([]);
    });

    expect(returnValue).toBe(false);
    expect(mockFetchVerifiedInstanceInfo).not.toHaveBeenCalled();
  });

  it('returns false when no enrollment is stale or needs a refresh', async () => {
    mockIsStale.mockReturnValue(false);
    mockNeedsRefresh.mockReturnValue(false);

    const {result} = renderHook(() => useRefreshInstallationMetadata());

    let returnValue: boolean | undefined;
    await act(async () => {
      returnValue = await result.current.mutateAsync([sampleEnrollment]);
    });

    expect(returnValue).toBe(false);
    expect(mockFetchVerifiedInstanceInfo).not.toHaveBeenCalled();
  });

  it('makes exactly one API call when multiple enrollments share the same installation', async () => {
    const secondEnrollment: StoredEnrollment = {
      ...sampleEnrollment,
      id: 'enr-2',
      installation: {...baseInstallation},
    };

    mockIsStale.mockReturnValue(true);
    mockNeedsRefresh.mockReturnValue(false);
    mockResolveAuthUrl.mockReturnValue('https://ezkey.example.com');
    mockFetchVerifiedInstanceInfo.mockResolvedValue({
      instanceName: 'Acme EU',
      instanceDescription: 'Primary European Ezkey installation',
      aboutUrl: null,
    });
    mockBuildInstallation.mockReturnValue({...baseInstallation});
    mockStorage.updateInstallationMetadata.mockResolvedValue(true);

    const {result} = renderHook(() => useRefreshInstallationMetadata());
    await act(async () => {
      await result.current.mutateAsync([sampleEnrollment, secondEnrollment]);
    });

    expect(mockFetchVerifiedInstanceInfo).toHaveBeenCalledTimes(1);
    expect(mockFetchVerifiedInstanceInfo).toHaveBeenCalledWith({
      authUrl: 'https://ezkey.example.com',
      enrollmentProofToken: 'token-abc',
      integrationPublicKey: 'integ-pk',
    });
    expect(mockStorage.updateInstallationMetadata).toHaveBeenCalledTimes(1);
    expect(mockStorage.updateInstallationMetadata).toHaveBeenCalledWith([
      {id: 'enr-1', installation: {...baseInstallation}},
      {id: 'enr-2', installation: {...baseInstallation}},
    ]);
  });

  it('handles a failed verified fetch gracefully and returns false without throwing', async () => {
    mockIsStale.mockReturnValue(true);
    mockNeedsRefresh.mockReturnValue(false);
    mockResolveAuthUrl.mockReturnValue('https://ezkey.example.com');
    mockFetchVerifiedInstanceInfo.mockResolvedValue(null);

    const {result} = renderHook(() => useRefreshInstallationMetadata());

    let returnValue: boolean | undefined;
    await act(async () => {
      returnValue = await result.current.mutateAsync([sampleEnrollment]);
    });

    expect(returnValue).toBe(false);
    expect(mockStorage.updateInstallationMetadata).not.toHaveBeenCalled();
  });

  it('persists updated enrollment records and returns true on a successful refresh', async () => {
    const updatedInstallation = {
      ...baseInstallation,
      name: 'Acme EU',
      description: 'Updated description',
    };

    mockIsStale.mockReturnValue(true);
    mockNeedsRefresh.mockReturnValue(false);
    mockResolveAuthUrl.mockReturnValue('https://ezkey.example.com');
    mockFetchVerifiedInstanceInfo.mockResolvedValue({
      instanceName: 'Acme EU',
      instanceDescription: 'Updated description',
      aboutUrl: null,
    });
    mockBuildInstallation.mockReturnValue(updatedInstallation);
    mockStorage.updateInstallationMetadata.mockResolvedValue(true);

    const {result} = renderHook(() => useRefreshInstallationMetadata());

    let returnValue: boolean | undefined;
    await act(async () => {
      returnValue = await result.current.mutateAsync([sampleEnrollment]);
    });

    expect(returnValue).toBe(true);
    expect(mockStorage.updateInstallationMetadata).toHaveBeenCalledWith([
      {id: sampleEnrollment.id, installation: updatedInstallation},
    ]);
  });

  it('invalidates the enrollment cache after a successful refresh', async () => {
    mockIsStale.mockReturnValue(true);
    mockNeedsRefresh.mockReturnValue(false);
    mockResolveAuthUrl.mockReturnValue('https://ezkey.example.com');
    mockFetchVerifiedInstanceInfo.mockResolvedValue({
      instanceName: 'Acme EU',
      instanceDescription: null,
      aboutUrl: null,
    });
    mockBuildInstallation.mockReturnValue({...baseInstallation});
    mockStorage.updateInstallationMetadata.mockResolvedValue(true);

    const {result, queryClient} = renderHook(() => useRefreshInstallationMetadata());
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    await act(async () => {
      await result.current.mutateAsync([sampleEnrollment]);
    });

    expect(invalidateSpy).toHaveBeenCalledWith({queryKey: ['enrollments']});
  });

  it('does not invalidate the cache when verified fetch returns null', async () => {
    mockIsStale.mockReturnValue(true);
    mockNeedsRefresh.mockReturnValue(false);
    mockResolveAuthUrl.mockReturnValue('https://ezkey.example.com');
    mockFetchVerifiedInstanceInfo.mockResolvedValue(null);

    const {result, queryClient} = renderHook(() => useRefreshInstallationMetadata());
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    await act(async () => {
      await result.current.mutateAsync([sampleEnrollment]);
    });

    expect(invalidateSpy).not.toHaveBeenCalled();
  });

  it('skips refresh when enrollment lacks proof material', async () => {
    mockIsStale.mockReturnValue(true);
    mockNeedsRefresh.mockReturnValue(false);
    mockResolveAuthUrl.mockReturnValue('https://ezkey.example.com');
    const withoutSecrets: StoredEnrollment = {
      ...sampleEnrollment,
      enrollmentProofToken: undefined as unknown as string,
      integrationPublicKey: undefined,
    };

    const {result} = renderHook(() => useRefreshInstallationMetadata());
    let returnValue: boolean | undefined;
    await act(async () => {
      returnValue = await result.current.mutateAsync([withoutSecrets]);
    });

    expect(returnValue).toBe(false);
    expect(mockFetchVerifiedInstanceInfo).not.toHaveBeenCalled();
  });
});
