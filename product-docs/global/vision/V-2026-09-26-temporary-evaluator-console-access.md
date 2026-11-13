# Vision Note — `V-2026-09-26-temporary-evaluator-console-access`
# Alpha community: temporary navigable Admin console after activation code (no device required)

## Metadata

- **ID:** `V-2026-09-26-temporary-evaluator-console-access`
- **Status:** `draft`
- **Lane:** `D` (community alpha evaluator funnel)
- **Created at:** `2026-09-26`
- **Updated at:** `2026-09-26`
- **Captured by:** Marc / Alex
- **Priority:** `P2` (friction reduction for timid community evaluators; gated; not a platform default)
- **Supersedes:** [`V-2026-09-26-evaluator-bootstrap-admin-session`](V-2026-09-26-evaluator-bootstrap-admin-session.md) (Path B bridled BOOTSTRAP — abandoned as product intent; #631 closed without merge)

## Intent

On the **community / alpha** instance only, after the evaluator enters an **activation code** on the
community Admin login (Path A unchanged from ezkey.org), the enrollment screen offers a clear fork:

1. **Explorer la console — accès temporaire (~8 h)** — mint a one-shot temporary evaluation session
   and land in a **navigable** tenant Admin console (dashboard and normal exploration), **without**
   requiring a device bind / SEAL / mobile / Docker demo-device first.
2. **Enrôler un appareil** — classic Path A enrollment (bind then normal session).

Target user: **timid** community alpha evaluators who will not yet commit to TestFlight, Play closed
testing, or Docker demo-device docs, but still want to **see what the console does**.

This note records **product locks** (settled), **storyboard / UI wording**, **security obligations**,
and **consequences** for a later execution slice on a **new branch**. It does **not** authorize
application implementation in this PR.

## Motivation

The earlier bootstrap attempt (`V-2026-09-26-evaluator-bootstrap-admin-session` / #631) drifted into
a **bridled BOOTSTRAP** surface (“finish enrollment only”) until activate+bind. That reduces signup
handoff friction but **does not** serve the cold-feet clientele Marc named: temporary **navigable**
tenant-admin access without a device.

Restart from Path A (activation code), fork at the enrollment screen, keep honesty tight, and treat
the temporary session as an ephemeral lab foothold — not a permanent password and not a promotion of
the same cookie into a post-bind session.

## Product locks (settled — do not reopen)

Settled by Marc (2026-09-26) via Mathieu / Alex. Do not re-litigate in execution unless a security
boundary forces a documented supersession.

### Storyboard (UI)

1. **Path A entry unchanged** — activation code from ezkey.org / existing public evaluator flow;
   community console: « Vous avez un code d’activation » → enter code.
2. **Enrollment-screen fork** (two equal paths, not a bridled-only shell):
   - `Explorer la console — accès temporaire (~8 h)` → temporary navigable console.
   - `Enrôler un appareil` → classic bind path.
3. **While in temporary session** — one honesty banner line; **no blocking bind CTA**.
4. **Bind later** — discreet link from the banner **and** Administrateur → Identifiant (both;
   Identifiant alone is too buried for alpha).
5. **UI language** — operator French as above. Zero jargon in the product UI: no « Mode C »,
   « BOOTSTRAP », « EVALUATOR_TEMP », « SEAL promise ».

### Session / lifecycle

6. **Same gate family as evaluator self-registration** — only when
   `EZKEY_EVALUATOR_SELF_REGISTRATION_ENABLED` /
   `ezkey.evaluator.self-registration.enabled` is **explicitly ON** (community/lab).
   **OFF by default**; refuse clean-start / self-host surprise. No separate Mode C feature flag.
7. **Purpose distinct** — temporary session purpose is **`EVALUATOR_TEMP`** (craft/security name).
   Never the same purpose type as post-bind **`SESSION`**.
8. **Recovery: one-shot** — cookie / bearer lost = access lost for that foothold. Banner must say
   so honestly. **Not** recoverable inside the window via re-issue of the same temp session.
   (Rejected soft preference for in-window recoverable temp session.)
9. **TTL ~8 h absolute** for the temporary foothold (exact property name finalized in craft).
10. **Expiry of `EVALUATOR_TEMP` without bind of the TEMP identity** — revoke TEMP tokens, then
    apply a **predicate** (not a blunt kill+wipe):
    - **Bound** means admin MFA enrollment **VERIFIED** — not end-user Demo Device enrollment
      alone, and not admin **PENDING**.
    - **Predicate:** does there exist **another** tenant-scoped ACTIVE admin (≠ the expiring TEMP
      identity) with MFA enrollment **VERIFIED/bound**?
      - **YES** → revoke `EVALUATOR_TEMP` + **deactivate TEMP identity only**; **do not**
        `deactivateTenant`.
      - **NO** → soft **`deactivateTenant`** + revoke TEMP + deactivate TEMP identity.
    - **Soft** = no destroy, no wipe, **no hard revoke of API keys**. Child entities may remain
      locally Active but are **non-operational** (« non opérationnels ») via the eligibility
      chain — **not** « passent inactifs ».
    - **Documented edge:** a 2nd tenant admin created + bound during the TEMP window → tenant
      **survives** TEMP expiry.
    - **Mandatory audit:** `TENANT_DEACTIVATED` (system job); `TENANT_ACTIVATED` (GLOBAL manual,
      actor + reason); `ADMIN_DEACTIVATED` for the TEMP identity. **Zero auto-reactivation.**
    - Manual GLOBAL reactivation (Marc community) is intentional; key resurrection via lifecycle
      is OK.
11. **Bind VERIFIED → supersede** — revoke `EVALUATOR_TEMP` **immediately**; force a **fresh**
    post-bind `SESSION` login. **Do not** promote / reuse the same cookie into `SESSION`.
12. **Tenant-scoped** — QR / onboarding self-only; **never GLOBAL**.
13. **Navigable console** — deny-list + `TENANT_ADMIN` ACL pass-through so the evaluator can explore
    (dashboard, etc.). Not a Path B allowlist grow into “finish enrollment only.” Security may still
    deny specific dangerous ops without SEAL; it must **not** empty the path back into bridled-only.

### Honesty / delivery

14. **Lab / discrete / alpha ≠ prod.** No SLA, no IdP parity, no WebAuthn/passkey equivalence.
15. **ezkey.org** alpha journey copy may mention temporary console exploration under community
    honesty — publish via Edgar / Cloudflare when funded (not required to land this vision note).
16. **Julie** — banner + fork wording per storyboard; minimal chrome.
17. **Isabelle** later — clean-start flag OFF; community profile flag ON; cover fork, one-shot loss,
    expiry predicate (soft `deactivateTenant` vs TEMP-identity-only), supersede-on-bind, no
    bridled-only regression.

## Security obligations (Christophe — capture for execution)

| Guard | Obligation |
| ----- | ---------- |
| Distinct purposes | `EVALUATOR_TEMP` ≠ `SESSION`; never promote the same cookie. |
| One-shot temp | No in-window re-mint of the same temp foothold after cookie loss. |
| Supersede on VERIFIED bind | Revoke TEMP immediately; require fresh SESSION login. |
| Expiry without TEMP bind | Predicate: other tenant ACTIVE admin with MFA VERIFIED? YES → revoke TEMP + deactivate TEMP identity only (no `deactivateTenant`). NO → soft `deactivateTenant` + revoke TEMP + deactivate TEMP identity. Soft = no destroy/wipe/hard API-key revoke; children may stay locally Active but **non-operational** via eligibility. Audit: `TENANT_DEACTIVATED` (system), `TENANT_ACTIVATED` (GLOBAL manual + reason), `ADMIN_DEACTIVATED` (TEMP). Zero auto-reactivation. |
| Tenant scope | Self-only QR/onboarding; never GLOBAL. |
| Transport | Opaque bearer and/or cookie: HttpOnly, Secure, SameSite; server-side invalidate on logout/expiry. |
| No leak surfaces | No temp token in URL query; no token in application logs / audit payloads. |
| Rate limits | Reuse / extend evaluator self-reg limiter posture on emit and redeem. |
| Fail-closed gate | Flag OFF → no temp session mint and no UI advertisement of the explore path. |

Craft/security joint lock (Patrick ↔ Christophe, 2026-09-26): Mode C scope = **deny-list +
TENANT_ADMIN ACL navigable**, not capability-allowlist growth of Path B.

## Rejected intentions (do not revive)

- **Path B** — signup → bridled BOOTSTRAP until activate+bind (#631 closed without merge).
- In-window **recoverable** temporary session after cookie loss.
- **Promoting** the temp cookie into post-bind `SESSION`.
- Hard wipe / hard revoke of API keys by default on TEMP expiry.
- Auto-reactivation after soft `deactivateTenant` / TEMP-identity deactivation.
- Treating Demo Device end-user enrollment alone (or admin PENDING) as **bound** for the expiry
  predicate.
- Clean-start / self-host default ON.
- Permanent password / remember-me / refresh for this path.
- UI jargon Mode C / BOOTSTRAP / SEAL-as-promise.

## Consequences (when executed later)

Orientation only — promote via `I-*` / `TB-*` on a **new branch**. No application code in this PR.

| Surface | Consequence |
| ------- | ----------- |
| **Flag / config** | Same self-reg flag only; temp-session TTL (~8h absolute); document OFF default. |
| **Admin API** | After activation-code accept, optional mint of `EVALUATOR_TEMP`; bind VERIFIED supersede; expiry predicate + soft `deactivateTenant` / TEMP-identity-only + mandatory audit events. |
| **Admin UI** | Enrollment fork + honesty banner + bind-later links; navigable shell under TEMP; force re-login after supersede. |
| **ezkey.org** | Optional honesty line on alpha journey (community/lab only). Edgar publish later. |
| **QA** | Flag OFF clean-start; flag ON community; one-shot loss; expiry predicate (other bound admin → tenant survives; else soft deactivateTenant); supersede-on-bind; no Path B regression. |

## Non-goals

- Implementing application code from this vision PR alone.
- Reviving #631 / Path B bridled BOOTSTRAP.
- A second feature flag for temporary console access.
- Full IdP / SSO / WebAuthn parity claims.
- Cloudflare / DNS publish as part of this vision PR.

## Relationship to existing artifacts

| Artifact | Relationship |
| -------- | ------------- |
| [`V-2026-09-26-evaluator-bootstrap-admin-session`](V-2026-09-26-evaluator-bootstrap-admin-session.md) | **Superseded** — wrong intention (bridled BOOTSTRAP). |
| [`V-2026-05-23-anonymous-evaluator-self-registration`](V-2026-05-23-anonymous-evaluator-self-registration.md) | Parent funnel — same gate family. |
| [`V-2026-09-22-exp1-to-ezkey-online-alpha`](V-2026-09-22-exp1-to-ezkey-online-alpha.md) | Community / alpha host honesty bounds. |
| [`V-2026-06-05-admin-enrollment-vs-admin-login-state-model`](V-2026-06-05-admin-enrollment-vs-admin-login-state-model.md) | Enrollment ≠ login; TEMP must not blur into durable SESSION. |
| [`../product-intent.md`](../product-intent.md) | Adopter posture and honesty of claims. |

## Promotion criteria

Promote (or spawn `I-*` / `TB-*`) when:

1. Craft + security accept deny-list + TENANT_ADMIN navigable scope, one-shot TEMP, supersede-on-bind, expiry predicate + soft deactivateTenant (no hard wipe / no auto-reactivation).
2. Enrollment-fork + banner wording accepted for Julie.
3. QA profile plan (flag OFF clean-start + flag ON community) acknowledged.
4. Execution opens on a **new branch** (not a revival of #631).

## Next step

Keep this note as the **decision lock**. Mathieu coordinates craft/security review on the branch,
then a bounded implementation slice. Alex does not implement.
