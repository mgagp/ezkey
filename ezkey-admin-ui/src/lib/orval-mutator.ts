/**
 * Orval custom HTTP mutator for Admin API generated client.
 *
 * Orval generates calls as: customInstance<T>(url, options: RequestInit)
 * where the URL already includes serialized query parameters and the body
 * is already JSON-stringified. We delegate straight to fetchApi which handles
 * BASE_URL, Bearer auth header, 401-redirect, 204 No Content, and ApiError.
 */
import { fetchApi } from './api-client';
import type { ApiError } from './api-client';

/** Orval uses these type exports for generic error and body typing in generated code. */
export type ErrorType<Error> = ApiError & { status: number; body: Error };
export type BodyType<BodyData> = BodyData;

/**
 * Custom HTTP instance used by all Orval-generated API functions.
 * The URL passed by Orval already includes any serialized query parameters.
 * Auth headers and BASE_URL prefix are injected by the underlying fetchApi.
 */
export const customInstance = <T>(url: string, options: RequestInit = {}): Promise<T> =>
  fetchApi<T>(url, options);
