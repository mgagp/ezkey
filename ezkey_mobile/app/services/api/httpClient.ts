import axios from 'axios';
import {env} from '../../config/env';

export const httpClient = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: env.requestTimeoutMs,
  headers: {
    'Content-Type': 'application/json',
  },
});

httpClient.interceptors.response.use(
  response => response,
  error => {
    if (error.response) {
      return Promise.reject(error);
    }
    return Promise.reject(error);
  },
);
