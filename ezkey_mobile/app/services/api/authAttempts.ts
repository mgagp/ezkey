import {httpClient} from './httpClient';
import {
  PendingAuthRequest,
  PendingAuthResponse,
  RespondAuthRequest,
  RespondAuthResponse,
} from './types';

const basePath = '/api/v1/auth-attempts';

export const authAttemptsApi = {
  pending: async (payload: PendingAuthRequest) => {
    const response = await httpClient.post<PendingAuthResponse>(`${basePath}/pending`, payload);
    return response.data;
  },
  respond: async (payload: RespondAuthRequest) => {
    const response = await httpClient.post<RespondAuthResponse>(`${basePath}/respond`, payload);
    return response.data;
  },
};
