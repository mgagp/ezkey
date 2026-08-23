# Backlog Idea — `I-2026-08-23-auth-unsigned-public-instance-info-retirement` Auth unsigned public instance-info retirement

## Metadata

- **ID:** `I-2026-08-23-auth-unsigned-public-instance-info-retirement`
- **Status:** `captured`
- **Priority:** `P3`
- **Created at:** `2026-08-23`
- **Updated at:** `2026-08-23`
- **Last reviewed at:** `2026-08-23`
- **Component tags:** `auth-api`, `docs`, `mobile`, `testing`, `security`
- **Captured by:** Marc
- **GitHub issue:** none

## Intent

Keep a parked, inventory-backed option to **remove** Auth API
`GET /api/v1/public/instance-info` once remaining operator/probe/hint consumers have an explicit
replacement — without reopening the official mobile display path, and without touching Admin API
`GET /api/v1/public/instance-info`.

## Problem and value

- **Problem:** After signed enrolled instance-info landed, the official mobile app no longer calls
  the unsigned Auth GET. The endpoint still exists as an anonymous Auth surface. Stale docs had
  made that split hard to discover (aligned 2026-08-23). Leaving it forever without a recorded
  retirement option invites later agents to treat it as a mobile contract again, or to delete it
  without replacing probes.
- **Expected value:** When the time comes, retire a unused-by-mobile anonymous Auth endpoint with
  a known consumer list and explicit gates — or consciously keep it as a durable operator probe.

## Settled posture (do not re-grill)

Recorded 2026-08-23. Closed unless a new signal appears.

| Surface | Role |
| --- | --- |
| `POST /api/v1/enrollments/instance-info` (Auth) | Official mobile display after enrollment. Verify integration Ed25519 before apply. Never fall back to the unsigned GET. |
| `GET /api/v1/public/instance-info` (Auth) | Unsigned. Operators, reachability probes, Bruno `g0`. Not a trust anchor. Not a first-party mobile caller today. |
| `GET /api/v1/public/instance-info` (Admin) | Same JSON on the Admin host. Admin UI login / header. **Out of this idea.** |

Network/HTTP failure on the enrolled path stays fail-open (keep last good or host-only branding).
Signature failure stays fail-closed on apply. Branding fields remain display-only; trust-zone
identity stays the normalized Auth URL.

Canon: [`docs/ENDPOINT.md`](../../../../docs/ENDPOINT.md) § Public instance metadata and
§ Enrolled instance-info.

## Scope

- **In scope (when this idea is later promoted):**
  - Confirm the consumer inventory below is still complete.
  - Replace Auth GET uses that must survive (Bruno `g0`, Docker public-probe fallback, deployment
    playbooks, OpenAPI “first public endpoint” ordering).
  - Remove Auth `PublicInstanceInfoController` (or equivalent) and generated Auth spec path.
  - Delete the unused mobile helper `instanceInfoApi.get` and stop generating/calling the public
    Auth Orval client for that path if nothing else needs it.
  - Update Bruno, ENDPOINT, Auth AGENTS, and any remaining “mobile can call this GET” copy.
- **Out of scope:**
  - Admin API public instance-info and Admin UI `usePublicInstanceInfo`.
  - Changing official mobile to use the unsigned GET again.
  - Honesty UI / branding-change confirmation (still on
    [`I-2026-08-09-mobile-signed-instance-info-integrity`](I-2026-08-09-mobile-signed-instance-info-integrity.md)).
  - Inventing a new instance-level signing key.

## Consumer inventory (2026-08-23)

