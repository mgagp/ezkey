# Quality-gate run 6 — remaining secret RED triage

For Christophe. Gate tip that produced these findings: `fd701c41`. Scanner jar/git/arch
false positives are fixed in a follow-up commit (not listed here). **Do not widen the
allowlist for these product signals until triaged.**

Counts = summed `secretFindings[].count` from
`logs/quality-gate/20261011T013732Z-fd701c41/health/health.json` (final health scan).

## 1. `admin-<epoch>` / test username opaque-token

Product INFO/WARN logs that embed administrator usernames (and sometimes tenant display
names). Functional / elective / churn fixtures mint mixed alphanumeric usernames ≥20 chars
(e.g. `admin-<epoch>`, `tenant-admin-<epoch>`), which match the opaque-token rule. Source:
**product log** of a **test-generated** username — not a session secret.

| Count | Service | Logger | Level | Redacted sample |
| ---: | --- | --- | --- | --- |
| 170 | ezkey-admin-api-1 | `o.ezkey.admin.service.AdminAuthService` | INFO | `Passwordless auth (challenge flow) successful for admin: <TOKEN> after 0s` / `Authentication successful for: <TOKEN>` |
| 166 | ezkey-admin-api-1/2 | `o.e.i.service.IntegrationService` | INFO | `Creating integration by admin: <TOKEN> (type: TENANT_ADMIN)` |
| 194 | ezkey-admin-api-1/2 | `o.e.a.service.AdminProvisioningService` | INFO | `Creating tenant admin: <TOKEN> for tenant N` / `Creating tenant: Churn Tenant <TOKEN>` |
| 12 | ezkey-admin-api-1 | `org.ezkey.security.TinkKeyManager` | INFO | `Set file permissions to 600: /etc/ezkey/keysets/<TOKEN>.json.encrypted` *(keyset filename)* |
| ~8 | ezkey-admin-api-1/2 | `o.e.admin.controller.ApiKeyController` / `ApiKeyService` | INFO/WARN | `Listing API keys for admin: <TOKEN>` / `API key created successfully…` |

**Origin:** product logging (`AdminAuthService`, provisioning, integration, API key controllers).
Values are test-minted admin usernames / tenant name fragments / keyset file basenames — not
recovery codes or proof tokens.

## 2. Auth DEBUG `PENDING_*_DIAG` and Spring MVC DTO dumps

| Count | Service | Logger | Level | Redacted sample |
| ---: | --- | --- | --- | --- |
| 1026 | ezkey-auth-api-1/2 | `o.e.a.service.AuthAttemptPendingService` | DEBUG | `PENDING_PAYLOAD_DIAG enrollmentId=N authAttemptId=N payloadLen=N <TOKEN>=<TOKEN…` / `PENDING_SIGNATURE_DIAG …` |
| 341 | ezkey-auth-api-1 | `m.m.a.RequestResponseBodyMethodProcessor` | DEBUG | `Read "application/json;charset=UTF-8" to [AuthAttemptPendingRequestDto[enrollmentId=…` *(kv-secret on proof-token fields)* |
| 342 | ezkey-auth-api-1/2 | `o.s.w.s.m.m.a.HttpEntityMethodProcessor` | DEBUG | `Writing [AuthAttemptPendingResponseDto[authAttempt…` *(kv-secret)* |

**Which profile enables DEBUG**

Quality-gate clean-start sets
`SPRING_PROFILES_ACTIVE=docker,docker-dev,docker-test` (see `scripts/quality-gate.sh` /
`ezkey-tests/clean-start.sh`).

From `ezkey-auth-api/config/application-docker-dev.properties`:

- `logging.level.org.ezkey=DEBUG` → product `PENDING_*_DIAG` in
  `AuthAttemptPendingService` (DEBUG-only; see `docs/PENDING_PAYLOAD_DIAGNOSTIC.md`)
- `logging.level.org.springframework.web=DEBUG` → Spring MVC
  `RequestResponseBodyMethodProcessor` / `HttpEntityMethodProcessor` dumps of request/response
  DTOs that contain proof tokens

Plain `docker` profile keeps `org.ezkey=INFO` and `org.springframework.web=WARN` (no these lines).

**Origin:** product DEBUG diagnostics + Spring framework DEBUG under **docker-dev** (always on
for the gate stack). Payload/signature material is protocol crypto produced by the running
product during functional/churn traffic — not test harness log injection.

## 3. Demo-device proof tokens

| Count | Service | Logger | Level | Redacted sample |
| ---: | --- | --- | --- | --- |
| 9 | ezkey-demo-device-ha | — | INFO | `enrollmentProofToken=***REDACTED***` / `deviceProofToken=***REDACTED***` / `authAttemptProofToken=***REDACTED***` *(kv-secret; value often already product-masked)* |
| 12 | ezkey-demo-device-ha | — | INFO | `authAttemptProofTokenSignedByIntegration: <TOKEN>` / `…SignedByDevice: <TOKEN>` / `deviceProofTokenSigned: …` *(opaque-token on Base64URL signatures)* |

**Origin:** **product log** of the Demo Device app (`ezkey-demo-device`) while exercising
enrollment/auth against Auth API during functional tests and churn. Values are real protocol
proof tokens / Ed25519 signatures held by the demo device — not scanner fiction. Demo Device is
test tooling relative to production adopters, but the logger is the product Demo Device process.

## Cleared by scanner fix (not for product triage)

Jar stems (`*.jar` / `~[artifact-N.N.N.jar!/`), `git: <hex>`, and uname triples
(`x86_64-pc-linux-musl`) — ~12 distinct findings on run 6 (javamelody collector, Postgres,
Spring stack traces). Fixed in `quality-gate-secrets.mjs` with negative unit tests; no allowlist
change.

## Out of scope crumbs (FYI)

- `ezkey-demo-app-acme-ha` `applyApiKey=***REDACTED***` rate-limit init line (1) — already masked value; residual kv hit.
- `AdminEnrollmentController` bearer WARN on invalid recovery-token path (1) — product WARN; value redacted in preview.
