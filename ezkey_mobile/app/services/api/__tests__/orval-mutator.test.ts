import {customInstance} from '../orval-mutator';
import {httpClient} from '../httpClient';

jest.mock('../httpClient', () => ({
  httpClient: {
    request: jest.fn(),
  },
}));

const mockedRequest = httpClient.request as jest.Mock;

describe('orval-mutator customInstance', () => {
  beforeEach(() => {
    mockedRequest.mockReset();
  });

  it('normalizes response headers into a Headers instance', async () => {
    mockedRequest.mockResolvedValueOnce({
      data: {ok: true},
      status: 200,
      headers: {
        'content-type': 'application/json',
        'x-trace-id': ['abc', 'def'],
      },
    });

    const response = await customInstance<{
      data: {ok: boolean};
      status: number;
      headers: Headers;
    }>('/api/v1/test', {
      method: 'GET',
    });

    expect(response.status).toBe(200);
    expect(response.data).toEqual({ok: true});
    expect(response.headers).toBeInstanceOf(Headers);
    expect(response.headers.get('content-type')).toBe('application/json');
    expect(response.headers.get('x-trace-id')).toBe('abc, def');
  });

  it('passes through baseURL, method, body and normalized headers', async () => {
    mockedRequest.mockResolvedValueOnce({
      data: {ok: true},
      status: 201,
      headers: {'content-type': 'application/json'},
    });

    await customInstance('/api/v1/test', {
      method: 'POST',
      baseURL: 'https://example.test',
      body: JSON.stringify({hello: 'world'}),
      headers: [
        ['x-api-key', 'k1'],
        ['x-tenant', 't1'],
      ],
    });

    expect(mockedRequest).toHaveBeenCalledWith({
      url: '/api/v1/test',
      method: 'POST',
      baseURL: 'https://example.test',
      data: JSON.stringify({hello: 'world'}),
      headers: {
        'x-api-key': 'k1',
        'x-tenant': 't1',
      },
      signal: undefined,
    });
  });
});
