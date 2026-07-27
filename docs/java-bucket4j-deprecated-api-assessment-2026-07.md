# Java Bucket4j Deprecated API Assessment (2026-07)

## Mandate

- **Surface:** Java Bucket4j rate-limiting call sites across `ezkey-auth-api`, `ezkey-admin-api`,
  `ezkey-integration-api`, and `ezkey-demo-app-acme` (filters and services that build token
  buckets).
- **Attention axes:** methods and types marked `@Deprecated` in Bucket4j **8.19.0**
  (`com.bucket4j:bucket4j_jdk17-core`); prefer mechanical, behavior-preserving replacement with
  `Bandwidth.builder()`; keep Auth API / Admin API / Integration API rate limits semantically
  equivalent (capacity + intervally refill over the configured window).
- **Non-goals:** implementing fixes in the assessment session until HITL decides; redesign of
  rate-limit keys, Caffeine cache sizing, or distributed Bucket4j; zero-warning campaigns;
  `java-doctor-curated` / Dependabot shortlists; inventing `I-*` / `TB-*` per finding.

## Scope and method

- Static white-box inventory of `io.github.bucket4j.*` imports and bandwidth construction call
  sites in production Java sources.
- Deprecation confirmation against Bucket4j **8.19.0** sources from the local Maven cache
  (`Bandwidth.java`, `Refill.java`).
- Cross-check of sibling modules already on the builder API (positive control / migration
  template).

## Stack versions (evidence)

| Component | Version |
| --- | --- |
| Parent POM `bucket4j.version` | **8.19.0** |
| Artifact | `com.bucket4j:bucket4j_jdk17-core` |
| Modules depending | `ezkey-auth-api`, `ezkey-admin-api`, `ezkey-integration-api`, `ezkey-demo-app-acme` |

## Deprecation map (Bucket4j 8.x — actionable in Ezkey)

| Deprecated API | Replacement | Semantics note |
| --- | --- | --- |
| `Bandwidth.classic(capacity, refill)` | `Bandwidth.builder()…build()` | Official javadoc: use builder |
| `Bandwidth.simple(capacity, period)` | `Bandwidth.builder()…build()` | Not used in Ezkey |
| `Bandwidth.withInitialTokens` / `withId` | builder stages | Not used in Ezkey |
| Entire class `Refill` (all factories) | builder `refillIntervally` / `refillGreedy` | `Refill.intervally` maps to `.refillIntervally(tokens, period)` |

**Not deprecated** (false-positive guard): `Bandwidth.builder()`, `Bucket.builder()`,
`Bucket.tryConsume`, `ConsumptionProbe` — current API used by migrated modules.

## Findings register

| ID | Title | Severity | Confidence | Category | Deprecated API in use | Sites | Replacement direction | Quick-win potential |
| --- | --- | ---: | --- | --- | --- | ---: | --- | --- |
| B4J-DEP-001 | Auth API `RateLimitFilter` classic bandwidth + `Refill` | P2 | High | **1 — quick win** | `Bandwidth.classic` + `Refill.intervally` | 1 → 0 | Mirror `AdminRateLimitFilter` builder pattern | **Closed (fixed 2026-07-27)** |

### Category 2 — complex / redesign

No deprecated Bucket4j call sites require redistributed buckets, API redesign, or behavioral
policy change. Category 2 is **empty** for this pass.

## Evidence

### B4J-DEP-001

Former usage (`ezkey-auth-api`):

```java
// RateLimitFilter.createBucket
Bandwidth limit =
    Bandwidth.classic(
        config.getRequests(),
        Refill.intervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes())));
return Bucket.builder().addLimit(limit).build();
```

File: `ezkey-auth-api/src/main/java/org/ezkey/auth/config/RateLimitFilter.java` (`createBucket`).

Deprecation verification (Bucket4j 8.19.0 sources):

- `Bandwidth.classic` is `@Deprecated` with javadoc: use `Bandwidth.builder()`.
- `Refill` class is `@Deprecated` as a whole; `Refill.intervally` is `@Deprecated`.

Replacement applied (2026-07-27), matching Admin / Integration / ACME:

```java
Bandwidth limit =
    Bandwidth.builder()
        .capacity(config.getRequests())
        .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
        .build();
return Bucket.builder().addLimit(limit).build();
```

Risk notes:

- Mechanical rename of construction API; capacity and intervally refill tokens/period stay the same.
- No remaining `Refill` import in Ezkey production Java.
- Low blast radius: one private factory method; no change to endpoint keys, HTTP 429 path, or
  Caffeine cache.

Disposition: **closed** — HITL **fix**; call site migrated.

## Checked clean (already on builder API)

| Module | Type | Construction API |
| --- | --- | --- |
| `ezkey-auth-api` | `RateLimitFilter` | `Bandwidth.builder()` + `refillIntervally` (after B4J-DEP-001) |
| `ezkey-admin-api` | `AdminRateLimitFilter` | `Bandwidth.builder()` + `refillIntervally` |
| `ezkey-admin-api` | `RateLimitService` | `Bandwidth.builder()` |
| `ezkey-admin-api` | `AdminOperationsRateLimitService` | `Bandwidth.builder()` |
| `ezkey-integration-api` | `RateLimitService` | `Bandwidth.builder()` |
| `ezkey-demo-app-acme` | `DemoRateLimitService` | `Bandwidth.builder()` |

## Suggested HITL lot (this pass)

1. **B4J-DEP-001** (P2) — migrate Auth API `RateLimitFilter.createBucket` to `Bandwidth.builder()`,
   drop `Refill` import. **Closed (fixed 2026-07-27).**

## Campaign note

`product-docs/global/hygiene/java-bucket4j-deprecations/2026-07-27-pass-1.md`
