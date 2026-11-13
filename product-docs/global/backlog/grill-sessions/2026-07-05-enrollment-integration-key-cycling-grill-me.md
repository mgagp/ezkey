# Grill Me — Enrollment integration key cycling (2026-07-05)

## Session control

| Field | Value |
| ----- | ----- |
| **Trigger** | Operator idea: shorten integration Ed25519 key lifetime by cycling keys through existing pending / respond exchanges; follows post-quantum posture review (2026-07-05). |
| **Vision** | [`V-2026-07-05-enrollment-integration-key-cycling-posture`](../../vision/V-2026-07-05-enrollment-integration-key-cycling-posture.md) |
| **Backlog** | [`I-2026-07-05-enrollment-integration-key-cycling`](../ideas/I-2026-07-05-enrollment-integration-key-cycling.md) |
| **Started** | `2026-07-05` |
| **Closed** | `2026-07-05` |
| **Status** | `complete` — pre-analysis accepted; idea **incubating**; execution deferred post–R1 |
| **Captured by** | Marc (operator); grill materialized by agent session |

## Executive summary (pre-analysis)

**The idea holds up as a controlled hardening direction**, not as a post-quantum silver bullet.

| Question | Verdict |
| -------- | ------- |
| Does the idea make sense? | **Yes** — shorter-lived integration signing keys reduce harvest-now / forge-later payoff per key epoch. |
| Does it legitimately improve posture? | **Yes, moderately** — window management on the **longest-lived MFA asymmetric secret** today; complements ephemeral proof tokens. |
| Is aiming at post-quantique exaggerated? | **Yes if sold as PQ resistance** — Ed25519 remains Shor-vulnerable. **No if framed as exposure-window reduction** ahead of a future PQC migration. |

**Recommended shape:** Opt-in policy; **do not rotate on pending alone**; anchor on **completed auth exchanges** with **min interval**; **two-phase handoff + grace period**; static-long remains default. Phase 0 = design pack + ADR only until post–R1.

---

## Context — current integration key lifecycle

| Event | Integration key role |
| ----- | ------------------- |
| Enrollment create (Admin API) | `generateEd25519KeyPair()` → stored on enrollment row |
| Bind | Integration signs `enrollmentBindPayloadSignedByIntegration`; mobile stores `integrationPublicKey` |
| Pending | Integration signs pending payload with **same** key |
| Respond result | Integration signs `{proofToken}|{authAttemptId}|{result}|{message}` with **same** key |

Keys are generated once in `EnrollmentService` / provisioning paths and used until enrollment
revoked — no rotation protocol exists today. Mobile persists `integrationPublicKey` on the secure
secret path for all subsequent verifications.

**Contrast:** `authAttemptProofToken` is one-time / short-lived; integration key is **epoch-long**.

---

## Q1 — Does shortening integration key life address the right threat?

**Yes, for a specific threat class — not for all crypto concerns.**

| Threat | Cycling helps? |
| ------ | -------------- |
| Harvest now, decrypt/for forge later (HNDL) on **integration signatures** | **Partially** — less traffic signed under one public key epoch |
| Online forgery today (no CRQC) | **No** — still need private key or break today’s crypto |
| Post-quantum Shor on recorded Ed25519 keys | **Partially** — attacker must harvest **per epoch**; older epochs may expire before CRQC |
| Proof-token replay | **No** — already handled by one-time tokens |

**Conclusion (D1):** Legitimate **classical + window-management** improvement. **Not** a substitute for ML-DSA / hybrid migration.

---

## Q2 — Is piggybacking on pending / respond architecturally sound?

**Yes, with strict rules — pending alone is the wrong hook.**

### Why pending-only fails

| Scenario | Problem |
| -------- | ------- |
| User polls pending 5×/min (legitimate) | 5 rotation proposals / min — **key thrashing** |
| Pending with no eventual respond | Mobile receives new key but **never commits** in a completed flow — ambiguous state |
| Integration creates pending, user ignores | Wasted rotation state; audit noise |

Operator intuition confirmed: rotation should tie to **real authentication activity**, not poll frequency.

### Why respond / terminal completion is the better anchor

