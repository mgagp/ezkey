# Feature Brief — `<feature-id>` `<feature name>`

> Template for a single entry in `global/features-and-phases.md` or a component-local feature list. Remove this blockquote on instantiation.

## Intent

One short paragraph: what the feature enables and for whom.

## Milestone

- **Milestone:** `<milestone-id>` — see [`../global/roadmap.md`](../global/roadmap.md).
- **Status:** `planned` / `in-progress` / `implemented` / `deprecated` / `removed`.

## Scope

- **In scope.** Bulleted list of capabilities this feature includes.
- **Out of scope.** Bulleted list of capabilities this feature explicitly excludes.

## User Stories (optional)

Use these only when they clarify scope. Keep them short.

- As a `<role>`, I want `<capability>` so that `<outcome>`.

## Functional Overview

Short description of the feature's behavior. For detailed workflows, link to the relevant entry in the component's [`functional-flows.md`](../components/<component>/functional-flows.md).

## Boundaries and Mappings

List the boundaries this feature crosses and link to the relevant mapping matrices.

- `<component>` ↔ `<external API or module>` — [`api-and-boundary-mappings.md#<anchor>`](../components/<component>/api-and-boundary-mappings.md#<anchor>).

## Persistence Impact

Describe any persistence implications. Link to the relevant section of [`data-model-and-persistence.md`](../components/<component>/data-model-and-persistence.md).

## Exception and Error Handling

Summarize how failure modes are handled. Link to the relevant entries in [`exception-and-error-model.md`](../components/<component>/exception-and-error-model.md).

## Acceptance Criteria

A short list of verifiable criteria. Each criterion is observable and mapped to at least one test in [`spec-test-traceability.md`](../global/spec-test-traceability.md).

- `<criterion>`
- `<criterion>`

## Dependencies

- Other features, milestones, or external systems this feature depends on.

## Open Questions

Questions still to resolve. Remove when empty.

## Related Documents

- [Feature catalog entry](../global/features-and-phases.md#<feature-id>)
- [Component pack](../components/<component>/README.md)
- [Specs](link-to-openapi-or-internal-spec)
- [Tests](link-to-test-suite)
