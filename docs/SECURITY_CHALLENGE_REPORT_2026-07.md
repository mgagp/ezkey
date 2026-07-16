# Ezkey Security Challenge Report
**Java, Admin UI, deployment, and dependency review — July 2026**

> **Posture:** Defensive pentest-style challenge using OWASP and OWASP API Security reasoning.
> **Scope:** Java APIs and core services, Admin UI trust boundary, deployment configuration, and
> production dependency exposure.
> **Method:** Static source review and software-composition analysis. No intrusive exploitation was
> performed.
> **Review date:** 2026-07-15.

---

## 1. Executive summary

Ezkey's security baseline remains credible. The previous June challenge produced material
improvements, and this pass did not find a regression in the remediated SEC-001 through SEC-012
controls.

This new pass found three high-priority authorization defects:

- **SEC-017 (HIGH): Tenant Admins can manage instance-wide encryption keys.** Every authenticated
  administrator receives `ROLE_ADMIN`, and all encryption-key endpoints authorize that generic
  role. A Tenant Admin can therefore bypass the Admin UI's Global-Admin-only visibility and call
  the API directly to list keys, rotate the primary key, resume re-encryption batches, or trigger
  global re-encryption.
- **SEC-021 (HIGH): Recovery tokens are accepted as full administrator sessions.** Recovery issues
  a normal active `AdminToken`; the shared authentication filter grants its administrator roles on
  every authenticated route. The documented reset-only permission boundary is not enforced.
- **SEC-022 (HIGH): Tenant Admins can revoke API keys across tenants.** The revoke path updates by
  `keyId` without checking ownership of the key's integration.

Several medium findings reinforce those primary defects:

- **SEC-018 (MEDIUM): Sensitive key operations are not reliably attributed to the acting
  administrator.** Several re-encryption audit rows hard-code `127.0.0.1`, and the controller does
  not attach the authenticated admin ID to these events.
- **SEC-023 (MEDIUM): API key detail and per-integration list endpoints lack tenant scope checks.**
- **SEC-024 (MEDIUM): Recovery failures disclose whether an administrator exists and has recovery
  codes.** Closed — generic client message (SEC-006 pattern); distinct reasons in audit/logs only.
- **SEC-025 (MEDIUM): API-key callers receive an instance-wide pending authentication count.**
- **SEC-026 and SEC-027 (MEDIUM):** recovery state survives login/logout, and cookie-mode logout
  presents local success even when server-side revocation failed.

The dependency review found current security advisories in the Maven graph. Most have
configuration-specific preconditions that are not present in the reviewed Ezkey source, but the
PostgreSQL JDBC driver finding is relevant to deployments that require TLS channel binding:

- **SEC-019 (MEDIUM, conditional): Backend security patch lag.** `postgresql:42.7.11` is affected by
  CVE-2026-54291 when `channelBinding=require`; `42.7.12` fixes it. Other reported Logback, Jackson,
  and Tomcat advisories are not currently reachable through the reviewed Ezkey configuration, but
  should still be cleared through a controlled dependency update.

No high-severity production dependency issue was reported for the Admin UI. The UI also has strong
baseline controls: no unsafe HTML rendering was found, production Caddy enforces a strict CSP, the
recommended split deployment uses an HttpOnly cookie, and cookie-authenticated unsafe requests use
a session-bound CSRF token.

### Current priority

| Priority | ID | Finding | Disposition |
| --- | --- | --- | --- |
| P0 | SEC-021 | Recovery token receives full administrator authority | Closed — PR #359 |
| P0 | SEC-022 | Cross-tenant API key revocation | Closed — PR #363 |
| P0 | SEC-017 | Tenant Admin can operate global encryption keys | Closed — Global Admin only on `/api/v1/encryption-keys/**` |
| P1 | SEC-023 | Cross-tenant API key metadata reads | Closed — PR #363 |
| P1 | SEC-024 | Recovery username/account-state enumeration | Closed — anti-enumeration (PR TBD) |
| P1 | SEC-025 | API-key pending count is instance-wide | Scope or deny |
| P1 | SEC-018 | Encryption-key audit attribution gap | Closed — with SEC-017 (manual ops: adminId + ClientContext; resume audited) |
| P1 | SEC-026–027 | Admin UI recovery/logout session lifecycle | Focused UI/API boundary PR |
| P2 | SEC-019 | Conditional backend dependency advisories | Focused dependency PR |
| P2 | SEC-020 | Native Auth API exposes metrics/info | Harden outside local QA |