| Property | Benefit |
| -------- | ------- |
| Cryptographic round-trip already completed | Device proved liveness; channel was used successfully |
| Natural delivery point | Respond already carries integration-signed outcome — can extend with **next key** signed by **current** key |
| Aligns with “short-lived secrets follow active exchange” narrative | Same mental model as proof tokens, but at key-epoch granularity |

**Conclusion (D2, D3):** Hook rotation proposal to **terminal auth attempt completion** (design pack decides APPROVED-only vs all terminal states). **Never** on pending poll alone.

---

## Q3 — What policy gates prevent unreasonable rotation frequency?

Minimum controls (all recommended):

| Policy | Purpose |
| ------ | ------- |
| **`minRotationInterval`** | e.g. 24h / 7d / N completed auths — prevents thrashing even under heavy auth volume |
| **`maxIntegrationKeyAge`** | Force rotation when key epoch exceeds ceiling regardless of auth count |
| **`rotationEnabled`** | Opt-in per deployment / tenant / enrollment — static-long default (D6) |
| **Cooldown after failed handoff** | Backoff if mobile never confirms new key within grace window |

**Example:** User completes 5 auths in one minute → **at most one** rotation if interval is 24h, not five.

---

## Q4 — How to hand off a new key without breaking the enrollment?

**Requires two-phase commit + grace period (D5, D7).**

```mermaid
sequenceDiagram
    participant Mobile
    participant AuthAPI
    participant DB

    Note over AuthAPI,DB: Current key K0 active
    Mobile->>AuthAPI: respond (device signature)
    AuthAPI->>AuthAPI: Policy says rotate; generate K1
    AuthAPI->>Mobile: respond-result signed with K0 includes nextPubKey K1
    Mobile->>Mobile: Verify with K0; persist K1 candidate
    Note over Mobile: Subsequent pending verified with K1 after commit
    AuthAPI->>DB: Commit K1 after grace / confirmation rules
    Note over AuthAPI,DB: K0 valid during grace only
```

### Failure modes — must remain safe

| Failure | Required behavior |
| ------- | ----------------- |
| User denies / timeout before respond | **No rotation** — K0 unchanged |
| Respond lost on network | Mobile keeps K0; server may abort pending rotation |
| Mobile verifies K1 proposal but crashes before persist | K0 still valid; retry on next eligible auth |
| MITM on respond | Existing respond signature verification fails — **fail closed**, no rotation |
| Datacenter outage mid-commit | Transactional state; on recovery K0 still authoritative until explicit commit |
| Rotation commit partial in DB | **Audit + reconcile job**; never INVALIDATE enrollment silently |

**Conclusion (D7):** VERIFIED enrollment association is **invariant**; worst case = rotation skipped.

---

## Q5 — Does this make Ezkey “post-quantique”?

**No — and must not be marketed that way.**

| Claim | Honest? |
| ----- | ------- |
| “Reduces integration key exposure window” | **Yes** |
| “Aligns long-lived asymmetric material with shorter epochs” | **Yes** |
| “Resistant to quantum attacks” (product-wide) | **No** — same mistake as over-reading recovery-code doc |
| “Prepares ground for future PQC / hybrid” | **Yes, weakly** — shorter classical epochs + future algorithm upgrade |

Recovery codes remain a **bootstrap secret** with operational mitigations, not PQ proof for the
protocol. Integration key cycling is **HNDL mitigation**, not NIST PQC compliance.

**Conclusion:** Ambitious **security evolution** — appropriate for Ezkey to **explore** — but
messaging stays **window reduction / gradual hardening**.

---

## Q6 — Default static-long vs optional rotation?

**Static-long default; opt-in policy (D6).**

| Mode | When |
| ---- | ---- |
| **Static-long** (today) | Default; zero protocol / mobile change |
| **Policy-gated cycling** | Operator enables; design pack defines scopes |
| **Per-auth ephemeral integration key** | **Defer / likely reject** — extreme complexity; device storage churn; unclear win vs interval policy |

Supports phased rollout: deployments choose when to enable; tests cover both modes.

---

## Q7 — Audit and operator visibility

Required audit events (D8) — names TBD in design pack:

