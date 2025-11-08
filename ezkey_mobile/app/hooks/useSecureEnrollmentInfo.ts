import {useQuery} from '@tanstack/react-query';
import {enrollmentStorage} from '../services/storage/enrollmentStorage';

type SecureEnrollmentInfo = {
  deviceAlias: string;
};

export const useSecureEnrollmentInfo = (enrollmentId: string) =>
  useQuery<SecureEnrollmentInfo | null>({
    queryKey: ['secure-enrollment', enrollmentId],
    enabled: Boolean(enrollmentId),
    queryFn: async () => {
      if (!enrollmentId) {
        return null;
      }
      const deviceAlias = await enrollmentStorage.getDeviceAlias(enrollmentId);
      return deviceAlias ? {deviceAlias} : null;
    },
  });

