import type {AxiosError, AxiosResponseHeaders, RawAxiosResponseHeaders} from 'axios';

import {httpClient} from './httpClient';

export type ErrorType<Error> = AxiosError<Error>;
export type BodyType<BodyData> = BodyData;

export type MobileRequestOptions = RequestInit & {
  baseURL?: string;
};

type HeaderInput = Headers | Record<string, string> | string[][];

function toHeaders(headers?: HeaderInput) {
  if (!headers) {
    return undefined;
  }

  if (headers instanceof Headers) {
    return Object.fromEntries(headers.entries());
  }

  if (Array.isArray(headers)) {
    return Object.fromEntries(headers);
  }

  return headers;
}

function toResponseHeaders(headers: RawAxiosResponseHeaders | AxiosResponseHeaders): Headers {
  return new Headers(
    Object.entries(headers).map(([key, value]) => [key, Array.isArray(value) ? value.join(', ') : String(value)]),
  );
}

export const customInstance = async <T>(
  url: string,
  options: MobileRequestOptions = {},
): Promise<T> => {
  const response = await httpClient.request({
    url,
    method: options.method,
    headers: toHeaders(options.headers),
    data: options.body,
    baseURL: options.baseURL,
    signal: options.signal,
  });

  return {
    data: response.data,
    status: response.status,
    headers: toResponseHeaders(response.headers),
  } as T;
};