There is no evidence supporting a new critical-severity finding in this pass.

---

## 2. Relationship to the June 2026 challenge

The June report remains the historical record for SEC-001 through SEC-016:

- SEC-001 through SEC-012 and the encryption read-path follow-up remain closed.
- SEC-013 through SEC-016 remain optional low-priority hardening.
- This report starts at SEC-017 and does not reinterpret the old historical backlog as open work.

Reference:
[`SECURITY_CHALLENGE_REPORT_2026-06.md`](SECURITY_CHALLENGE_REPORT_2026-06.md).

---

## 3. Findings

### SEC-017 — Tenant Admin can manage instance-wide encryption keys

**Severity:** HIGH  
**Confidence:** High — confirmed by static authorization flow  
**OWASP:** A01 Broken Access Control  
**OWASP API:** API5 Broken Function Level Authorization  
**CWE:** CWE-285 Improper Authorization

#### Evidence

`AdminTokenAuthenticationFilter` grants every authenticated administrator the generic
`ROLE_ADMIN`, then adds the type-specific authority:

- `ezkey-admin-api/src/main/java/org/ezkey/admin/security/AdminTokenAuthenticationFilter.java`
  lines 135–143.

`EncryptionKeyController` protects all read and write endpoints with only
`@PreAuthorize("hasRole('ADMIN')")`:

- list keys: line 153;
- get primary: line 194;
- get key: line 217;
- rotate: line 253;
- list batches: line 340;
- resume batch: line 426;
- trigger full re-encryption: line 470;
- trigger key-specific re-encryption: line 553;
- create batches: line 644.

The product trust model says these are Global Admin responsibilities:

- `PRD.md` lines 43–46;
- `docs/LIFECYCLE_GOVERNANCE.md` lines 293–299;
- `ezkey-admin-ui/src/components/layout/sidebar.tsx` lines 44–55 hides the encryption-key route
  from Tenant Admins.

The UI restriction is presentation only and does not compensate for backend authorization.
Moreover, `ezkey-admin-ui/src/routes.tsx` lines 183–190 protects `/encryption-keys` with the normal
authenticated `ProtectedRoute` but has no role-specific route guard. A Tenant Admin who navigates
directly to the route can therefore reach the page shell as well as call the backend directly.

#### Abuse path

1. An attacker obtains or legitimately holds a Tenant Admin session.
2. The attacker calls `/api/v1/encryption-keys/**` directly.
3. Spring grants access because the session includes `ROLE_ADMIN`.
4. The controller executes instance-wide key rotation or re-encryption operations.

No cross-tenant identifier is required because encryption keys are global platform assets.

#### Impact

- Unauthorized visibility into global key lifecycle and migration state.
- Unauthorized key rotation affecting every tenant.
- Ability to enqueue expensive global re-encryption work.
- Potential availability degradation and operational confusion.
- Violation of the explicit Global Admin / Tenant Admin trust split.

The finding is HIGH rather than CRITICAL because it requires an already authenticated Tenant Admin,
does not expose raw Tink key material, and existing key-state guards limit some repeated operations.

#### Recommended remediation

1. Require `ROLE_GLOBAL_ADMIN` at controller class level or on every encryption-key endpoint.
2. Remove generic `ROLE_ADMIN` annotations from this controller to avoid mixed policy.
3. Add controller integration tests proving:
   - Global Admin receives the expected success response;
   - Tenant Admin receives 403 for every read and write route;
   - API-key authentication receives 403.
4. Update endpoint documentation from generic “ADMIN role” to “Global Admin only.”
5. Review other platform-global controllers using the same role vocabulary; alerts and audit-chain
   integrity endpoints already use the narrower role and provide a useful pattern.

**Status (2026-07-15):** Closed — class-level `@PreAuthorize("hasRole('GLOBAL_ADMIN')")` on
`EncryptionKeyController`; WebMvc negative tests for Tenant Admin / `ROLE_ADMIN` / API key; docs
updated to Global Admin only.