- `INTEGRATION_KEY_ROTATION_PROPOSED`
- `INTEGRATION_KEY_ROTATION_COMMITTED`
- `INTEGRATION_KEY_ROTATION_ABORTED`
- `INTEGRATION_KEY_ROTATION_GRACE_EXPIRED`

Include enrollment id, key epoch / fingerprint, auth attempt id, policy reason (age vs interval).

Admin UI surfacing: **optional later slice** — not blocking Phase 0.

---

## Q8 — Protocol versioning and client impact

Any new respond / pending fields require:

- Auth API DTO + Springdoc change
- Mobile + Demo Device + golden fixtures
- [`I-2026-0025`](../ideas/I-2026-0025-auth-api-protocol-capability-versioning.md) capability flag
- Postman + `update-specs` on implementation

**Conclusion (D9):** Non-trivial cross-boundary program — reinforces post–R1 sequencing (D10).

---

## Q9 — Phased trajectory

| Phase | Deliverable | When |
| ----- | ----------- | ---- |
| **0** | Grill (done), ADR, design pack, failure matrix | Pre-implementation |
| **1** | Policy schema + docs only (no wire change) | Optional early |
| **2** | Protocol + backend state machine + audit | Post–R1 |
| **3** | Mobile / Demo Device / tests | With Phase 2 |
| **4** | Operator UI + advanced policies | Later |

**Do not** implement Phase 2+ before September 2026 operable-release gate unless operator elevates (D10).

---

## Q10 — Minimal evidence that would disprove the plan

1. Design pack shows mobile **cannot** atomically persist new integration key without UX regression → rethink delivery channel (e.g. dedicated rotate endpoint).
2. Grace-period dual-key verification ** doubles** pending/respond bug surface beyond maintainability budget → reject in-protocol rotation; consider operator-triggered re-enrollment instead.
3. Simulation: typical auth rate × min interval → epoch length still **>1 year** → negligible HNDL win; not worth protocol complexity.

---

## Settled decisions (operator session 2026-07-05)

| ID | Decision | Status |
| -- | -------- | ------ |
| D1 | Idea **valid** for exposure-window reduction; **not** PQ algorithm fix | **Confirmed** |
| D2 | **No** rotation on pending poll alone | **Confirmed** |
| D3 | Anchor on **terminal auth completion**; APPROVED-only vs all terminals → design pack | **Confirmed** |
| D4 | **Min interval** + optional **max key age** required | **Confirmed** |
| D5 | **Two-phase handoff + grace period** with old-key verification window | **Confirmed** |
| D6 | **Static-long default**; cycling **opt-in** | **Confirmed** |
| D7 | Failed rotation **must not** break VERIFIED enrollment | **Confirmed** |
| D8 | **Audit trail** mandatory | **Confirmed** |
| D9 | Wire changes via **capability versioning** (`I-2026-0025`) | **Confirmed** |
| D10 | Execution **post–R1**; Phase 0 design only until then | **Confirmed** |

---

## Closeout (2026-07-05)

Pre-analysis and grill complete. **`I-2026-07-05` incubating** — next artifact is **Phase 0
component design pack + ADR**, not `TB-*` or code.

**Follow-up (2026-09-15):** the broader post-quantum protocol map that this grill assumed is now
[`V-2026-09-15-post-quantum-crypto-posture`](../../vision/V-2026-09-15-post-quantum-crypto-posture.md).
That note **reaffirms** D1 (cycling is not PQ) and **rejects** per-authentication device-key
rotation as the PQ strategy. Do not reopen D1–D10 without a new signal.

**Resume execution when:** post–September 2026 operable-release gate; or operator elevates crypto
hardening with explicit capacity.

## Related code anchors (evidence)

- Integration key generation: `EnrollmentService` (create), `AdminProvisioningService`, `AdminBootstrapService`
- Signature service: `ezkey-core/.../SignatureService.java`
- Payload contracts: `docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`, `docs/CRYPTO.md`
- Mobile key storage: `ezkey_mobile` secure path for `integrationPublicKey` (PRD, MOBILE_DEVELOPER_GUIDE)
- Platform encryption key rotation (contrast): `docs/REENCRYPTION_OPERATIONS.md` — **different** key domain
