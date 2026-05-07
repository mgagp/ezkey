---
name: ezkey-traceability-sync
description: Synchronizes Ezkey feature, specification, and test traceability across global and component documentation. Use when feature status, behavior, or validation evidence changes.
disable-model-invocation: true
---
# Ezkey Traceability Sync

## Purpose

Keep feature-to-spec-to-test linkage current after any meaningful change.

## Inputs

- Changed feature or tracer bullet
- Updated specs/docs/tests

## Target documents

- `product-docs/global/features-and-phases.md`
- `product-docs/global/spec-test-traceability.md`
- relevant `product-docs/components/*/spec-test-traceability.md`

## Steps

1. Confirm impacted feature IDs.
2. Update feature status if it changed.
3. Update spec references and acceptance criteria links.
4. Update test-suite references and current status.
5. Record open gaps explicitly when coverage is incomplete.

## Rule

Never hide coverage gaps. Track them explicitly with ownership and next action.
