# Methodological Values

## Purpose

This document defines the values that guide Ezkey's product-docs methodology.

The methodology is project-backed, but the values are intentionally broader: they describe how a
human and AI collaboration workflow should preserve clarity, judgment, and traceability without
adding ceremony for its own sake.

Use these values when a process choice is ambiguous: whether to create a living document, close a
blitz, promote an idea, add a skill, run another gate, or stop and pivot.

## Core Values

### 1. Proportional rigor

Use the lightest process that still preserves the quality of the decision.

The amount of methodology should follow uncertainty and risk, not artifact size or process habit.
A simple, bounded change should move quickly. A risky or unclear change deserves more challenge,
design, and evidence.

Decision test: **what concrete risk does this extra artifact, gate, or template reduce?**

### 2. Internal methodological integrity

The methodology should be self-contained enough to guide a tired human and a cold agent.

Each lane, skill, status, and artifact type should connect to the others through explicit entry and
exit criteria. A session should be resumable from the corpus without relying on memory of the
previous operator or agent.

Decision test: **can someone resume this state later and know what is valid, what is next, and what
is no longer needed?**

### 3. Explicit uncertainty

Uncertainty must be named instead of hidden behind fluent prose.

Statuses such as `captured`, `incubating`, `ready`, `mapped`, `integrated`, `dropped`, and
`superseded` exist to express what is known and what remains open. A methodology output is better
when it makes uncertainty visible and actionable.

Decision test: **does this artifact say what confidence level or lifecycle state it supports?**

### 4. Readable state boundaries

Every lane and skill should answer four questions:

- **Enter when:** what condition makes this step useful?
- **Exit when:** what evidence means this step has done its job?
- **Call next:** what step usually follows?
- **Not needed when:** what condition keeps the fast path fast?

This keeps the workflow close to a finite-state machine without turning it into a rigid machine.

Decision test: **are the transition boundaries understandable by a tired human and a cold agent?**

### 5. Bidirectional traceability

Traceability must work forward and backward.

Forward traceability answers where an idea goes next. Backward traceability answers why an artifact
exists, which source produced it, what superseded it, and whether the original source was fully
integrated.

Decision test: **from this document, can we navigate both to the source and to the durable outcome?**

### 6. Earned permanence

A living document must earn its maintenance cost.

Materializing an artifact is not the same as adopting it as a standing registry or living matrix.
Living documents need a trigger, an owner or owning workflow, and a clear failure signal when they
remain stale or empty.

Decision test: **who updates this, when, and what happens if it stays empty or stale for 30 days?**

### 7. Evidence before automation

Create skills, checks, and sync workflows after the manual pattern has proven useful or drift has
been observed repeatedly.

Automation is valuable when it protects a real workflow. It becomes debt when it stabilizes an
artifact before the artifact has proven that it deserves to exist.

Decision test: **has the manual version produced value, or has repeated drift shown the need for a
check?**

### 8. Preserved history, clear current truth

Historical reasoning should remain recoverable, but it must not be mistaken for current canon.

Grill sessions, archives, and retrofit slices preserve how decisions emerged. Current truth belongs
in canonical documents, decisions, policies, or explicit post-decision notes.

Decision test: **does this preserve the old reasoning while making the current state unambiguous?**

### 9. Honest closure

Closing a methodology slice means the methodology obligations are complete, not that every spawned
product implementation is finished.

For example, a blitz can be fully integrated at corpus level while leaving normal product backlog
items active. Closure should separate corpus integration, decision capture, implementation state,
and residual risks.

Decision test: **does the closeout say what is closed, what remains active, and why that split is
legitimate?**

### 10. Reflective improvement with budget

The methodology should improve from its own real failures, but each improvement should stay small,
testable, and reversible.

When a session exposes drift, ambiguity, or over-materialization, fix the smallest process surface
that would have prevented the recurrence. Do not respond to every gap with a new ceremony layer.

Decision test: **where will the next application prove that this process change helped?**

## Applying These Values

Use these values as a short preflight when modifying the methodology itself:

1. Identify the observed friction or failure mode.
2. Name the value most directly involved.
3. Choose the smallest rule, document change, skill boundary, or checklist that addresses it.
4. Record the evidence that will show whether the change worked.

These values complement the product-level [design principles](../global/design-principles.md). The
product principles guide what Ezkey should become; these values guide how the human and AI workflow
gets there while preserving integrity.
