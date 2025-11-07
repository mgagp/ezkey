import {httpClient} from './httpClient';
import {
  BindEnrollmentRequest,
  BindEnrollmentResponse,
  VerifyEnrollmentRequest,
  VerifyEnrollmentResponse,
} from './types';

const basePath = '/api/v1/enrollments';

export const enrollmentsApi = {
  bind: async (payload: BindEnrollmentRequest) => {
    const response = await httpClient.post<BindEnrollmentResponse>(`${basePath}/bind`, payload);
    return response.data;
  },
  verify: async (payload: VerifyEnrollmentRequest) => {
    const response = await httpClient.post<VerifyEnrollmentResponse>(`${basePath}/verify`, payload);
    return response.data;
  },
};
