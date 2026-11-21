# Ezkey Demo Device — Agent Notes

Spring Boot app simulating a mobile device for Auth API enrollment and MFA flows. Calls Auth API via **WebClient**; persists enrollments as JSON files under `data/enrollments/`.

Monorepo copy lives under `ezkey/ezkey-demo-device/`; public standalone mirror: `mgagp/ezkey-demo-device` (sync Java `src/`, `openapi-spec.json`, `pom.xml`, `AGENTS.md` after monorepo changes).

## Home screen tenant grouping

Bind response `tenantId` / `tenantName` / `tenantDescription` are persisted on `EnrollmentStoreService.Record`. The Ezkey app home groups enrollments via `EnrollmentTenantGrouper` into `tenantGroups` (header + list per tenant). Use `EnrollmentTenantGrouper.normalizeTenantName`: null `tenantId` / absent name → **Platform**. Do not invent “Unknown tenant”. Prefer that helper over ad-hoc sorting in the controller or template.

## Claimed pending session expiry (#895 PR-C)

Auth API pending is read-once (`PENDING` → `READ`). After a successful claim, `ClaimedPendingAuth` is stored in the HTTP session so a second GET can re-show Approve/Deny without calling pending again (204).

**Claim `expiresAt` resolution** (`ClaimExpiresAtResolver`):

1. Prefer server `expiresAt` from the pending JSON when present (`AuthApiService` keeps the raw field even if the generated DTO omits it).
2. Otherwise fall back to `claimedAt + ezkey.demo-device.claimed-pending-attempt-ttl` (default **120s**, aligned with `ezkey.core.auth-attempt.ttl-seconds`).

