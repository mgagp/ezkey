# Handoff — <finding-id>-<short-kebab-title>

**Status:** `open` — analysis only / **fix authorized** / ordered after …  
**Keyword:** `assessment-curated`  
**Finding:** \<ID\> (severity, confidence)  
**Campaign:** `product-docs/global/hygiene/<lane>/YYYY-MM-DD-pass-N.md`  
**Assessment:** path + section

Use this prompt to start a **new Cursor session**. Default: do **not** implement unless status says
fix authorized. Delete this file on PR/closeout after consolidating into the campaign note.

---

## Operator decisions (already made)

1. **Disposition:** …
2. **Prerequisites / order:** …
3. HITL briefing accepted YYYY-MM-DD.

---

## One-sentence problem

…

---

## Scenario that led to the observation (preserve this narrative)

…

**Non-claim:** …

---

## Evidence map (read these first)

- …

---

## Intended fix shape _(if fix)_ / analysis goals _(if analysis)_

…

### Tests (minimum)

- …

---

## Product / methodology constraints

- Lane D hygiene; no `I-*` / `TB-*` unless operator asks after Grill Me.
- Out of scope: …

---

## Paste-ready starter message

```text
Read product-docs/global/backlog/handoffs/HANDOFF-<this-file>.md end-to-end.
Then read the assessment section cited above.

Task: <one paragraph>. Do not create I-*/TB-* unless I ask. Do not commit until I ask.
```
