/** Configuration of the Integration API client. */
export interface EzkeyIntegrationConfig {
    /** Integration API base URL, e.g. `http://localhost:7080` or `https://integration-api.example.com`. */
    baseUrl: string;
    /** Public integration key (`ezkey_ikey_...`). */
    integrationKey: string;
    /** Secret key (`ezkey_skey_...`). Keep it on the server; never send it to a browser. */
    secretKey: string;
    /** Timeout of each HTTP request in milliseconds (default 60 000; the wait call long-polls). */
    requestTimeoutMs?: number;
    /** Optional fetch implementation (defaults to the global `fetch`). */
    fetch?: typeof fetch;
}
/** Options shared by both ways of creating an authentication attempt. */
export interface AuthAttemptCreateOptions {
    /** Ask Ezkey for a two-digit challenge code that the user picks on the device. */
    challengeRequested: boolean;
    /** Lifetime of the attempt in seconds (minimum 15; the server caps it, typically at 120). */
    ttlSeconds?: number;
    /** Optional short title shown on the device (max 200 characters). */
    contextTitle?: string;
    /** Optional message shown on the device (max 2 000 characters). */
    contextMessage?: string;
}
/** Result of creating an authentication attempt. */
export interface AuthAttemptCreated {
    authAttemptId: number;
    /** Two-digit challenge code to show the user, or null when none was requested. */
    authAttemptChallenge: number | null;
    timeoutSeconds: number;
    expiresAt: string;
}
/** Status of an authentication attempt as reported by the wait call. */
export type AuthAttemptStatus = 'PENDING' | 'READ' | 'INVALID' | 'REJECTED' | 'ACCEPTED' | 'EXPIRED';
/** Result of a wait call. */
export interface AuthAttemptWaitResult {
    status: AuthAttemptStatus;
    /** True when the attempt reached a final state. */
    completed: boolean;
    /** True when the server-side wait ended before the attempt completed. */
    timeoutReached: boolean;
}
/** Result of a cancel call (a cancelled attempt is reported as `EXPIRED`). */
export interface AuthAttemptCancelled {
    authAttemptId: number;
    authAttemptStatus: string;
}
/**
 * Error raised by the Integration API client.
 *
 * `status` is the HTTP status (0 when the server could not be reached). `problemType`, `title` and
 * `detail` come from the RFC 9457 problem document when the server sent one.
 */
export declare class EzkeyIntegrationError extends Error {
    readonly status: number;
    readonly problemType: string | null;
    readonly title: string | null;
    readonly detail: string | null;
    constructor(message: string, status: number, problemType?: string | null, title?: string | null, detail?: string | null);
}
/**
 * Integration API client: what an application backend needs to sign its users in with Ezkey.
 *
 * ```ts
 * const ezkey = new EzkeyIntegrationClient({ baseUrl, integrationKey, secretKey });
 * const attempt = await ezkey.createAuthAttemptByUserIdentifier('alice', { challengeRequested: true });
 * const result = await ezkey.waitForAuthAttempt(attempt.authAttemptId, { timeoutSeconds: 25 });
 * ```
 */
export declare class EzkeyIntegrationClient {
    private readonly baseUrl;
    private readonly authorization;
    private readonly requestTimeoutMs;
    private readonly fetchImpl;
    constructor(config: EzkeyIntegrationConfig);
    /**
     * Creates an authentication attempt for the user your application knows as `userIdentifier`.
     * Ezkey resolves it to the active, verified enrollment of that user within the integration of
     * the API key.
     */
    createAuthAttemptByUserIdentifier(userIdentifier: string, options: AuthAttemptCreateOptions): Promise<AuthAttemptCreated>;
    /** Creates an authentication attempt for a known enrollment id. */
    createAuthAttempt(enrollmentId: number, options: AuthAttemptCreateOptions): Promise<AuthAttemptCreated>;
    /**
     * Long-polls until the attempt completes or `timeoutSeconds` (1–300, default 30) elapses.
     * Call it again while `completed` is false and the attempt has not expired.
     */
    waitForAuthAttempt(authAttemptId: number, options?: {
        timeoutSeconds?: number;
        pollingSeconds?: number;
    }): Promise<AuthAttemptWaitResult>;
    /** Cancels a pending attempt (only `PENDING` or `READ` attempts can be cancelled). */
    cancelAuthAttempt(authAttemptId: number): Promise<AuthAttemptCancelled>;
    private create;
    private request;
}
//# sourceMappingURL=integration-api.d.ts.map