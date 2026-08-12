# Cursor plans — archive staging

**Purpose:** Holding area for completed Cursor plans while the **corpus-ablation** hygiene pass
liquidates them into living canon (`docs/`, `product-docs/`, module `AGENTS.md`). Git history is
the durable archive — do not rely on this folder as a discovery layer after liquidation.

**Active pass:** [`product-docs/global/hygiene/corpus-ablation/2026-08-cursor-plans-pass.md`](../../../product-docs/global/hygiene/corpus-ablation/2026-08-cursor-plans-pass.md)
(skill: `.cursor/skills/corpus-ablation/SKILL.md`).

## Remaining on disk (pending HITL prune)

| Location | Notes |
|----------|--------|
| `2026-04/` | Large set (~40 plans); ablation in progress alphabetically |
| `2026-05/` | Smaller set |
| Loose files | `jpa_field_initialization_final_recommendations.md`, `phase2_orval_hooks_migration_inventory.md`, `rejected_showing_as_expired_fix.md` |

Folders `2026-01/` and `2026-03/` were emptied by this pass (plans deleted after canon capture).

**Retained (not under `archived/`):** `.cursor/plans/authentication_wait_evolution_brainstorm.plan.md`
(operator Keep — Lane B option space).

## Operator rule

Do **not** invent a second inventory of every deleted plan here. When a plan is liquidated, update
the pass **Done** table only. Delete this README when `archived/` is empty or reduced to intentional
keepers.
