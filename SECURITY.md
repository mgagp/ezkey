# Security Policy

Ezkey is a **public, experimental security and MFA laboratory** (community lab, public alpha). It is **not** production-qualified and offers **no bug bounty**.

For honest product-level security claims and limits, see [`docs/SECURITY_POSTURE.md`](docs/SECURITY_POSTURE.md).

## Supported versions

Security attention focuses on:

- the **`main`** branch (current development line), and
- the **latest published release**, once GitHub Releases exist.

There are **no published release packages yet**. Do not treat historical tags or third-party builds as supported unless they match `main` or a later published release.

## Reporting a vulnerability

**Do not open public GitHub issues for security vulnerabilities.**

Report privately using GitHub’s **Report a vulnerability** form (private vulnerability reporting):

https://github.com/mgagp/ezkey/security/advisories/new

You may also email **security@ezkey.org** if the GitHub form is unavailable.

Please include:

1. A short description of the issue and its impact
2. Affected component or path (API, Admin UI, mobile, crypto, deploy, etc.)
3. Steps to reproduce, or a minimal proof of concept when practical
4. The commit, branch, or deploy SHA you tested against, if known

## What to expect

This is a small community lab project maintained primarily by [@mgagp](https://github.com/mgagp), with AI-assisted review.

Response is best effort, with no SLA. We usually acknowledge reports within a few days.

After remediation where applicable, we may publish a GitHub Security Advisory and credit the reporter unless anonymity is requested.

Published advisories: https://github.com/mgagp/ezkey/security/advisories

## Out of scope

Reports that are generally out of scope include:

- Social engineering or physical attacks on devices or operators
- Issues solely in third-party services or dependencies (prefer reporting upstream)
- Generic denial-of-service without a clear, significant impact demonstration
- Unverified automated-scanner output with no reproduction steps
- Issues already fixed on `main` or already published in advisories

## Design compass (fail-open vs fail-closed)

When a control or side effect can fail at a trust or availability boundary, name whether the primary
path **continues** (fail-open; failure must stay observable) or **stops** (fail-closed). Canon:
[`product-docs/global/design-principles.md`](product-docs/global/design-principles.md) §17.

## Security practices in this repository

The following are **actually in use** today (they are hygiene and review practices, not a production guarantee):

- Required pull-request CI aggregate check **`ci-gate`** (GitHub Actions)
- **CodeQL** code scanning (default setup, non-blocking)
- **Dependabot** alerts and security updates; version updates for Maven, npm, pip and GitHub Actions
- **Secret scanning** with **push protection**
- Periodic AI-assisted security and hygiene passes, reviewed by the maintainer (static analysis, local API pentest campaigns, Dependabot triage); see `product-docs/global/hygiene/`

Ezkey does **not** claim mandatory peer review of every change, anomaly-detection product features, or real-time operational alerting as part of this policy.

## Non-security contact

For ordinary bugs and questions, use [GitHub Issues](https://github.com/mgagp/ezkey/issues).

---

**Last updated:** 2026-10-08
