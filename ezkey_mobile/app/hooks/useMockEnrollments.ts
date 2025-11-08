import {useQuery} from '@tanstack/react-query';
import {MOCK_ENROLLMENTS} from '../services/api/mock/enrollments';
import {EnrollmentSummary} from '../services/api/types';

const fetchMockEnrollments = async (): Promise<EnrollmentSummary[]> => {
  await new Promise(resolve => setTimeout(resolve, 150));
  return MOCK_ENROLLMENTS;
};

export const useMockEnrollments = () =>
  useQuery({
    queryKey: ['mock-enrollments'],
    queryFn: fetchMockEnrollments,
  });
