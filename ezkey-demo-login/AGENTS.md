# Ezkey Demo Login — agent notes

## Purpose

Reference integration for evaluating Ezkey (#1430): a web app whose back end signs users in through
the **Integration API** with the **TypeScript SDK** (`EzkeyIntegrationClient`). It replaced the Demo
App Acme; there is no compatibility with Acme (no `/t/{code}` links, no pasted API keys, no
business-approval / context mode — deferred).

## Layout

| Path | What |
|------|------|
| `src/server/main.ts` | Entry: config, database, SDK client, HTTP server |
| `src/server/login-flow.ts` | Sign-in flow: app user check → create attempt → long-poll wait (25 s slices) → cancel; maps SDK errors to `LoginErrorCode` |
| `src/server/http.ts` | Routes (`/api/info`, `/api/login`, `/api/login/:id/wait`, `/api/login/:id/cancel`, `/api/me`, `/api/logout`, `/healthz`), session cookie, security headers, static files |
| `src/server/db.ts` | `node:sqlite`: `users`, `sessions` (token hash only) |
| `src/server/cli.ts` | `user list | add | remove | disable | enable` |
| `src/web/` | React app: `login-page.tsx` (form, waiting with countdown, results), `home-page.tsx`, `i18n.ts` (EN/FR) |
| `test/server/` | Back-end tests with a fake Ezkey client (`npm test`) |
| `e2e/` | Playwright matrix against a clean start; results in `docs/e2e/` |

## Rules

- No new runtime dependency: the server bundle is Node built-ins plus the SDK (esbuild bundles it).
- Pending sign-ins live in memory (one container); sessions and users in SQLite.
- Never log the secret key, the session token or the challenge code.
- User-facing errors stay generic per family; the reason goes to the log. Unknown and disabled app
  users share one message (no account enumeration).
- The image builds from the repository root (`docker build -f ezkey-demo-login/Dockerfile .`) because
  it links `ezkey-sdk/javascript`. When the SDK changes, rebuild its `dist/` (`npm run build` there).
- Changing a screen: rerun `npx playwright test` and refresh `docs/e2e/` screenshots.

## Known

- Case 06 cancels with no pause on purpose: it guards #1433 (concurrent calls on one API key got 401).
