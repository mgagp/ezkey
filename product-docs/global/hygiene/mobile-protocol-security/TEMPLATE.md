# Mobile protocol / crypto security campaign - YYYY-MM-DD pass-N

## Metadata

- **Date:** YYYY-MM-DD
- **Branch / PR:** _(fill when opened)_
- **Assessment:** `docs/security/mobile-protocol-crypto-assessment-YYYY-MM.md`
- **Method:** non-intrusive white-box / static only _(or note approved dynamic follow-up)_
- **Operator:** Marc (+ agent)

## Lot under review

Brief numbered overview only (finding ID + one-line title + severity). Keep small (about 3-6).

**Agent briefing style:** iterate one finding at a time in dialogue with the operator; record the
final decisions table here after HITL closes. Do not paste raw tool dumps.

## Decisions

| # | Finding | Severity | Decision | Action |
| --- | --- | --- | --- | --- |
| 1 | `MOB-NNN` | P1/P2/P3 | fix / defer / suppress / skip | PR / commit / evidence (not a durable handoff path) |

Decision values:

- **fix** - source/test/docs change accepted after HITL; optional ephemeral `HANDOFF-*.md` while coding, then consolidate here and delete the handoff on closeout
- **defer** - acknowledged; track for later program or next pass
- **suppress** - intentional posture; reason recorded; do not “perfect” harmless over-defense
- **skip** - duplicate, fuzzy, or not worth acting on

## Rationale (short)

One short subsection per item (2-8 lines). Record risks, design posture, and why not the alternatives.

### 1 - ...

### 2 - ...

## Follow-ups

- Ephemeral handoffs deleted after consolidation (list which)
- `I-*` / `TB-*` promoted (if any)
- Code/docs/tests touched
- Physical-device validation required?
- Assessment § backlog / dispositions updated?

## Out of scope this pass

- Active exploitation / MITM lab
- iOS parity (unless documentation overclaims)
- Unrelated Admin UI or API DAST findings
