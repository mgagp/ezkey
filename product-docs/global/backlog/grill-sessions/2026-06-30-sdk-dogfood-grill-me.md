# Grill Me — SDK dogfood vs Admin UI / Admin API (2026-06-30)

## Session control

| Field | Value |
| ----- | ----- |
| **Trigger** | Operator question: does Admin UI (and Admin API backend) consume published SDKs under "eat your own dog food"? |
| **Vision** | [`V-2026-06-30-sdk-dogfood-integrity-posture`](../../vision/V-2026-06-30-sdk-dogfood-integrity-posture.md) |
| **Backlog** | [`I-2026-0031`](../ideas/I-2026-0031-sdk-dogfood-first-party-consumption.md) |
| **Started** | `2026-06-30` |
| **Closed** | `2026-06-30` |
| **Status** | `complete` — pre-analysis accepted; program **parked** (`I-2026-0031`) |
| **Captured by** | Marc (operator); pre-analysis materialized by agent session |

## Executive summary (pre-analysis)

**The intuition is partially correct.** Ezkey strongly dogfoods **cryptographic admin MFA** (layer
A) but does **not** dogfood **published Integration SDKs** through Admin UI or Admin API auth
(layer B). That is mostly **by design** (different API surface and trust boundary), not a silent
failure — but the ** narrative and SDK docs have not stated this clearly**, which creates the
credibility concern.

**Recommendation:** Do **not** big-bang migrate Admin UI to Integration SDK. Proceed with Phase 0
canon + Phase 1 M2M alignment (`I-2026-0004`), then a bounded design pack for optional admin-session
client work.

---

## Q1 — Do we dogfood Ezkey MFA for the Admin console?

**Yes.**

| Evidence | Location |
| -------- | -------- |
| Passwordless-only admin auth | `AdminAuthService` — uses `AuthAttemptService`, no passwords |
| Admin UI login flow | `login.tsx` — `loginApi` + `passwordlessWait` from Orval Admin API client |
| Bootstrap | `AdminBootstrapService` — system integration + admin enrollment |
| Public/docs thesis | `ADMIN_PASSWORDLESS_LOGIN.md`, `design-decisions.md` |

Layer A is defensible and should remain the headline for "Ezkey secures Ezkey."

---

## Q2 — Does Admin UI use `ezkey-integration-sdk` (TypeScript) or Java SDK?

**No.**

| Surface | Actual client |
| ------- | ------------- |
| Admin UI | Orval-generated `@/generated/admin-api/*` + `fetchApi` wrapper |
| Admin API auth | In-process `ezkey-core` (`AuthAttemptService`) |
| TypeScript SDK | Integration API only; explicit non-goal: browser-first SDK |
| Java SDK (`EzkeyClient`) | Integration API M2M (`createAuthAttempt`, `wait`, `cancel`) |

Admin UI login imports:

```typescript
import { login as loginApi, passwordlessWait } from '@/generated/admin-api/admin-authentication/admin-authentication';
```

This is spec-first Admin API consumption — parallel to how a third party might integrate admin
operations, but **not** the Integration SDK product we publish for M2M integrators.

---

## Q3 — Is that a defect or a category mismatch?

**Category mismatch for Integration SDK; gap for SDK narrative.**

| Factor | Assessment |
| ------ | ---------- |
| Integration SDK credentials | API key + secret in **backend** — must not ship to browser |
| Admin UI credentials | Username → passwordless MFA → bearer token or HttpOnly cookie |
| API endpoints | Admin auth: `/api/v1/admin/auth/*` vs Integration: `/api/v1/auth-attempts` |
| Admin API implementation | In-process core is **stronger** first-party coupling than HTTP SDK |

