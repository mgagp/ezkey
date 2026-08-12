# Ezkey integration ecosystem repositories

This document describes **separate GitHub repositories** (“integration repos” / “ecosystem repos”) that will implement optional connectivity from the Ezkey platform to vendor systems—for example SMS delivery or identity-directory synchronization—once a stable **service integration contract** exists in core.

Ezkey stays **self-contained** in this monorepo. Integration repos remain **thin adapters** (+ documentation) keyed by vendor and capability. They must not redefine Ezkey lifecycle semantics; operators still govern enrollments and tenants according to [`LIFECYCLE_GOVERNANCE.md`](LIFECYCLE_GOVERNANCE.md).

**Terminology note:** Prefer **integration repository** or **ecosystem repository** here. Do not confuse these with operational **“peripheral” APIs** described in audit-chain heartbeat wording in [`AUDIT_LOG_INTEGRITY.md`](AUDIT_LOG_INTEGRITY.md).

## Naming convention

Repositories follow:

`ezkey-<integration-family>-<vendor-or-tech>[-<qualifier>]`

| Segment | Role | Examples |
|--------|------|----------|
| `ezkey-` | Organization-level prefix | (fixed) |
| `<integration-family>` | Ezkey capability extended | `sms`, `directory`, optionally `email` |
| `<vendor-or-tech>` | Vendor or neutral technology token | `twilio`, `aws-sns`, `microsoft-ad`, `ldap` |
| `<qualifier>` | Disambiguation only when needed | Rare |

Rules:

1. **Do not** put `spi` in the repo name. SPI naming belongs in documented contracts inside core and README “Contract” sections.
2. **Prefer one capability per repo** rather than mega-vendor packages. Example: SMS via AWS SNS is **`ezkey-sms-aws-sns`** (or `pinpoint` if standardized later); email via SES would be **`ezkey-email-aws-ses`**—not a vague `ezkey-aws`.
3. **Directory propagation** uses **`ezkey-directory-*`**—distinct from messaging so corporate IdP/sync stories stay clear.
4. **`ezkey-contrib`** remains a future option for community samples—not for curated first-party shells.

Use **canonical vendor spelling** in names (e.g. `twilio`).

## Relation to vision and backlog

- **SMS peripheral strategy and SPI direction:** [`V-2026-0007`](../product-docs/global/vision/product-orientation-notes.md) (`product-docs/global/vision/product-orientation-notes.md`).
- **Backlog tracker for shells + catalog:** [`I-2026-0020`](../product-docs/global/backlog/ideas/I-2026-0020-integration-ecosystem-shell-catalog.md).

## Email stance

Operational email remains **in-core** today (Java Mail and configuration—see **`V-2026-0005`** in [`product-orientation-notes.md`](../product-docs/global/vision/product-orientation-notes.md)). This catalog **does not** list a transactional-email shell unless product direction explicitly adds out-of-core API providers alongside that model.

## Curated shell list (planned integration repos)

Each row is an **approved name** for a future public repository. **Repository URL** placeholders—fill after you create repos on GitHub.

| Planned repository name | Capability | Draft / publish status |
|-------------------------|------------|------------------------|
| *(add URL)* | SMS via Twilio | Staging tree not in git yet — drive via `I-2026-0020` |
| *(add URL)* | SMS via AWS SNS | Staging tree not in git yet — drive via `I-2026-0020` |
| *(add URL)* | Directory ↔ Ezkey enrollment (Microsoft AD) | Staging tree not in git yet — drive via `I-2026-0020` |
| *(add URL)* | Directory ↔ Ezkey enrollment (LDAP) | Staging tree not in git yet — drive via `I-2026-0020` |

After repositories exist, replace *(add URL)* with full `https://github.com/...` links. Optional local staging under `docs/staging/ecosystem-shells/` may be regenerated when publish work resumes (`I-2026-0020`).

## Standard README skeleton (for shells)

Integration-repo README files should remain **honest**:

- **Status** — Shell / implementation not started until SPI contracts land; link `V-2026-0007` and future ADRs or integration guides.
- **Purpose** — One short paragraph on the corporate or operator problem.
- **Relationship to core** — Link to this monorepo; core remains runnable without the shell.
- **Planned responsibilities** — Secrets/credentials, vendor SDK or API calls, Ezkey-facing adapter boundary.
- **Non-goals** — No timelines or certification promises.
- **Security** — Never commit secrets; describe expected secret posture at a high level.

Suggested GitHub **topics:** `ezkey`, `ezkey-ecosystem`, `shell`, plus vendor and capability tags.

## Publish checklist for maintainers

1. Prepare shell content (regenerate under `docs/staging/ecosystem-shells/<repo-name>/` if using a local staging tree, or author directly in the new repo).
2. Create empty Git repository on GitHub with matching name.
3. Copy files into that repo root; push **initial commit**.
4. Attach topics listed above.
5. Update **Repository URL** column in this file.
6. If a local staging tree was used, delete published subtrees (ephemeral by design). Remaining work: `I-2026-0020`.