Auth API pending currently omits expiry on the wire (#834 — countdown stays on the integrating app). The local fallback can drift: e.g. PAM `ttlSeconds` 90 with a 120 s fallback leaves a stale Approve/Deny visible for ~30 s after server expiry. Prefer aligning the property with the initiator TTL, or rely on respond **409** as the fail-closed net. `PendingAuthClaimSession.findOpen` invalidates at `min(expiresAt, claimedAt + 10 min)`.

On `POST …/auth/respond`, HTTP **409** with ProblemDetail type ending in `auth-attempt-expired` (PR-A) shows the expired result. Other 409s (including `auth-attempt-state-conflict` or a missing type) show “no longer active”. Outcomes and copy: § *Visible parity with the mobile app*.

## Visible parity with the mobile app (#943)

Rule: at every moment an evaluator sees, the Demo Device shows the same outcome, tone, and
possible action as the mobile app (`ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md`); internals stay
simple. On-screen strings marked "mobile" in `EzkeyAppController` are the mobile app's English
strings (`ezkey_mobile/app/i18n/resources.ts`); change them together. Never show HTTP codes,
server `detail`, exception text, URLs, or hosts.

| Moment | Demo Device |
| --- | --- |
| Bind answers a key algorithm other than Ed25519 | Refused before keys are generated or saved |
| Bind / verify / pending error | Fixed message per case (429, binding failed, unreachable, …) |
| Respond 409 `auth-attempt-expired` | `expired` result, Back only |
| Respond other 409 (cancel, decided, superseded, bad signature #916) | Neutral `inactive` result, Back only; never split the causes |
| Respond 503 `integration-signing-unavailable` or 429 | Same request again with a banner (`retryMessage`); the session claim is kept |
| Respond 200, result signature missing or invalid | `unconfirmed` result (mobile Q2/Q9), Back only |
| Respond sent, then transport failure or other 5xx | `uncertain` result (mobile Q10), Back only |
| Leaving the request screen | The exit control (`demo-device-auth-back` on `auth_pending`) sends a **Deny**, then returns to the list whatever the outcome (mobile Q3); the hint line says so |

The session claim is cleared on every outcome except the retryable ones above. Agents approving an
Admin UI login must use Approve, not the exit control, on the request screen.

**Non-goals (accepted limits, reopen only with a reason):** biometrics and protected approval; a
countdown on the phone (#834 Q1); the `UncertainDecision` id lock (a server-rendered result page
is already Back only); leave-as-deny on browser Back or tab close (only the in-app exit control
declines); i18n (English only); an in-memory bind draft (the record is saved at bind, as before);
`expiresAt` on the pending wire; verifying the bind and verify-result signatures (the evaluator
does not see them; reopen if the Demo Device becomes a protocol reference).

## QR `authUrl` routing (mobile parity)

When an Admin UI enrollment QR includes `authUrl`, the demo device routes **bind**, **verify**, **pending**, and **respond** for that enrollment to that base URL. The validated URL is persisted in `EnrollmentStoreService.Record.enrollmentUrl`.

| Entry path | Auth API base used | Persisted `enrollmentUrl` |
|------------|-------------------|---------------------------|
| QR with valid `authUrl` | QR URL | QR URL |
| QR without `authUrl` (pipe format) | `ezkey.auth.api.url` | null |
| Manual ID + token only | `ezkey.auth.api.url` | null |
| Later ops (verify, auth) | stored `enrollmentUrl` or config default | unchanged |

- Client: `enrollment-qr-import.js` populates hidden form field `authApiBaseUrl`.
- Server: `EnrollmentAuthApiUrlResolver` + `AuthApiUrlValidator` (mirrors mobile `urlValidation.ts`).
- Invalid non-blank QR URL → hard error (no silent fallback to config).
- **Host allowlist** (`ezkey.demo-device.allowed-auth-api-hosts`): QR `authUrl` / bind overrides must
  target an allowed hostname. Default = host of `ezkey.auth.api.url` + `localhost` + `127.0.0.1`.
  Rejection message is constant (no echo of the submitted URL). Userinfo in the URL is rejected;
  normalized form is scheme + host + port + path only. Stored `enrollmentUrl` is revalidated on
  verify/pending/respond (`resolveForStoredEnrollment`) with the same allowlist (fail closed).
  Docker `http://auth-api:8080` is the **config default** (not an override); HTTP outside loopback
  still fails override validation — see `CONFIGURATION.md` § Docker. (Code-scanning triage
  2026-10-13, alerts #9 / #10.)

**Auth API URL defaults** (`ezkey.auth.api.url` / `EZKEY_AUTH_API_URL`):

| Mode | Default / example |
|------|-------------------|
| Docker Compose (monorepo / `application-docker.properties`) | `http://auth-api:8080` |
| Standalone against community | set `EZKEY_AUTH_API_URL=https://auth-api.ezkey.online` |

QR local enrollments override without `.env` change when that host is on the allowlist (default
includes the configured Auth API host). Public community hostnames (and names that must not be
used): [`docs/lightsail/community-host.md`](../docs/lightsail/community-host.md) § *Product
hostnames (ezkey.online)*.

## Cloudflare error bodies

When Auth is the Cloudflare hostname, HTTP errors may be **origin** RFC 9457
(`type` under `https://ezkey.io/problems/`) or **edge** Cloudflare 403/1020
(`cloudflare_error: true`). Schema-invalid probes fail at the edge; a well-formed Demo Device
request should not. Do not treat Cloudflare `detail` / `what_you_should_do` as Auth API copy.
Operator distinction: [`docs/cloudflare/auth-api-schema-validation.md`](../docs/cloudflare/auth-api-schema-validation.md)
§ Reading responses. The Demo Device is a lab UI — keep bind/pending messages actionable for the
maintainer, but do not invent a Cloudflare error catalog here.

## Jackson 3 posture (Boot 4)

Ezkey uses the **Jackson 3 engine** with **FasterXML annotations** unchanged ([JSTEP-1](https://github.com/FasterXML/jackson-future-ideas/wiki/JSTEP-1)):

| Layer | Package | Demo-device usage |
|-------|---------|-------------------|
| Engine (`ObjectMapper`, serializers) | `tools.jackson.databind` | `EnrollmentStoreService`, tests |
| Annotations (`@JsonProperty`, `@JsonInclude`, …) | `com.fasterxml.jackson.annotation` | `EnrollmentStoreService.Record`, OpenAPI-generated DTOs |

Do **not** migrate annotations to `tools.jackson.annotation` — that namespace does not exist by design.

- **No** explicit `com.fasterxml.jackson.databind` or `jackson-datatype-jsr310` dependencies in this module.
- **OpenAPI generator** (`openapi-generator-maven-plugin`, `java` + `resttemplate`): models only (`generateApis=false`, `generateSupportingFiles=false`, `addCompileSourceRoot=false`). Compile path is scoped to `generated/dto` via build-helper. DTOs use `com.fasterxml.jackson.annotation`; HTTP JSON uses Spring Boot 4 WebClient codecs (Jackson 3).
- **`openApiNullable=false`** — no `jackson-databind-nullable` dependency.

## Regression anchor

`EnrollmentStoreRecordJsonRoundtripTest` guards enrollment file JSON field naming and round-trip. Run after mapper or `Record` changes:

```bash
mvn test -pl ezkey-demo-device -Dtest=EnrollmentStoreRecordJsonRoundtripTest
```

## OpenAPI models

- Spec: Maven reads `../specs/auth-api/openapi-spec.json` in the monorepo (no local symlink/copy — #833). Standalone mirror `mgagp/ezkey-demo-device` keeps its own committed `openapi-spec.json` when synced.
- Generated package: `org.ezkey.demodevice.generated.dto`.
- Hand-written HTTP: `AuthApiService` (WebClient + generated DTOs).

## Agent-assisted Admin UI login (clean-start)

When an agent (Cursor MCP browser or similar) must approve passwordless Admin UI login for the
bootstrap Global Admin:

1. Prefer **`http://localhost:8083/phone/ezkey`** (hostname **`localhost`**, not `127.0.0.1`, when
   using the Cursor embedded browser on Windows).
2. Open the **`admin.docker`** enrollment card (`data-testid="demo-device-enrollment-link"`), then
   the auth page; reload until **Approve** / **Deny** appear while the Admin UI waiting countdown
   is still live.
3. After **Approve** and the success result, click
   `[data-testid="demo-device-back-to-enrollments"]` (primary **Back to Enrollments**). The side-exit
   control (`demo-device-auth-back`) is a secondary escape hatch — do not prefer it for the
   standard handshake.
4. Full Admin UI ↔ Demo Device recipe for agents:
   [`docs/testing/AGENT_UI_VALIDATION.md`](../docs/testing/AGENT_UI_VALIDATION.md) § *Step 4 — MCP browser*
   (Playwright mirror: `ezkey-admin-ui/e2e/support/auth-flow.ts`).

## Running

**Clean-start stack (monorepo):** demo device on http://localhost:8083, Auth API via Docker internal URL.

**Standalone Docker:** `./start.sh` — http://localhost:3080 (see standalone repo).

**Local JDK:** from module directory, `mvn test` then `mvn spring-boot:run` or run the repackaged JAR.
