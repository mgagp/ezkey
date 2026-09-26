# Vision Note — `V-2026-09-26-evaluator-bootstrap-admin-session`
# Alpha community: public evaluator activation also issues an Admin UI bootstrap session

## Metadata

- **ID:** `V-2026-09-26-evaluator-bootstrap-admin-session`
- **Status:** `draft`
- **Lane:** `D` (post-delivery evolution of evaluator self-registration / community alpha funnel)
- **Created at:** `2026-09-26`
- **Updated at:** `2026-09-26`
- **Captured by:** Marc / Alex
- **Priority:** `P2` (friction reduction on community alpha path; gated; not a platform default)

## Intent

On the **community / alpha** instance only, when public evaluator signup emits an activation code,
**also mint an opaque Admin UI bootstrap session** so the evaluator can reach the console without
first binding a device and completing a normal login. Reduce funnel friction while keeping
activation, enrollment, and session semantics honest and separable.

This note records **product locks** (settled), **security guards**, the **craft gap** (today vs
intended), and **consequences** for a later execution slice. It does **not** authorize
implementation in this PR.

## Motivation

Today the anonymous signup path returns an **activation code** and leaves the evaluator to activate,
enroll a device, then log in under the normal Admin UI session model (~2h sliding). That is correct
for self-host and production-like posture, but it is **extra friction** for a public alpha
evaluator who just self-provisioned and wants to see the console immediately.

The desired middle path: **same gating family as evaluator self-registration**, community/lab
honesty only, **session ≠ password**, **activation remains one-shot enrollment bootstrap**, and
logout must **not** destroy the incomplete tenant/admin.

## Product locks (settled — do not reopen)

Settled by Alex / Marc. Do not re-litigate in execution planning unless a security boundary forces
a documented supersession.

1. **Scope = community / alpha only.** Same gating family as evaluator self-registration:
   - Flag **OFF by default**.
   - **ON** only via explicit install config:
     env `EZKEY_EVALUATOR_SELF_REGISTRATION_ENABLED` /
     property `ezkey.evaluator.self-registration.enabled`.
   - **Refuse** enabling this as a clean-start self-host default.
2. **When public signup emits an activation code, ALSO mint** an opaque Admin UI **session** token
   (not a permanent password, not a recovery secret).
3. **Bootstrap session TTL = exactly 8 hours, absolute** (no sliding for this bootstrap session).
   Separate from activation-code TTL (**keep 7 days**).
4. **Activation code remains one-shot** enrollment bootstrap (redeem once; independent of session).
5. **Logout kills the session**; tenant / admin account **persists** as incomplete enrollment
   (not dead-on-logout). Evaluator can return later via activation / device bind / normal login as
   applicable.
6. **QR / enrollment always visible and completable** during the incomplete-enrollment window
   (bootstrap session or otherwise).
7. **Delivery includes ezkey.org alpha-path honesty update.** Cloudflare publish = Edgar later —
   **not** in the first docs/vision PR and not required to promote this note.
8. **Julie (Admin UI):** minimal chrome — honesty line / small banner when enrollment is incomplete;
   default = little chrome + one honesty line (no heavy onboarding wizard in v1).
9. **Isabelle (QA) later:** exhaustive clean-start QA + a local profile with the mode **ON**.
10. **Lab / discrete / alpha ≠ prod.** No SLA claims. No IdP parity claims.

## Security guards (Christophe — capture for execution)

These are product-level security obligations for any later `I-*` / `TB-*` / implementation:

| Guard | Obligation |
| ----- | ---------- |
| Separate secrets | Activation mint and session id are **distinct**; do not reuse one as the other. |
| Activation one-shot | Activation code is consumed once at redeem; not a standing bearer. |
| Session transport | Opaque bearer and/or cookie: **HttpOnly**, **Secure**, **SameSite**; server-side invalidation on logout. |
| No leak surfaces | No session token in URL query; no session token in application logs / audit payloads. |
| Rate limits | Rate-limit **emit** (signup) and **redeem** (activation); reuse / extend existing evaluator self-reg limiter posture. |
| No long-lived refresh | No refresh token / remember-me for this bootstrap path. Absolute 8h only. |
| Honesty scope | Public claims scoped to **community / lab / alpha** only — never imply this is the self-host or prod default. |

Fail-closed preference at the gate: if the feature flag is off, public signup must not mint a
bootstrap session (and must not advertise one). Prefer fail-closed on session mint failure during
signup only if product decides “no half-provisioned console promise”; record that choice in the
execution slice (activation code may still be the durable enrollment path).

## Craft gap (Patrick — current vs intended)

