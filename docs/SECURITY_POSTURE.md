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
  Provider Interfaces (SPIs) implemented by separate peripheral projects.
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
  **503** `integration-signing-unavailable` (clé manquante, transaction annulée, **pas de corps
  signé**) → **retryable** (exception au chemin neutre Back-only) ;
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
  Ezkey uses Google Tink for the at-rest keyset and data-encryption primitives. The platform keyset
  is protected by a file-based master key; both the file keyset and the `ezkey_keyset_blob` database
  synchronization row use Tink's encrypted-keyset JSON envelope. Tink envelope metadata such as key
  IDs and primary-key status is treated as non-secret operational metadata, not as a security layer.
- **HA keyset contract (#914 / #920 / #921):** the **database blob is the source of truth**; in-memory
  and **file copies are caches** (the keyset file is **best-effort** — a local cache rewrite failure
  after a successful CAS is logged at ERROR and does not fail the mutation in DATABASE/HYBRID).
  Admin writers **reload under the write lock before every mutation** (`promote` / `add` / `rotate`)
  when the blob is newer, then **write only via CAS**
  (`UPDATE ezkey_keyset_blob … WHERE version = :read`). Zero rows updated → typed
  `KeysetConcurrentModificationException` (no auto-retry). **H3 / N1:** CAS (or DB error) is decided
  **before** installing the new handle; on CAS success, memory is installed **immediately**, then the
  file cache is rewritten best-effort. A lost CAS leaves memory and file unchanged. Reload-before-
  mutation DB errors **propagate in DATABASE** (fail-closed: do not mutate from stale memory). A
  traffic-independent version tick (default 5 s, must be **strictly less than the sync window**) keeps
  idle replicas current without putting `findVersion` on the encrypt/decrypt hot path (SEC-009).
  Decrypt-failure may perform one throttled synchronous reload per node (≤ 1/s; throttle stamped per
  attempt). Re-encryption chooses the target primary from the reloaded blob; `old == new` is refused
  (HTTP 409), never “accepted” as a silent no-op. Unknown kid after reload → fail-closed (no
  `setPrimary`, no blob write). Expected HA conflicts audit as `KEY_PROMOTION_CONFLICT` (WARN /
  FAILURE); ERROR only for true inconsistency or ≥ 3 consecutive conflicts.

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
- **It does not depend on, or guarantee, any third-party channel.** SMS and email are optional,
  operator-triggered, peripheral capabilities; Ezkey is fully operational without them.
- **It does not claim guaranteed delivery of every audit event.** Audit writes use a dedicated
  transaction and are sealed when persisted, but write/seal failures are absorbed so business
  operations are not blocked (**fail-open**). Tamper-evidence applies to rows that were stored;
  omission at the source is a separate concern (tracked in
  [`I-2026-07-18-audit-log-fail-open-exception-swallow`](../product-docs/global/backlog/ideas/I-2026-07-18-audit-log-fail-open-exception-swallow.md)).
- **It does not rely on hiding Tink keyset metadata.** Key material is secret; key IDs, primary-key
  status, and similar Tink envelope metadata are not treated as secrets. Ezkey deliberately avoids a
  second custom envelope whose only purpose would be to hide non-secret metadata.

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

## Session and cookies

- **2026-10-14:** Stateless APIs — no servlet session cookie emitted, 401 included
  ([#816](https://github.com/mgagp/ezkey/issues/816)). Reason: `ExceptionTranslationFilter`'s
  `HttpSessionRequestCache` was creating `EZKEY_*_API_SESSION` cookies on anonymous 401s with
  only `HttpOnly` (no `Secure` / `SameSite`). Auth uses Bearer, API key, or Mode B
  `EZKEY_ADMIN_SESSION`; servlet sessions are unused. Fix: `SessionCreationPolicy.STATELESS` +
  `NullRequestCache` on Admin/Integration/Auth/Crypto APIs, plus defense-in-depth
  `Secure`/`SameSite=Strict` on servlet cookie properties when a session is ever created.

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

CodeQL runs on every PR and on `main` (advanced setup:
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
| False positive / won't-fix | Motivated dismissal at the **next triage** |

### PAM / experimental components

Non-deployed lab surfaces (personal-host PAM, Python CLI) stay out of the operable-release path.
Experimental marking alone is not enough for critical/high alerts: pair with a **dated hardening
issue** (table above). Decision records for those components land in this subsection only.

- **2026-10-14:** PAM debug file logs leave `/tmp` — directory via `open(O_DIRECTORY|O_NOFOLLOW)`, files via `openat(…, O_WRONLY|O_CREAT|O_APPEND|O_NOFOLLOW|O_CLOEXEC|O_NONBLOCK, 0600)` then `fstat` (regular, uid 0, `nlink==1`) + `fchmod 0600`, under root-owned `/var/log/ezkey-pam/` (mode `0700`), gated by conf/PAM `debug` (sample default `false`; Docker lab entrypoint may set `true`). Log lines neutralize CR/LF/controls before syslog/file. Closes CodeQL **123** / **124** ([#818](https://github.com/mgagp/ezkey/issues/818); idea `I-2026-10-13-pam-tmp-log-hardening`). Reason: `fopen("/tmp/…","a")` in a root-loaded PAM `.so` is a classic symlink / TOCTOU race on multi-user hosts.
- **2026-10-14:** CodeQL `paths-ignore: ezkey-pam/test/**` — test harness only, not shipped; product path is `ezkey-pam/src/` ([#818](https://github.com/mgagp/ezkey/issues/818)).

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
- In `credentials-output-mode=full` (default), first-start logs print the single-use bootstrap enrollment proof token, challenge and QR — by design (#750, maintainer 2026-10-09); token valid 24 h and only while CREATED; `recovery_primary` omits them (`docs/OPERATIONAL.md:547`).
- Triage campaign notes: [`../product-docs/global/hygiene/code-scanning/`](../product-docs/global/hygiene/code-scanning/). Prior triage plan approved by Marc 2026-10-13 19:40; cadence/SLA decision [#815](https://github.com/mgagp/ezkey/issues/815).
- CI/CD and repository-settings parity inventory (evaluator-facing): [`engineering/ci-cd-best-practices-plan.md`](engineering/ci-cd-best-practices-plan.md).

## Review note

Keep this document aligned with the current release line. **Supersede, do not silently inflate**, if
the threat model or target market changes (e.g. a move toward regulated/enterprise customers with a
dedicated security team). When the export SPI ships, move its rows from "aims to do" to "does."
