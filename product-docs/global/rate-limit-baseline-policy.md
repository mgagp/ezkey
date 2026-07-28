# Rate-Limit Baseline Policy

## Purpose

Canonical **analysis output** for cross-cutting rate-limit posture across Ezkey API modules. Records
what is limited today, why overrides exist, and whether a single numeric baseline is justified.

**Why (decision record):** the choice to key by actor identity (API key id, enrollment id, admin id)
rather than by client IP, and to not adopt a single global baseline, is recorded as
[`ADR-0010`](architecture-decisions.md#adr-0010-rate-limiting-scoped-by-actor-identity-not-by-ip).
This document is the living **inventory** (current surfaces, defaults, per-profile overrides); the
ADR is the **rationale**.

**Grilled:** Blitz 2026-05-08-2 D1 ([`backlog/grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md`](backlog/grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md)).

**Backlog:** [`I-2026-0008`](backlog/ideas/I-2026-0008-rate-limit-baseline-analysis.md) — **closed** (policy + CONFIGURATION alignment; no code unification R1).

**Discovery method:** OpenAPI + `RateLimit*` implementations + module `CONFIGURATION.md` + targeted
controller reads (see downscope decision [`../methodology/decisions/2026-05-24-controllers-registry-downscope.md`](../methodology/decisions/2026-05-24-controllers-registry-downscope.md)).

**Not a substitute for:** per-module `CONFIGURATION.md` property tables, [`docs/ENDPOINT.md`](../../docs/ENDPOINT.md), or [`ezkey-admin-api/README_RATE_LIMITING.md`](../../ezkey-admin-api/README_RATE_LIMITING.md) (operational tuning guide).

## Executive conclusion

**There is no coherent single numeric baseline** for all REST surfaces. Forcing one band would
misrepresent product semantics (device user-initiated polling vs integration throughput vs sensitive
admin mutations).

**R1 posture (grill-aligned):**

- **Document** the existing **policy families** and overrides below.
- **Keep** specialized limits; tune via `CONFIGURATION.md` properties per deployment profile.
- **Do not** unify implementations in code unless a future slice proves measurable simplification
  (#1 pragmatism — no refactor for refactor's sake).

Honest outcome: **baseline + overrides model is already present in code**; this policy names it and
maps surfaces to families.

## Policy model — four families

| Family | Audience | Intent | Key dimension | Typical window |
|--------|----------|--------|---------------|----------------|
| **D — Device auth** | Mobile / device | Abuse resistance on enrollment + auth-attempt flows; strict respond limit | `enrollment-id`, `auth-attempt-id`, or `client-ip` | 1–5 minutes |
| **I — Integration throughput** | API key (M2M) | Fair usage on create/wait auth-attempt | Per API key id | 1–15 minutes |
| **A — Admin sensitive ops** | Admin session | Cap destructive or high-impact mutations | Per admin id or recovery token | 15–30 minutes |
| **L — Admin login** | Public admin auth | Brute-force resistance + optional IP block | Client IP | 1–5 minutes |

Volume tier alignment (paginated matrix): device and login surfaces are **operator-light /
user-initiated**; integration wait/create are **throughput**; most other Admin API CRUD list endpoints
are **unrate-limited** at HTTP layer today (rely on session auth + infra).

## Inventory — rate-limited surfaces

Defaults below are **Java `@ConfigurationProperties` defaults** when enabled. **Docker / clean-start**
profiles often override (notably shorter windows and lower caps). See each module `CONFIGURATION.md`.

### Auth API — `ezkey.rate-limit.*` (`RateLimitFilter`)

Global flag: `ezkey.rate-limit.enabled` (default `false`; **`true` in docker profile**).

| Endpoint | Method | Family | Key strategy | Default (enabled) | Docker profile (typical) | Sensitivity |
|----------|--------|--------|--------------|-------------------|--------------------------|-------------|
| `/api/v1/auth-attempts/pending` | POST | D | `enrollment-id` (docker) / `client-ip` (code default) | 10 / 1 min | 10 / 1 min, `enrollment-id` | high |
| `/api/v1/auth-attempts/respond` | POST | D | `auth-attempt-id` | **1 / 5 min** | 1 / 5 min | **critical** — do not loosen |
| `/api/v1/enrollments/verify` | POST | D | `client-ip` | 10 / 1 min | 5 / 5 min | high |
| `/api/v1/enrollments/bind` | POST | D | `client-ip` | 10 / 1 min | 3 / 5 min | high |

**Unrate-limited Auth API:** `GET /api/v1/public/instance-info` (public metadata).

Reference: [`ezkey-auth-api/CONFIGURATION.md`](../../ezkey-auth-api/CONFIGURATION.md), [`RateLimitFilter.java`](../../ezkey-auth-api/src/main/java/org/ezkey/auth/config/RateLimitFilter.java).

### Admin API — integration-style API key usage — `ezkey.api-key.rate-limit.*`

Used on Admin API **AuthAttemptController** when called with API key auth (create + wait paths).

| Operation | Family | Key | Default (Java) | Docker profile (typical) | Sensitivity |
|-----------|--------|-----|----------------|--------------------------|-------------|
| Create auth attempt | I | API key id | 100 / 15 min | 10 / 1 min | medium |
| Wait auth attempt | I | API key id | 200 / 15 min | 20 / 1 min | medium |

Reference: [`ezkey-admin-api/CONFIGURATION.md`](../../ezkey-admin-api/CONFIGURATION.md) §7.

### Integration API — `ezkey.api-key.rate-limit.*`

Same property prefix; separate module `RateLimitService` (per-instance buckets; non-distributed).

| Operation | Family | Key | Default (Java) | Typical docker | Sensitivity |
|-----------|--------|-----|----------------|----------------|-------------|
| `POST /api/v1/auth-attempts` | I | API key id | 100 / 15 min | 100 / 1 min | medium |
| `GET /api/v1/auth-attempts/{id}/wait` | I | API key id | 200 / 15 min | 200 / 1 min | medium |

Reference: [`ezkey-integration-api/CONFIGURATION.md`](../../ezkey-integration-api/CONFIGURATION.md).

### Admin API — sensitive admin operations — `ezkey.admin-operations.rate-limit.*`

Service-level limits (Bucket4j); keyed by admin id or recovery token as implemented per call site.

| Operation | Controller / flow | Family | Default (Java) | Docker profile (typical) | Sensitivity |
|-----------|-------------------|--------|----------------|--------------------------|-------------|
| API key create | `ApiKeyController` | A | 5 / 15 min | 5 / 15 min | high |
| API key revoke | `ApiKeyController` | A | 10 / 15 min | 10 / 15 min | high |
| API key update | `ApiKeyController` | A | 20 / 15 min | 20 / 15 min | medium |
| Enrollment reset (recovery) | `AdminEnrollmentController` | A | 3 / 30 min | 3 / 30 min | high |

Reference: [`AdminOperationsRateLimitService.java`](../../ezkey-admin-api/src/main/java/org/ezkey/admin/security/AdminOperationsRateLimitService.java).

### Admin API — login — `ezkey.admin.rate-limit.*` (`AdminRateLimitFilter`)

| Endpoint | Family | Key | Default (Java) | Docker profile (typical) | Notes |
|----------|--------|-----|----------------|--------------------------|-------|
| `POST /api/v1/admin/auth/login` | L | client IP | 5 / 5 min | 5 / 1 min | + block after 10 failures / 30 min |

Reference: [`AdminRateLimitFilter.java`](../../ezkey-admin-api/src/main/java/org/ezkey/admin/security/AdminRateLimitFilter.java).

### Admin API — ad hoc (feature-scoped)

| Surface | Prefix / mechanism | Family | Default | Notes |
|---------|-------------------|--------|---------|-------|
| `POST /api/v1/public/evaluator-signup` | `ezkey.evaluator.self-registration.*` + `EvaluatorSelfRegistrationRateLimiter` | L (capacity) | daily cap 5; 1 success / IP / 24h | EXP1 preview only; in-memory |

Reference: [`EvaluatorSelfRegistrationProperties.java`](../../ezkey-admin-api/src/main/java/org/ezkey/admin/config/EvaluatorSelfRegistrationProperties.java).

### Crypto API

**No HTTP rate limiting** in R1 (internal/crypto surface; not in grill D1 scope).

## Surfaces without HTTP rate limits (R1)

Most **authenticated Admin API** CRUD/list endpoints (tenants, integrations, enrollments, audit logs,
dashboard, encryption keys, alerts, provisioning, etc.) rely on **session/API-key auth** and
operational deployment controls — not token-bucket filters.

This is **acceptable for R1 PME posture** unless profiling shows abuse. Future limits belong in
**targeted** follow-ups (e.g. public signup patterns), not a forced global baseline.

## Profile matrix (summary)

| Profile | Auth API limits | Admin login | API-key / integration | Admin ops |
|---------|-----------------|-------------|----------------------|-----------|
| Default (properties file) | Often **disabled** until enabled | enabled | enabled | enabled |
| `docker` / clean-start | **enabled**, stricter bind/verify | enabled, 1 min window | lower caps, 1 min windows | enabled |
| `docker-test` | disabled | disabled | disabled | disabled |

See module `CONFIGURATION.md` profile tables for authoritative values.

## Configuration hygiene notes (from analysis)

| Item | Status |
|------|--------|
| `ezkey.admin.rate-limit.recovery.*` in some `application*.properties` | **Not bound** to `AdminRateLimitProperties` — dead config keys; remove or implement in a future slice |
| `ezkey.admin.rate-limit.api-key.*` | Documented as **unused** in docker comments — API key usage uses `ezkey.api-key.rate-limit.*`. Incident detail: [`ezkey-admin-api/API_KEY_RATE_LIMIT_NOTE.md`](../../ezkey-admin-api/API_KEY_RATE_LIMIT_NOTE.md) |
| Integration vs Admin duplicate `RateLimitService` | **Known duplication** — deferred extraction (#1); document together via this policy |
| Distributed rate limits | **Per-instance** (Caffeine); acceptable R1; Redis noted in Integration service Javadoc when scale warrants |

## R1 recommendations

1. **Operators:** tune via existing property prefixes; prefer Integration API for high M2M throughput
   ([`docs/API_KEYS_GUIDE.md`](../../docs/API_KEYS_GUIDE.md)).
2. **Developers:** when adding a **new public or high-abuse** endpoint, assign a **family** (D/I/A/L)
   and properties — do not add to a fictional global baseline number.
3. **Tests:** keep `docker-test` profile limits disabled for churn; do not weaken **respond** defaults
   in production profiles.
4. **Code unification:** **not recommended R1** — inventory does not show simplification win.

## Related documents

| Document | Role |
|----------|------|
| [`I-2026-0008`](backlog/ideas/I-2026-0008-rate-limit-baseline-analysis.md) | Backlog closure |
| [`ezkey-auth-api/CONFIGURATION.md`](../../ezkey-auth-api/CONFIGURATION.md) | Auth API properties |
| [`ezkey-admin-api/CONFIGURATION.md`](../../ezkey-admin-api/CONFIGURATION.md) | Admin API properties |
| [`ezkey-integration-api/CONFIGURATION.md`](../../ezkey-integration-api/CONFIGURATION.md) | Integration API properties |
| [`ezkey-admin-api/README_RATE_LIMITING.md`](../../ezkey-admin-api/README_RATE_LIMITING.md) | Tuning examples |
| [`ezkey-admin-api/API_KEY_RATE_LIMIT_NOTE.md`](../../ezkey-admin-api/API_KEY_RATE_LIMIT_NOTE.md) | Historical incident: unused `ezkey.admin.rate-limit.api-key.*` config-naming collision |
| [`admin-ui-paginated-screens-matrix.md`](admin-ui-paginated-screens-matrix.md) | Volume tiers (complementary) |
| [`sql-business-limits-policy.md`](sql-business-limits-policy.md) | Repository-layer limits (complementary) |
