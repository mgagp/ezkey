# Spec-Test Traceability — `<scope>`

> Template for `global/spec-test-traceability.md` and component-level `spec-test-traceability.md`. Remove this blockquote on instantiation.

## Intent

One short paragraph describing what this traceability covers and how it supports spec-first, test-driven work.

## Traceability Model

The traceability links four artifacts:

1. **Feature** — an entry in [`../../global/features-and-phases.md`](../../global/features-and-phases.md) (or the global catalog for a global matrix).
2. **Spec** — the artifact that defines expected behavior (OpenAPI operation, internal spec section, workflow document, mapping matrix).
3. **Acceptance criteria** — observable criteria from the feature brief or workflow document.
4. **Tests** — the suites that verify the acceptance criteria.

```mermaid
flowchart LR
    featureEntry[Feature Entry] --> specArtifact[Spec Artifact]
    specArtifact --> acceptance[Acceptance Criteria]
    acceptance --> tests[Tests]
    tests --> evidence[Evidence and Status]
```

## Traceability Matrix

| Feature | Spec | Acceptance criterion | Test suite | Test identifier | Status |
|---------|------|----------------------|------------|-----------------|--------|
| [`<feature-id>`](../../global/features-and-phases.md#<feature-id>) | [`<spec link>`](#) | `<observable criterion>` | `<suite>` | `<test id>` | `planned` / `implemented` |

## Coverage Summary

- **Feature count.** Total features in scope of this matrix.
- **Covered features.** Features with at least one passing test for each criterion.
- **Gaps.** Features with missing tests or specs.

## Open Gaps

List unresolved gaps with an owner and a proposed next step. Remove entries when resolved.

- `<feature-id>` — `<gap description>` — owner: `<role>` — next: `<action>`.

## Update Cadence

- When a feature is added or modified, update this matrix in the same change set.
- When a test is added or removed, update the matrix in the same change set.
- Review the matrix at the start and end of each phase.

## Related Documents

- [Features and phases](../../global/features-and-phases.md).
- [Roadmap](../../global/roadmap.md).
- Component-level matrices linked from [components index](../../components/README.md).
