import {useQuery} from '@tanstack/react-query';
import {MOCK_ENROLLMENTS, MockEnrollment} from '../services/api/mock/enrollments';

const fetchMockEnrollments = async (): Promise<MockEnrollment[]> => {
  await new Promise(resolve => setTimeout(resolve, 150));
  return MOCK_ENROLLMENTS;
};

export const useMockEnrollments = () =>
  useQuery({
    queryKey: ['mock-enrollments'],
    queryFn: fetchMockEnrollments,
  });
