/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
import {EnrollmentSummary} from '../services/api/types';
import {MOCK_ENROLLMENTS} from '../services/api/mock/enrollments';
import {enrollmentStorage, StoredEnrollment} from '../services/storage/enrollmentStorage';

/**
 * Transforms an enrollment API response into the persisted format expected by the secure storage module.
 *
 * The mock implementation decorates the record with deterministic proof tokens to simulate the cryptographic
 * material described in `docs/features/AUTH_SECURITY.md`. Real implementations must never generate proof tokens
 * client-side.
 *
 * @param summary Enrollment summary returned by the Admin API mock.
 * @return Stored representation ready for secure persistence.
 * @since 2025
 */
const toStoredEnrollment = (summary: EnrollmentSummary): StoredEnrollment => ({
  ...summary,
  enrollmentProofToken: `mock-proof-${summary.id}`,
  deviceAlias: `mock-alias-${summary.id}`,
});

let seeded = false;

/**
 * Fetches enrollments from secure storage, seeding mock data when running in developer mode.
 *
 * The seeding logic is strictly for local development; production code must always rely on server-provided
 * tokens that comply with the anti-enumeration guarantees referenced in `docs/ENDPOINT.md`.
 *
 * @return List of persisted enrollments with locally cached proof tokens.
 * @since 2025
 */
const fetchEnrollments = async (): Promise<StoredEnrollment[]> => {
  const current = await enrollmentStorage.listEnrollments();
  if (current.length === 0 && !seeded) {
    await Promise.all(
      MOCK_ENROLLMENTS.map(item => enrollmentStorage.saveEnrollment(toStoredEnrollment(item))),
    );
    seeded = true;
    return enrollmentStorage.listEnrollments();
  }
  return current;
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
 * Persists an enrollment using the secure storage abstraction.
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
 * Removes an enrollment record from secure storage.
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

