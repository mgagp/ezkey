# Tracer Bullet Brief — `TB-YYYY-MM-DD-<slug>` `<title>`

## Metadata

- **ID:** `TB-YYYY-MM-DD-<slug>`
- **Status:** `draft` / `under-review` / `promoted` / `archived`
- **Related idea:** `I-YYYY-MM-DD-<slug>`
- **GitHub issue:** `#NNN` _(optional)_
- **GitHub branch:** `feature/<NNN>-<i-artifact-id-lowercase>` _(optional; set when implementation starts)_
- **GitHub PR:** `#NNN` _(optional)_
- **Created at:** `YYYY-MM-DD`
- **Updated at:** `YYYY-MM-DD`
- **Captured by:** `<name or initials of the human contributor>`

## Objective

Define the smallest end-to-end slice that proves the intended direction.

> Example: Prove that Admin UI initiated API key rotation can complete end to end through the Admin
> API, expose the secret once, and leave a traceable audit event.

## Vision lock trace

Map every vision lock to a brief section or an explicit deferral. Product Direction signs this table
at G1; an unmapped lock blocks G1.

| Vision lock | Brief section | Or deferral + reason |
|-------------|---------------|----------------------|
| | | |

## Pre-existing conditions touched

Required only for briefs that touch runtime paths (docs-only briefs are exempt). Keep to 5–10 lines
with `path:line`. Craft GO requires this section when applicable. Cover:

- waits / long-polls and client cadences
- client / SDK timeouts
- proxy timeouts (Cloudflare, Caddy)
- TTLs, concurrency, rate limits
- dependency loggers and effective log level
- error-detection contract (classify by exception type, not message; fail-open / fail-closed per
  `design-principles.md` §17)
- existing user-facing copy on the path

## Host

For exposed surfaces or wait paths only. ≤5 lines; mark each point `[live]` or `[repo]`. A
header-trust point marked `[repo]` blocks GO until proven live.

- IP chain and trusted headers
- ports
- proxy timeouts on the path
- runtime output of all containers for "never logged" properties
- deploy pitfalls

## Boundaries in scope

- Component A ↔ Component B
- Component B ↔ External contract

## Out of scope

- explicit exclusions for this slice

## First executable slice

- the smallest meaningful end-to-end cut

> Example: rotate one API key from the Admin UI detail screen with backend validation, success
> feedback, and one targeted regression test.

## Rollback or fallback posture

- how to stop, defer, or narrow the slice if evidence is negative

## Critical flows

- nominal path
- critical exception path

## Evidence plan

- expected tests
- expected documentation updates
- expected traceability updates

> Example:
>
> unit test for secret-once enforcement; one functional UI or API path for successful rotation;
> updates to the mapping matrix and spec-test traceability row.

## Quality gates

- analysis gate
- design gate
- implementation gate

## Exit criteria

Conditions that mark this tracer bullet as validated.

> Example: one operator can rotate a key end to end on the clean stack, the secret is never shown a
> second time, and the updated behavior is traceable in docs and tests.
