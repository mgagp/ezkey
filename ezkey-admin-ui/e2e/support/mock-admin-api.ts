import type { BrowserContext, Page, Route } from '@playwright/test';

/**
 * Mocked Admin API support for Walk Gate specs (`e2e/walks/`, Playwright project `walk-mocked`).
 *
 * The walk runs the Vite dev server only: no Docker stack, no Demo Device. A Global Admin session
 * and the UI language are injected before the app boots, and `/api/v1/**` is answered by
 * `page.route` handlers the spec declares. Anything not declared gets an empty page (or `{}` for
 * count-like paths) and is listed by {@link MockAdminApi.unmocked} so a walk can report it.
 */

export type WalkLocale = 'en' | 'fr';

/** Same storage keys as `src/lib/auth.ts` and `src/i18n.ts`. */
const AUTH_KEY = 'ezkey_admin_auth';
const LANG_KEY = 'ezkey-admin-ui-lang';

/** Injects a Global Admin session and the UI language before every page load of the context. */
export async function injectSession(context: BrowserContext, locale: WalkLocale): Promise<void> {
  await context.addInitScript(
    ([authKey, langKey, lng]) => {
      sessionStorage.setItem(
        authKey,
        JSON.stringify({
          token: 'walk',
          username: 'walk',
          adminType: 'GLOBAL_ADMIN',
          adminId: 1,
          expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
        }),
      );
      localStorage.setItem(langKey, lng);
    },
    [AUTH_KEY, LANG_KEY, locale] as const,
  );
}

/** Admin API paged body: `{ content, page: { size, number, totalElements, totalPages } }`. */
export function paged<T>(content: T[]) {
  return {
    content,
    page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 },
  };
}

/** Returns the JSON body for a request, or `undefined` to fall through to the next handler. */
export type MockHandler = (request: { method: string; path: string; url: URL }) => unknown;

export interface MockAdminApi {
  /** `METHOD /path` of every request no handler answered. */
  unmocked: Set<string>;
}

/**
 * Routes `/api/v1/**` to the given handlers, tried in order. Path is the URL pathname without the
 * `/api/v1` prefix (e.g. `/admin/enrollments/10`).
 */
export async function mockAdminApi(page: Page, handlers: MockHandler[]): Promise<MockAdminApi> {
  const api: MockAdminApi = { unmocked: new Set() };
  await page.route('**/api/v1/**', async (route: Route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace(/^\/api\/v1/, '');
    const method = request.method();
    const json = (body: unknown) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

    for (const handler of handlers) {
      const body = handler({ method, path, url });
      if (body !== undefined) return json(body);
    }
    api.unmocked.add(`${method} ${path}`);
    return json(/count|stats|summary|overview/.test(path) ? {} : paged([]));
  });
  return api;
}
