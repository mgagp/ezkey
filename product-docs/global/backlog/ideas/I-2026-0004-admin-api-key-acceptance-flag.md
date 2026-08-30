# Backlog Idea — `I-2026-0004` Admin API: configurable acceptance of API-key authentication

## Metadata

- **ID:** `I-2026-0004`
- **Status:** `promoted`
- **Tracer bullet:** `TB-2026-05-25-admin-api-key-acceptance-flag`
- **GitHub issue:** [#169](https://github.com/mgagp/ezkey/issues/169)
- **Priority:** `P1`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-08-29`
- **Last reviewed at:** `2026-08-29`
- **Successor (deferred removal):** `I-2026-08-29-admin-api-remove-m2m-hatch` / `TB-2026-08-29-admin-api-remove-m2m-hatch`
- **Phase tags:** `P2-hardening`
- **Component tags:** `admin-api`, `sdk-java`, `docs`
- **Captured by:** Marc

## Intent

Introduce a configuration flag on `admin-api` that controls whether **API-key M2M auth-attempt** traffic is accepted on Admin API. Default **`false`** (deny) in all environments; explicit **`true`** only for documented minimal deployments (Admin + Auth binaries, no Integration API). Integration API remains the canonical M2M surface. No profile-aware platform code (`V-2026-0010`).

## Problem and value

- **Problem:** API-key auth is implicitly accepted on Admin API for auth attempts today. Demo ACME + Java SDK were observed targeting Admin API while operators assumed Integration API — it worked, masking misalignment. Weakens trust boundary (`Design Principle #4`).
- **Expected value:** Deliberate posture; smaller Admin attack surface; clear operator/SDK guidance; PME minimal-install escape hatch without forcing a third binary.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-1-D3-api-key-grill-me.md`](../grill-sessions/blitz-2026-05-08-1-D3-api-key-grill-me.md).

| Topic | R1 decision |
|-------|-------------|
| Mechanism | Single boolean property |
| Default | **`false`** everywhere (clean-start, dev, HA, new installs) |
| Opt-in `true` | Minimal Admin+Auth deployment only (documented) |
| Scope | `ROLE_API_KEY` auth-attempt M2M only; no per-endpoint flag |
| Removal | Deferred in R1 — now `I-2026-08-29` / `TB-2026-08-29` |
| Errors | RFC 9457; point to Integration API + property |
| Migration | Brief docs/release note; no production fleet today |
| Profiles | Phase 2 elaboration refines; not blocked on catalog |

## Scope

- **In scope:**
  - Property + filter/guard on Admin API for API-key auth-attempt acceptance.
  - Default `false`; document minimal-install opt-in.
  - RFC 9457 rejection response.
  - `CONFIGURATION.md`, API keys guide, SDK README/examples → Integration API for M2M.
  - Review Demo ACME + functional tests for Integration API base URL where API keys are used.
- **Out of scope:**
  - Removing API-key code from Admin API.
  - Integration API behavior changes.
  - Per-endpoint policy matrix.
  - Profile-aware code or UI toggles.

## Key assumptions

- Existing `ROLE_API_KEY` restriction (auth attempts only) remains; the flag is an install-level gate on top.
- HA and standard stacks keep `false`; operators use Integration API behind HAProxy.

## Risks and exceptions

- Local/dev setups that still point SDK at Admin API for API-key flows will fail until retargeted to Integration API or minimal-install opt-in is set — acceptable given uniform `false` default (grill D3-A).
- Functional tests using API keys against Admin API for auth attempts must be updated in the same change set.

## Promotion notes

✅ Promoted to tracer bullet `TB-2026-05-25-admin-api-key-acceptance-flag` on 2026-05-25.

Tracer bullet includes:
- Full technical design (property name, filter ordering, RFC 9457 response shape).
- Component-level boundary analysis.
- Test strategy by layer (unit, functional, integration).
- Implementation plan with execution order.
- Exit criteria for vertical slice validation.

Ready for implementation.

## Links

- **Tracer bullet (R1):** `TB-2026-05-25-admin-api-key-acceptance-flag`
- **Successor (R2 removal):** [`I-2026-08-29-admin-api-remove-m2m-hatch.md`](I-2026-08-29-admin-api-remove-m2m-hatch.md), [`../TB-2026-08-29-admin-api-remove-m2m-hatch.md`](../TB-2026-08-29-admin-api-remove-m2m-hatch.md)
- **Vision:** `V-2026-0003`, `V-2026-0010`
- **Grill session:** `../grill-sessions/blitz-2026-05-08-1-D3-api-key-grill-me.md`
- **Features:** `F-integration-api-maturity`, `F-api-key-lifecycle`
- **Principles:** `#1`, `#4`, `#5`, `#12`
