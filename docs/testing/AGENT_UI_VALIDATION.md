# Agent UI validation — quick recipe

Short guide for **coding agents** (especially cold sessions) closing the feedback loop after Admin UI
changes that affect operator lists or workflows. Complements `ezkey-admin-ui/AGENTS.md` (Playwright
details) and `ezkey-tests/AGENTS.md` (API functional tests).

**Principle:** build → API regression (when list DTOs change) → optional device-backed UI smoke.
Do not default to MCP browser for every column tweak.

---

## Validation ladder (by change type)

| Change | Always | If Docker stack is up |
|--------|--------|------------------------|
| Java / list DTO enrichment | `./scripts/build.sh` | Targeted `ezkey-tests` assertion on new field |
| Admin UI columns / labels only | `npm run build` in `ezkey-admin-ui` | Playwright `@smoke` (see below) |
| Login / Demo Device interaction | Unit tests + Playwright | Full `./scripts/run-ui-tests.sh` |

For **operator list slices** (`I-2026-0013` / `I-2026-0014`): prefer **one REST assertion** on the
enriched list field (stable, no browser) before optional UI smoke.

---

## Prerequisites (clean-start baseline)

From repo root:

```bash
cd ezkey-tests
./clean-start.sh
```

Default ports (host):

| Service | URL |
|---------|-----|
| Admin API | `http://localhost:9080` |
| Admin UI (dev) | `http://127.0.0.1:5173` or preview `http://127.0.0.1:4173` |
| Demo Device | `http://127.0.0.1:8083` |

Bootstrap Global Admin username: **`admin.docker`** (passwordless MFA via Demo Device enrollment
created during bootstrap).

---

## Step 0 — OpenAPI spec refresh (when Java DTO/API contract changed)

Same procedure for humans and agents. **Do not hand-edit** `specs/**` or dispatched copies such as
`ezkey-admin-ui/openapi-spec.json`.

```bash
cd ezkey-tests && ./clean-start.sh
# wait for Admin API healthy, then from repo root:
./scripts/update-specs.sh --admin-only   # or full script if multiple APIs changed
cd ezkey-admin-ui && npm run generate:api
```

See `.cursor/rules/openapi-specs.mdc` for when this is mandatory vs deferred.

---

## Step 1 — Build (no stack required)

```bash
# From repo root (Git Bash, JDK 25)
./scripts/build.sh
```

Admin UI only:

```bash
cd ezkey-admin-ui
npm run generate:api   # when OpenAPI / DTO contract changed
npm run build
```

---

## Step 2 — API functional check (list DTO changes)

When a list endpoint gains operator labels (e.g. `tenantName` on integrations), run **after** Step 0
(clean-start + update-specs) so the running Admin API matches the Java code.

```bash
cd ezkey-tests
mvn test -pl ezkey-tests -Dtest=IntegrationManagementSecurityTest#testAdminCanListIntegrations,IntegrationManagementSecurityTest#testAdminCanGetIntegrationById
```

Example assertion pattern: any row with `tenantId` must have non-empty `tenantName`.

---

## Step 3 — Device-backed UI smoke (optional)

**Canonical harness:** Playwright (not ad-hoc MCP sequencing).

Terminal 1 — Admin UI dev server (if not already running):

```bash
cd ezkey-admin-ui
npm run dev -- --host 127.0.0.1 --port 4173
```

Terminal 2 — run smoke tests against live stack + Demo Device:

```bash
cd ezkey-admin-ui
PLAYWRIGHT_SKIP_WEBSERVER=1 ./scripts/run-ui-tests.sh --grep @smoke
```

Artifacts: `ezkey-admin-ui/test-results/browser/` (`run-info.txt`, `summary.txt`, traces on failure).

Environment overrides:

- `EZKEY_ADMIN_UI_URL` — default `http://127.0.0.1:4173`
- `EZKEY_DEMO_DEVICE_URL` — default `http://127.0.0.1:8083`
- `EZKEY_ADMIN_UI_TEST_USERNAME` — default `admin.docker`

Login flow is implemented in `e2e/support/auth-flow.ts`: Admin UI waiting state → Demo Device
pending auth → approve → dashboard.

---

## MCP browser (Cursor) — spot check only

Use when Playwright is unavailable or for quick visual confirmation. **Mirror Playwright order:**

1. Navigate Admin UI `/login`, fill `admin.docker`, submit → wait for `login-waiting-state`.
2. Open Demo Device enrollment auth URL (repeat navigation until pending — see `auth-flow.ts`).
3. Approve on Demo Device → expect Admin UI `/dashboard`.

If pending never appears: check `docker ps`, Demo Device logs, do **not** loop more than a few retries.

---

## When to add Playwright coverage

Add or extend a browser test only when:

- role-based column visibility changes,
- a critical workflow path changes, or
- Tier A list pattern is stable and one representative test covers the whole group.

Do **not** add a Playwright test per column change.

---

## Instrumentation (progressive)

Prefer `data-testid` on pages being touched in the operator-lists program:

- Shell / sidebar: already tagged (`sidebar-link-integrations`, etc.).
- List pages: add page-level + shared `paginated-table` when editing that screen.

---

## Related docs

- [`ezkey-admin-ui/AGENTS.md`](../ezkey-admin-ui/AGENTS.md) — Playwright, MCP notes
- [`ezkey-tests/AGENTS.md`](../ezkey-tests/AGENTS.md) — functional test philosophy
- [`product-docs/global/admin-ui-paginated-screens-matrix.md`](../product-docs/global/admin-ui-paginated-screens-matrix.md) — operator list targets
