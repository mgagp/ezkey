"use strict";
/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyIntegrationClient
 * Description: Integration API client (API key, machine to machine): create, wait for and cancel
 * authentication attempts. Uses the standard fetch API only, so it runs on Node 18+ and on any
 * runtime that provides fetch.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.EzkeyIntegrationClient = exports.EzkeyIntegrationError = void 0;
/**
 * Error raised by the Integration API client.
 *
 * `status` is the HTTP status (0 when the server could not be reached). `problemType`, `title` and
 * `detail` come from the RFC 9457 problem document when the server sent one.
 */
class EzkeyIntegrationError extends Error {
    constructor(message, status, problemType = null, title = null, detail = null) {
        super(message);
        this.name = 'EzkeyIntegrationError';
        this.status = status;
        this.problemType = problemType;
        this.title = title;
        this.detail = detail;
    }
}
exports.EzkeyIntegrationError = EzkeyIntegrationError;
const AUTH_ATTEMPTS_PATH = '/api/v1/auth-attempts';
const DEFAULT_REQUEST_TIMEOUT_MS = 60000;
/**
 * Integration API client: what an application backend needs to sign its users in with Ezkey.
 *
 * ```ts
 * const ezkey = new EzkeyIntegrationClient({ baseUrl, integrationKey, secretKey });
 * const attempt = await ezkey.createAuthAttemptByUserIdentifier('alice', { challengeRequested: true });
 * const result = await ezkey.waitForAuthAttempt(attempt.authAttemptId, { timeoutSeconds: 25 });
 * ```
 */
class EzkeyIntegrationClient {
    constructor(config) {
        var _a, _b;
        if (!config.baseUrl)
            throw new Error('baseUrl is required');
        if (!config.integrationKey || !config.secretKey) {
            throw new Error('integrationKey and secretKey are required');
        }
        // Trim trailing slashes without a regular expression (no backtracking on long inputs).
        let baseUrl = config.baseUrl;
        while (baseUrl.endsWith('/'))
            baseUrl = baseUrl.slice(0, -1);
        this.baseUrl = baseUrl;
        this.authorization = 'Basic ' + toBase64(`${config.integrationKey}:${config.secretKey}`);
        this.requestTimeoutMs = (_a = config.requestTimeoutMs) !== null && _a !== void 0 ? _a : DEFAULT_REQUEST_TIMEOUT_MS;
        const fetchImpl = (_b = config.fetch) !== null && _b !== void 0 ? _b : globalThis.fetch;
        if (!fetchImpl)
            throw new Error('No fetch implementation available (Node 18+ required)');
        this.fetchImpl = fetchImpl;
    }
    /**
     * Creates an authentication attempt for the user your application knows as `userIdentifier`.
     * Ezkey resolves it to the active, verified enrollment of that user within the integration of
     * the API key.
     */
    async createAuthAttemptByUserIdentifier(userIdentifier, options) {
        const trimmed = (userIdentifier !== null && userIdentifier !== void 0 ? userIdentifier : '').trim();
        if (!trimmed)
            throw new Error('userIdentifier must not be blank');
        return this.create({ userIdentifier: trimmed, ...createBody(options) });
    }
    /** Creates an authentication attempt for a known enrollment id. */
    async createAuthAttempt(enrollmentId, options) {
        return this.create({ enrollmentId, ...createBody(options) });
    }
    /**
     * Long-polls until the attempt completes or `timeoutSeconds` (1–300, default 30) elapses.
     * Call it again while `completed` is false and the attempt has not expired.
     */
    async waitForAuthAttempt(authAttemptId, options = {}) {
        var _a, _b;
        const timeout = (_a = options.timeoutSeconds) !== null && _a !== void 0 ? _a : 30;
        const polling = (_b = options.pollingSeconds) !== null && _b !== void 0 ? _b : 2;
        const body = await this.request('GET', `${AUTH_ATTEMPTS_PATH}/${encodeURIComponent(String(authAttemptId))}/wait?timeout=${timeout}&polling=${polling}`, undefined, 200);
        return {
            status: body.status,
            completed: body.completed === true,
            timeoutReached: body.timeoutReached === true,
        };
    }
    /** Cancels a pending attempt (only `PENDING` or `READ` attempts can be cancelled). */
    async cancelAuthAttempt(authAttemptId) {
        var _a;
        const body = await this.request('POST', `${AUTH_ATTEMPTS_PATH}/${encodeURIComponent(String(authAttemptId))}/cancel`, undefined, 200);
        return {
            authAttemptId: typeof body.authAttemptId === 'number' ? body.authAttemptId : authAttemptId,
            authAttemptStatus: String((_a = body.authAttemptStatus) !== null && _a !== void 0 ? _a : ''),
        };
    }
    async create(body) {
        var _a;
        const res = await this.request('POST', AUTH_ATTEMPTS_PATH, body, 201);
        return {
            authAttemptId: Number(res.authAttemptId),
            authAttemptChallenge: typeof res.authAttemptChallenge === 'number' ? res.authAttemptChallenge : null,
            timeoutSeconds: typeof res.timeoutSeconds === 'number' ? res.timeoutSeconds : 120,
            expiresAt: String((_a = res.expiresAt) !== null && _a !== void 0 ? _a : ''),
        };
    }
    async request(method, path, body, expectedStatus) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), this.requestTimeoutMs);
        let response;
        try {
            response = await this.fetchImpl(this.baseUrl + path, {
                method,
                headers: {
                    Authorization: this.authorization,
                    Accept: 'application/json',
                    ...(body ? { 'Content-Type': 'application/json' } : {}),
                },
                body: body ? JSON.stringify(body) : undefined,
                signal: controller.signal,
            });
        }
        catch (err) {
            const reason = controller.signal.aborted ? 'request timed out' : 'server unreachable';
            throw new EzkeyIntegrationError(`Integration API ${reason}: ${err.message}`, 0);
        }
        finally {
            clearTimeout(timer);
        }
        const text = await response.text();
        let json = {};
        if (text) {
            try {
                json = JSON.parse(text);
            }
            catch (_a) {
                json = {};
            }
        }
        if (response.status !== expectedStatus) {
            const str = (v) => (typeof v === 'string' && v ? v : null);
            const title = str(json.title);
            const detail = str(json.detail);
            throw new EzkeyIntegrationError(`Integration API ${method} ${path.split('?')[0]} returned ${response.status}` +
                (title ? `: ${title}` : ''), response.status, str(json.type), title, detail);
        }
        return json;
    }
}
exports.EzkeyIntegrationClient = EzkeyIntegrationClient;
function createBody(options) {
    const body = { challengeRequested: options.challengeRequested };
    if (options.ttlSeconds != null)
        body.ttlSeconds = options.ttlSeconds;
    if (options.contextTitle != null)
        body.contextTitle = options.contextTitle;
    if (options.contextMessage != null)
        body.contextMessage = options.contextMessage;
    return body;
}
function toBase64(value) {
    const bytes = new TextEncoder().encode(value);
    let binary = '';
    bytes.forEach((b) => {
        binary += String.fromCharCode(b);
    });
    return btoa(binary);
}
//# sourceMappingURL=integration-api.js.map