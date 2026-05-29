# Mapping Matrix — `<mapping-id>` `<short title>`

> Template for a single mapping entry inside a component's `api-and-boundary-mappings.md`. Remove this blockquote on instantiation.

## Intent

One short paragraph describing what is being mapped and why. A mapping always involves a **boundary**: a place where one representation is translated into another.

> Example: Map the Admin UI API key rotation form to the Admin API request and response contract
> while making one-time secret display rules explicit.

## Boundary

- **Left side.** `<internal model, service, screen, or internal component>`.
- **Right side.** `<external API, DTO, database schema, or sibling component>`.
- **Direction.** `inbound` / `outbound` / `bidirectional`.
- **Trigger.** When this mapping is invoked (workflow, user action, scheduler).

> Example: **Left side** Admin UI rotation form state. **Right side** Admin API
> `POST /api/v1/api-keys/{id}/rotate` contract. **Direction** `bidirectional`.
> **Trigger** Tenant Admin confirms key rotation.

## Reference Artifacts

- **Source of truth on the right side.** Link to spec or schema (for example, generated OpenAPI file, DTO class, or database table).
- **Source of truth on the left side.** Link to internal model, class, or concept.

## Field Mapping

| Left field | Right field | Transformation | Required | Notes |
| --- | --- | --- | --- | --- |
| `<leftField>` | `<rightField>` | `identity` / `enum.name()` / `toBase64Url()` / custom | `yes` / `no` / `conditional` | Explanation if needed. |
| `integrationId` | `integrationId` | `identity` | `yes` | Stable identifier selected from current screen context. |
| `rotationReason` | `reason` | `trim()` | `conditional` | Required only when the operator provides an explicit reason. |
| `newSecret` | `secret` | `identity` | `response-only` | Must never be persisted in UI state beyond the display session. |

Add additional columns only when they add real signal (for example: nullability rules, default values, version gating).

## Decision Table (optional)

When the mapping involves conditional logic, express it as a decision table.

| Input condition | Left value | Right value | Outcome |
| --- | --- | --- | --- |
| `<condition>` | `<leftValue>` | `<rightValue>` | `<outcome>` |

## Constraints and Invariants

- Explicit invariants that must hold after mapping (for example, size limits, encoding, signing rules).

## Error Mapping

When an error can be raised during mapping or during the call, reference the corresponding entry in [`exception-and-error-model.md`](exception-and-error-model.md) and explain the policy (for example, fail-closed, propagate, wrap).

## Lifecycle Coupling

- Is the mapping versioned? If so, how (spec version, feature flag).
- What is the compatibility policy?

## Related Documents

- Workflow that invokes this mapping in [`functional-flows.md`](functional-flows.md).
- Data model entry in [`data-model-and-persistence.md`](data-model-and-persistence.md).
- Acceptance criteria and tests in [`spec-test-traceability.md`](spec-test-traceability.md).
