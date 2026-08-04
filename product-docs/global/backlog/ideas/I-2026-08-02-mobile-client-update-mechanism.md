# Backlog Idea — `I-2026-08-02-mobile-client-update-mechanism` Mobile: client app-update mechanism

## Metadata

- **ID:** `I-2026-08-02-mobile-client-update-mechanism`
- **Status:** `incubating`
- **Priority:** `P3`
- **Created at:** `2026-08-02`
- **Updated at:** `2026-08-02`
- **Last reviewed at:** `2026-08-02`
- **Progression markers:** `P3-distribution`
- **Component tags:** `mobile`, `docs`
- **Lane:** `D`
- **Captured by:** Marc
- **GitHub issue:** none

## Intent

Add a best-practice mechanism for telling an installed Ezkey Mobile app that a newer version is
available, and — only if a concrete need is demonstrated — for discouraging continued use of a
severely outdated build. Anchor this signal in a **distribution-controlled** source (Play Store
metadata or a channel Ezkey itself controls), not in a claim made solely by the self-hosted Auth API
backend the app happens to be enrolled with.

## Problem and value

- **Problem:** Nothing today tells a user with an installed Ezkey Mobile app that a newer version
  exists. This is a normal gap for a one-shot experimental drop, but becomes a real product gap once
  Ezkey Mobile is an official, evolving Play app with a real install base: users can silently sit on
  old builds indefinitely, which compounds both UX debt (missed improvements) and eventual protocol
  drift if `I-2026-0025` capability negotiation ever needs a break.
- **Expected value:** Reduce silent client staleness, give users a clear, low-friction path to
  update, and reduce the surface area on which `I-2026-0025`'s "current + previous generation"
  window has to carry very old, unmaintained clients.

## Scope

- **In scope:**
  - Soft "update available" guidance, sourced from Play in-app update APIs (Google Play In-App
    Updates) or a lightweight Ezkey-controlled distribution channel (e.g. a signed metadata endpoint
    hosted on `ezkey.org`), not from the self-hosted Auth API instance.
  - Analysis of whether a **hard** minimum-version gate is warranted at all for Ezkey's threat model
    and market (self-hosted, operator-controlled backends, not a multi-tenant SaaS with a single
    vendor-controlled fleet), and if so, what triggers it (e.g. only after `I-2026-0025` actually
    retires a generation).
  - Explicit statement of how this mechanism composes with `I-2026-0025` protocol capability
    negotiation: this idea handles "tell the user a newer app exists"; `I-2026-0025` handles "refuse
    or degrade an incompatible protocol exchange."
- **Out of scope:**
  - Any hard-block keyed **solely** on a claim from the self-hosted backend instance (see the
    threat-model note in
    [`I-2026-08-02-mobile-installation-version-and-compat-discovery`](I-2026-08-02-mobile-installation-version-and-compat-discovery.md)
    for why that would be a fail-closed design keyed on an unauthenticated, operator-controlled
    signal).
  - Any change to Auth API protocol contracts — that remains `I-2026-0025`'s scope.
  - Multiple Play Store app lines or version-specific store listings — explicitly out of scope per
    `V-2026-0008` / `I-2026-0025` and unchanged here.

## Key assumptions

- A real install base (beyond the invited experimental audience) is either already forming or
  expected soon after the official release ships — this idea's priority should be revisited once
  that is true.
- Play's own in-app update mechanism is the lowest-effort, most trustworthy distribution-anchored
  signal available and should be evaluated first before building a custom Ezkey-hosted channel.

## Risks and exceptions

- Building a custom update-check endpoint before evaluating Play's native mechanism risks
  reinventing a solved problem and adding an unnecessary network dependency.
- A hard update gate implemented too aggressively could lock out users in environments with delayed
  Play access (e.g. managed devices, regions with delayed rollout) — any hard gate must have a
  documented escape hatch.

## Promotion notes

**Incubating** — a light grill session is recommended before promotion to `ready`, to settle
whether any hard gate is warranted at all (see Scope) and to confirm the boundary against
`I-2026-0025` and against
[`I-2026-08-02-mobile-installation-version-and-compat-discovery`](I-2026-08-02-mobile-installation-version-and-compat-discovery.md).
Keep `incubating` until:

- the official release (messaging + compliance gate ideas) has shipped and a real install base
  exists to justify prioritizing this,
- Play's native in-app update capability has been evaluated against Ezkey's actual Play Console
  setup,
- the grill session above has run.

## Links

- Vision:
  [`V-2026-08-02-mobile-official-play-release-posture`](../../vision/V-2026-08-02-mobile-official-play-release-posture.md)
- Sibling ideas in the same program:
  [`I-2026-08-02-mobile-exit-experimental-messaging`](I-2026-08-02-mobile-exit-experimental-messaging.md),
  [`I-2026-08-02-mobile-play-official-compliance-gate`](I-2026-08-02-mobile-play-official-compliance-gate.md),
  [`I-2026-08-02-mobile-installation-version-and-compat-discovery`](I-2026-08-02-mobile-installation-version-and-compat-discovery.md)
- Complementary (not duplicated) protocol-compatibility track:
  [`V-2026-0008-auth-api-protocol-versioning.md`](../../vision/V-2026-0008-auth-api-protocol-versioning.md),
  [`I-2026-0025-auth-api-protocol-capability-versioning.md`](I-2026-0025-auth-api-protocol-capability-versioning.md)
