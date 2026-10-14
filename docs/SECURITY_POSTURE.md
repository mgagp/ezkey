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

- CodeQL runs on every PR and on `main` (advanced setup: [`.github/workflows/codeql.yml`](../.github/workflows/codeql.yml), config [`.github/codeql/codeql-config.yml`](../.github/codeql/codeql-config.yml) with `queries: security-extended`); open alerts are triaged against these SLAs: **critical 7 days, high 30 days, medium 90 days** (fix or justified dismiss).
- **2026-10-14:** pin advanced CodeQL to `security-extended` (not the default suite) so log-injection / sensitive-log and related queries keep running after the #805 default→advanced switch — those alerts had been marked fixed without code fixes when the advanced workflow briefly used the default suite ([#809](https://github.com/mgagp/ezkey/pull/809)).
- An alert is dismissed only with a GitHub reason (false positive, used in tests, won't fix), a one-line technical justification, and Marc's explicit go.
- Every security review of a PR includes the code-scanning alerts raised on that PR; a PR does not merge with a new unresolved high/critical alert.
- Test-only code (`ezkey-tests/**`) is excluded via the CodeQL config, not by mass dismissal. A local model pack under `.github/codeql/extensions/ezkey-java-models/` declares `org.ezkey.util.LogSanitizer#sanitizeForLog` as a `log-injection` barrier and `AuthApiUrlValidator#validate(String,Set)` ReturnValue as a `request-forgery` barrier (not `validate(String)`); advanced setup loads it explicitly via `CODEQL_ACTION_EXTRA_OPTIONS` on the java-kotlin analyze step (`--additional-packs` + `--extension-packs=ezkey/java-models`) — not via default-setup auto-detection. Proven on PR #805 (Actions run [`38488035924`](https://github.com/mgagp/ezkey/actions/runs/38488035924) java-kotlin analyze: `run-queries` CLI includes `--extension-packs=ezkey/java-models`). SARIF upload may still fail while default setup remains enabled.
- **2026-10-14:** code-scanning alert #9 (`java/ssrf` at demo-device `AuthApiService`) is a false positive after Lot A allowlist — input reaches `WebClient.baseUrl` only via `AuthApiUrlValidator.validate(String,Set)`; modeled as a `request-forgery` barrier so CodeQL recognizes the sanitizer (defense in depth: `EnrollmentAuthApiUrlResolver.resolveForStoredEnrollment` also revalidates persisted URLs) ([#810](https://github.com/mgagp/ezkey/pull/810)). Confirmation of alert closure needs [#809](https://github.com/mgagp/ezkey/pull/809) `security-extended` on `main` where applicable; dismiss only after Marc's go.
- **2026-10-14:** code-scanning alert #16 (`java/sensitive-log` at `AdminOperationsRateLimitService.recordResetEnrollment`) — never log the raw recovery token; log `RecoveryAuditDetails.recoveryTokenFingerprint` (and wrap caller-supplied log values with `LogSanitizer`) ([#810](https://github.com/mgagp/ezkey/pull/810)). Alert reappearance/closure confirmation depends on [#809](https://github.com/mgagp/ezkey/pull/809) restoring `security-extended`.
- Any caller-controlled value written to logs goes through `org.ezkey.util.LogSanitizer.sanitizeForLog`; secrets are never logged, at any level. Production call sites that still need wrapping are tracked in [#801](https://github.com/mgagp/ezkey/issues/801).
- In `credentials-output-mode=full` (default), first-start logs print the single-use bootstrap enrollment proof token, challenge and QR — by design (#750, maintainer 2026-10-09); token valid 24 h and only while CREATED; `recovery_primary` omits them (`docs/OPERATIONAL.md:547`).
- Triage campaign notes: [`../product-docs/global/hygiene/code-scanning/`](../product-docs/global/hygiene/code-scanning/). Decision: approved by Marc 2026-10-13 19:40.

## Review note

Keep this document aligned with the current release line. **Supersede, do not silently inflate**, if
the threat model or target market changes (e.g. a move toward regulated/enterprise customers with a
dedicated security team). When the export SPI ships, move its rows from "aims to do" to "does."
