import {
  pending,
  respond,
} from '../generated/auth-api/authentication-attempts/authentication-attempts';
import {bind, instanceInfo, verify} from '../generated/auth-api/enrollments/enrollments';
import {httpClient} from '../httpClient';

jest.mock('../httpClient', () => ({
  httpClient: {
    request: jest.fn(),
  },
}));

const mockedRequest = httpClient.request as jest.Mock;

type CallOptions = NonNullable<Parameters<typeof bind>[1]>;

type MutatorResponse = {
  data: unknown;
  status: number;
  headers: Headers;
};

const responseData = {ok: true};

/**
 * Exercises the real Orval output through the real mutator. Only httpClient,
 * the layer under customInstance, is mocked. A stale generated client that
 * still spreads headers case-sensitively, or that stops JSON-encoding the
 * body, fails these assertions.
 */
describe('generated auth client contract', () => {
  const signal = new AbortController().signal;

  beforeEach(() => {
    mockedRequest.mockReset();
  });

  function mockTransport(): void {
    mockedRequest.mockResolvedValueOnce({
      data: responseData,
      status: 200,
      headers: {
        'content-type': 'application/json',
        'x-trace-id': ['abc', 'def'],
      },
    });
  }

  async function expectOperation(
    invoke: (options: CallOptions) => Promise<MutatorResponse>,
    url: string,
    body: unknown,
  ): Promise<void> {
    mockTransport();

    const options: CallOptions = {
      // The generated operation must keep its verb when the caller sets another.
      method: 'GET',
      baseURL: '/auth-base',
      signal,
      headers: {'X-Trace': 'trace-1'},
    };

    const result = await invoke(options);

    expect(mockedRequest).toHaveBeenCalledTimes(1);
    expect(mockedRequest).toHaveBeenCalledWith({
      url,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Trace': 'trace-1',
      },
      data: JSON.stringify(body),
      baseURL: '/auth-base',
      signal,
    });
    expect(result).toMatchObject({
      data: responseData,
      status: 200,
    });
    expect(result.headers).toBeInstanceOf(Headers);
    expect(result.headers.get('content-type')).toBe('application/json');
    expect(result.headers.get('x-trace-id')).toBe('abc, def');
  }

  it('bind posts JSON to the bind URL and returns the mutator response', async () => {
    const body = {enrollmentProofToken: 'proof-token'};
    await expectOperation(options => bind(body, options), '/api/v1/enrollments/bind', body);
  });

  it('verify posts JSON to the verify URL and returns the mutator response', async () => {
    const body = {
      enrollmentId: 7,
      challengeResponse: 654321,
      devicePublicKey: 'device-public-key',
      enrollmentProofTokenSigned: 'signed-proof',
    };
    await expectOperation(options => verify(body, options), '/api/v1/enrollments/verify', body);
  });

  it('instanceInfo posts JSON to the instance-info URL and returns the mutator response', async () => {
    const body = {enrollmentProofToken: 'proof-token'};
    await expectOperation(
      options => instanceInfo(body, options),
      '/api/v1/enrollments/instance-info',
      body,
    );
  });

  it('pending posts JSON to the pending URL and returns the mutator response', async () => {
    const body = {
      enrollmentId: 7,
      enrollmentProofToken: 'proof-token',
      deviceProofToken: 'device-proof',
      deviceProofTokenSigned: 'signed-device-proof',
    };
    await expectOperation(options => pending(body, options), '/api/v1/auth-attempts/pending', body);
  });

  it('respond posts JSON to the respond URL and returns the mutator response', async () => {
    const body = {
      authAttemptId: 11,
      authAttemptProofTokenSignedByDevice: 'signed-attempt',
      authAttemptAccepted: true,
    };
    await expectOperation(options => respond(body, options), '/api/v1/auth-attempts/respond', body);
  });

  async function expectCallerContentTypeWins(
    invoke: (options: CallOptions) => Promise<unknown>,
    url: string,
    body: unknown,
  ): Promise<void> {
    mockTransport();

    await invoke({
      signal,
      baseURL: '/auth-base',
      headers: {
        'CONTENT-TYPE': 'text/plain',
        'X-Trace': 'trace-1',
      },
    });

    expect(mockedRequest).toHaveBeenCalledWith({
      url,
      method: 'POST',
      headers: {
        'CONTENT-TYPE': 'text/plain',
        'X-Trace': 'trace-1',
      },
      data: JSON.stringify(body),
      baseURL: '/auth-base',
      signal,
    });
    const headers = mockedRequest.mock.calls[0][0].headers as Record<string, string>;
    expect(Object.keys(headers).filter(name => name.toLowerCase() === 'content-type')).toEqual([
      'CONTENT-TYPE',
    ]);
  }

  it('bind drops the generated Content-Type when the caller uses different casing', async () => {
    const body = {enrollmentProofToken: 'proof-token'};
    await expectCallerContentTypeWins(
      options => bind(body, options),
      '/api/v1/enrollments/bind',
      body,
    );
  });

  it('pending drops the generated Content-Type when the caller uses different casing', async () => {
    const body = {
      enrollmentId: 7,
      enrollmentProofToken: 'proof-token',
      deviceProofToken: 'device-proof',
      deviceProofTokenSigned: 'signed-device-proof',
    };
    await expectCallerContentTypeWins(
      options => pending(body, options),
      '/api/v1/auth-attempts/pending',
      body,
    );
  });
});
