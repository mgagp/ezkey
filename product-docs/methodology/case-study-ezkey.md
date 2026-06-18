# Ezkey Case Study

## Purpose

Ezkey is the source project where this methodology was developed and tested. This page records how
Ezkey instantiates the method's generic concepts: corpus roots, progression markers, component tags,
and local governance bindings.

This is a case study, not a requirement for every adopting project. Other projects can reuse the
method while replacing these concrete values with their own bounded contexts, roadmap vocabulary,
and documentation roots.

## Source project

Ezkey is an open-source cryptographic MFA platform with a backend-first trust model. The method was
extracted from the practical work of building and governing this project, so examples in the corpus
may still refer to Ezkey when the concrete context improves clarity.

## Corpus roots

| Root | Role in the Ezkey instantiation |
|------|----------------------------------|
| `PRD.md` | Product requirements and functional constraints. |
| `README.md` | Repository setup, build, and high-level project organization. |
| `docs/` | Operator-facing and cross-cutting technical documentation. |
| `product-docs/global/` | Product vision, backlog, roadmap, policies, and global design canon. |
| `product-docs/components/` | Component packs for local design, decisions, mappings, and traceability. |
| `product-docs/methodology/` | The methodology corpus itself. |
| `.cursor/skills/` | Agent skills that operationalize repeatable parts of the method. |

## Progression markers

Ezkey uses the method's default progression markers as product milestone metadata:

| Marker | Meaning in Ezkey |
|--------|------------------|
| `P0-foundations` | Foundational model, protocol, persistence, and baseline API capability. |
| `P1-operability` | Operator workflows, lifecycle controls, observability, and administration ergonomics. |
| `P2-hardening` | Security, correctness, reliability, validation depth, and production-readiness hardening. |
| `P3-distribution` | Packaging, SDKs, demos, mobile/desktop surfaces, and broader adoption paths. |
| `P4-compliance-readiness` | Evidence, process, and documentation posture needed for later compliance-oriented work. |

In Ezkey, changes to these markers are coordinated with `product-docs/global/roadmap.md`,
`product-docs/global/features-and-phases.md`, and a global architecture decision when the meaning
changes materially. That synchronization rule is local to Ezkey's documentation architecture.

## Component tags

Ezkey uses the following component tags in backlog, vision, and GitHub issue metadata:

| Tag | Typical scope |
|-----|---------------|
| `core` | Shared domain, persistence, cross-cutting Java modules. |
| `admin-api` | Admin API boot module and controllers. |
| `auth-api` | Auth API boot module. |
| `integration-api` | Integration API boot module. |
| `crypto-api` | Crypto API module. |
| `admin-ui` | Admin UI frontend. |
| `mobile` | Mobile apps (Android / iOS). |
| `sdk` | Client SDKs, language-agnostic. Use `sdk-java` only when Java SDK alone is affected. |
| `sdk-java` | Java SDK specifically. |
| `infra` | Docker, compose, deployment, HA, and observability stack. |
| `bootstrap` | First-run and clean-start bootstrap flows. |
| `audit` | Audit chain, checkpoints, and integrity. |
| `repositories` | Data-access and SQL-layer concerns. |
| `migration` | Flyway and schema migrations. |
| `docs` | `docs/` hub, operator docs, and generated specs workflow. |
| `methodology` | `product-docs/methodology/`, templates, and skills mechanics. |
| `skills` | `.cursor/skills/` or equivalent agent skills. |
| `site` | Public static site under `sites/`. |
| `demo-device` | Demo Device simulator (`ezkey-demo-device/` in monorepo; see dual-repo delivery below). |

When GitHub Issues are used, Ezkey aligns these tags with `component:*` labels as described in
[`github-issues-workflow.md`](github-issues-workflow.md).

## Dual-repo delivery: Demo Device (`ezkey-demo-device`)

Ezkey maintains the Demo Device in **two places**:

| Copy | Role |
|------|------|
| `ezkey-demo-device/` in the **private monorepo** | Source of truth for implementation, Maven tests, and clean-start Docker stack (`:8083`). |
| **`mgagp/ezkey-demo-device`** (public GitHub) | EXP1 evaluator mirror; Docker default Exp1 Auth API (`:3080` on host). |

For any Demo Device feature or fix that affects both copies, use this **delivery order** (record it
in the tracer bullet and closeout — do not treat standalone sync as optional follow-up):

1. **Implement and validate in the monorepo** — code under `ezkey-demo-device/`, `mvn test`, clean-start smoke as applicable; merge via the monorepo PR.
2. **Sync to the public standalone repo** — copy aligned paths (`src/`, `pom.xml`, `openapi-spec.json`, `AGENTS.md`, Docker assets when touched); commit and push on `mgagp/ezkey-demo-device`.
3. **Maintainer final validation on standalone** — operator runs the standalone Docker path (e.g. `./start.sh` on `:3080`) against the intended Auth API scenario; slice is not **done** until this step is explicitly recorded in the TB closeout or PR notes.

Automated tests in the monorepo do not replace step 3: standalone Compose defaults, ports, and Auth
API targets differ from clean-start.

Reference slice: `TB-2026-06-18-demo-device-qr-auth-url-parity`.

## How examples should use Ezkey

Methodology documents may mention Ezkey when doing so provides a concrete example or preserves
historical provenance. They should avoid carrying Ezkey-specific enumerations inline when a generic
method concept plus a link to this case study is clearer.
