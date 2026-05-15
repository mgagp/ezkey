# `I-2026-0020` — Integration ecosystem shell catalog and publish workflow

- **Date:** `2026-05-11`
- **Status:** `captured`
- **Priority:** `P2`
- **Components:** `docs`, GitHub ecosystem (outside monorepo), optional future `product-docs`

## Problem

Expose a **small, honest set** of named integration-repository shells (SMS vendors, directory sync) aligned with Ezkey naming rules so adopters see integrability posture without implying finished SPI code.

## Desired outcome

- Canonical naming + curated list documented in **[`docs/ECOSYSTEM_REPOSITORIES.md`](../../../../docs/ECOSYSTEM_REPOSITORIES.md)**.
- Staging payloads under **`docs/staging/ecosystem-shells/<repo>/`** consumed for **manual** GitHub repo creation and initial pushes.
- **`V-2026-0007`** stays the vision anchor for peripheral SMS + SPI framing; extend with directory-family bullets when grilling converges.

## Exit criteria

- [ ] Operators can follow publish checklist end-to-end.
- [ ] Public repo URLs mirrored in **`docs/ECOSYSTEM_REPOSITORIES.md`** after creation.
- [ ] Scratch staging deleted post-publish (no lingering duplicate READMEs).

## Traceability

- Vision: **`V-2026-0007`** (SMS + SPI periphery); adjacent directory sync likely follows same SPI/integration discipline once scoped.
- Spec: **`docs/ECOSYSTEM_REPOSITORIES.md`**, shell READMEs under `docs/staging/ecosystem-shells/`.