---

### SEC-018 — Encryption-key operations lack reliable actor and source attribution

**Severity:** MEDIUM  
**Confidence:** High — confirmed by static audit construction  
**OWASP:** A09 Security Logging and Monitoring Failures  
**CWE:** CWE-223 Omission of Security-relevant Information

#### Evidence

`EncryptionKeyController` does not accept `Authentication` or extract an `AdminPrincipal` for its
mutating operations. Consequently, its audit rows do not set the acting admin ID.

For re-encryption operations, the controller also records a synthetic loopback source instead of
the request source:

- `EncryptionKeyController.java` lines 484, 515, 568, 610, 666, and 682 use
  `.ipAddress("127.0.0.1")`.

Manual rotation uses `ClientContext` for the source IP, but still does not record the actor:

- `EncryptionKeyController.java` lines 255–272.

The generic rotation error path also omits actor identity (`EncryptionKeyController.java` lines
297–308). The batch-resume operation at lines 426–448 mutates execution state but writes no audit
event at all.

This is especially important because SEC-017 currently lets a tenant-scoped actor reach these
operations.

#### Impact

- Incident responders cannot reliably identify who initiated a key operation.
- Multiple remote operations appear to originate from localhost.
- Audit-chain integrity can prove that the row was not modified later, but cannot restore actor
  data that was never captured.
- Accountability for one of the platform's most sensitive administrative surfaces is weaker than
  for normal tenant and admin lifecycle actions.

#### Recommended remediation

1. Accept `Authentication` and `HttpServletRequest` on every mutating key endpoint.
2. Extract the authenticated `AdminPrincipal` and set `adminId`.
3. Use `ClientContext.from(request)` for IP and user agent on all manual operations.
4. Keep system-scheduled key operations distinct with an explicit system actor rather than
   overloading loopback IP as identity.
5. Add audit tests for actor ID, source IP, user agent, operation ID, success, rejection, and
   enqueue failure.

Do not let the broader audit cleanup delay the SEC-017 authorization fix.

**Status (2026-07-15):** Closed with SEC-017 — manual controller mutations attach `adminId` and
`ClientContext` (IP / user-agent); `resumeBatch` now audits; generic rotate error path attributes
the actor. Scheduler / core-service jobs remain explicit system actors via `triggeredBy` /
`createdBy` (`SYSTEM`); loopback IP there is in-process, not a remote operator claim.

---

### SEC-019 — Conditional backend dependency advisories

**Severity:** MEDIUM remediation priority; current exploitability varies  
**Confidence:** High for affected versions, conditional for Ezkey exposure  
**OWASP:** A06 Vulnerable and Outdated Components

#### SCA result

A Snyk Maven aggregate scan reported 44 module-level occurrences that reduce to these distinct
runtime concerns:

| Component | Current | Advisory | Fixed | Ezkey applicability |
| --- | --- | --- | --- | --- |
| PostgreSQL JDBC | 42.7.11 | CVE-2026-54291 | 42.7.12 | Conditional: only `channelBinding=require`; relevant to hardened remote-DB deployments |
| Logback Core | 1.5.34 | CVE-2026-13006 | 1.5.36 or later per Snyk | Preconditions not found: requires Janino conditional config plus config/env control |
| Jackson Databind 2.x / 3.x | 2.21.4 / 3.1.4 | CVE-2026-59889 | 2.21.5 / 3.1.5 | Vulnerable pattern not found: no `@JsonView` or `@JsonUnwrapped` usage |
| Tomcat Embed Core | 11.0.22 | CVE-2026-55955 | 11.0.23 | Vulnerable cluster `EncryptInterceptor` not configured |
| Tomcat Embed Core | 11.0.22 | CVE-2026-53434 | 11.0.23 | Vulnerable FFM connector/CRL configuration not found |

The Admin UI production dependency scan reported zero high-or-critical issues.

#### PostgreSQL-specific risk

CVE-2026-54291 can silently downgrade `SCRAM-SHA-256-PLUS` to plain `SCRAM-SHA-256` when
`channelBinding=require` and the server certificate algorithm cannot produce the expected channel
binding hash. Ezkey's default local Docker connection does not use this posture, but a production
operator may reasonably enable it for a remote PostgreSQL service.

