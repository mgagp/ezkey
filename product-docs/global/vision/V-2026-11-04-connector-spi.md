# Vision Note — `V-2026-11-04-connector-spi` Connector SPI for external services

## Metadata

- **ID:** `V-2026-11-04-connector-spi`
- **Status:** `under-review`
- **Created at:** `2026-11-04`
- **Updated at:** `2026-11-04`
- **Captured by:** Marc
- **Priority:** `P2`
- **GitHub issue:** `#1206`
- **Supersedes:** the in-core email transport stated in `V-2026-0005` (the rest of that note stands)

## Intent

Connect optional external services to an Ezkey base by configuration, through one modular approach:
email first, then SMS, then corporate directories. Ezkey stays complete with no connector; on-screen
QR and challenge remain a valid posture for evaluation and for operators who accept that risk.

## Motivation

Integrated delivery works, but an adopting SME will want invitations sent for them, and eventually
wants people onboarded and offboarded from its directory. The original example is a Duo integration
in Marc's workplace: every four hours, a job read a chosen Active Directory subtree and brought those
people into the platform to complete their setup. For Ezkey, the same mechanism should also withdraw
an enrollment when IT removes someone from the directory.

Email is the quick win (low risk, high value) and the first real test of the approach.

**What directory sync does and does not bring.** It automates creating and withdrawing enrollments, and
withdrawal then needs no human action, which is the immediate gain. Until SMS and automatic sending are
decided, getting each new person enrolled stays one operator action per person. That fits the SME target,
where changes typically involve a handful of people at a time.

## Direction

- **Connector:** a separate process, reached over a versioned HTTP contract.
- **Delivery family:** Ezkey calls the connector when an operator sends.
- **Directory family:** the connector calls Ezkey on its own schedule, from inside the company network.
- **Secrets:** provider credentials stay on the connector side.
- **Message content:** email carries the QR; the challenge stays on screen, or goes by SMS later. An
  admin activation code never goes through a connector.
- **Two modes:** integrated delivery, and connector-assisted delivery in whatever form. The operator
  passes on what remains by any means they accept.
- **Bind session:** scanning starts a sequence, so both items must be in hand first. They may arrive
  days apart, and Ezkey never synchronizes channels or connectors.
- **Code location:** the monorepo, like the SDKs, PAM and Jenkins; extraction is a later question.
- **First target:** the mechanism with a mock connector usable by Docker, unit and functional tests,
  and as a CI gate. Then SMTP, a real trial with a Google account, then the community instance.

The analysis, diagrams and rejected options are in the
[decision pack](../decisions/2026-11-04-connector-spi/README.md). The decision is
[ADR-0018](../architecture-decisions.md#adr-0018--connector-spi-for-external-services).

## Potential impact

- **Product milestones:** none blocked; this must not slow down PAM or the community alpha.
- **Components:** `admin-api`, `admin-ui`, `infra`, `docs`, a new `ezkey-connectors/` folder; later a
  new inbound surface and enrollment schema changes for directories.
- **User segments:** SME operators, and evaluators on the community instance.

## Signals and constraints

- Design principle #16 already calls for an SPI; `V-2026-0005` contradicted it for email.
- The 2026-05-19 SMS decisions (`V-2026-0007`) already chose an HTTP adapter for delivery. They stand.
- The Admin API machine-to-machine hatch was removed on purpose (`I-2026-08-29`), so a directory
  connector needs its own surface.
- Lab posture covers the connectors. Core changes keep normal rigor because they are meant to ship.

## Promotion criteria

Promote once ADR-0018 is accepted and the delivery contract exists as a `TB-*` (step 2 of `#1206`).
SMS and directory work are decided separately after the community instance step.

## Related documents

- [`V-2026-0005`](V-2026-0005-email-integration-strategy.md), [`V-2026-0007`](V-2026-0007-sms-integration-spi.md),
  [`V-2026-06-28`](V-2026-06-28-audit-archive-export-spi.md)
- `I-2026-0020`, `I-2026-0023`, `I-2026-0024`, `I-2026-11-03-enrollment-invitation-links`
- [`docs/ECOSYSTEM_REPOSITORIES.md`](../../../docs/ECOSYSTEM_REPOSITORIES.md),
  [`docs/LIFECYCLE_GOVERNANCE.md`](../../../docs/LIFECYCLE_GOVERNANCE.md)
