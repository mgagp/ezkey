# Error and Exception Model — `<component>`

> Template for a component's `exception-and-error-model.md`, or for a scoped section inside it. Remove this blockquote on instantiation.

## Intent

One short paragraph describing the error handling philosophy for this component (fail-closed, propagate, wrap, localize, audit-only, etc.).

## Error Categories

Short taxonomy that groups errors by nature. Keep the list small.

| Category | Description | Typical response | Examples |
|----------|-------------|------------------|----------|
| **Validation** | Inputs fail business rules. | Reject early, structured response. | Missing field, invalid format. |
| **Authorization** | Caller lacks required access. | Deny and audit. | Wrong role, wrong tenant. |
| **Conflict** | State prevents the action. | Reject with structured reason. | Already completed, lock held. |
| **External** | A dependency fails. | Wrap, retry, or fail-closed. | Downstream API, infra. |
| **Internal** | Programming error. | Fail-closed, log. | Null pointer, assertion failure. |

Adapt the table to the component's reality.

## Error Contract

Describe the shape of errors exposed at each boundary. Link to the [mapping matrices](api-and-boundary-mappings.md) that carry them.

- **External contract.** Examples: RFC 9457 Problem Details, structured JSON, HTTP status codes.
- **Internal contract.** Examples: typed exceptions, result objects.

## Decision Matrix

Turn non-trivial error decisions into a decision table. Use this when behavior branches based on input or state.

| Scenario | Input | State | Decision | Outcome | Reference |
|----------|-------|-------|----------|---------|-----------|
| `<scenario>` | `<input>` | `<state>` | `<branch>` | `<outcome>` | [`anchor`](#anchor) |

## Error Inventory

For each error the component owns, a short entry:

### `<error-id>` `<short name>`

- **Category.** One of the categories above.
- **Trigger.** The precise condition that raises the error.
- **Externalization.** How the error is surfaced to the caller (status code, problem type, UI state).
- **Operator response.** What an operator does when this error happens.
- **Audit behavior.** Whether and how the error is audited.
- **Related workflow.** Link to the workflow that can raise it in [`functional-flows.md`](functional-flows.md).

## Cross-Component Propagation

Describe how errors cross boundaries. For each outbound call:

- What is the expected error shape?
- What is the wrapping policy?
- What is the retry policy?

## Related Documents

- [Workflow definitions](functional-flows.md).
- [Mapping matrices](api-and-boundary-mappings.md).
- [Design decisions](design-decisions.md).
- [Traceability](spec-test-traceability.md).
