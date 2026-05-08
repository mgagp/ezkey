# Backlog Idea — `I-2026-0004` Admin API: configurable acceptance of API-key authentication

## Metadata

- **ID:** `I-2026-0004`
- **Status:** `triaged`
- **Priority:** `P1`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P2-hardening`
- **Component tags:** `admin-api`, `sdk-java`, `docs`

## Intent

Introduce a configuration flag in `admin-api` that controls whether API-key authentication is accepted on Admin API endpoints. Default to **deny** (security-by-default), with an explicit opt-in for the simplified deployment profile (`V-2026-0002`). This enforces a deliberate posture and prevents accidental misconfigurations where SDK clients silently target Admin API instead of Integration API.

## Problem and value

- **Problem:** Today, API-key authentication is implicitly accepted on both Admin API and Integration API. A real misconfiguration was observed where the Java SDK pointed at Admin API while the operator believed Integration API was in use; everything worked, masking the misalignment. The current behavior also weakens the trust-boundary intent (`Design Principle #4`): Admin API is the operator interface; Integration API is the high-throughput machine-to-machine surface.
- **Expected value:** Predictable, deliberate posture; reduced attack surface on Admin API; clarity for operators about which binary handles M2M traffic; alignment with deployment-profile positioning (`V-2026-0003`).

## Scope

- **In scope:**
  - Add a configuration property on `admin-api` to enable or disable API-key acceptance on endpoints that today accept both API key and admin session.
  - Default value: **deny** (security-by-default).
  - Honest, structured error response (RFC 9457) when API-key auth is rejected because of profile policy.
  - Documentation update: explain the flag, the default, the deployment-profile rationale, and the simplified all-in-one opt-in.
  - SDK and operator guidance: clarify Integration API as the canonical M2M surface.
- **Out of scope:**
  - Full removal of API-key acceptance from Admin API (separate decision tied to `V-2026-0003`).
  - Changes to Integration API authentication behavior.
  - Changes to admin session authentication on Admin API.

## Key assumptions

- The flag is binary (allow or deny). Per-endpoint granularity is not required for V1.
- Integration API remains the canonical M2M surface and continues to accept API-key authentication unconditionally.

## Risks and exceptions

- Existing operators relying on API-key authentication against Admin API must be notified before the default flips on upgrade. The migration path must be explicit (release notes plus a clear error message pointing at the flag and at Integration API).
- Authentication test suites and SDK example configurations must be reviewed to ensure they target Integration API where appropriate.

## Promotion notes

Move to `ready` after `V-2026-0002` deployment-profile catalog is stable enough to confirm which profiles flip the flag to allow by default. Until then, ship with global deny-by-default and explicit opt-in.

## Links

- Related vision: `V-2026-0002` (deployment profiles), `V-2026-0003` (API-key acceptance posture)
- Related features: `F-integration-api-maturity`, `F-api-key-lifecycle`
- Related principles: `#4` (trust boundaries), `#12` (security as posture)
