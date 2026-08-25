# JavaMelody curated — campaign notes

Lightweight HITL decision track for punctual `javamelody-curated` passes.

This folder is peripheral to product vision / ADR / backlog execution. It records per-campaign
performance triage (fix / defer / skip) so cold sessions can see why a JavaMelody signal was acted
on or left alone — without inventing `I-*` / `TB-*` / GitHub issues per row.

## When to use

After a **known workload** on a live clean-start stack with `--with-java-melody` (typically
operational churn). The script freezes collector XML, ranks HTTP/SQL/Spring by total time, and
proposes a small lot. High hit count on churn protocol paths is **not** a signal by itself.

## Contents

| Path | Role |
| --- | --- |
| [`TEMPLATE.md`](TEMPLATE.md) | Copy for each new campaign / lot |
| `YYYY-MM-DD-pass-N.md` | Dated instance (decisions table + short rationale) |

## Operating contract

1. Prefer not to clean-start until `logs/javamelody/raw/` holds a dump of the current counters.
2. Run `./scripts/javamelody-curated.sh` (or `--offline` against an existing dump).
3. Read `logs/javamelody/javamelody.curated.md`.
4. Propose a small lot (about 3–6). Walk **one finding at a time**.
5. Record decisions as `fix` / `defer` / `skip`. Do not implement patches until Go.
6. An empty high-signal lot is a valid closeout (baseline captured, no campaign).

## Related

- Hygiene index: [`../README.md`](../README.md)
- Keyword contract: root [`AGENTS.md`](../../../AGENTS.md) § JavaMelody curated
- Config: [`config/javamelody/`](../../../config/javamelody/)
- Collector: [`docker/README.md`](../../../docker/README.md) § JavaMelody collector
