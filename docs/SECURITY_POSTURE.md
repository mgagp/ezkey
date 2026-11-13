# Ezkey Security Posture — What It Does, What It Doesn't, What It Aims To Do

## Why this document exists

Ezkey is an opinionated, self-hosted cryptographic MFA platform. Trust in such a system depends on
**honest claims**, not aspirational ones. This document states plainly what the current release line
guarantees, where its limits are, and what is deliberately planned for later. It is written for a
technical evaluator deciding whether Ezkey's posture fits their context.

It is the **discoverable, plain-language bridge** between:

- the product identity in [`PROJECT_POSITIONING.md`](PROJECT_POSITIONING.md),
- the internal design analysis in
  [`../product-docs/global/integrity-assurance-honest-line.md`](../product-docs/global/integrity-assurance-honest-line.md)
  (the integrity complexity compass), and
- the Phase-2 orientation in
  [`../product-docs/global/vision/V-2026-06-28-audit-archive-export-spi.md`](../product-docs/global/vision/V-2026-06-28-audit-archive-export-spi.md).

This document intentionally does **not** live in the README or PRD: it is a focused honesty surface,
meant to be linked from trust-facing material without inflating the top-level pitch.

## Guiding stance

- **Tamper-evident, not tamper-proof.** Ezkey aims to *detect* integrity problems honestly, not to
  claim they are impossible.
- **Self-contained by design.** Core function requires **no external vendor** — no SMS provider, no
  email provider, no cloud storage provider. External capabilities arrive through optional Service
  Provider Interfaces (SPIs) implemented by connectors outside the core, whether they live in this
  repository or their own.
- **Pragmatic for SMEs.** The target operator adopts Ezkey for the security service it renders and
  does not want to spend their working life inside it. Controls are sized for that reality.

## What Ezkey does (current release line)

- **Backend-first cryptographic chain.** Enrollment (`bind`/`verify`) and authentication
  (`pending`/`respond`) are cryptographically linked; the backend is the authoritative verifier;
  one-time proof tokens and signatures bind requests to flows.
