---
name: admin-ui-doc-reframing
overview: "Revise the high-level Ezkey documentation so the Admin UI is explicitly recognized as a first-class product surface and the two administrative audiences are clearly framed: Global Admin for platform/operations and Tenant Admin for day-to-day tenant administration."
status: completed
completedAt: 2026-04-02
todos:
  - id: identify-core-doc-insertions
    content: Mark the exact short sections in README, PRD, PROJECT_POSITIONING, and ARCHITECTURE where Admin UI and role framing should be inserted.
    status: completed
  - id: define-canonical-role-wording
    content: Draft one compact wording set for Admin UI, Global Admin, and Tenant Admin that can be reused across all revised docs.
    status: completed
  - id: align-supporting-docs
    content: Update docs index and supporting Admin UI/CLI READMEs so they reinforce the same hierarchy and audience split.
    status: completed
  - id: consistency-review
    content: Review all revised docs together for brevity, tone, and consistent positioning of Admin UI versus CLI and APIs.
    status: completed
isProject: false
---

# Admin UI Documentation Reframing

## Goal

Add a short, consistent Admin UI presence across the core project documentation without undoing the recent simplification. The revised wording should stay brief, pragmatic, and aligned with the new product posture.

## Documentation Focus

Update the top-level docs that define how newcomers understand Ezkey:

- [c:\github\ezkey\README.md](c:\github\ezkey\README.md)
  - Add `Admin UI` to the current product surface beside the APIs, mobile app, and CLI.
  - Clarify that the web UI is the primary human administration surface.
  - Keep the CLI mention, but position it as complementary rather than the main admin entry point.
- [c:\github\ezkey\PRD.md](c:\github\ezkey\PRD.md)
  - Expand the current “administration” framing so it explicitly includes the Admin UI.
  - Add concise audience language for:
    - developers and technical readers approaching the project,
    - Global Admin operators managing tenants, platform operations, and cryptographic keys,
    - Tenant Admin operators managing integrations and day-to-day tenant administration.
  - Reflect this split in the product surface list and/or target audience section without turning the PRD into role documentation.
- [c:\github\ezkey\docs\ARCHITECTURE.md](c:\github\ezkey\docs\ARCHITECTURE.md)
  - Update the high-level system view so the Admin UI appears as part of the architecture, not only the Admin API.
  - Make the operator-facing path explicit: Admin UI -> Admin API -> Core.
  - Add a short note that the UI serves both platform-wide and tenant-scoped administration while the backend remains the root of trust.
- [c:\github\ezkey\docs\PROJECT_POSITIONING.md](c:\github\ezkey\docs\PROJECT_POSITIONING.md)
  - Keep the existing backend-first thesis, but add one concise passage that the Admin UI is an operational surface for real administrators, not an afterthought.
  - Tie this to the two admin audiences in one short, sober paragraph.

## Alignment Pass

Adjust supporting entry-point docs so the messaging stays coherent:

- [c:\github\ezkey\docs\README.md](c:\github\ezkey\docs\README.md)
  - Surface the Admin UI documentation more clearly in the index and quick-start paths.
  - Make it easier for readers to understand where web administration fits relative to APIs and operations docs.
- [c:\github\ezkey\ezkey-cli-python\README.md](c:\github\ezkey\ezkey-cli-python\README.md)
  - Keep the current “Admin UI is primary” direction.
  - Tighten any wording if needed so it matches the revised top-level docs exactly.
- [c:\github\ezkey\ezkey-admin-ui\README.md](c:\github\ezkey\ezkey-admin-ui\README.md)
  - Verify that its role description aligns with the new core framing for Global Admin and Tenant Admin.
  - Only make small wording adjustments if the main docs introduce new canonical phrasing.

## Canonical Messaging To Apply

Use one consistent, short framing across the revised docs:

- Ezkey includes an `Admin UI` for human administration, in addition to its APIs, mobile app, and CLI/tooling.
- `Global Admin` is the platform and operations audience: tenant management, operational oversight, and sensitive platform actions such as cryptographic key operations.
- `Tenant Admin` is the business-side administration audience: integrations, enrollments, API keys, and day-to-day tenant operations.
- The Admin UI is the main day-to-day operator surface; the backend APIs remain the source of trust and control.

## Editing Approach

Keep the revision small and intentional:

- Prefer short additions to existing sections over new long sections.
- Reuse the same role wording across files to avoid drift.
- Avoid overexplaining the UI; the purpose is visibility and clarity, not feature inventory.
- Preserve the recent simplified tone and avoid marketing language.

## Validation

After drafting the changes:

- Re-read the touched docs together to confirm the same Admin UI / Global Admin / Tenant Admin wording appears consistently.
- Check that the CLI is still represented, but no longer reads like the only human administration surface.
- Confirm the architecture and product-surface sections now mention the Admin UI explicitly.