Forcing Integration SDK into Admin UI would violate backend-first integrity (#3) and trust
boundaries (#4).

**Real gap:** We lack a published **admin-session / passwordless-login client** for TypeScript
SPAs. Admin UI reimplements that flow well — but externally we market **Integration SDK**, not
"Admin SPA client."

---

## Q4 — Where do we actually dogfood the SDK today?

| App | Uses published SDK? | Notes |
| --- | ------------------- | ----- |
| `ezkey-demo-app-acme` | **Yes** (Java `EzkeyClient`) | Reference M2M; URL alignment tracked under `V-2026-0003` |
| `ezkey-sdk-typescript` demo-api | **Yes** (`EzkeyIntegrationClient`) | Official Node reference |
| Admin UI | **No** | Orval Admin API |
| Admin API | **No** (core in-process) | Appropriate for authority module |
| Mobile | **No** (Auth API Orval + device crypto) | Different product surface |

SDK dogfood is **real but narrow** — demo/reference tier, not flagship console.

---

## Q5 — Doc / messaging integrity issues found

1. **Java SDK README** (`ezkey-sdk/java/README.md`) still describes Admin + Auth API coverage;
   **`EzkeyClient` source** implements Integration API M2M only (2025 refactor). Stale README
   amplifies confusion.
2. **Public essays** cite "eat your own dog food" for self-contained integrity — correct for layer A;
   may be read as SDK consumption without qualification.
3. **`LoginController` javadoc** in demo-acme still says "Admin API" in places while moving to
   Integration API — alignment in flight via `I-2026-0004`.

---

## Q6 — Is the strategic idea valid?

**Yes, with refinement:**

- **Valid:** First-party apps should consume published SDKs **where the integration shape matches**;
  SDKs should improve from that use; public story must be honest.
- **Invalid as stated:** "Admin UI should use Integration SDK" — wrong tool.
- **Valid alternative paths:**
  1. Document layers A vs B explicitly (Phase 0).
  2. Finish M2M alignment on Demo ACME + SDK docs (Phase 1).
  3. Optionally package admin passwordless login helpers for SPA integrators (Phase 2+).
  4. Optionally add automation proving reference apps pin to reactor SDK versions.

---

## Q7 — Priority and sequencing

| Item | Suggested priority | Rationale |
| ---- | ------------------ | --------- |
| Phase 0 canon + README fix | P3, quick | Low risk, high clarity |
| `I-2026-0004` / Demo ACME → Integration API | P1 (existing) | Trust boundary + SDK example correctness |
| Admin-session TypeScript client | P3 → P2 after design pack | Only if we want third-party admin SPAs to reuse login flow |
| Admin UI BFF for SDK parity | Defer / likely out | Operational cost; demo-api already shows backend pattern |

Does **not** displace integrity cluster or September operable-release P1 work unless operator
elevates distribution credibility to P1.

---

## Settled decisions (operator confirmed 2026-06-30)

| ID | Decision | Status |
| -- | -------- | ------ |
| D1 | Separate **cryptographic dogfood** (A) from **SDK consumption dogfood** (B) in canon | **Confirmed** |
| D2 | Admin UI must **not** adopt Integration SDK for login | **Confirmed** |
| D3 | Admin API auth stays **in-process core**; SDK HTTP loop not required for integrity | **Confirmed** |
| D4 | Phase 0 docs + Java SDK README accuracy before new SDK features | **Confirmed** (deferred with park) |
| D5 | Phase 1 blocked on / aligned with `I-2026-0004` | **Confirmed** (`I-2026-0004` stays independent) |
| D6 | Optional admin-session client requires design pack + ADR before TB | **Confirmed** |
| D7 | Park active program until post-release or distribution trigger | **Confirmed** |

---

## Closeout (2026-06-30)

Pre-analysis value captured. **`I-2026-0031` parked** — no TB, no design pack, no Phase 0 doc slice
until resume trigger. Grill + vision remain reference to avoid re-deriving the same conclusion.

**Resume when:** distribution milestone, third-party admin-SPA client demand, SDK adoption
blockers on reference apps, or post–September 2026 operable-release calendar.

## Related code anchors (evidence)

- Admin UI login: `ezkey-admin-ui/src/pages/login.tsx`
- Admin API auth: `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java`
- Java SDK: `ezkey-sdk/java/src/main/java/org/ezkey/sdk/EzkeyClient.java`
- TypeScript SDK brief: `ezkey-sdk-typescript/PRODUCT_BRIEF.md` (non-goals: browser-first)
- Demo ACME SDK usage: `ezkey-demo-app-acme/.../LoginController.java`
