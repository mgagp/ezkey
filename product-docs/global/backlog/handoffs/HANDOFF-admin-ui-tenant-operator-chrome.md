# Handoff — Admin UI tenant-operator chrome (session workspace vs instance branding)

**Status:** `open` — analysis first; do **not** implement until the operator authorizes a fix shape
**Keyword:** none (product UX, not `assessment-curated`)
**Observed:** 2026-09-16 — Tenant Admin session in Admin UI
**Related living canon:** [`../../operator-alignment-guide.md`](../../operator-alignment-guide.md) § Admin UI copy posture; [`../../product-intent.md`](../../product-intent.md); [`../../../../ezkey-admin-ui/AGENTS.md`](../../../../ezkey-admin-ui/AGENTS.md) § layout / demo-mode; [`TB-2026-07-26-admin-ui-help-corpus-overhaul.md`](../TB-2026-07-26-admin-ui-help-corpus-overhaul.md) (French **tenant** loanword)

Use this prompt to start a **new Cursor session**. Keep this work **out of** the mobile identity-grid session. Delete this file on PR/closeout after reinjecting durable UX decisions into Admin UI living docs (`ezkey-admin-ui/AGENTS.md` layout/chrome) and, if needed, `operator-alignment-guide.md`.

---

## Operator decisions (already made)

1. **Disposition:** analysis + recommendations in a separate session; implementation only after HITL on the chrome shape.
2. **Do not** fold this into Ezkey Mobile enrollment identity work.
3. French role copy already canon: **Admin tenant** (not *admin de locataire*). Do not relitigate that here.

---

## One-sentence problem

A Tenant Admin can tell they are a Tenant Admin, and they can see **instance** branding (for example Unicorn Farm), but the console chrome does not clearly say **which tenant workspace** they are operating.

---

## Scenario that led to the observation (preserve this narrative)

The operator signed into the Admin console as a **Tenant Admin** for a business tenant (demo: Unicorn Farm Accountability, distinct from the instance name Unicorn Farm). Personalized instance chrome is correct: sidebar/header show Unicorn Farm from **public instance info**. The only role cue besides that branding is the top-left block: EZKey + **Admin tenant** + instance name, plus a small `TENANT` badge next to the username.

What is missing: visual landmarks that the session is the **admin console of that particular tenant**. Tenant description and tenant name exist as entity fields, but they are not used as session chrome. After creating integrations and enrollments inside that tenant, the gap became obvious: instance identity is loud; **tenant workspace identity is almost silent**.

**Non-claim:** this is not a mobile authenticator identity-grid defect. It is not a missing French translation of Tenant Admin. It is not a request to paint every list row with the tenant name for a Tenant Admin (lists are already auto-scoped).

---

## Evidence map (read these first)

- `ezkey-admin-ui/src/components/layout/sidebar.tsx` — brand block: `tagline.tenantAdmin` + `publicInstanceInfo.instanceName` (instance, not session tenant).
- `ezkey-admin-ui/src/components/layout/header.tsx` — center banner is instance name/description; signed-in chip is `username` + raw `adminType.replace('_ADMIN', '')` (`TENANT` / `GLOBAL`).
- `ezkey-admin-ui/src/lib/auth.ts` — `AuthSession` has `tenantId` for Tenant Admin, **no `tenantName`**.
- Login / session DTO: confirm whether Admin API already returns tenant display fields on passwordless login; do not invent a second fetch if login can carry them.
- `ezkey-admin-ui/src/locales/fr/layout.json` — `tagline.tenantAdmin` = `Admin tenant` (keep).
- Comparables (light): GitHub org switcher, AWS account alias in the console header, Google Cloud project picker — session **workspace** is named in chrome, distinct from product/instance branding.

---

## Product intent

- **Global Admin** = IT / instance. Chrome should keep saying they operate the **Ezkey instance**.
- **Tenant Admin** = business operator of **one tenant**. Chrome should name that tenant the way an org switcher names the current org — without implying they can switch tenants (they cannot).
- Sobriety: one durable landmark in shell chrome beats decorating every page. Prefer sidebar + header over per-route banners.
- Fail-open vs fail-closed: missing tenant **label** in chrome is a UX gap (fail-open for operations — the token already scopes the API). Do not weaken object-level tenant checks to “fix” chrome.

---

## Recommended investigation (cold agent)

1. **Map current chrome layers** (instance vs role vs tenant vs user) in a short table: where each string comes from, GA vs TA.
2. **Confirm session contract:** can the login response already supply `tenantName` (and optional `tenantDescription`)? If only `tenantId` exists, recommend the smallest DTO addition — do not hand-edit OpenAPI.
3. **Propose 2–3 chrome shapes**, 80/20:

   | Option | Sketch | Cost |
   | --- | --- | --- |
   | A | Sidebar: keep instance name; add tenant name as the primary workspace line for TA only | Small UI; may need `tenantName` on session |
   | B | Header center: TA sees tenant name/description instead of (or under) instance banner | More visible; do not hide instance entirely if operators still need it |
   | C | Dedicated workspace chip (tenant name) next to the `TENANT` badge; leave instance banner | Smallest visual change |

   Default recommendation unless research contradicts: **A + a quieter B** (sidebar names the tenant; header keeps instance as secondary or drops duplicate instance name for TA).

4. **Out of scope unless operator expands:** color themes per tenant, logos, tenant switcher, mobile app, Playwright for copy-only if you only change strings — **do** add/adjust a smoke if login session shape or chrome landmarks change (`login-username-input` path + dashboard assertion).
5. **Do not** create `I-*` / `TB-*` until the operator asks after the recommendation. If the slice stays chrome + optional login DTO field, implement on a focused Admin UI branch after Go.

---

## Paste-ready starter message

```text
Read product-docs/global/backlog/handoffs/HANDOFF-admin-ui-tenant-operator-chrome.md end-to-end.
Then read product-docs/global/operator-alignment-guide.md § Admin UI copy posture and ezkey-admin-ui/AGENTS.md layout notes.

Task: analysis only. Produce the chrome-layer table (instance vs role vs tenant vs user, GA vs TA), confirm whether login/session already has tenant display fields, and recommend option A/B/C (or a hybrid) with a smallest-diff implementation sketch. Do not implement. Do not create I-*/TB-* unless I ask. Do not touch ezkey_mobile. Keep French copy as Admin tenant (loanword), never locataire.
```