Documented for V-* consequences. **Not** implemented in this note’s PR.

### Today (current craft)

- Public signup returns **`activationCode`** (plus URLs / labels as today).
- Activate performs enrollment bootstrap; **still no console session** from that path alone.
- UI access after signup = **device bind + login**; normal Admin session is ~**2h sliding**.

### Intended craft direction (for later execution)

- Purpose **`BOOTSTRAP`** allowlist while admin is **`PENDING_ACTIVATION`** (narrow privilege surface
  — enough to complete enrollment / see honesty UI; not a full post-login operator session).
- Signup response gains **`sessionToken` / `sessionExpiresAt` / `username`** (names indicative;
  finalize in DTO/OpenAPI during implementation).
- Dedicated **bootstrap TTL** configuration property = **8h absolute** (distinct from normal
  session TTL and from activation-code TTL).

## Consequences (when executed later)

Orientation only — promote via `I-*` / `TB-*` when funded. No application code in this PR.

| Surface | Consequence |
| ------- | ----------- |
| **Flag / config** | Remain on the existing evaluator self-registration gate family; add bootstrap-session TTL prop (8h); document OFF default and community-only enablement. |
| **Admin API** | Signup emit path mints activation **and** bootstrap session; activate/redeem stays one-shot for the code; logout invalidates session server-side; rate limits on emit + redeem. |
| **Admin UI** | Accept bootstrap session; show Julie’s minimal incomplete-enrollment honesty line/banner; keep QR/enrollment completable; no heavy chrome. |
| **ezkey.org** | Alpha-path honesty copy update (community/lab only; friction-reduction narrative without SLA/IdP parity). Publish via Edgar / Cloudflare later. |
| **QA** | Isabelle: clean-start remains flag-OFF; separate local/community profile with mode ON; cover logout-persists-account, 8h absolute expiry, activation still one-shot. |

## Honesty

- This path is for the **alpha community instance** (and explicit lab profiles), not for default
  Docker clean-start or production self-host claims.
- Bootstrap session is a **temporary console foothold**, not an identity-provider session product
  and not password equivalence.
- Incomplete enrollment after logout is **expected** and recoverable — not a “deleted trial.”

## Non-goals

- Enabling the flag (or bootstrap session mint) on clean-start / self-host defaults.
- Permanent password issuance, email password reset, or remember-me / refresh tokens.
- Sliding TTL for the bootstrap session.
- Changing activation-code TTL away from 7 days.
- Killing tenant/admin on logout.
- Full IdP / SSO / WebAuthn parity claims on the public site.
- Cloudflare / DNS publish work (Edgar) as part of this vision PR.
- Exhaustive QA in the vision PR (Isabelle track is a later execution gate).
- Replacing the existing email-reviewed or guided-tour pedagogy paths.

## Relationship to existing artifacts

| Artifact | Relationship |
| -------- | ------------- |
| [`V-2026-05-23-anonymous-evaluator-self-registration`](V-2026-05-23-anonymous-evaluator-self-registration.md) | Parent funnel vision — this note extends the **post-signup console foothold**, same gate family. |
| [`V-2026-09-22-exp1-to-ezkey-online-alpha`](V-2026-09-22-exp1-to-ezkey-online-alpha.md) | Community / alpha host posture and honesty bounds this path must respect. |
| [`V-2026-06-05-admin-enrollment-vs-admin-login-state-model`](V-2026-06-05-admin-enrollment-vs-admin-login-state-model.md) | Adjacent mental model: enrollment ≠ login; bootstrap session must not blur those outcomes. |
| `I-2026-05-23-exp1-anonymous-evaluator-onboarding` / `TB-2026-05-23-exp1-anonymous-evaluator-signup-first-cut` | Shipped/first-cut signup returns activation only; this vision names the **next friction-reduction** layer. |
| `docs/LIFECYCLE_GOVERNANCE.md` | Incomplete enrollment persistence and admin lifecycle must stay aligned when executed. |

## Promotion criteria

Promote (or spawn `I-*` / `TB-*`) when:

1. Execution owners accept the locked TTL split (activation 7d vs bootstrap session 8h absolute).
2. `BOOTSTRAP` allowlist while `PENDING_ACTIVATION` is sketched enough to avoid accidental full-privilege session.
3. Site honesty copy outline is agreed (community/lab only).
4. QA profile plan (flag OFF clean-start + flag ON community/lab) is acknowledged.

## Next step

Keep this note as the **decision lock**. When prioritized, open a bounded `I-*` / `TB-*` for Admin
API + Admin UI + ezkey.org honesty (publish deferred to Edgar). Do not implement application code
from this vision PR alone.
