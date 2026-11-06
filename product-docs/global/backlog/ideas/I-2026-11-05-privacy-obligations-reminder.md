# Backlog Idea — `I-2026-11-05-privacy-obligations-reminder` Privacy obligations: what to pick up if Ezkey meets real use

## Metadata

- **ID:** `I-2026-11-05-privacy-obligations-reminder`
- **Status:** `parked`
- **Priority:** `P3`
- **Created at:** `2026-11-05`
- **Updated at:** `2026-11-06`
- **Last reviewed at:** `2026-11-05`
- **Component tags:** `core`, `admin-api`, `docs`
- **Captured by:** Marc
- **GitHub issue:** _(none; surfaced on `#1206`)_

## Intent

Keep a short reminder of privacy obligations so they are not lost, without taking on work now. Ezkey is a
lab for experimenting with AI on a topic of interest. It was not requested by, and is not promised to, any
company, and its real-world future remains to be discovered. So: take notes seriously, and keep the effort
proportionate.

## What to remember

- **Right to be forgotten:** personal data such as `contact_email`, `user_identifier` and IP addresses in
  audit entries. The tamper-evident audit chain limits what can be erased, so the two must be reconciled.
- **Right to export** personal data in a simple, standard technological format.
- **Connectors (`#1206`):** a mail or SMS provider learns who is enrolling where, which makes it a processor
  for the operator. The operator chooses it and answers for it.
- **Send audit (`#1206`):** each send writes the full recipient address to the tamper-evident chain, to answer
  "where did this QR go?" in an investigation.

## Hypothesis to check

Marc's understanding, not verified (2026-11-06): Law 25 may allow keeping what is required for the normal conduct
of business. Strong authentication, with investigations over a longer retention period, could fall within that
for this segment. This is an allegation to confront with the official sources of the Quebec authority concerned
before relying on it.

## Trigger to reopen

A real adopter, or personal data on an instance beyond lab and evaluation use.

## Starting point if reopened

[`plans/loi25_operator_support_analysis.plan.md`](../../../../plans/loi25_operator_support_analysis.plan.md)
is a detailed Quebec Law 25 work plan (about 1,900 lines, all tasks pending). It was set aside as out of
proportion for a lab. Reduce it before using it. It is not a legal opinion.

## Links

- `V-2026-11-04-connector-spi`, `docs/SECURITY_POSTURE.md`
