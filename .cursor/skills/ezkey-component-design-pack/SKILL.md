---
name: ezkey-component-design-pack
description: Builds component-focused Ezkey design briefs for impacted boundaries, mappings, validation rules, and error handling. Use after tracer bullet definition and before implementation.
disable-model-invocation: true
---
# Ezkey Component Design Pack

## Purpose

Create complementary component-level design slices that align to one global feature or tracer bullet.

## Boundary contract

- **Enter when:** a `TB-*` or feature scope touches component boundaries, mappings, validations, or error behavior.
- **Exit when:** each impacted component has local responsibilities, boundaries, contracts, tests, and doc impact named.
- **Call next:** `ezkey-test-strategy-planner`, then `ezkey-quality-gatekeeper` before implementation.
- **Not needed when:** the change is local, internal, and does not alter component responsibilities or observable behavior.

## Inputs

- `TB-*` or feature scope
- impacted components

## Output target

Use `product-docs/templates/component-design-brief.template.md`.

## Steps

1. For each component, define local responsibilities.
2. Identify inbound/outbound boundaries.
3. Specify mapping and invariant implications.
4. Specify validation and exception behavior.
5. Specify contract and compatibility implications.
6. Specify test and documentation impact.

## Rule

Keep component briefs separable but linked to the same global intent and IDs.
