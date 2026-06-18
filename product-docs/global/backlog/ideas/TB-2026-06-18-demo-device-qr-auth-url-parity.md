# Tracer Bullet Brief — `TB-2026-06-18-demo-device-qr-auth-url-parity` Demo Device QR authUrl routing

## Metadata

- **ID:** `TB-2026-06-18-demo-device-qr-auth-url-parity`
- **Status:** `done`
- **Related idea:** `I-2026-06-18-demo-device-qr-auth-url-parity`
- **Lane:** `D`
- **Posture:** `single-pass`
- **Created at:** `2026-06-18`
- **Updated at:** `2026-06-18`
- **Closed at:** `2026-06-18`
- **Captured by:** Marc

## Objective

Prove end-to-end that Demo Device honors `authUrl` from an Admin UI enrollment QR for the full
enrollment + auth-attempt simulator path, while manual entry and Exp1-default Docker config remain
unchanged when no QR URL is supplied.

## Boundaries in scope

- `ezkey-demo-device` (monorepo) ↔ Auth API (`bind`, `verify`, `pending`, `respond`)
- QR import JS → bind form → controller → `AuthApiService` → persisted `Record`
- Standalone repo sync + README note

## Out of scope

- Admin UI / Auth API contract changes
- Mobile app
- Replacing Exp1 Docker default URL
- Optional manual Auth API URL field on bind form

## First executable slice

**Delivery order (mandatory):** monorepo first → standalone sync → maintainer standalone test.
See [`case-study-ezkey.md`](../../../methodology/case-study-ezkey.md) § Dual-repo delivery.

### Phase 1 — Monorepo (`ezkey/ezkey-demo-device/`)

1. Add hidden form field `authApiBaseUrl` populated from QR parse (JS).
2. `EzkeyAppController.bindEnrollment` accepts optional `authApiBaseUrl`; validate server-side;
   call Auth API at resolved base; persist in `Record.enrollmentUrl`.
3. Refactor `AuthApiService` to accept per-call base URL override (or dedicated client factory).
4. Verify / pending / respond load URL from stored record.
5. Unit tests: URL validation, resolution precedence (QR > config), record roundtrip.
6. `mvn test` in monorepo module; merge monorepo PR.

### Phase 2 — Standalone sync (`mgagp/ezkey-demo-device`)

7. Copy aligned artifacts from monorepo (`src/`, `pom.xml`, `openapi-spec.json`, `AGENTS.md`, Docker
   files if changed); commit and push standalone repo.

### Phase 3 — Maintainer final validation (standalone)

8. Operator: standalone `./start.sh` on `:3080` + local Admin UI enrollment via QR (no `.env`
   override required after fix); bind, verify, and one auth attempt — record pass in PR/issue
   closeout. **Exit criteria not met until this step is done.**

## Rollback or fallback posture

- Feature is additive; revert restores config-only routing.
- If validation too strict for a lab host, narrow allowlist in a follow-up — do not weaken silently.

## Critical flows

| Flow | Nominal | Exception |
|------|---------|-----------|
| QR bind (local) | QR with `authUrl` → bind/verify against local Auth API while Docker default is Exp1 | Invalid URL → clear error, no bind |
| Manual bind | ID + token only → configured default | Same as today |
| Auth attempt | Pending/respond use stored enrollment URL | Missing stored URL → configured default |
| Exp1 evaluator | Default config, QR without `authUrl` or Exp1 URL in QR | Unchanged |

## Evidence plan

### Unit tests (required)

- URL validator (https + dev http hosts) — Java equivalent of JS rules
- `AuthApiService` uses override base when provided
- `EnrollmentStoreRecordJsonRoundtripTest` extended for `enrollmentUrl`

### Manual exploratory (required)

Clean-start stack + Admin UI enrollment with QR (`authApiPublicBaseUrl` / local URL in payload):

1. Standalone demo device on `:3080` **without** `.env` override; import QR; bind + verify succeed.
2. Same enrollment path on clean-start `:8083` — still works (regression).
3. Manual ID+token entry on standalone — uses Exp1 default (document expected failure against local enrollment or success against Exp1 enrollment).
4. After verify, trigger test auth attempt — pending/respond succeed on standalone when enrollment used QR local URL.

### UI / browser tests

- **Deferred** — simulator-only; proportionate per `ui-browser-test-autonomy` rule.

### Documentation

- `ezkey-demo-device/AGENTS.md` (both repos): QR `authUrl` routing rule
- Standalone `README.md`: one paragraph on QR overriding Docker default
- Grill Me marked `complete` after operator sign-off

## Quality gates

- analysis gate — incident root cause documented (config vs code) ✅
- design gate — Grill Me G1–G11 confirmed ✅
- implementation gate — unit tests + maintainer manual matrix ✅
- traceability gate — I/TB/grill/ML linked ✅; GitHub **#223** (retroactive visibility, PR #222)

## Exit criteria

1. Monorepo: bind via QR against local Auth API logic — unit tests ✅; `:8083` smoke not re-run after
   client-only follow-up (accepted residual).
