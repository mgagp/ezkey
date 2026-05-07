---
name: ezkey-retrofit-curator
description: Curates legacy retrofit slices by mapping extracted signal (plans, verbal, ad hoc) into canonical product-docs destinations, recording residual gaps, and promoting principle candidates when relevant.
disable-model-invocation: true
---
# Ezkey Retrofit Curator

## Purpose

Convert mined historical signal into canonical updates with explicit traceability.

## Inputs

- Extracted signal from legacy plans
- Source metadata and source type
- Target topic and canonical destinations

## Output target

Use `product-docs/templates/legacy-plan-retrofit.template.md` and create/update `R-*` retrofit slices under `product-docs/global/legacy-retrofit/`.

## Steps

1. Create or update an `R-*` slice.
2. Map each signal item to canonical destination docs.
3. Apply targeted canonical updates.
4. Record residual gaps and next action.
5. Update `product-docs/global/legacy-retrofit/index.md`.
6. When durable project values emerge, propose principle adoption targets.

## Rule

Do not duplicate old plans. Keep canonical docs concise and link source references in the retrofit slice.
