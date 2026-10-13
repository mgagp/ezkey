# Global Test Strategy

Living document for how Ezkey plans, names, executes, and records testing across the monorepo
and its operational surfaces. Vocabulary and levels take inspiration from
**ISO/IEC/IEEE 29119** (policy → plan → design → case → execution → report). That is a shared
language, not a certification claim.

Security case sources:

- **OWASP ASVS** (web and API) — cite by requirement code (for example `asvs-V2`).
- **OWASP MASVS** (mobile) — cite by control family (for example `masvs-auth`).

This document **complements** [`normative-posture.md`](normative-posture.md) (SOC 2 as reference
vocabulary only). It does **not** create a parallel compliance, attestation, or auditor-evidence
program.

**Decision:** [ADR-0014](architecture-decisions.md#adr-0014-qa-sphere-for-test-execution-tracking)
— QA Sphere for execution tracking; the repository remains the source of truth.

## Purpose and scope

Cover all testing that protects or demonstrates Ezkey behavior:

| Surface | What “testing” means here |
| ------- | ------------------------- |
| Admin UI | Unit, Playwright, exploratory operator flows |
| Admin / Auth / Integration APIs | Unit, integration, contract, Bruno, security cases |
| Mobile | Unit, Maestro / device, MASVS-oriented cases |
| Demos | Demo Device and ACME demo app smoke and campaign runs |
| CLI / SDK | Smoke and contract-parity checks |
| Experimental PAM | `ezkey-pam` only — experimental, not product-core |
| Core / crypto / migration | Prefer **black-box** and API-level evidence; avoid white-box churn as the primary story |
| Ops | Docker stack, scripts, community host (`ezkey.online`) |
| Documentary / normative conformity | Docs and product-docs stay consistent with behavior; normative posture stays vocabulary, not certification |

Out of scope for this file: per-feature acceptance criteria (those live in feature briefs and
[`spec-test-traceability.md`](spec-test-traceability.md)), and CI job wiring (see
[`docs/DEVELOPMENT.md`](../../docs/DEVELOPMENT.md) § Continuous Integration).

## Taxonomy codes

Stable module codes for folder names, case titles, and campaign filters. Paths were verified
against the monorepo layout (2026-10-13).

| Code | Module / surface paths | Notes |
| ---- | ---------------------- | ----- |
| `ADMUI` | `ezkey-admin-ui/` | Operator UI |
| `ADMAPI` | `ezkey-admin-api/` | Admin HTTP API |
| `AUTH` | `ezkey-auth-api/` | Device / enrollment / auth-attempt protocol |
| `INTEG` | `ezkey-integration-api/`, `ezkey-sdk/` | M2M API + client SDK |
| `MOB` | `ezkey_mobile/` | Reference mobile app |
| `DEV` | `ezkey-demo-device/` | Browser demo device |
| `ACME` | `ezkey-demo-app-acme/` | Sample integrating app |
| `CLI` | `ezkey-cli-python/` | Python CLI |
| `PAM` | `ezkey-pam/` | **Experimental** — do not treat as release-critical by default |
| `CORE` | `ezkey-core/`, `ezkey-core-security/`, `ezkey-crypto-api/`, `ezkey-migration/` | Prefer black-box / API-level cases |
| `OPS` | `docker/`, `scripts/`, community host **ezkey.online** | Host is an ops surface (Lightsail / Caddy), not a top-level repo directory |
| `DOC` | `docs/`, `product-docs/` | Documentary and normative conformity |

## Nomenclature (QA Sphere)

### Folders

Use at most **two levels** initially:

```text
CODE / Functional area
```

Examples: `ADMUI / Enrollment`, `AUTH / Pending respond`, `MOB / Biometric unlock`,
`OPS / Clean-start stack`.

Do not invent deep trees for “future taxonomy.” Split a second level only when a functional area
has enough cases to justify it.

### Case titles

Start every case title with a **stable repo code**, then a short area token and a numeric id:

```text
ADMUI-ENR-001 Enrollment QR binds to Demo Device
AUTH-PEN-012 Pending claim rejects stale signature
MOB-AUTH-003 Approve path signs auth-attempt payload
```

QA Sphere also assigns its own id (for example `EZK-42`). Keep the **repo code in the title** so
exports, MCP results, and git docs stay joinable without opening the tool.

### Type tags

Apply one primary type (more are allowed when useful):

| Tag | Use for |
| --- | ------- |
| `functional` | Nominal and exception workflows |
| `security` | Abuse, authz, crypto, ASVS/MASVS-oriented cases |
| `i18n` | Locale / copy / RTL when in scope |
| `accessibility` | Keyboard, labels, contrast when in scope |
| `exploratory` | Time-boxed charter, not a fixed script |
| `regression` | Known past defect or high-churn path |

### Norm tags

When a case maps to an external requirement, tag it — do not paste the full standard text into
the case body:

- ASVS: `asvs-V2`, `asvs-V4`, … (requirement family / id as published)
- MASVS: `masvs-auth`, `masvs-storage`, …

Norm tags support later gap review. They do **not** imply certification coverage.

### Milestones and runs

| QA Sphere concept | Meaning in Ezkey |
| ----------------- | ---------------- |
| **Milestone** | A **campaign** (for example “Play closed testing 2026”, “Public alpha posture rehearse”) |
| **Run** | A **session** — one tester or one automated pass against a milestone |

Name milestones after the campaign goal and date window. Name runs after who/what executed them
(person handle or `mcp-agent-<date>`), never after secrets or personal contact data.

## Relationship with the test pyramid

```text
        /\
       /E2E\          Playwright, Maestro, clean-start demos, Bruno smoke
      /------\
     / integ  \       Spring / API module tests, SDK contract checks
    /----------\
   /   unit     \     JUnit, Vitest, mobile unit
  /--------------\
```

| Layer | Canonical home | QA Sphere role |
| ----- | -------------- | -------------- |
| Unit / integration / automated E2E | Repo + CI; inventory in [`spec-test-traceability.md`](spec-test-traceability.md) and component matrices | Optional pointer only |
| Manual / exploratory / campaign execution | Thin pointers in repo docs when needed | **Primary** tracking surface |

**Repo is the truth** for what a case *means* (spec, feature, acceptance criterion, automated
proof). **QA Sphere holds pointers and execution history** (who ran what, pass/fail, campaign
membership). Do not duplicate long specs into QA Sphere case bodies — link to
`product-docs/`, `docs/`, or OpenAPI paths instead.

Related methodology note (workflow embedding, not this canon): product-docs methodology reading
order under [`../methodology/README.md`](../methodology/README.md).

## For a cold agent (QA Sphere)

| Item | Value |
| ---- | ----- |
| Workspace | `https://isd.eu1.qasphere.com` |
| Project code | `EZK` |
| Hosted MCP (Standard) | `https://isd.eu1.qasphere.com/api/mcp` |
| Hosted MCP (read-only) | `https://isd.eu1.qasphere.com/api/mcp/readonly` |

### Auth

- Use a **personal API key** from QA Sphere **Settings → API Keys**.
- Read the key from an **environment variable** in the agent or local shell (for example
  `QASPHERE_API_KEY`). **Never commit** keys, `.env` files with secrets, or paste keys into
  issues/PRs/docs.
- Prefer read-only MCP when the task is inventory or reporting only.

### MCP constraints (remember these)

- MCP **cannot delete** entities — destructive cleanup is a human web-UI action.
- Results submitted through MCP are flagged **automated**.
- **Custom fields** are editable only in the web UI, not via MCP.
- Do not store secrets, tokens, passwords, recovery codes, or **tester personal data**
  (emails, phone numbers, home addresses) in case text, comments, or run notes.

### How to name cases

1. Place under `CODE / Functional area` (two levels max).
2. Title = `CODE-AREA-NNN Short imperative or observable outcome`.
3. Tags = type + optional norm tags.
4. Body = short steps + expected result + **links** to repo canon (feature id, ENDPOINT section,
   CRYPTO doc, Playwright file path). Keep bodies short.

### How to report results

1. Attach work to the correct **milestone** (campaign).
2. Create or reuse a **run** for this session/pass.
3. Record pass / fail / blocked with a one-line note and a repo pointer when useful
   (PR URL, commit SHA, log path under `/opt/cursor/artifacts/` for cloud agents).
4. On fail: link the defect tracker item if one exists; do not paste stack traces that contain
   secrets.
5. Do not mark “compliance done” from a green run — normative posture forbids certification
   theatre ([`normative-posture.md`](normative-posture.md)).

## Hygiene rules

- **No secrets** in QA Sphere or in this corpus (API keys, JWTs, Tink material, DB URLs with
  passwords).
- **No tester personal data** beyond a short handle needed to identify a run.
- Prefer **links** over pasted specifications (GOVERNANCE: one canonical place per concept).
- When automated coverage lands in the repo, update [`spec-test-traceability.md`](spec-test-traceability.md)
  (and the component matrix). QA Sphere gets a pointer or a retired manual case, not a second
  full copy of the acceptance criterion.

## Related documents

- [`spec-test-traceability.md`](spec-test-traceability.md) — feature ↔ spec ↔ automated tests
- [`normative-posture.md`](normative-posture.md) — SOC 2 reference vocabulary; no parallel program
- [`architecture-decisions.md`](architecture-decisions.md) — ADR-0014
- [`../GOVERNANCE.md`](../GOVERNANCE.md) — ownership and change rules
- [`../../docs/DEVELOPMENT.md`](../../docs/DEVELOPMENT.md) — CI and local validation entrypoints
- [`../../docs/testing/AGENT_UI_VALIDATION.md`](../../docs/testing/AGENT_UI_VALIDATION.md) — Admin UI agent validation ladder
