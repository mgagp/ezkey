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

From repo root (Git Bash, JDK 25):

```bash
mvn test -pl ezkey-tests \
  -Dtest=IntegrationManagementSecurityTest#testAdminCanListIntegrations,IntegrationManagementSecurityTest#testAdminCanGetIntegrationById \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Enrollments slice example:

```bash
mvn test -pl ezkey-tests \
  -Dtest=EnrollmentManagementSecurityTest#testAdminCanListEnrollments,EnrollmentManagementSecurityTest#testAdminCanGetEnrollmentById \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Example assertion pattern: any row with `tenantId` must have non-empty `tenantName`; enrollment list
rows with `integrationId` must have non-empty `integrationName` (and `tenantName` when `tenantId` is
present).

Functional tests also **seed the database** with integrations/enrollments — useful before optional UI
smoke so list screens are non-empty.

---

## Step 3 — Device-backed UI smoke (Playwright — preferred)

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

## Step 4 — MCP browser (Cursor) — optional spot check

Use when Playwright is not run but the operator wants a **quick visual confirmation** after Steps
0–2 (clean-start + API tests + spec refresh). **Playwright remains ground truth** for automation;
MCP is a pragmatic smoke tool for agent closeout.

### When to use

- Operator-lists slice closeout (Integrations, Enrollments, …) after API tests pass.
- Login / Demo Device / list column visibility sanity check.
- **Not** for copy-only or spacing tweaks.

### Prerequisites

Same as clean-start baseline (Step 0). Admin UI dev server on port **5173** (Vite proxy to Admin API
9080) is fine.

### Two-tab sequence (mirror Playwright)

1. **Tab A — Admin UI:** navigate to `http://localhost:5173/login`.
2. Fill username **`admin.docker`** (see MCP pitfalls below), submit login → page shows waiting
   state (Cancel button; minimal interactive snapshot).
3. **Tab B — Demo Device:** open `http://localhost:8083/phone/ezkey/enrollments/1/auth` in a
   **new tab** (`newTab: true`). Repeat navigation until **Approve** / **Deny** appear (pending
   claim). If you see only “Check for Authentication Requests” / “No pending”, the admin login may
   have expired — cancel Tab A, restart login, then reload Tab B immediately.
4. Click **Approve** on Demo Device → Tab A should land on `/dashboard` as **Global Admin**.
5. Spot-check list routes for the slice, e.g. `/integrations`, `/enrollments` — confirm operator
   labels (not raw FK IDs) and role-based tenant column for Global Admin.

Optional: open **New Enrollment** dialog on Enrollments to confirm create workflow still loads.

### MCP pitfalls (observed)

| Issue | Mitigation |
|-------|------------|
| `browser_fill` failed with `text` param | Use **`value`**, not `text`. |
| `browser_type` appends text | Prefer `browser_fill`, or Ctrl+A then type. |
| Login / pending timing | Start Demo Device poll **while** Admin UI is waiting; do not poll after expiry. |
| Snapshot lags route change | Take a second snapshot after navigation if content looks stale. |
| Demo Device `unhealthy` in `docker ps` | Smoke can still work; investigate healthcheck separately. |

### Representative checks (operator lists)

| Screen | Global Admin expects |
|--------|----------------------|
| Integrations | Tenant column with name + link; primary ID kept |
| Enrollments | Integration name + link; tenant column (GA only); trimmed columns (~7) |

---

## Closeout validation — operator trigger phrase

One sentence is enough for an agent session:

> **Closeout validation:** clean-start, API tests for [slice], update-specs, optional UI smoke
> [integrations + enrollments] via `admin.docker`.

Cursor rule: [`.cursor/rules/agent-ui-closeout-validation.mdc`](../.cursor/rules/agent-ui-closeout-validation.mdc).

GitHub program issue: [#236](https://github.com/mgagp/ezkey/issues/236) — add a short comment with
test classes run and smoke result when closing a slice.

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

- [`.cursor/rules/agent-ui-closeout-validation.mdc`](../.cursor/rules/agent-ui-closeout-validation.mdc) — closeout trigger for agents
- [`ezkey-admin-ui/AGENTS.md`](../ezkey-admin-ui/AGENTS.md) — Playwright, MCP notes
- [`ezkey-tests/AGENTS.md`](../ezkey-tests/AGENTS.md) — functional test philosophy
- [`product-docs/global/admin-ui-paginated-screens-matrix.md`](../product-docs/global/admin-ui-paginated-screens-matrix.md) — operator list targets