- **Respond/pending :** seules les décisions et statuts finaux sont signés par l'intégration (un 200
  non signé est refusé, le serveur ne l'émet jamais). **Decision (#844 / #833, lived loop):**
  **409** `auth-attempt-state-conflict` et **200** dont la signature résultat n'est pas vérifiable
  → état neutre Back-only (catalogue local ; pas d'effacement, deny ni retry d'approbation) ;
  **503** `integration-signing-unavailable` (clé manquante, transaction annulée — sauf FAILED
  sur un mauvais challenge : INVALID est déjà commité, un nouvel essai reçoit un 409 ; **pas de
  corps signé**) → **retryable** (exception au chemin neutre Back-only) ;
  coupure / timeout **après** que le `respond` HTTP a été envoyé → `UncertainDecision` (Q10 :
  résultat incertain, pas un échec, regarder l'écran de connexion d'origine, verrou
  `authAttemptId`, pas de leave-as-deny ni seconde décision — pas d'idempotent respond). Un MITM
  qui forge ou coupe le trafic n'obtient qu'un DoS déjà possible en coupant le réseau. Clé
  d'intégration **par enrollment** : pas de refus de démarrage JVM (#844, revue Christophe m2).
- **Decision (#844 Marc m1):** On `POST /api/v1/auth-attempts/respond`, attempt not found,
  still `PENDING`, and terminal / cancelled / superseded all map to a single client **409**
  `auth-attempt-state-conflict` so sequential auth-attempt ids do not reveal existence or state
  before the caller is verified; distinction stays in server logs only. Missing integration key
  remains **503** `integration-signing-unavailable`.
- **Decision (#912, Christophe):** Before proof of possession (invalid device signature or missing
  device public key), **no** attempt status transition and the same generic **409**
  `auth-attempt-state-conflict`. Invalid signature also emits audit
  `AUTH_ATTEMPT_RESPOND_SIGNATURE_REJECTED` (attempt id, enrollment id, IP/UA via `LogSanitizer` —
  never the signature). `markAsInvalid` remains only for post-ownership failures (e.g. wrong
  challenge) and is conditional on status still `READ` (atomic `updateStatusIfCurrent`). Sequential
  ids aggravate enumeration but are not required once an id is known.
- **Decision (ADR-0019):** The enrollment invitation (QR, deep link, paste) carries `enrollmentProofToken` and optional `authUrl`. It does not carry `enrollmentId`. Bind resolves the row by the unique proof-token hash and returns the id. A leftover `enrollmentId` key in an already-printed QR is ignored. Read-once `CREATED` to `BOUND` still requires the token. Unauthenticated `POST /verify` that can mark a `CREATED` row `INVALID` from a guessed id is a separate follow-up, not fixed by this invitation change.
- **Decision (#912 C1):** The respond per-`authAttemptId` budget (default 1 / 5 min) is consumed
  **only after** a valid device signature. The filter applies an IP ceiling on **all** respond
  requests (not failures only); a third-party invalid respond must not starve the owner into HTTP
  429 past the attempt TTL. `Retry-After` uses Bucket4j refill delay (also for pending / verify /
  bind keyed denials).
- **Decision (#912 C2, accepted):** Timing differences between not-found and invalid-signature
  respond paths can hint that an id is a live `READ` attempt. That gap is **not** equalized: the
  same existence signal is already available from sequential auth-attempt ids. Documented; no
  latency padding.
- **Decision (#895, Christophe + Patrick M1/M2):** Wall-clock expiry after `expiresAt` maps to
  **409** `auth-attempt-expired` so Demo Device / PAM / mobile can show a clear expired message.
  That typed problem is emitted **only after** enrollment load and device-signature (ownership)
  verification succeed, and **before** challenge validation (owner + valid signature + wrong
  challenge on an expired attempt → typed expiry, not `INVALID`/`FAILED`). An unauthenticated or
  non-owner caller, a missing device public key, an unknown attempt, or a still-`PENDING` /
  terminal attempt keeps the generic **409** `auth-attempt-state-conflict` (no `INVALID` flip) so
  sequential ids do not reveal expiry or READ state before the caller is verified.
### Challenge-code direction (Decision #960)

- **Decision (#960, Christophe):** When a challenge is required for an auth attempt, challenge-code
  direction is intentional: the integrating app shows a two-digit code; the user enters it in the
  mobile app, then approves or denies. **When it applies:** Integration API create requires
  `challengeRequested` (boolean, per attempt); a code is generated when that flag is `true`
  **and/or** the enrollment has `authAttemptChallengeRequired`. Without either, approve/deny alone
  still applies. PAM host conf `challenge_requested` defaults to **true** (#995 — safer SSH
  default); operators may set `false` for approve/deny-only SSH (enrollment-forced codes still
  display). Same principle as CISA number matching
  ([Implementing Number Matching in MFA Applications](https://www.cisa.gov/sites/default/files/publications/fact-sheet-implement-number-matching-in-mfa-applications-508c.pdf),
  Oct 2022) — approving requires access to the login screen, so prompt spam (MFA fatigue) does not
  work; no vendor implementation equivalence is implied. Honest entropy: two digits are a 1-in-100
  blind guess; the code counters reflex approval, nothing more. The approve/deny decision is signed
  by the device key within the signed protocol (explicit deny remains an audit/alert signal). This
  does **not** resist real-time proxy phishing and is **not** WebAuthn/FIDO2/passkey-equivalent.
  An inverted mode (mobile shows the code; integration enters it) stays parked as additive-only —
  see
  [`I-2026-10-21-inverted-challenge-code-mode`](../product-docs/global/backlog/ideas/I-2026-10-21-inverted-challenge-code-mode.md).

- **Audit log integrity (tamper-evident).** Every audit entry is signed with **HMAC-SHA256** using a
  key held in a **file separate from the database** (and separate from the encryption master key).
  Periodic **checkpoints** chain windows together for a completeness proof.
- **Honest two-layer detection.** A **rolling** checkpoint layer *attaches* what it observes; a
  **nightly retroactive batch** *detects* per-entry HMAC and chain-continuity anomalies and raises
  operator alerts.
- **Degraded-mode honesty.** A checkpoint **heartbeat** guard drives peripheral APIs to fail closed
  (HTTP 503) and raises an alert rather than presenting a false "all healthy" state.
- **Operator-actionable signals.** Integrity problems surface as alerts a Global Admin can triage;
  declared gaps and recovered outages are reconciled in the chain rather than hidden.
- **Encryption at rest with rotatable keys**, self-hosted control, and inspectable open-source code.
  Ezkey uses Google Tink for the at-rest keyset and data-encryption primitives. The runtime source of
  truth for the platform keyset is the `ezkey_keyset_blob` database row (Tink encrypted-keyset JSON
  envelope; ADR-0016). A file-based **master key** wraps that envelope; API processes do not treat a
  keyset file as authority. Tink envelope metadata such as key IDs (**kids**) and primary-key status
  is treated as non-secret operational metadata, not as a security layer. **PII and most operator
  contact fields remain cleartext in the database** — the keyset protects selected at-rest secrets
  (for example enrollment private keys, proof tokens, API key material), not general PII
  confidentiality.

### Sending the enrollment QR by email (#1206 step 5)

Sending is optional. With no connector, the QR and the challenge stay on screen and Ezkey is complete.

- **The proof token leaves Ezkey.** The QR encodes the enrollment proof token, a bearer secret until
  bind. A send passes it to the delivery connector and then to the email provider, and it sits in the
  person's mailbox. Whoever scans the QR first can bind, so the challenge stays out of the email and
  goes through another channel, and an email sent to a wrong address means revoke and recreate.
- **The provider is the operator's processor.** Choosing and contracting it, and what it keeps, belong
  to the operator. Ezkey sends only the fixed template, the recipient, the organisation name, the
  expected server and the QR.
- **Controls:** the core sends only to the enrollment's `contactEmail`, only at `CREATED`, and caps
  sends per tenant and per instance each day. The connector authenticates the core with its own token,
  keeps its own daily cap against a stolen token, uses STARTTLS or TLS towards the provider by default,
  and logs no recipient, token or QR (checked by a log canary in CI). Plain SMTP is refused unless a
  private network is declared, which the local Mailpit setup does.
- **Audit:** each send records the recipient, the outcome and the operator, never the QR or the token.

## What Ezkey does not do (current limits, stated honestly)

- **It does not defend against a host-rooted adversary in-database.** Because the HMAC key lives on
  the host, an attacker who controls **both** the database **and** the host filesystem could rewrite
  history *and* recompute the chain. No in-database mechanism detects that. The current integrity
  ceiling is the **host trust boundary**.
- **It does not yet export audit data to an external immutable store.** The seal / archive-eligibility
  state machine exists, but the actual external export (and DB purge) is **deliberately deferred** —
  see "What it aims to do." Until then, integrity evidence lives on the same host as the data.
- **It does not claim standards equivalence or certification.** Ezkey is not WebAuthn/FIDO2, not a
  passkey compatibility layer, and makes no SOC 2 / formal-attestation equivalence claim.
- **It does not claim post-quantum resistance.** Device signatures (EC P-256) and integration
  signatures (Ed25519) are pre-quantum: a future quantum computer able to run Shor's algorithm
  could forge them from the public keys. Proof tokens, AES-256-GCM at rest, and HMAC-SHA256 are
  symmetric and do not rely on elliptic-curve hardness. Hybrid post-quantum TLS key exchange
  depends on the deployment: the community instance negotiates X25519MLKEM768 at the Cloudflare
  edge and its Caddy origin accepts it (probe 2026-11-03,
  [`lightsail/community/DEPLOYED.md`](lightsail/community/DEPLOYED.md) § Transport TLS); no claim
  is made for self-hosted edges or for every client stack. Recovery-code length
  (106 bits) is classical hardening, not a post-quantum property. Algorithms:
  [`CRYPTO.md`](CRYPTO.md). Map, decisions and watch list:
  [`V-2026-09-15-post-quantum-crypto-posture`](../product-docs/global/vision/V-2026-09-15-post-quantum-crypto-posture.md).
- **It does not depend on, or guarantee, any third-party channel.** SMS and email are optional,
  operator-triggered, peripheral capabilities; Ezkey is fully operational without them.
- **It does not claim guaranteed delivery of every audit event.** Audit writes use a dedicated
  transaction and are sealed when persisted, but write/seal failures are absorbed so business
  operations are not blocked (**fail-open**). Tamper-evidence applies to rows that were stored;
  omission at the source is a separate concern (tracked in
  [`I-2026-07-18-audit-log-fail-open-exception-swallow`](../product-docs/global/backlog/ideas/I-2026-07-18-audit-log-fail-open-exception-swallow.md)).
- **It does not rely on hiding Tink keyset metadata.** Key material is secret; key IDs (**kids**),
  primary-key status, and similar Tink envelope metadata are not treated as secrets. Ezkey
  deliberately avoids a second custom envelope whose only purpose would be to hide non-secret
  metadata.
- **It does not encrypt general PII under the platform keyset.** Contacts, admin names/emails, audit
  IP/UA, and free-text auth context are stored in cleartext. Keyset loss is an availability /
  re-enrollment problem for encrypted columns, not a PII confidentiality guarantee.
- **Demo App ACME access-code slot secrets are plaintext on the host volume.** Integration keys and
  secrets for evaluator `/t/{code}` slots live in `/app/config/application.properties` on the
  `demo-app-acme-config` volume (non-root `spring` uid). The entrypoint applies `umask 077`,
  `chmod 600`, and **fail-closes** (refuses to start) unless the file mode is **0600**. Slots load
  at JVM start — **restart required** after edit. There is **no** encryption-at-rest guarantee for
  those demo secrets (Related to [#958](https://github.com/mgagp/ezkey/issues/958)).

## What Ezkey aims to do (planned, if adoption justifies)

- **Vendor-neutral immutable audit export (SPI).** When a sealed, cryptographically-linked audit
  batch becomes archive-eligible, an optional **Service Provider Interface** — implemented by a
  separate peripheral project (e.g. an S3 / object-lock adapter, a Cloudflare R2 adapter) — exports
  the detached batch to a **WORM / append-only** store, then the core purges the exported range from
  the database. This bounds even a host-rooted adversary **for already-exported windows** and keeps
  the database lean via retention. Reference adapter repositories are currently placeholders
  announcing this direction.
- **Off-box verification.** A tool to re-verify a published export bundle digest against the chain
  **without** the live database.

These are **Phase 2** by deliberate choice: Phase 1 ships on empty systems with months of runway
before audit volume forces external retention, and the project will not couple its core to any single
storage vendor. A **self-contained operator who never enables export is fully supported** and simply
accepts the host-boundary limit described above.

## Threat model at a glance

| Adversary / fault | Detected by current release? |
|---|---|
| Accidental corruption (bug, disk, replication) | **Yes** |
| DB-only adversary (SQL injection, stolen DB credentials, rogue DBA without host access) | **Yes** (nightly batch) |
| Host-rooted adversary (reads HMAC key file + DB) | **No** today; bounded for exported windows once the Phase-2 export SPI is enabled |

## Honest claims we make (and avoid)

| We may say | We do not say |
|---|---|
| "Tamper-**evident** audit chain with a planned off-box verifiable export." | "Tamper-proof" / "immutable" (while export is unbuilt). |
| "Detects accidental corruption and DB-level tampering via nightly cryptographic validation." | "Detects any tampering, including by a compromised host." |
| "Audit rows that were stored are sealed and tamper-evident; write failures are logged and do not block business operations." | "Guaranteed audit delivery for every security-relevant operation" / "comprehensive audit trail with no omissions." |
| "Self-hosted, opinionated, stronger than passwords and classic TOTP for backend-oriented contexts." | Formal attestation chains, SOC 2 equivalence, or certification-grade assurance. |
| "Proof tokens and at-rest AES-256/HMAC-SHA256 are symmetric and do not rely on elliptic-curve hardness." | "Post-quantum MFA" / "quantum-resistant" signatures, recovery codes, or protocol (device P-256 and integration Ed25519 are pre-quantum). |

SOC 2 remains a **reference vocabulary** for operational discipline, not a certification target.
See [`../product-docs/global/normative-posture.md`](../product-docs/global/normative-posture.md).

## Observability

- **2026-10-16:** Community host (`ezkey.online`) may enable an opt-in JavaMelody collector bound
  to `127.0.0.1:8088` and reached only over an SSH local forward (`Host ezkey-online-metrics`).
  Reason: runtime visibility for the lab without a public metrics surface — Caddy returns 404 for
  `/monitoring`, `/actuator`, and nested paths under those segments (case-insensitive; also
  `//…` and `;…` variants); monitoring reports stay on management ports inside the Docker
  network; system actions off; CSRF on; update check disabled; no HTTP request logging; URL id
  normalization; no secrets in JVM `-D` flags
  ([#846](https://github.com/mgagp/ezkey/issues/846)). Operator runbook:
  [`lightsail/community-host.md`](lightsail/community-host.md) § Observability. Companion memory
  cgroup / heap ceilings: [#749](https://github.com/mgagp/ezkey/issues/749).
- **2026-10-19:** Management ports are internal-only (community: `MANAGEMENT_SERVER_ADDRESS=127.0.0.1`
  unless the JavaMelody overlay reopens `0.0.0.0` for the collector). Demo App ACME uses management
  port **8086** (Actuator off business port 8082). Startup fails closed if JavaMelody is enabled
  without management-endpoint monitoring, or if management port equals business port. Spring
  Security `permitAll` on `/actuator/**` applies on the management context (health and
  `/actuator/monitoring` need no auth). Caddy deny remains defense in depth. ADR-0015.
- **2026-10-19 (#905):** Local/HA Docker profiles set `management.server.address=0.0.0.0` so
  HAProxy and peers can health-check management ports; IDE defaults stay `127.0.0.1`. Host
  exposure remains `127.0.0.1:port:port` (non-HA) or `expose` only (HA API replicas).
- **2026-11-02 (#1140):** Client IP trust order behind a reverse proxy: when `remoteAddr` is
  outside `ezkey.trusted-proxies.cidrs`, headers are ignored (fail-closed). When trusted, walk
  `X-Forwarded-For` **right-to-left** (skip trusted hops; never prefer the leftmost entry);
  `X-Real-IP` is never used; `CF-Connecting-IP` only when
  `ezkey.trusted-proxies.trust-cloudflare-connecting-ip=true` (OFF by default).
- **2026-11-02 (#1142):** In-memory rate limits are **per replica**. Behind HAProxy round-robin
  with *N* API replicas and no shared store, the effective allowance is approximately
  **configured limit × N**. Operators sizing limits for HA must account for that multiplier
  until a shared rate-limit store exists.

## Session and cookies

- **2026-10-14:** Stateless APIs — no servlet session cookie emitted, 401 included
  ([#816](https://github.com/mgagp/ezkey/issues/816)). Reason: `ExceptionTranslationFilter`'s
  `HttpSessionRequestCache` was creating `EZKEY_*_API_SESSION` cookies on anonymous 401s with
  only `HttpOnly` (no `Secure` / `SameSite`). Auth uses Bearer, API key, or Mode B
  `EZKEY_ADMIN_SESSION`; servlet sessions are unused. Fix: `SessionCreationPolicy.STATELESS` +
  `NullRequestCache` on Admin/Integration/Auth/Crypto APIs, plus defense-in-depth
  `Secure`/`SameSite=Strict` on servlet cookie properties when a session is ever created.
- **2026-11-03:** Deferred-onboarding activation codes (`ezkey_activation_…`) are invite
  material, never an Admin API session
  ([#1122](https://github.com/mgagp/ezkey/issues/1122)). Reason: codes were stored with the
  default `SESSION` purpose and a pending admin has no enrollment, so the code authenticated as a
  full session (no `/activate`, device bind, MFA or Mode C) and its expiry slid like a session.
  Fix, fail closed at three layers: codes persist as purpose `ACTIVATION` (absolute TTL, consumed
  only by `POST /api/v1/admin/auth/activate`); session validation accepts only `SESSION` and
  `EVALUATOR_TEMP`, rejects any token of a `PENDING_ACTIVATION` admin, and requires a
  `VERIFIED` enrollment for `SESSION`; the bearer filter refuses the `ezkey_activation_` prefix
  from header or cookie. `AdminToken` has no default purpose: every mint states one, a null
  purpose fails fast. A successful `/activate` revokes every other outstanding activation code of
  that admin. Flyway V26 converts unused active legacy codes to `ACTIVATION` and revokes codes
  already presented as a bearer; its header documents the manual rollback and an
  `ezkey_audit_log` query to review what each revoked code was used for.
- **2026-11-13 (#1123):** An `EVALUATOR_TEMP` session cannot call administrator credential
  sub-resources. Reason: the Mode C deny-list matched onboarding and QR only, so
  `POST /api/v1/admins/{id}/recovery-codes/regenerate` and `…/issue-initial` stayed open. In a
  multi-admin tenant a temporary session could replace a peer's unused recovery codes with a
  plaintext set and then take over that peer through `/auth/recover`. A decimal-only match on the
  raw URI also failed open: Spring binds `@PathVariable Integer` with `NumberUtils.parseNumber`,
  which accepts hex (`0x63`) and a leading `+`. Fix, fail closed: an id segment is allowed only
  when it is the canonical decimal form of the caller's own id (no sign, no hex, no leading
  zeros, no trailing slash). That caller may open only their own profile and onboarding
  (including QR). Every other suffix is denied, including activation-code reissue and
  deactivate/activate, and any non-canonical spelling is denied even for profile and onboarding.
  The same five mutations are refused again in `AdminProvisioningService`. The rest of the
  console stays a deny-list so the evaluator can still navigate. Enrollment QR delivery is denied
  for any id spelling. Encryption keys, enrollment reset, recover, and passwordless session mint
  were already denied.

## Traceability

- Internal analysis & decision compass:
  [`../product-docs/global/integrity-assurance-honest-line.md`](../product-docs/global/integrity-assurance-honest-line.md)
- Integrity strategy vision: `product-docs/global/vision/V-2026-0004-integrity-validation-strategy.md`
- Export SPI vision (Phase 2): `product-docs/global/vision/V-2026-06-28-audit-archive-export-spi.md`
- Export SPI backlog idea: `product-docs/global/backlog/ideas/I-2026-06-28-audit-archive-export-spi.md`
- Audit write fail-open / delivery honesty:
  [`../product-docs/global/backlog/ideas/I-2026-07-18-audit-log-fail-open-exception-swallow.md`](../product-docs/global/backlog/ideas/I-2026-07-18-audit-log-fail-open-exception-swallow.md)
- Runtime alert reference: [`ALERTS.md`](ALERTS.md)
- Product identity: [`PROJECT_POSITIONING.md`](PROJECT_POSITIONING.md)

## Code scanning policy

CodeQL runs on PRs and on `main`, analyzing only the languages whose files changed; the weekly
schedule, `workflow_dispatch` and any change to the CodeQL setup analyze every language (advanced setup:
[`.github/workflows/codeql.yml`](../.github/workflows/codeql.yml), config
[`.github/codeql/codeql-config.yml`](../.github/codeql/codeql-config.yml) with
`queries: security-extended`). Open alerts follow the cadence and SLAs below.

### Triage cadence

- **Weekly** triage at **Monday 10:15 ET**: **Fred proposes, Christophe decides and
  executes**. (**2026-10-19:** moved from 09:17 ET so the pass does not collide with
  Dependabot curated Monday full at 09:14 ET — see
  [`../product-docs/global/hygiene/README.md`](../product-docs/global/hygiene/README.md)
  § *Cadence & automation*.)
- **Same Monday slot — remediation budget:** remediate **up to 3 real alerts per week**
  (severity first, then nearest SLA deadline); **one fix PR per alert** via cloud agent.
  Security/crypto fixes need **Christophe's** review; behavior changes need **Marc's** go;
  **no deploy**. (**2026-10-19**, Marc — triage without a bounded fix budget leaves the open
  stock growing.)
- **Weekday light:** check for **new high/critical** alerts without waiting for Monday
  (same immediate-trigger table below).
- Fred (hygiene bot) runs the weekly pass and proposes dispositions; Christophe
  (security bot) decides and executes dismissals with the read-write code-scanning
  token. Each dismissal carries a GitHub reason (false positive, used in tests,
  won't fix), a one-line technical justification, and a triage reference (campaign
  note under
  [`../product-docs/global/hygiene/code-scanning/`](../product-docs/global/hygiene/code-scanning/)).
- **High-impact** = critical or high alert on **deployed, exposed** code (see
  immediate-trigger table). Dismissing a high-impact alert requires **Marc's
  explicit go**. For all other alerts, Fred proposes and Christophe decides and
  executes alone (one-line reason; no Marc gate).

**Immediate triggers** (do not wait for the weekly pass):

| Trigger | Action |
|---|---|
| New **critical/high** on **deployed, exposed** code (`admin-api`, `auth-api`, `integration-api`, `admin-ui`, `crypto-api`, `demo-app-acme`, Caddy) | Triage within **24 h**; alert Marc; fix or mitigate within **14 days** |
| Dependency CVE in **CISA KEV** on deployed code | **3 days** |
| Abnormal jump in alert count (e.g. suite change as in [#805](https://github.com/mgagp/ezkey/pull/805)/[#809](https://github.com/mgagp/ezkey/pull/809)) or upcoming **ezkey.online** upgrade | Triage **before deploy** |

### Remediation SLAs

| Class | SLA |
|---|---|
| Critical/high on **non-deployed** code (CLI, PAM) | **30 days**, or mark experimental only if paired with a dated hardening issue (CLI [#802](https://github.com/mgagp/ezkey/issues/802); PAM [#818](https://github.com/mgagp/ezkey/issues/818), target 2026-11-13) |
| Medium | **60 days** ([OpenSSF `vulnerabilities_fixed_60_days`](https://www.bestpractices.dev/en/criteria/0#vulnerabilities_fixed_60_days)) |
| Low | **180 days** |
| Dev / build tooling | **30 / 60 / 180 days** (critical or high / medium / low). Wait for the parent release. No override, except a CI job that holds secrets and processes untrusted input. At the deadline, `tolerable_risk` dated at most **90 days**. If the only fix is a major, `tolerable_risk` immediately, still dated |
| False positive / won't-fix | Motivated dismissal at the **next triage** |

### Dependency alerts

Dependabot alerts are triaged by what ships. Before dismissing or accepting a third-party
vulnerability, check upstream, because the fix may already be on its way: see
[`hygiene/dependabot/README.md`](../product-docs/global/hygiene/dependabot/README.md)
§ *Check upstream before dismissing a vulnerability*. Transitive overrides, the dev-tooling
row above, and the dismissal line follow § *Transitive dependency overrides*.

### Transitive dependency overrides

**Principle (2026-11-13, Marc).** Rely on the upstream fix process and on a normal Dependabot
parent bump. An override (`package.json` `overrides`, a parent `dependencyManagement` pin
ahead of an imported BOM, or the same shape in another manifest) is a dated, temporary
exception. It is never a permanent habit: a standing override becomes maintenance debt and
drifts from upstream.

**D** is first detection on `main` (same clock as rule 1 in this policy). Deployed,
exposed code is the set in the immediate-trigger table (`admin-api`, `auth-api`,
`integration-api`, `admin-ui`, `crypto-api`, `demo-app-acme`, Caddy).

#### Decision matrix

| Situation | Action | Override |
|---|---|---|
| Parent range already allows the fixed version | Refresh the lockfile or bump the parent | Never |
| Deployed, advisory on the **CISA KEV** list | Override or mitigate now. Deadline **3 days** | Immediate |
| Deployed critical or high, parent blocks the fix | Wait **7 days** for upstream, then meet the **14 day** SLA | Patch or minor, from **D+7** |
| Deployed medium, parent blocks the fix | Wait **30 days**, then meet the **60 day** SLA | Patch or minor, from **D+30** |
| Deployed low, parent blocks the fix | Wait **90 days**, then meet the **180 day** SLA | Patch or minor, from **D+90** |
| Shipped, and the vulnerable path is shown to be unreachable | Dismiss `not_used`, plus an [osv-scanner](https://google.github.io/osv-scanner/configuration/) exception with `effectiveUntil` | No |
| Dev or build tooling | Wait for the parent release until the dev-tooling SLA (**30 / 60 / 180 days**) | No, except a CI job that holds secrets and processes untrusted input. At the deadline, `tolerable_risk` for at most **90 days**. If the only fix is a major, `tolerable_risk` immediately, still dated |

Medium and low findings on non-deployed product code (CLI, PAM) keep the **60 day** and
**180 day** SLAs. No separate override window was set for them.

Critical or high on non-deployed code (CLI, PAM) waits **21 days**. A patch or minor override
is allowed from **D+21** when the path is reachable. Deadline **30 days**. An unreachable path
uses the `not_used` row.

#### Timeline

| Class | Upstream wait | Override allowed | Deadline |
|---|---|---|---|
| KEV, deployed | 0 | Immediate | 3 days |
| Critical or high, deployed and exposed | 7 days | From D+7 | 14 days |
| Medium, deployed | 30 days | From D+30 | 60 days |
| Low, deployed | 90 days | From D+90 | 180 days |
| Critical or high, non-deployed (CLI, PAM) | 21 days | From D+21, when the path is reachable | 30 days |
| Dev / build tooling | Until the deadline | No, except a CI job that holds secrets and processes untrusted input | 30 / 60 / 180 days |

#### Override guardrails

- Patch or minor only. A major stays on the Dependabot T4 path (Marc decides). Dev tooling
  whose only fix is a major takes dated `tolerable_risk`, as in the matrix.
- Every live override is one row under **Active overrides** in
  [`../product-docs/global/hygiene/dependabot/README.md`](../product-docs/global/hygiene/dependabot/README.md).
  Columns: GHSA, manifest, blocking parent, removal condition, expiry (at most **90 days**),
  owner.
- At most **5** overrides per manifest and **10** in the repo. Above either cap, Marc decides.
  Pins that are not security pins (ahead of Boot, or a compatibility pin) sit **outside the cap**.
- **2026-11-13 (Marc):** #1348 removed the 6 obsolete Admin UI overrides. That manifest has
  **0** active overrides. SEC-019 (logback, Jackson 3, Jackson 2) was removed on 2026-11-13
  (#1349): Boot 4.1.2 covers the original CVEs, and no active GHSA or OSV advisory applies to
  the Boot-managed versions. `caffeine.version` stays: it is a compatibility pin, outside the cap.
- A CI warning when a row has no expiry, is past expiry, or crosses a cap is a follow-up.
  This change does not add that check.
- The repo has no osv-scanner config yet. The `not_used` row is the rule for the exception
  file when that config is added. Adding the scanner is a follow-up.

#### Dismissal reason

One line:

```text
<Reason>: <fact path:line>; <why not exploitable or not deployed>; ref <triage YYYY-MM-DD / #NNN>
```

`Reason` is the GitHub dismissal (`not_used`, `tolerable_risk`, false positive, won't fix).
The fact is a path and line a reviewer can open. The ref is the campaign-note date or the
issue number.

**Sources:** [OWASP Top 10 2025 A03](https://owasp.org/Top10/2025/A03_2025-Software_Supply_Chain_Failures/)
(software supply chain failures);
[OpenSSF Concise Guide for Developing More Secure Software](https://best.openssf.org/Concise-Guide-for-Developing-More-Secure-Software);
[About Dependabot security updates](https://docs.github.com/en/code-security/dependabot/dependabot-security-updates/about-dependabot-security-updates);
[osv-scanner configuration](https://google.github.io/osv-scanner/configuration/);
[Backstage `SECURITY.md`](https://github.com/backstage/backstage/blob/master/SECURITY.md).

### PAM / experimental components

Non-deployed lab surfaces (personal-host PAM, Python CLI) stay out of the operable-release path.
Experimental marking alone is not enough for critical/high alerts: pair with a **dated hardening
issue** (table above). Decision records for those components land in this subsection only.

- **2026-10-14:** PAM debug file logs leave `/tmp` — directory via `open(O_DIRECTORY|O_NOFOLLOW)`, files via `openat(…, O_WRONLY|O_CREAT|O_APPEND|O_NOFOLLOW|O_CLOEXEC|O_NONBLOCK, 0600)` then `fstat` (regular, uid 0, `nlink==1`) + `fchmod 0600`, under root-owned `/var/log/ezkey-pam/` (mode `0700`), gated by conf/PAM `debug` (sample default `false`; Docker lab entrypoint may set `true`). Log lines neutralize CR/LF/controls before syslog/file. Closes CodeQL **123** / **124** ([#818](https://github.com/mgagp/ezkey/issues/818); idea `I-2026-10-13-pam-tmp-log-hardening`). Reason: `fopen("/tmp/…","a")` in a root-loaded PAM `.so` is a classic symlink / TOCTOU race on multi-user hosts.
- **2026-10-14:** CodeQL `paths-ignore: ezkey-pam/test/**` — test harness only, not shipped; product path is `ezkey-pam/src/` ([#818](https://github.com/mgagp/ezkey/issues/818)).
- **2026-10-22, updated 2026-11-09 (#992, residual risk #1311):** A per-integration pending-attempt policy now exists (default **REPLACE**). **REFUSE** returns HTTP 409 `auth-attempt-pending-exists` with `Retry-After`. A global cooldown suspends creates after 3 device-signed outcomes in 10 minutes (5 minutes, HTTP 409 `auth-attempt-creation-cooldown`). The counted set is `REJECTED` and `INVALID` (wrong challenge digits) on every integration. `EXPIRED` is not counted: supersession, cancel, and timeout must not let an unauthenticated caller, such as `ssh user@host` with PAM, suspend the user. Supersession and cancel write `EXPIRED` only from `PENDING` or `READ`, so those attempts do not arm the cooldown and do not raise the enrollment alert, whatever status they carry. A device `REJECTED` or a challenge-mismatch `INVALID` raises the same deduplicated operator alert. The per-API-key create bucket remains the only **429**. Residual risk, stated honestly: the challenge code prevents reflex approval; it does not bound prompt volume. Under **REPLACE**, prompt volume is bounded only by rate limits (the per-API-key create bucket). Under **REFUSE**, one live attempt at a time also limits how many requests are waiting. A per-enrollment create rate was **not** delivered. Under **REFUSE** the attacker's attempt blocks the legitimate user for the attempt's whole TTL. The connector still does not parse the problem type (lot 3b, after this platform change). Connector-side mitigation remains: `ezkey-pam` requests the two-digit challenge code by default (`challenge_requested=true`, [#995](https://github.com/mgagp/ezkey/issues/995)). The code is shown when the enrollment requires it (`authAttemptChallengeRequired`, enforced server-side regardless of the connector) **or** the request asks for it; connector `false` only means "not requested by the connector". Because the code appears on the requester's terminal, a victim cannot approve an attacker's login by reflex; this does not resist real-time phishing relay. The pre-create `ok` prompt is an anti-accident filter for honest batch clients, **not a defense**. Experimental module, never released.
- **2026-11-09 (#1311):** The system integration (administrator sign-in) is fixed to **REPLACE**. Its cooldown uses the same device-signed set as every other integration (`REJECTED` and `INVALID` only). Admin login is passwordless (`AdminAuthService`): no password step precedes the MFA attempt. A login by username allows admin-name enumeration (generic 401 when the name is unknown, 200 with a pending attempt when it exists). A prompt flood on admin login remains a residual risk that predates #1311. It is bounded only by the per-IP limit in `AdminRateLimitFilter` (15 per minute, and a 30 minute block after 10 failures). Multiple IPs can bypass that limit, and there is no per-account limit. A cooldown block, once armed by signed outcomes, clears itself after 5 minutes without another administrator's action.

**Rules (Christophe):**

1. The clock starts at **first detection on `main`**.
2. For the **existing stock**, the clock starts at the **2026-10-14 baseline**
   (`115dde38`) — e.g. the 22 log-injection alerts of
   [#801](https://github.com/mgagp/ezkey/issues/801) are due ~**2026-12-13**.
3. An alert that becomes `fixed` **without a real fix** (suite/config change)
   **keeps its original clock**; weekly triage compares `fixed` alerts against
   the diff.

The **24 h** and **14 d** windows are Ezkey's adaptation of CISA guidance for
this lab posture — **not** a claimed industry norm.

**Sources:** [OpenSSF Best Practices](https://www.bestpractices.dev/en/criteria/0)
([silver](https://www.bestpractices.dev/en/criteria/1));
[CISA BOD 26-04](https://www.cisa.gov/news-events/directives/bod-26-04-prioritizing-security-updates-based-risk)
(supersedes BOD 19-02 and 22-01; timelines 3D/3DF/14D/60D/fix-on-upgrade — see
[SSVC / CISA response](https://certcc.github.io/SSVC/howto/cisa_response/));
FedRAMP POA&M conventional windows (30/90/180) via
[FedRAMP Rev5 legacy docs](https://www.fedramp.gov/rev5/) (POA&M template listed
there; direct XLSX URL 404 as of 2026-10-14);
[PCI DSS](https://www.pcisecuritystandards.org/standards/pci-dss/) 4.0.1 req 6.3.3;
[NIST SSDF SP 800-218](https://csrc.nist.gov/pubs/sp/800/218/final) RV.1 / RV.2.

- **2026-10-14:** weekly triage cadence and remediation SLAs adopted as above
  ([#815](https://github.com/mgagp/ezkey/issues/815)). Reason: replace the interim
  critical-7 / high-30 / medium-90 shorthand with exposure-aware SLAs aligned to
  CISA BOD 26-04 / SSVC risk factors while keeping lab-proportionate windows.
- **2026-11-13 (Marc):** transitive fixes default to upstream and to a Dependabot parent bump.
  An override is a dated exception (patch or minor, expiry at most 90 days) listed under
  **Active overrides** in the Dependabot hygiene README. Matrix and timeline:
  § *Transitive dependency overrides*. #1348 removed the 6 obsolete Admin UI overrides
  (0 active overrides on that manifest). SEC-019 (logback, Jackson 3, Jackson 2) was removed
  on 2026-11-13 (#1349): Boot 4.1.2 covers the original CVEs, and no active GHSA or OSV
  advisory applies to the Boot-managed versions. Caffeine stays, outside the cap.
- **2026-11-13, proposed, not adopted:** extend Monday 10:15 from CodeQL to Scorecard SARIF
  and Dependabot alerts, one grouped issue per rule (one Scorecard check, or one GHSA).
  Monday 10:15 stays CodeQL, one fix PR per alert.
- **2026-10-14 13:16 ET (Marc):** high-impact dismissals require Marc's explicit go;
  high-impact = critical/high on deployed exposed code. All other alerts: Fred
  proposes, Christophe decides and executes alone with a one-line reason
  ([#815](https://github.com/mgagp/ezkey/issues/815)).
- **2026-10-14:** pin advanced CodeQL to `security-extended` (not the default suite) so log-injection / sensitive-log and related queries keep running after the #805 default→advanced switch — those alerts had been marked fixed without code fixes when the advanced workflow briefly used the default suite ([#809](https://github.com/mgagp/ezkey/pull/809)).
- Every security review of a PR includes the code-scanning alerts raised on that PR; a PR does not merge with a new unresolved high/critical alert.
- Test-only code (`ezkey-tests/**`) is excluded via the CodeQL config, not by mass dismissal. A local model pack under `.github/codeql/extensions/ezkey-java-models/` declares `org.ezkey.util.LogSanitizer#sanitizeForLog` as a `log-injection` barrier and `AuthApiUrlValidator#validate(String,Set)` ReturnValue as a `request-forgery` barrier (not `validate(String)`); advanced setup loads it explicitly via `CODEQL_ACTION_EXTRA_OPTIONS` on the java-kotlin analyze step (`--additional-packs` + `--extension-packs=ezkey/java-models`) — not via default-setup auto-detection. Proven on PR #805 (Actions run [`38488035924`](https://github.com/mgagp/ezkey/actions/runs/38488035924) java-kotlin analyze: `run-queries` CLI includes `--extension-packs=ezkey/java-models`). Five language analyses uploaded on `main` at merge commit `f26cbe55` (#805); query suite restored to `security-extended` in [#809](https://github.com/mgagp/ezkey/pull/809).
- **2026-10-14:** code-scanning alert #9 (`java/ssrf` at demo-device `AuthApiService`) is a false positive after Lot A allowlist — input reaches `WebClient.baseUrl` only via `AuthApiUrlValidator.validate(String,Set)`; modeled as a `request-forgery` barrier so CodeQL recognizes the sanitizer (defense in depth: `EnrollmentAuthApiUrlResolver.resolveForStoredEnrollment` also revalidates persisted URLs) ([#810](https://github.com/mgagp/ezkey/pull/810)). With [#809](https://github.com/mgagp/ezkey/pull/809) `security-extended` on `main`, confirm alert closure; dismiss only after Marc's go if still open.
- **2026-10-14:** code-scanning alert #16 (`java/sensitive-log` at `AdminOperationsRateLimitService.recordResetEnrollment`) — never log the raw recovery token; log `RecoveryAuditDetails.recoveryTokenFingerprint` (and wrap caller-supplied log values with `LogSanitizer`) ([#810](https://github.com/mgagp/ezkey/pull/810)). Confirm closure under `security-extended` after #809.
- Any caller-controlled value written to logs goes through `org.ezkey.util.LogSanitizer.sanitizeForLog`; secrets are never logged, at any level. Production call sites that still need wrapping are tracked in [#801](https://github.com/mgagp/ezkey/issues/801).
- **2026-10-26 (#801, [#1040](https://github.com/mgagp/ezkey/pull/1040)):** Auth API `GlobalExceptionHandler` routes caller-controlled log args through `LogSanitizer.sanitizeForLog`, which neutralizes CR/LF only (not U+0085, U+2028/2029, or ESC). Exception messages logged with stack traces (`LOG.error("Unexpected runtime error", ex)`, `"Unhandled exception"`) are not sanitized and are not flagged by CodeQL.
- In `credentials-output-mode=full` (default), first-start logs print the single-use bootstrap enrollment proof token, challenge and QR — by design (#750, maintainer 2026-10-09); token valid 24 h and only while CREATED; `recovery_primary` omits them (`docs/OPERATIONAL.md:547`).
- Triage campaign notes: [`../product-docs/global/hygiene/code-scanning/`](../product-docs/global/hygiene/code-scanning/). Prior triage plan approved by Marc 2026-10-13 19:40; cadence/SLA decision [#815](https://github.com/mgagp/ezkey/issues/815).
- CI/CD and repository-settings parity inventory (evaluator-facing): [`engineering/ci-cd-best-practices-plan.md`](engineering/ci-cd-best-practices-plan.md).

## Review note

Keep this document aligned with the current release line. **Supersede, do not silently inflate**, if
the threat model or target market changes (e.g. a move toward regulated/enterprise customers with a
dedicated security team). When the export SPI ships, move its rows from "aims to do" to "does."