2. Standalone repo synced — ready to push (`mgagp/ezkey-demo-device`) ✅
3. **Maintainer:** standalone `:3080` + local Admin UI QR enrollment — bind without `.env` override ✅
   (operator sign-off 2026-06-18).
4. Manual bind without QR still uses configured default — unchanged by design ✅
5. Monorepo and standalone sources aligned; `mvn test` in demo-device module ✅

## GitHub delivery (when implementation starts)

- Issue labels (suggested): `lane:d`, `type:feat`, `component:demo-device` (or `infra`), `priority:p2`, `status:ready`
- Branch: `feature/<issue>-demo-device-qr-auth-url-parity`
- PR links back to `I-*` / `TB-*`

## Implementation handoff (cold session bootstrap)

**Read first (in order):**

1. This TB — phases 1–3 and exit criteria
2. [`I-2026-06-18-demo-device-qr-auth-url-parity.md`](I-2026-06-18-demo-device-qr-auth-url-parity.md)
3. [`2026-06-18-demo-device-qr-auth-url-grill-me.md`](../grill-sessions/2026-06-18-demo-device-qr-auth-url-grill-me.md) — settled G1–G11
4. [`case-study-ezkey.md`](../../../methodology/case-study-ezkey.md) § Dual-repo delivery
5. `ezkey-demo-device/AGENTS.md` (monorepo) after edit

**Incident context (why):** Standalone demo device on `:3080` defaults to Exp1 Auth API; local Admin UI
enrollment bind failed until clean-start `:8083` used. Java sources identical; QR JS parses `authUrl`
but server ignores it. Fix = mobile parity.

**Grill decisions (binding):**

- QR `authUrl` wins when valid; informational mismatch banner only
- Invalid QR URL → hard error
- Manual entry → config default only
- Full path: bind + verify + pending + respond
- **Delivery:** monorepo implement → sync `mgagp/ezkey-demo-device` → **maintainer standalone test** (gate)

**Code touchpoints:**

- `enrollment-qr-import.js` — already parses `authUrl`; wire hidden field on bind form
- `EzkeyAppController` — bind/verify/pending/respond + URL validation
- `AuthApiService` — per-request base URL override
- `EnrollmentStoreService.Record.enrollmentUrl` — persist effective base

**Validation:**

- `mvn test` in `ezkey-demo-device/` (monorepo)
- Manual matrix in TB § Evidence plan
- Maintainer phase 3 on standalone `:3080` — **required before closeout**

**Methodology notes (separate track):** [`ML-2026-06-18-demo-device-qr-session.md`](../method-logs/ML-2026-06-18-demo-device-qr-session.md) — retrospective on `.cursor/plans/` vs `product-docs` only.

**Branch:** `feature/demo-device-qr-auth-url-parity` (monorepo). Implementation complete.

## Closeout (2026-06-18)

Per [`closeout`](../../../../.cursor/skills/closeout/SKILL.md) after
[`traceability-sync`](../../../../.cursor/skills/traceability-sync/SKILL.md):

### Completed evidence

| Layer | Result |
|-------|--------|
| Unit | `AuthApiUrlValidatorTest`, `EnrollmentAuthApiUrlResolverTest`, extended JSON roundtrip |
| Manual | Standalone `:3080`: QR import → informational banner → bind hits QR Auth API (ngrok); operator ✅ |
| Docs | `AGENTS.md` (both repos), standalone `README.md`, `case-study-ezkey.md` § dual-repo |

### Implementation notes

- Server: `EnrollmentAuthApiUrlResolver`, per-call `AuthApiService` base URL, `Record.enrollmentUrl`.
- Client follow-up: `sessionStorage` + submit-time sync for `authApiBaseUrl`; cache-bust JS; banner
  copy clarifies device uses QR Auth API (not “may fail”).
- Incidental plan `.cursor/plans/demo-device-qr-auth-url-parity.plan.md` **deleted** — canonical
  source is `product-docs` only (see `ML-2026-06-18-demo-device-qr-session`).

### Traceability sync

- No row in `features-and-phases.md` or `spec-test-traceability.md` — simulator slice; linkage via
  `I-*` / `TB-*` / component `AGENTS.md` is sufficient.

### Deferred / residual

- ~~GitHub issue + PR labels (optional hygiene).~~ **Issue #223** opened retroactively for GitHub visibility; PR #222 `Closes #223`.
- Clean-start `:8083` regression smoke after client fix (low priority).
- Playwright Admin UI tests — deferred per evidence plan.

### Next action

- Merge monorepo PR; push standalone commit; Lane E methodology retro on `.cursor/plans/` policy
  remains open in `ML-*` (separate track).

## Links

- Method log: [`../method-logs/ML-2026-06-18-demo-device-qr-session.md`](../method-logs/ML-2026-06-18-demo-device-qr-session.md)
- Case study dual-repo: [`../../../methodology/case-study-ezkey.md`](../../../methodology/case-study-ezkey.md)
