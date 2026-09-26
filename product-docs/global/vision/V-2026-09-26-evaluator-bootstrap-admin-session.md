# Vision Note — `V-2026-09-26-evaluator-bootstrap-admin-session`
# Alpha community: public evaluator activation also issues an Admin UI bootstrap session

## Metadata

- **ID:** `V-2026-09-26-evaluator-bootstrap-admin-session`
- **Status:** `draft`
- **Lane:** `D` (post-delivery evolution of evaluator self-registration / community alpha funnel)
- **Created at:** `2026-09-26`
- **Updated at:** `2026-09-26` (gap #1 option B — Patrick craft: onboarding-resume secret)
- **Captured by:** Marc / Alex
- **Priority:** `P2` (friction reduction on community alpha path; gated; not a platform default)
- **Marc defaults confirmed:** `2026-09-26` — same self-reg flag only; auto-redirect after emit + QR in account; one-sentence expiry message

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

### Marc-confirmed product defaults (2026-09-26)

These three are **explicit product defaults** — capture them in execution UX/API, do not invent a
second flag or a silent expiry.

1. **Same flag as self-reg — bootstrap session only when ON.** There is **no separate** bootstrap
   session flag. Minting the opaque Admin UI bootstrap session happens **if and only if**
   evaluator self-registration is enabled via the existing gate:
   env `EZKEY_EVALUATOR_SELF_REGISTRATION_ENABLED` /
   property `ezkey.evaluator.self-registration.enabled`.
   When the flag is **OFF**, public signup must not mint a bootstrap session and must not advertise
   one. Flag remains **OFF by default**; refuse clean-start self-host default.
2. **Auto-redirect to Admin UI after emission; QR stays on the account.** After successful public
   signup emit (activation code + bootstrap session), the evaluator funnel **auto-redirects** into
   the Admin UI under that bootstrap session. The enrollment **QR remains accessible and
   completable inside the account** (incomplete-enrollment surfaces) — redirect must not hide or
   drop the QR path.
3. **Clear one-sentence message when the bootstrap session expires.** On absolute 8h expiry (or
   equivalent server rejection of an expired bootstrap session), the UI shows a **single clear
   sentence** stating that the temporary console session has ended and how to continue (e.g.
   complete enrollment / sign in again) — not a blank failure or a stack-trace page.

### Additional settled locks

4. **Scope = community / alpha only** (lab / discrete instance with the flag explicitly ON). Not a
   platform-wide default.
5. **When public signup emits an activation code and the flag is ON, ALSO mint** an opaque Admin UI
   **session** token (not a permanent password, not a recovery secret).
6. **Bootstrap session TTL = exactly 8 hours, absolute** (no sliding for this bootstrap session).
   Separate from activation-code TTL (**keep 7 days**).
7. **Activation code remains one-shot** enrollment bootstrap (redeem once; independent of session).
8. **Logout kills the session**; tenant / admin account **persists** as incomplete enrollment
   (not dead-on-logout). Evaluator can return later via activation / device bind / normal login as
   applicable.
9. **QR / enrollment always visible and completable** during the incomplete-enrollment window
   (bootstrap session or otherwise) — reinforces Marc default #2 inside the account.
10. **Delivery includes ezkey.org alpha-path honesty update.** Cloudflare publish = Edgar later —
    **not** in the first docs/vision PR and not required to promote this note.
11. **Julie (Admin UI):** minimal chrome — honesty line / small banner when enrollment is incomplete;
    default = little chrome + one honesty line (no heavy onboarding wizard in v1). Expiry UX follows
    Marc default #3 (one sentence).
12. **Isabelle (QA) later:** exhaustive clean-start QA + a local profile with the mode **ON**.
13. **Lab / discrete / alpha ≠ prod.** No SLA claims. No IdP parity claims.

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

Fail-closed at the shared self-reg gate (Marc default #1): if
`EZKEY_EVALUATOR_SELF_REGISTRATION_ENABLED` / `ezkey.evaluator.self-registration.enabled` is off,
public signup must not mint a bootstrap session and must not advertise one. Prefer fail-closed on
session mint failure during signup only if product decides “no half-provisioned console promise”;
record that choice in the execution slice (activation code may still be the durable enrollment
path).

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
- **No new enablement property** for bootstrap session — reuse the self-reg flag only.
- Post-emit UX: **auto-redirect** into Admin UI with bootstrap session established; account UI keeps
  enrollment QR reachable.
- Expiry UX: **one-sentence** clear message when bootstrap session TTL elapses.

## Consequences (when executed later)

Orientation only — promote via `I-*` / `TB-*` when funded. No application code in this PR.

| Surface | Consequence |
| ------- | ----------- |
| **Flag / config** | **Same flag only** (`EZKEY_EVALUATOR_SELF_REGISTRATION_ENABLED`); add bootstrap-session TTL prop (8h); document OFF default and community-only enablement; no second bootstrap toggle. |
| **Admin API** | When flag ON, signup emit path mints activation **and** bootstrap session; activate/redeem stays one-shot for the code; logout invalidates session server-side; rate limits on emit + redeem. |
| **Admin UI** | Accept bootstrap session; **auto-land** after emit redirect; Julie’s minimal incomplete-enrollment honesty line/banner; **QR/enrollment completable in account**; **one-sentence expiry message** when 8h absolute TTL ends; no heavy chrome. |
| **ezkey.org** | Alpha-path honesty copy update (community/lab only; friction-reduction narrative without SLA/IdP parity); signup success path hands off via auto-redirect. Publish via Edgar / Cloudflare later. |
| **QA** | Isabelle: clean-start remains flag-OFF (no bootstrap mint); separate local/community profile with mode ON; cover auto-redirect + QR-in-account, logout-persists-account, 8h absolute expiry + one-sentence message, activation still one-shot. |

### Gap #1 consequence (Alex product lock 2026-09-26 — option B; Patrick craft)

Logout (or absolute BOOTSTRAP TTL) **after activate and before device bind** must not be a dead end. Global Admin `POST /admins/{id}/activation-code/regenerate` only covers `PENDING_ACTIVATION` with **no** enrollment — it does **not** cover the post-activate `CREATED` enrollment gap.

**Settled product choice: B — bounded re-issue onboarding** (do **not** block logout (A); do **not** accept a dead-end trap (C)), with Patrick craft imposition:

- **Do not** reopen the activation code (stays one-shot).
- **Do not** expose QR / onboarding by username alone publicly.
- On successful **activate** (same self-reg flag ON): mint distinct opaque resume secret `ezkey_onboarding_resume_*`, hashed at rest (`ONBOARDING_RESUME`), absolute **8h** TTL, max **3** uses.
- Redeem: `POST /api/v1/admin/auth/onboarding-resume` (body = resume secret); rate-limit with login/activate bucket.
- Success: remint **BOOTSTRAP** (absolute **2h**, no sliding, same allowlist). QR via existing authenticated `…/onboarding` + `…/qrcode` — no second QR payload surface on the resume response.
- Failure: opaque 401/404; no tenant oracle.
- Optional soft logout honesty warn when BOOTSTRAP + incomplete enrollment (not an API block).

## Honesty

- This path is for the **alpha community instance** (and explicit lab profiles), not for default
  Docker clean-start or production self-host claims.
- Bootstrap session is a **temporary console foothold**, not an identity-provider session product
  and not password equivalence.
- Incomplete enrollment after logout is **expected** and recoverable — not a “deleted trial.”

## Non-goals

- Enabling the flag (or bootstrap session mint) on clean-start / self-host defaults.
- A **separate** feature flag for bootstrap session (must stay coupled to self-reg).
- Permanent password issuance, email password reset, or remember-me / refresh tokens.
- Sliding TTL for the bootstrap session.
- Changing activation-code TTL away from 7 days.
- Killing tenant/admin on logout.
- Hiding enrollment QR after auto-redirect into Admin UI.
- Opaque / empty UX when the bootstrap session expires (must be one clear sentence).
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
2. Marc defaults are accepted in the execution slice: same self-reg flag only; post-emit
   auto-redirect + QR in account; one-sentence expiry message.
3. `BOOTSTRAP` allowlist while `PENDING_ACTIVATION` is sketched enough to avoid accidental full-privilege session.
4. Site honesty copy outline is agreed (community/lab only).
5. QA profile plan (flag OFF clean-start + flag ON community/lab) is acknowledged.

## Next step

Keep this note as the **decision lock**. When prioritized, open a bounded `I-*` / `TB-*` for Admin
API + Admin UI + ezkey.org honesty (publish deferred to Edgar). Do not implement application code
from this vision PR alone.
