# Quality-gate run 6 — secret RED triage (Christophe)

Gate tip that produced the findings: `fd701c41`. Counts from
`logs/quality-gate/20261011T013732Z-fd701c41/health/health.json` (final health).

**Decision (Christophe):** no allowlist entries. Scanner false positives are fixed in
`#746` pattern work. Two real product leaks stay RED in the gate (no exemption /
tracked-leak mechanism):

| Bucket | Disposition |
| --- | --- |
| §1 admin username / keyset filename opaque | Scanner FP → fixed in `#746` (AMBER username glue; ignore known filename extensions) |
| §2a `*Sha256*Hex=` digests on `PENDING_*_DIAG` | Scanner FP → fixed in `#746` |
| §2b Auth DTO `toString` + DEBUG MVC dumps | **Product leak → [#752](https://github.com/mgagp/ezkey/issues/752)** — stay RED until fixed |
| §3 Demo-device INFO DTO / proof-token logging | **Product leak → [#753](https://github.com/mgagp/ezkey/issues/753)** — stay RED until fixed |

Run 7 happens after `#752` / `#753` merge. `#746` may merge as tooling while a run report
honestly still shows those product REDs.

## 1. `admin-<epoch>` / test username opaque-token → scanner FP (fixed)

Product INFO/WARN logs that embed administrator usernames (and sometimes tenant display
names). Fixtures mint mixed alphanumeric usernames ≥20 chars (`admin-<epoch>`, …).

| Count | Service | Logger | Level | Redacted sample |
| ---: | --- | --- | --- | --- |
| 170 | ezkey-admin-api-1 | `o.ezkey.admin.service.AdminAuthService` | INFO | `… successful for admin: <TOKEN> …` / `Authentication successful for: <TOKEN>` |
| 166 | ezkey-admin-api-1/2 | `o.e.i.service.IntegrationService` | INFO | `Creating integration by admin: <TOKEN>` |
| 194 | ezkey-admin-api-1/2 | `o.e.a.service.AdminProvisioningService` | INFO | `Creating tenant admin: <TOKEN> …` |
| 12 | ezkey-admin-api-1 | `org.ezkey.security.TinkKeyManager` | INFO | `…/keysets/<TOKEN>.json.encrypted` |
| ~8 | ezkey-admin-api-1/2 | ApiKey controllers | INFO/WARN | `… for admin: <TOKEN>` |

**Fix in gate:** opaque after `for admin: ` / `by admin: ` / `tenant admin: ` /
`successful for: ` → AMBER `opaque-username`; stems followed by known extensions
(`.json.encrypted`, …) ignored. Positives kept RED: `proof=<token> for admin: alice`,
`/t/<hex32>`, `token=<32>`.

## 2. Auth DEBUG `PENDING_*_DIAG` and Spring MVC DTO dumps

### 2a. Digest fields (scanner FP — fixed)

`payloadSha256Utf8Hex=` / `signatureSha256Utf8Hex=` / etc. on `PENDING_*_DIAG` lines —
opaque lookalikes of hex digests. Gate now skips `\w*Sha256\w*Hex=` values (and the field
name). `proofToken=<same hex>` stays RED.

### 2b. DTO dumps → [#752](https://github.com/mgagp/ezkey/issues/752)

| Count | Service | Logger | Level | Redacted sample |
| ---: | --- | --- | --- | --- |
| 1026 | ezkey-auth-api-1/2 | `o.e.a.service.AuthAttemptPendingService` | DEBUG | `PENDING_PAYLOAD_DIAG …` / `PENDING_SIGNATURE_DIAG …` *(non-digest payload material may still RED)* |
| 341 | ezkey-auth-api-1 | `m.m.a.RequestResponseBodyMethodProcessor` | DEBUG | `Read "application/json…" to [AuthAttemptPendingRequestDto[…` |
| 342 | ezkey-auth-api-1/2 | `o.s.w.s.m.m.a.HttpEntityMethodProcessor` | DEBUG | `Writing [AuthAttemptPendingResponseDto[…` |

**Profile:** quality-gate uses `SPRING_PROFILES_ACTIVE=docker,docker-dev,docker-test`.
`application-docker-dev.properties` sets `logging.level.org.ezkey=DEBUG` and
`logging.level.org.springframework.web=DEBUG`.

**Origin:** product DEBUG + Spring MVC DEBUG under **docker-dev**. Real protocol material in
DTO `toString` / request-response dumps. **Stay RED** in the gate until `#752` lands — no
allowlist.

## 3. Demo-device proof tokens → [#753](https://github.com/mgagp/ezkey/issues/753)

| Count | Service | Logger | Level | Redacted sample |
| ---: | --- | --- | --- | --- |
| 9 | ezkey-demo-device-ha | — | INFO | `enrollmentProofToken=***REDACTED***` / `deviceProofToken=…` / `authAttemptProofToken=…` |
| 12 | ezkey-demo-device-ha | — | INFO | `authAttemptProofTokenSignedByIntegration: <TOKEN>` / device-signed proofs |

**Origin:** Demo Device product INFO logging of enrollment/auth DTOs during functional tests
and churn. **Stay RED** until `#753` lands — no allowlist.

## Cleared by scanner fix (not product issues)

- Jar stems, word-boundary `git:` hex, real uname triples (`x86_64-pc-linux-musl`)
- Tightening: `legit: <hex>` and `x86_64-<opaque>` stay RED; Bearer is case-sensitive
  `Bearer` + 8+ token chars (prose `got Bearer token` is not RED)

## Out of scope crumbs

- `ezkey-demo-app-acme-ha` `applyApiKey=***REDACTED***` rate-limit init (1)
- `AdminEnrollmentController` “got Bearer token” prose — scanner FP fixed (Bearer rule)