| Consumer | Needs the Auth GET? | Replacement if retired |
| --- | --- | --- |
| Official mobile wizard + Home refresh | No — uses signed POST | None |
| Mobile `instanceInfoApi.get` | No first-party caller | Delete helper (cheap independent hygiene) |
| Demo Device | No | None |
| Admin UI | No — calls **Admin** GET | Keep Admin GET |
| Bruno `g0` / `bruno/public-auth/get-public-instance-info.bru` | Yes today | Actuator health, or drop Auth from `g0` and keep Admin public GET |
| `ezkey-tests` Docker public-probe fallback | Yes as Actuator fallback | Prefer Actuator only, or a dedicated public health path |
| EXP1 / hybrid deployment playbook curl | Yes as reachability check | Actuator or Admin GET |
| OpenAPI presentation (Auth first public item) | Cosmetic | Reorder after removal |
| Planned pinning hint (`spkiPinningMode` on unsigned GET) | Possible future | Signed POST as authority already; need another pre-enrollment hint or drop the hint channel |
| Planned version / compat discovery on public instance-info | Possible future | Put advisory fields on signed enrolled instance-info and/or enrollment payload, or keep a public GET |

## Key assumptions

- Official mobile will not grow a pre-enrollment instance-branding screen that requires an
  unauthenticated Auth GET. QR + bind already supply integration / tenant / enrollment labels.
- Admin GET remains the unauthenticated branding surface for operators in the Admin UI.
- Pinning (`I-2026-07-25`) and version/compat discovery (`I-2026-08-02`) must either stop needing
  an Auth unsigned GET or this idea stays parked.

## Risks and exceptions

- Deleting the Auth GET while Bruno `g0` or Docker still probe it breaks smoke/health.
- Deleting it while pinning or version-discovery still specify “hint on public GET” forces those
  ideas to re-open their discovery channel.
- Third-party clients following old docs may still call the GET; retirement is a public Auth
  contract change — announce in ENDPOINT / changelog, do not silent-break.
- A dedicated public health endpoint is a new contract if Actuator stays off the public Auth host.

## Promotion notes

Stay `captured` / `P3` until an operator explicitly wants Auth-surface reduction. Move to
`ready` only when **all** are true:

1. [`I-2026-07-25-mobile-certificate-pinning-middle-path`](I-2026-07-25-mobile-certificate-pinning-middle-path.md)
   no longer requires `spkiPinningMode` on Auth unsigned GET (shipped elsewhere, or parked).
2. [`I-2026-08-02-mobile-installation-version-and-compat-discovery`](I-2026-08-02-mobile-installation-version-and-compat-discovery.md)
   no longer requires Auth unsigned GET as the discovery vehicle.
3. Probe replacement is named (Actuator-only vs dedicated health vs keep Admin GET only).
4. Consumer inventory re-checked in source (not this snapshot alone).

Optional cheap slice that does **not** require promoting this idea: delete unused
`instanceInfoApi.get` in `ezkey_mobile` as local hygiene.

Do **not** open a tracer bullet until those gates are closed. Do **not** invent a GitHub issue
until an operator wants board visibility.

## Links

- Posture canon:
  [`docs/ENDPOINT.md`](../../../../docs/ENDPOINT.md) § Public instance metadata
- Signed payload:
  [`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](../../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md)
  § Enrolled instance-info
- Parent integrity track (GET retained on purpose for v1):
  [`I-2026-08-09-mobile-signed-instance-info-integrity`](I-2026-08-09-mobile-signed-instance-info-integrity.md),
  [`TB-2026-08-09-mobile-signed-instance-info`](../TB-2026-08-09-mobile-signed-instance-info.md)
- Adjacent discovery ideas that can block retirement:
  [`I-2026-07-25-mobile-certificate-pinning-middle-path`](I-2026-07-25-mobile-certificate-pinning-middle-path.md),
  [`I-2026-08-02-mobile-installation-version-and-compat-discovery`](I-2026-08-02-mobile-installation-version-and-compat-discovery.md)
- Mobile interpretation:
  [`ezkey_mobile/docs/MOBILE_API_MAPPINGS.md`](../../../../ezkey_mobile/docs/MOBILE_API_MAPPINGS.md)
