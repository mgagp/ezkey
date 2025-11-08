import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {EnrollmentSummary} from '../services/api/types';
import {MOCK_ENROLLMENTS} from '../services/api/mock/enrollments';
import {enrollmentStorage, StoredEnrollment} from '../services/storage/enrollmentStorage';

const toStoredEnrollment = (summary: EnrollmentSummary): StoredEnrollment => ({
  ...summary,
  enrollmentProofToken: `mock-proof-${summary.id}`,
  deviceAlias: `mock-alias-${summary.id}`,
});

let seeded = false;

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

export const useEnrollments = () =>
  useQuery({
    queryKey: ['enrollments'],
    queryFn: fetchEnrollments,
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

