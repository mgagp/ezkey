# API Controllers Registry — Archived (Downscoped)

> **Status:** archived — not a living document.
>
> **Superseded by:** [`../methodology/decisions/2026-05-24-controllers-registry-downscope.md`](../methodology/decisions/2026-05-24-controllers-registry-downscope.md)
>
> **Reason:** At current Ezkey scale (~17 API controllers), a permanently synchronized registry
> duplicated OpenAPI and targeted code discovery without enough payoff. Cross-cutting **semantic**
> attributes (audience, tier, sensitivity, rate-limit posture) are captured in **policy outputs**
> when analyses run — not in a standing parallel index.

## Where to look instead

| Need | Canonical home |
|------|----------------|
| Endpoint / controller structure | OpenAPI under `specs/`, [`docs/ENDPOINT.md`](../../docs/ENDPOINT.md), component `functional-flows.md` |
| Rate-limit baseline analysis | [`backlog/ideas/I-2026-0008-rate-limit-baseline-analysis.md`](backlog/ideas/I-2026-0008-rate-limit-baseline-analysis.md) → future policy + `CONFIGURATION.md` |
| SQL volume / repository limits | [`sql-business-limits-policy.md`](sql-business-limits-policy.md) |
| Admin list volume / joins | [`admin-ui-paginated-screens-matrix.md`](admin-ui-paginated-screens-matrix.md) |
| Targeted structural discovery | `@RestController` in `ezkey-*-api` modules (targeted `rg`; no whole-repo scan) |

## Historical context

- Blitz 2026-05-08-2 D1 grill: [`backlog/grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md`](backlog/grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md)
- Backlog item dropped: [`I-2026-0009`](backlog/ideas/I-2026-0009-global-controllers-registry.md)
