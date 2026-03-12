/*
 * useEnrollments – React Query hooks for enrollment persistence and retrieval.
 * @since 2025
 */

import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {enrollmentStorage, StoredEnrollment} from '../services/storage/enrollmentStorage';

const fetchEnrollments = async (): Promise<StoredEnrollment[]> =>
  enrollmentStorage.listEnrollments();

export const useEnrollments = () =>
  useQuery({
    queryKey: ['enrollments'],
    queryFn: fetchEnrollments,
  });

export const useEnrollmentById = (id: string) =>
  useQuery({
    queryKey: ['enrollments', id],
    queryFn: async () => {
      const items = await fetchEnrollments();
      return items.find(item => item.id === id);
    },
  });

export const useSaveEnrollment = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (record: StoredEnrollment) => enrollmentStorage.saveEnrollment(record),
    onSuccess: () => queryClient.invalidateQueries({queryKey: ['enrollments']}),
  });
};

export const useDeleteEnrollment = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => enrollmentStorage.deleteEnrollment(id),
    onSuccess: () => queryClient.invalidateQueries({queryKey: ['enrollments']}),
  });
};