#### Recommended remediation

1. Upgrade PostgreSQL JDBC to 42.7.12 or later through the normal Maven dependency-management path.
2. Upgrade the Spring Boot dependency set or apply narrow managed overrides that clear the
   Logback, Jackson, and Tomcat advisories without mixing unrelated framework changes.
3. Run the canonical Java baseline and clean-start functional tests after the update.
4. Add dependency scanning to the release-preparation checklist; do not treat raw scanner severity
   as product severity without checking preconditions.

#### Tooling limitation

Snyk Code SAST could not run because Snyk Code is not enabled for the configured organization.
This report does not silently substitute SCA results for source-code SAST. The manual source review
and repository-specific static analysis remain the evidence for the source-level findings.

---

### SEC-020 — Native Auth API exposes metrics and info without authentication

**Severity:** LOW  
**Confidence:** High for the native-compose topology  
**OWASP:** A05 Security Misconfiguration  
**CWE:** CWE-200 Exposure of Sensitive Information

#### Evidence

- `ezkey-auth-api/src/main/java/org/ezkey/auth/config/SecurityConfig.java` lines 65–70 permits all
  requests.
- `ezkey-auth-api/src/main/resources/application-native.properties` lines 37–42 exposes
  `health,metrics,info`.
- `docker/docker-compose.native.yml` publishes management port `8085` to the host.

The normal Docker profile exposes health only, so this is topology-specific rather than a default
release-wide defect.

#### Impact

Unauthenticated callers who can reach the native management port can enumerate runtime metrics and
application information. This is reconnaissance value, not a direct authentication bypass.

#### Recommended remediation

- Expose only `health` by default.
- Keep metrics on a non-published internal management network, or protect them with an explicit
  monitoring authentication boundary.
- Add a deployment test asserting that `/actuator/metrics` is unavailable from the public edge.

---

### SEC-021 — Recovery token is accepted as a full administrator session

**Severity:** HIGH  
**Confidence:** High — confirmed by the complete token issuance and authentication path  
**OWASP:** A01 Broken Access Control; A07 Identification and Authentication Failures  
**OWASP API:** API2 Broken Authentication; API5 Broken Function Level Authorization  
**CWE:** CWE-285 Improper Authorization

#### Evidence

`AdminRecoveryService.validateRecoveryCode()` documents a limited enrollment-reset token but stores
it as a normal active `AdminToken`:

- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminRecoveryService.java` lines 208–223.

`AdminTokenAuthenticationFilter` accepts any active token returned by
`AdminTokenValidationService`, then grants `ROLE_ADMIN` and the associated `ROLE_GLOBAL_ADMIN` or
`ROLE_TENANT_ADMIN`:

- `AdminTokenAuthenticationFilter.java` lines 87–148.

Neither the entity nor the validation path carries or enforces a token-purpose discriminator.
Only `AdminEnrollmentController.resetEnrollment()` performs a recovery-prefix check. Other
authenticated controllers cannot distinguish a recovery token from a normal passwordless session.

#### Abuse path

1. An attacker obtains a valid recovery token after recovery-code consumption or from browser
   storage, logs, interception, or an unattended recovery session.
2. The attacker supplies it as `Authorization: Bearer ezkey_recovery_...` to an ordinary Admin API
   route.
3. The shared filter creates a full admin security context.
4. For a Global Admin recovery token, the caller receives platform-wide authority for the token's
   configured lifetime.

#### Impact

The documented break-glass funnel is not technically constrained. A credential intended only to
reset one enrollment can manage tenants, administrators, integrations, enrollments, API keys,
audit data, and—until SEC-017 is fixed—encryption keys.

#### Recommended remediation

1. Add an explicit token purpose (`SESSION`, `RECOVERY`) to `AdminToken`, or store recovery grants
   in a dedicated persistence/authentication path.
2. Reject recovery-purpose tokens everywhere except a strict allowlist containing enrollment reset
   and, optionally, logout.
3. Do not rely on the plaintext prefix as the sole durable authorization model.
4. Add integration tests proving a recovery token:
   - succeeds on enrollment reset for its own enrollment;
   - receives 403 on representative read and write Admin API routes;
   - cannot inherit Global Admin or Tenant Admin business authorities.

---

### SEC-022 — Cross-tenant API key revocation

**Severity:** HIGH  
**Confidence:** High — confirmed object-level authorization omission  
**OWASP:** A01 Broken Access Control  
**OWASP API:** API1 Broken Object Level Authorization  
**CWE:** CWE-639 Authorization Bypass Through User-Controlled Key

#### Evidence

`ApiKeyController.revokeApiKey()` accepts a `keyId`, resolves the current administrator, and calls
`apiKeyService.revokeApiKey(keyId, currentAdmin)` without checking whether the actor can access the
key's integration:

- `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/ApiKeyController.java` lines 706–763.

The service performs a direct update by key ID:

- `ezkey-core/src/main/java/org/ezkey/integration/service/ApiKeyService.java` lines 460–474.

The update endpoint demonstrates the expected control by loading the key and calling
`accessControlService.canAccessIntegration(...)` at `ApiKeyController.java` lines 547–566.

#### Impact

A Tenant Admin who knows or guesses another tenant's API key ID can revoke that machine credential,
causing immediate loss of Integration API access for the victim tenant. The operation is
irreversible and audit attribution to the attacking admin does not compensate for the missing
authorization.

#### Recommended remediation

- Load the key before mutation and enforce integration access using the same pattern as update.
- Prefer a not-found-equivalent response for inaccessible keys if enumeration resistance is
  desired consistently.
- Add a cross-tenant functional test: Tenant A deleting Tenant B's key returns 403 or 404 and the
  key remains active.

---

### SEC-023 — Cross-tenant API key metadata reads

**Severity:** MEDIUM  
**Confidence:** High  
**OWASP:** A01 Broken Access Control  
**OWASP API:** API1 Broken Object Level Authorization; API3 Broken Object Property Level
Authorization  
**CWE:** CWE-639 Authorization Bypass Through User-Controlled Key

#### Evidence

Two authenticated endpoints query caller-supplied identifiers without tenant authorization:

- `GET /api/v1/api-keys/integration/{integrationId}` builds a repository specification directly
  from the path ID (`ApiKeyController.java` lines 439–491).
- `GET /api/v1/api-keys/{keyId}` loads and returns the key directly
  (`ApiKeyController.java` lines 640–671).

The general list endpoint and update path already contain tenant-aware patterns, so this is a
localized consistency defect rather than a missing access-control architecture.

#### Impact

Cross-tenant disclosure includes integration key identifiers, descriptions, IP allowlists,
expiration, revocation, and last-use metadata. Secret keys are not returned.

#### Recommended remediation

- Scope per-integration lists through `AccessControlService` before querying.
- Load and authorize the parent integration before returning key details.
- Add negative tests for both foreign `integrationId` and foreign `keyId`.

---

### SEC-024 — Recovery endpoint discloses username and recovery state

**Severity:** MEDIUM  
**Confidence:** High  
**OWASP:** A07 Identification and Authentication Failures  
**OWASP API:** API2 Broken Authentication  
**CWE:** CWE-204 Observable Response Discrepancy  
**Status:** Closed — generic client failure message (SEC-006 pattern); distinct reasons in audit/logs
only. PR TBD.

#### Evidence

`AdminRecoveryService.validateRecoveryCode()` previously produced distinguishable failures:

- unknown username: `Invalid credentials`;
- inactive account: `Account is inactive`;
- no recovery codes: `No recovery codes available for this account`;
- wrong recovery code: `Invalid recovery code`.

Evidence: `AdminRecoveryService.java` (pre-fix). `AdminAuthController` returned the exception
message to the unauthenticated caller instead of applying the generic login-failure policy.

#### Impact

An unauthenticated caller within rate limits can identify valid administrator usernames and profile
whether accounts are active and recovery-enabled. This reintroduces the class of account
enumeration already corrected for normal login by SEC-006.

#### Recommended remediation

- Return one status and one generic client-safe message for all pre-authentication recovery
  failures.
- Preserve the internal reason only in structured audit logs.
- Add response-equivalence tests for unknown, inactive, depleted, and wrong-code cases.

---

### SEC-025 — API-key pending count is instance-wide

**Severity:** MEDIUM  
**Confidence:** High  
**OWASP:** A01 Broken Access Control  
**OWASP API:** API1 Broken Object Level Authorization; API3 Excessive Data Exposure  
**CWE:** CWE-200 Exposure of Sensitive Information

#### Evidence

`GET /api/v1/auth-attempts/pending-count` allows `ROLE_API_KEY` and scopes only through
`extractTenantId(authentication)`:

- `AuthAttemptController.java` lines 259–275.

`extractTenantId()` returns a tenant only for `AdminPrincipal` and returns `null` for the Integration
API-key principal:

- `AuthAttemptController.java` lines 940–950.

The resulting filter treats the API-key request like a Global Admin request and returns the
instance-wide pending count.

#### Impact

An integration credential can observe cross-tenant MFA activity volume and timing. No individual
attempt details are exposed, but this is unnecessary operational telemetry outside the
integration's scope.

#### Recommended remediation

- Deny API keys on this dashboard endpoint, or add an integration-scoped count query using the
  integration ID carried by the API-key principal.
- Test two tenants with pending attempts and verify each key sees only its own integration count.

---

### SEC-026 — Recovery token persists across normal login and logout

**Severity:** MEDIUM  
**Confidence:** High  
**OWASP:** A07 Identification and Authentication Failures  
**CWE:** CWE-922 Insecure Storage of Sensitive Information

#### Evidence

The Admin UI stores the recovery token under `ezkey_admin_recovery` in `sessionStorage`:

- `ezkey-admin-ui/src/lib/recovery-session.ts` lines 17–48.

`AuthProvider.login()` and `AuthProvider.logout()` manage the normal session, query cache, and
integrity-investigation state but never clear recovery state:

- `ezkey-admin-ui/src/context/auth-context.tsx` lines 54–64.

The login page restores the unfinished recovery flow when that state exists. SEC-021 increases the
impact because the retained token currently works as a full administrator bearer.

#### Impact

On a shared browser profile or unattended tab, a later user can recover the still-valid token and
resume the recovery funnel—or call ordinary Admin API routes while SEC-021 remains open.

#### Recommended remediation

- Clear recovery state on successful normal login, explicit logout, recovery completion, recovery
  cancellation, and session invalidation.
- Add lifecycle tests covering recover → normal login → logout → reload.

---

### SEC-027 — Cookie-mode logout reports local success when server revocation fails

**Severity:** MEDIUM  
**Confidence:** High for configured HttpOnly-cookie mode  
**OWASP:** A07 Identification and Authentication Failures  
**CWE:** CWE-613 Insufficient Session Expiration

#### Evidence

`HeaderLogoutButton` swallows any logout API error and always clears local state and navigates to
login:

- `ezkey-admin-ui/src/components/layout/header-logout-button.tsx` lines 17–24.

In cookie mode, JavaScript cannot delete the HttpOnly session cookie. If the logout request fails,
the server token and cookie remain valid. On a subsequent page load, `AuthProvider` calls
`GET /api/v1/admin/auth/me` and restores that session:

- `ezkey-admin-ui/src/context/auth-context.tsx` lines 20–48.

#### Impact

The operator sees a completed logout even though the browser still holds an authenticated session.
Reloading or reopening the application can silently restore access.

#### Recommended remediation

- Do not present logout as complete until server revocation succeeds in cookie mode.
- Surface a retryable error and retain enough local state to retry safely.
- Add a cookie-mode browser test where logout returns 500 and verify the application does not claim
  successful termination.

---

## 4. Admin UI security assessment

The UI's rendering and browser-header posture is strong, but SEC-026 and SEC-027 identify two
confirmed session-lifecycle defects at the Admin UI / Admin API boundary.

### Strong controls observed

- No `dangerouslySetInnerHTML`, raw `innerHTML`, `eval`, or dynamic function construction was found
  in application source.
- Production Caddy applies:
  - `script-src 'self'`;
  - `frame-ancestors 'none'`;
  - `base-uri 'self'`;
  - `form-action 'self'`;
  - `X-Frame-Options: DENY`;
  - `X-Content-Type-Options: nosniff`.
- The recommended split deployment keeps the opaque admin token in an HttpOnly, Secure,
  host-only cookie.
- Cookie-authenticated unsafe requests require a CSRF token cryptographically bound to the session
  token.
- CORS uses explicit origins rather than origin patterns.
- Query cache is cleared on logout, reducing cross-session data residue.
- Demo-mode code has a production stripping and verification path.
- The runtime Admin UI container uses a non-root user.

### Accepted residual posture

- Local/default Mode A stores a bearer token in `sessionStorage`; an origin-level XSS could read it.
  This is documented and is not a new finding. Mode B plus CSP is the recommended production
  posture.
- `aboutUrl` comes from public instance configuration and is used as an external link. Production
  CSP and React URL handling provide mitigation, but validating the configured scheme as
  `https:` (and optionally `http:` for local development) would be inexpensive defense in depth.
  This is not promoted to a numbered finding without a demonstrated executable path.

### Browser-test judgment

No browser test was run because this pass changed no behavior and no live stack was running.
Follow-up implementation should add one focused cookie-mode logout failure scenario for SEC-027
and lifecycle-level unit/integration coverage for SEC-026. A role-visibility regression for
SEC-017 is useful, but backend 403 tests—not Playwright—must prove that security boundary.

---

## 5. Deployment boundary assessment

The production-oriented Docker profile and EXP1 edge have meaningful controls: required Tink and
audit integrity, health-only management exposure, TLS 1.3 at the EXP1 edge, blocked public Swagger
on EXP1, loopback-bound PostgreSQL, and non-root Java containers.

The standard local stack intentionally optimizes demonstration and test productivity. It must not
be mistaken for a production baseline:

- the unauthenticated Crypto API is published on port 9090 and provides signing, encryption, and
  decryption utilities;
- default `clean-start` includes `docker-test`, which disables several rate limiters;
- PostgreSQL is published as `5432:5432` with development credentials;
- API and management ports remain directly published alongside Caddy, allowing proxy bypass;
- full bootstrap mode can write enrollment credentials to logs and a shared Docker volume;
- Swagger/OpenAPI remains public on direct Docker API ports.

These are not promoted into separate product-vulnerability IDs because the repository documents
them as local/demo tooling and provides `--prod-safe` plus an EXP1-specific edge posture. They are,
however, a material deployment trap. Release and operator guidance should make the boundary
unmistakable:

1. use `./clean-start.sh --prod-safe --with-proxy` for security validation;
2. never deploy Crypto API, Demo Device, or bootstrap-init on a production-facing host;
3. remove direct API/management host mappings in production overlays;
4. use `recovery_primary` bootstrap output outside isolated local development;
5. disable demo MITM behavior and replace example database credentials before shared deployment;
6. expose Swagger only through an explicit non-production profile.

---

## 6. Cryptographic and protocol assessment

The reviewed protocol posture remains strong:

- Enrollment bind and verify retain explicit state and signature checks.
- Pending and respond remain proof-token-bound.
- Device proof tokens are hash-only at rest.
- ECDSA high-S signatures are rejected.
- Tink required-mode and encrypted read-path controls remain represented in code.
- The Admin browser CSRF token uses constant-time comparison.
- API-key secrets and proof material use high-entropy generation.

No new evidence-backed cryptographic bypass was identified. This does not constitute formal
verification of the protocol; it means this static challenge did not find a new practical bypass
worthy of the active backlog.

Three lower-confidence or defense-in-depth candidates merit targeted characterization before
promotion:

- concurrent device responses do not visibly use an atomic `READ → terminal` compare-and-set;
- integration-signature self-verification failures are logged but do not fail the server response;
- when `encryption.required=false`, a missing keyset file can auto-generate a new keyset and make
  existing ciphertext unreadable.

The normal Docker/EXP1 posture already mitigates the last item with `encryption.required=true`.
None of these candidates is presented as a demonstrated authentication bypass.

---

## 7. Prioritized remediation plan

### Immediate

1. **SEC-021:** Closed — PR #359 (recovery token purpose boundary).
2. **SEC-022:** Closed — PR #363 (API key object authorization).
3. **SEC-017:** Closed — Global Admin only on all encryption-key routes.
4. **SEC-018:** Closed with SEC-017 — actor and client context on manual key operations.

### Next authorization and authentication corrections

5. **SEC-023:** Closed — PR #363 (scoped with SEC-022).
6. **SEC-024:** Closed — recovery failure responses normalized (generic client message; distinct
   reasons in audit/logs only). PR TBD.
7. **SEC-025:** scope or deny API-key access to pending count.

### Admin UI session lifecycle

9. **SEC-026:** clear recovery state on login, logout, completion, cancellation, and invalidation.
10. **SEC-027:** make cookie-mode logout failure explicit and retryable.

### Dependency maintenance

11. **SEC-019:** update PostgreSQL JDBC first, then clear applicable BOM advisories.
12. Re-run Maven SCA and preserve a deduplicated advisory/precondition summary.

### Deployment hardening

13. **SEC-020:** stop publishing unauthenticated metrics/info in the native Auth API topology.
14. Tighten the documented dev/demo versus production boundary described in section 5.

SEC-021, SEC-022, and SEC-017 are program-level security corrections. SEC-019, SEC-020, and the
deployment checklist are bounded maintenance/hardening work and should not be inflated into
unrelated architectural projects.

---

## 8. Recommended validation evidence

### Authorization

- Recovery token:
  - enrollment reset succeeds for its own administrator;
  - ordinary Admin API reads and writes return 403.
- Tenant A:
  - cannot get, list, or revoke Tenant B's API keys;
  - Tenant B's key remains active after the rejected request.
- Tenant Admin:
  - all `GET /api/v1/encryption-keys/**` routes return 403;
  - all `POST /api/v1/encryption-keys/**` routes return 403.
- Global Admin:
  - representative read and write routes pass authorization and preserve existing domain guards.
- API key:
  - encryption-key routes return 403;
  - pending count is denied or integration-scoped.

### Authentication and UI session lifecycle

- Recovery returns the same public response for unknown user, inactive user, depleted codes, and
  wrong code.
- Normal login/logout and recovery completion remove `ezkey_admin_recovery`.
- Cookie-mode logout returning 500 does not claim success or silently restore a session later.

### Audit

- Manual rotation and re-encryption events contain:
  - acting `adminId`;
  - real resolved client IP;
  - user agent;
  - target key or batch identifier;
  - outcome and safe error summary.
- Scheduled operations use an explicit system actor.

### Dependencies

- `./scripts/build.sh` passes.
- Clean-start health checks pass.
- Security SCA no longer reports the remediated versions.
- Runtime configuration confirms whether production PostgreSQL uses
  `channelBinding=require`.

### Optional bounded runtime pass

The repository's `security-pentest-curated` runner can add Schemathesis, first-party Nuclei, and
ZAP baseline evidence against a local `--prod-safe --with-proxy` stack. That is a useful follow-up
for HTTP behavior and headers, but it does not replace the direct role-based authorization tests
required for SEC-017, SEC-021, SEC-022, SEC-023, and SEC-025.

---

## 9. OWASP summary

| Category | Findings | Maximum severity |
| --- | --- | --- |
| A01 Broken Access Control | SEC-017, SEC-021, SEC-022, SEC-023, SEC-025 | HIGH |
| A05 Security Misconfiguration | SEC-020 | LOW |
| A06 Vulnerable and Outdated Components | SEC-019 | MEDIUM, conditional |
| A07 Identification and Authentication Failures | SEC-021, SEC-024, SEC-026, SEC-027 | HIGH |
| A09 Security Logging and Monitoring Failures | SEC-018 | MEDIUM |

---

## 10. Scope and limitations

- Static source and configuration review; no destructive or intrusive action.
- Maven and Admin UI production dependencies scanned with Snyk SCA.
- Snyk Code SAST unavailable for the configured organization.
- No live stack was running, so the bounded runtime pentest runner was not executed.
- Mobile was not a primary scope in this pass; the existing mobile security assessment remains the
  dedicated source for that surface.
- Findings are ranked using Ezkey-specific exploitability and blast radius, not scanner labels
  alone.

---

*Security Challenge, July 2026. Defensive review for Ezkey product credibility and release
hardening. No active exploitation performed.*
