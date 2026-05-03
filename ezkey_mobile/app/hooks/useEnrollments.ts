/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: useEnrollments Hook Suite
 * Description: Centralized React Query hooks that coordinate secure enrollment persistence and retrieval.
 * Security Context: Adheres to local storage handling guidelines in docs/CRYPTO.md and docs/features/AUTH_SECURITY.md
 *                   by treating proof tokens as read-once artifacts and avoiding data exposure during hydration.
 * @since 2025
 */

import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {instanceInfoApi} from '../services/api/instanceInfo';
import {enrollmentStorage, StoredEnrollment} from '../services/storage/enrollmentStorage';
import {
  buildInstallation,
  isInstallationMetadataStale,
  needsInstallationMetadataRefresh,
  resolveEnrollmentAuthUrl,
} from '../utils/installationMetadata';

/**
 * Fetches enrollments from local metadata storage plus secure proof-token rehydration.
 *
 * No seeding logic - enrollments must be created through the normal enrollment flow.
 *
 * @return List of persisted enrollments rehydrated with proof tokens from secure storage.
 * @since 2025
 */
const fetchEnrollments = async (): Promise<StoredEnrollment[]> => {
  return enrollmentStorage.listEnrollments();
};

const refreshInstallationMetadata = async (
  enrollments: StoredEnrollment[],
): Promise<boolean> => {
  if (enrollments.length === 0) {
    return false;
  }

  const staleByInstallation = new Map<string, {authUrl: string; indices: number[]}>();

  enrollments.forEach((enrollment, index) => {
    if (!isInstallationMetadataStale(enrollment) && !needsInstallationMetadataRefresh(enrollment)) {
      return;
    }

    const authUrl = resolveEnrollmentAuthUrl(enrollment.installation?.authUrl);
    const installationId = enrollment.installation?.id ?? authUrl;
    if (!authUrl || !installationId) {
      return;
    }

    const existing = staleByInstallation.get(installationId);
    if (existing) {
      existing.indices.push(index);
      return;
    }

    staleByInstallation.set(installationId, {authUrl, indices: [index]});
  });

  if (staleByInstallation.size === 0) {
    return false;
  }

  const nextItems = [...enrollments];
  let updated = false;

  for (const installation of staleByInstallation.values()) {
    try {
      const instanceInfo = await instanceInfoApi.get(installation.authUrl);
      const refreshedAt = new Date().toISOString();
      const nextInstallation = buildInstallation(
        installation.authUrl,
        instanceInfo,
        refreshedAt,
      );

      installation.indices.forEach(index => {
        nextItems[index] = {
          ...nextItems[index],
          installation: nextInstallation,
        };
      });
      updated = true;
    } catch (error) {
      console.warn('[useEnrollments] Failed to refresh installation metadata:', error);
    }
  }

  if (!updated) {
    return false;
  }

  await enrollmentStorage.replaceAll(nextItems);
  return true;
};

/**
 * Retrieves all enrollments using React Query.
 *
 * @return Query result containing enrollment data and loading state.
 * @since 2025
 */
export const useEnrollments = () =>
  useQuery({
    queryKey: ['enrollments'],
    queryFn: fetchEnrollments,
  });

/**
 * Retrieves a single enrollment by identifier while propagating React Query cache updates.
 *
 * @param id Enrollment identifier (string form to align with Admin API DTOs).
 * @return Query result containing the matched enrollment if present.
 * @since 2025
 */
export const useEnrollmentById = (id: string) =>
  useQuery({
    queryKey: ['enrollments', id],
    queryFn: async () => {
      const items = await fetchEnrollments();
      return items.find(item => item.id === id);
    },
  });

/**
 * Persists an enrollment using the split metadata plus secure proof-token storage model.
 *
 * @return React Query mutation handler that invalidates enrollment caches on success.
 * @since 2025
 */
export const useSaveEnrollment = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (record: StoredEnrollment) => enrollmentStorage.saveEnrollment(record),
    onSuccess: () => queryClient.invalidateQueries({queryKey: ['enrollments']}),
  });
};

/**
 * Removes an enrollment record from local metadata storage and secure proof-token storage.
 *
 * @return React Query mutation handler that invalidates enrollment caches on success.
 * @since 2025
 */
export const useDeleteEnrollment = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => enrollmentStorage.deleteEnrollment(id),
    onSuccess: () => queryClient.invalidateQueries({queryKey: ['enrollments']}),
  });
};

/**
 * Persists the timestamp of the last user-initiated pending check for an enrollment.
 *
 * @return React Query mutation handler that keeps enrollment caches synchronized locally.
 * @since 2025
 */
export const useMarkEnrollmentPendingChecked = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({id, checkedAt}: {id: string; checkedAt: string}) =>
      enrollmentStorage.updateEnrollmentLastActivity(id, checkedAt),
    onSuccess: (updatedRecord, variables) => {
      if (!updatedRecord) {
        return;
      }

      queryClient.setQueryData<StoredEnrollment[] | undefined>(['enrollments'], current => {
        if (!current) {
          return current;
        }
        return current.map(item =>
          item.id === variables.id ? {...item, lastActivityAt: variables.checkedAt} : item,
        );
      });
      queryClient.setQueryData<StoredEnrollment | undefined>(['enrollments', variables.id], current => {
        if (!current) {
          return current;
        }
        return {...current, lastActivityAt: variables.checkedAt};
      });
    },
  });
};

/**
 * Opportunistically refreshes stale Ezkey installation metadata from the public instance-info endpoint.
 *
 * @return React Query mutation handler for silent installation metadata refresh.
 * @since 2025
 */
export const useRefreshInstallationMetadata = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (records: StoredEnrollment[]) => refreshInstallationMetadata(records),
    onSuccess: didUpdate => {
      if (didUpdate) {
        queryClient.invalidateQueries({queryKey: ['enrollments']});
      }
    },
  });
};

