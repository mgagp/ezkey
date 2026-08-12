# Plan: Admin UI Rebranding — Tenant + Global Admin Support

> **Completed historically.** Living Admin UI is `ezkey-admin-ui/` (`docker-compose.admin-ui.yml`,
> `./start.sh`). Do not recreate `ezkey-tenant-ui` or `docker-compose.tenant-ui.yml`.

The `ezkey-admin-ui` folder was already renamed via Git. The goal is to: (1) complete the in-file rename sweep removing all "Tenant UI" references, (2) make the sidebar branding dynamic based on `adminType`, (3) add conditional navigation for global-admin-only features, and (4) add an **Encryption Keys** mock page behind a global-admin gate. The login flow and Admins page require no changes — they already work correctly for both roles.

---

## Phase 1 — In-file rename sweep

1. Update `ezkey-admin-ui/package.json` — change `"name": "ezkey-tenant-ui"` → `"ezkey-admin-ui"`

2. Rename `docker-compose.tenant-ui.yml` → `docker-compose.admin-ui.yml` and update its internals: `name: ezkey-tenant-ui` → `ezkey-admin-ui`, service key `tenant-ui:` → `admin-ui:`, `container_name: ezkey-tenant-ui` → `ezkey-admin-ui`

3. Update `ezkey-admin-ui/start.sh` — fix the comment on line 2: `Ezkey Tenant UI` → `Ezkey Admin UI` and update the `docker compose -f` filename reference

4. Update `ezkey-admin-ui/README.md` — H1: `EZKey Admin UI`, update description to cover both Global and Tenant admins, update Docker command to use `docker-compose.admin-ui.yml`

5. Update `ezkey-admin-ui/AGENTS.md` — H1: `EZKey Admin UI — Agent Notes`, update Purpose section to reflect dual-role scope, update Docker deploy command, update nav table to include Encryption Keys (global-only, flagged as mock)

6. Update `AUTH_KEY` in `ezkey-admin-ui/src/lib/auth.ts` — `'ezkey_tenant_auth'` → `'ezkey_admin_auth'`
   > Note: clears existing dev sessions — acceptable in dev context. Previous plan had deferred this; including it now for full consistency.

---

## Phase 2 — Dynamic sidebar branding

7. In `ezkey-admin-ui/src/components/layout/sidebar.tsx` — import `useAuth` from `@/context/auth-context`. Replace the hardcoded `"Tenant Admin"` tagline with a dynamic label derived from `session.adminType`:
   - `GLOBAL_ADMIN` → `"Global Admin"`
   - `TENANT_ADMIN` → `"Tenant Admin"`
   - fallback → `"Admin Console"`

---

## Phase 3 — Conditional navigation

8. In `ezkey-admin-ui/src/components/layout/sidebar.tsx` — extend `NavItem` with an optional `roles?: AdminType[]` field. Filter `navItems` before rendering: if `roles` is set, only show the item when `session.adminType` matches. Add `Encryption Keys` entry with `roles: ['GLOBAL_ADMIN']`, icon `KeyRound`, path `/encryption-keys`

---

## Phase 4 — Encryption Keys mock page

9. Create `ezkey-admin-ui/src/pages/encryption-keys.tsx` — a mock page wrapped in `<AppShell title="Encryption Keys">`. Display a `Card` with a brief description and a structured table listing the 9 operations from the Postman collection (`EZ Key Encryption Keys admin.postman_collection.json`):

   | Operation | Method | Path |
   |---|---|---|
   | List keys | GET | `/api/v1/encryption-keys` |
   | Get primary key | GET | `/api/v1/encryption-keys/primary` |
   | Get key by ID | GET | `/api/v1/encryption-keys/:keyId` |
   | Rotate key | POST | `/api/v1/encryption-keys/rotate` |
   | List re-encryption batches | GET | `/api/v1/encryption-keys/reencryption-batches` |
   | Resume batch | POST | `/api/v1/encryption-keys/reencryption-batches/:batchId/resume` |
   | Trigger full re-encryption | POST | `/api/v1/encryption-keys/reencrypt/trigger` |
   | Trigger re-encryption for key | POST | `/api/v1/encryption-keys/:keyId/reencrypt` |
   | Create re-encryption batches | POST | `/api/v1/encryption-keys/reencrypt/create-batches` |

   Include a `"Feature coming soon"` `Alert` at the top — no real API calls.

10. Register the route in `ezkey-admin-ui/src/routes.tsx` — add `/encryption-keys` as a lazy-loaded `ProtectedRoute`, pointing to `EncryptionKeysPage`. No route-level role guard needed (nav already hides the link; direct URL access by a non-global admin lands on the page but sees the mock content harmlessly).

---

## Verification

- `npm run build` inside `ezkey-admin-ui/` — zero TypeScript / lint errors
- Log in as a `TENANT_ADMIN`: sidebar tagline shows `Tenant Admin`, no Encryption Keys entry visible
- Log in as a `GLOBAL_ADMIN`: sidebar tagline shows `Global Admin`, Encryption Keys entry appears, mock page renders cleanly
- Existing tenant flows (Integrations, Enrollments, Admins, API Keys, Audit Logs, Auth Attempts) unaffected
- Docker: `docker compose -f docker-compose.admin-ui.yml up --build` succeeds

---

## Decisions

- `AUTH_KEY` rename (`ezkey_tenant_auth` → `ezkey_admin_auth`) **included** — no production sessions exist; rename is clean and consistent with the overall rebranding
- Encryption Keys page is **mock only** — no API wiring in this phase
- Admin Provisioning page (`/admins`) **unchanged** — already covers both roles adequately for this phase; the existing screen handles create tenant admin, onboarding credentials/QR codes, and deactivate — all scoped correctly per `adminType`
- No route-level role guard on `/encryption-keys` — sidebar gate is sufficient for this phase; a hard guard can be added in a future phase when the page becomes functional
- `.cursor/plans/` and `.github/prompts/` history files referencing "Tenant UI" are **not updated** — they are historical artifacts, not active documentation
