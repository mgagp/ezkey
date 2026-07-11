# Backlog Idea — `I-2026-07-11` API key PATCH: edit or clear `expiresAt`

## Metadata

- **ID:** `I-2026-07-11-api-key-expires-at-edit`
- **Status:** `captured`
- **Priority:** `P2`
- **Created at:** `2026-07-11`
- **Updated at:** `2026-07-11`
- **Last reviewed at:** `2026-07-11`
- **Phase tags:** `P3-polish`
- **Component tags:** `admin-api`, `admin-ui`, `core`, `docs`
- **Lane:** `D`
- **Captured by:** Marc (operator observation: Edit Key Config lacks expiration; agent contract review)

## Intent

Allow operators to **change or clear** an API key’s `expiresAt` after creation, via the existing Admin API PATCH path and the Admin UI **Edit Key Config** dialog, without minting a new key pair or revoking the current secret.

## Problem and value

- **Problem:** Create supports optional `expiresAt`; detail shows expiry; Edit Key Config only updates `description` and `ipWhitelist`. Extending TTL, shortening it, or moving to “never expires” today requires **create new key + revoke old** — unnecessary secret rotation for a metadata change.
- **Expected value:** Same operational flexibility as IP whitelist edits: adjust rotation policy in place while the integration key/secret remain valid. Aligns Admin UI with a small, deliberate contract extension.

## Contract analysis (2026-07-11)

| Surface | Editable / settable fields |
|---------|----------------------------|
| **POST create** | `integrationId`, `description`, `expiresAt` (`@Future`), `ipWhitelist` |
| **PATCH update** | `version`, `description`, `ipWhitelist` only — **not** `expiresAt` |
| **Edit Key Config UI** | Matches PATCH (description + IP whitelist) |
| **Revoke** | Separate action; not a PATCH of `active` |

Anchors: `ApiKeyCreateRequestDto`, `ApiKeyUpdateRequestDto`, `ApiKeyService.updateApiKey`, `ezkey-admin-ui/src/pages/api-key-detail.tsx`, `docs/ENDPOINT.md` § PATCH `/api/v1/api-keys/{keyId}`.

### Attribute triage (non-goals confirmed)

| Attribute | Make editable? | Rationale |
|-----------|----------------|-----------|
| `description`, `ipWhitelist` | Already yes | Ops labeling + network posture |
| `expiresAt` | **Yes — this idea** | TTL policy without secret rotation |
| `integrationId` | No | Re-home breaks trust/audit; create new key |
| Secret / `integrationKey` | No | Rotation = new key + revoke |
| `active` via PATCH | No | Keep explicit revoke |
| Scopes / permissions | N/A today | Separate product model if ever introduced |

## Scope

- **In scope:**
  - Extend `ApiKeyUpdateRequestDto` + `ApiKeyService.updateApiKey` to apply `expiresAt` (future when set) and an explicit **clear** path (prefer enrollment-style `clearExpiresAt` or documented partial-update clear semantics — decide at TB).
  - Admin UI Edit Key Config: datetime control + clear “never expires”; optimistic `version` unchanged.
  - `docs/ENDPOINT.md`, OpenAPI refresh (`update-specs`), Postman if impacted; EN/FR i18n.
  - Audit if other API-key mutations are audited — keep parity.
- **Out of scope:**
  - Changing create/revoke/secret-once semantics.
  - Moving keys across integrations.
  - Soft-toggle `active` via PATCH.
  - API-key acceptance flag / scopes (`I-2026-0004` and related).

## Key assumptions

- Partial-update null-vs-clear rules must be explicit (same class of footgun as enrollment `expiresAt`).
- Only **active** (non-revoked) keys remain updatable.
- UI gap is intentional reflection of today’s contract, not a client bug.

## Risks and exceptions

- Clearing expiry widens key lifetime — operators should see clear copy; optional confirm for clear-only.
- Setting past dates must stay rejected (`@Future` or equivalent).
- Spec/client regen required after DTO change.

## Candidate first slice (TB candidate)

1. PATCH: `expiresAt` + clear semantics; service + unit tests.
2. Admin UI Edit Key Config field parity with create (including clear).
3. ENDPOINT + specs + Postman as needed.

## Promotion notes

Status `captured`. Promote to `ready` / `TB-*` when clear-semantics choice is fixed (flag vs null convention) and audit expectation is confirmed. Priority `P2` — useful operator polish, not a release blocker. No `V-*` (bounded intent gap, not strategic vision).

## Links

- Related (distinct): `I-2026-0004-admin-api-key-acceptance-flag`
- Code / docs anchors:
  - `ezkey-admin-api/.../ApiKeyUpdateRequestDto.java`
  - `ezkey-admin-api/.../ApiKeyCreateRequestDto.java`
  - `ezkey-core/.../ApiKeyService.java` (`updateApiKey`)
  - `ezkey-admin-ui/src/pages/api-key-detail.tsx`
  - `docs/ENDPOINT.md` (API keys PATCH)
  - Enrollment clear pattern reference: enrollment PATCH `clearExpiresAt` in ENDPOINT.md

Incubation note: evaluated in a Cursor Plan mode session (2026-07-11); the working plan was **ephemeral scaffolding** and is not retained as a repo-hosted incubation artifact. This `I-*` is the sole durable record.