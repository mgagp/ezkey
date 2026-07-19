# Ezkey Mobile Documentation

This directory contains the primary conceptual documentation for the React Native reference app in `ezkey_mobile/`.
It is intended for human reviewers and coding agents who need to understand the mobile product, its flows,
its internal data model, and its relationship to the Auth API without reopening the codebase for every question.

## Current Scope Note

The current mobile implementation and security posture should be read as **Android-first**.
iOS remains a **later planned milestone** and is **not** a short-term parity or release target.
Reviewers should not treat missing iOS parity as a current defect unless a document explicitly claims
that parity already exists.

Root-level documents remain canonical for shared Ezkey product identity, protocol semantics, cryptographic wire
formats, and signed payload definitions. The documents in this directory interpret those shared truths for the
reference mobile app.

## Reading Order

1. [MOBILE_API_MAPPINGS.md](MOBILE_API_MAPPINGS.md)
2. [MOBILE_FUNCTIONAL_FLOWS.md](MOBILE_FUNCTIONAL_FLOWS.md)
3. [MOBILE_DATA_MODEL.md](MOBILE_DATA_MODEL.md)
4. [MOBILE_SCREENS_AND_WIREFLOWS.md](MOBILE_SCREENS_AND_WIREFLOWS.md)
5. [MOBILE_STACK_AND_ARCHITECTURE.md](MOBILE_STACK_AND_ARCHITECTURE.md)
6. [MOBILE_POSITIONING.md](MOBILE_POSITIONING.md)

## Which Document Answers Which Question

| Question type | Best document | Notes |
| --- | --- | --- |
| Which Auth API fields does the mobile app send, store, or display? | [MOBILE_API_MAPPINGS.md](MOBILE_API_MAPPINGS.md) | Primary mapping reference for `bind`, `verify`, `pending`, and `respond`. |
| How do enrollment and authentication run end to end? | [MOBILE_FUNCTIONAL_FLOWS.md](MOBILE_FUNCTIONAL_FLOWS.md) | Includes nominal and exception paths plus trust checks. |
| What internal concepts and persisted structures exist in the app? | [MOBILE_DATA_MODEL.md](MOBILE_DATA_MODEL.md) | Covers local entities, storage split, and source-of-truth rules. |
| What does each primary screen show and do? | [MOBILE_SCREENS_AND_WIREFLOWS.md](MOBILE_SCREENS_AND_WIREFLOWS.md) | Screen-level responsibilities and navigation model. |
| How is the mobile app assembled technically? | [MOBILE_STACK_AND_ARCHITECTURE.md](MOBILE_STACK_AND_ARCHITECTURE.md) | Stack, module structure, boundaries, and generation workflow. |
| How should Ezkey Mobile be positioned conceptually? | [MOBILE_POSITIONING.md](MOBILE_POSITIONING.md) | Product-facing positioning and scope boundaries. |

## Canonical Root-Level References

| Document | Canonical for | Why it stays outside `ezkey_mobile/docs` |
| --- | --- | --- |
| [../../docs/PROJECT_POSITIONING.md](../../docs/PROJECT_POSITIONING.md) | Shared Ezkey product identity | Applies to the whole platform, not only the reference app. |
| [../../docs/CRYPTO.md](../../docs/CRYPTO.md) | Shared cryptographic contract and trust boundaries | Must stay shared across backend, mobile, and integration surfaces. |
| [../../docs/ENDPOINT.md](../../docs/ENDPOINT.md) | Auth API endpoint semantics | The mobile docs map the protocol; they do not replace the protocol reference. |
| [../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md](../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md) | Canonical pending/respond signed payload formats | Exact signature rules are shared protocol truth. |
| [../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md](../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md) | Canonical bind/verify signed payload formats | Exact signature rules are shared protocol truth. |
| [../../docs/MOBILE_DEVELOPER_GUIDE.md](../../docs/MOBILE_DEVELOPER_GUIDE.md) | Third-party mobile protocol implementation guidance | Broader than the reference app and intentionally rooted at the repository level. |

## Quality and Continuous Improvement

| Document | Category | When to read it |
| --- | --- | --- |
| [../../docs/security/mobile-protocol-crypto-assessment-2026-07.md](../../docs/security/mobile-protocol-crypto-assessment-2026-07.md) | Security assessment — protocol / crypto / Keystore | Formal Android-first white-box assessment (2026-07); prioritized MOB-* findings and claim verdict. HITL: `product-docs/global/hygiene/mobile-protocol-security/`. |
| [../../docs/security/mobile-security-assessment-2026-05.md](../../docs/security/mobile-security-assessment-2026-05.md) | Security assessment — prior | May 2026 mobile posture assessment; reconcile before re-opening storage/pinning topics. |
| [MOBILE_DEPENDENCY_HYGIENE_CEREMONY.md](MOBILE_DEPENDENCY_HYGIENE_CEREMONY.md) | Maintenance workflow | Lightweight and repeatable dependency-update routine (monitor, isolate, validate, decide, trace). |

## Supporting and Operational Docs

| Document | Category | When to read it |
| --- | --- | --- |
| [NATIVE_MODULES.md](NATIVE_MODULES.md) | Supporting technical detail | When the primary architecture doc is not deep enough on Android/iOS bridges. |
| [MOBILE_CRYPTO_REFERENCE.md](MOBILE_CRYPTO_REFERENCE.md) | Supporting security detail | When reviewing mobile-specific crypto wording and storage caveats. |
| [MOBILE_SECURITY_INVESTIGATION_TECHNIQUES.md](MOBILE_SECURITY_INVESTIGATION_TECHNIQUES.md) | Supporting security practice | When validating local secret handling, sandbox artifacts, log hygiene, and debug-only instrumentation choices. |
| [MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md](MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md) | Test automation / release posture | When adding Maestro, F2a bypass, or other test-only surfaces — mechanical gates and scrutiny resistance. |
| [MOBILE_LOCAL_AUTH_POLICY_AND_AUDIT_FUTURE_WORK.md](MOBILE_LOCAL_AUTH_POLICY_AND_AUDIT_FUTURE_WORK.md) | Supporting future design note | When revisiting local-auth integrity, future enrollment policy, or audit/protocol extensions around `respond`. |
| [MOBILE_ESLINT10_JEST30_UNBLOCK_MEMO.md](MOBILE_ESLINT10_JEST30_UNBLOCK_MEMO.md) | Supporting dependency governance | Unblock gates and trial protocol for deferred ESLint 10 / Jest 30 majors. |
| [MOBILE_RELEASE_SIGNING.md](MOBILE_RELEASE_SIGNING.md) | Operational | When preparing signed Android release artifacts. |
| [MOBILE_PLAY_PUBLISHING.md](MOBILE_PLAY_PUBLISHING.md) | Operational | When preparing Google Play submission. |
| [MOBILE_PLAY_RELEASE_READINESS_AUDIT.md](MOBILE_PLAY_RELEASE_READINESS_AUDIT.md) | Operational decision support | When deciding whether the current workspace is close enough to a Play release candidate. |
| [MOBILE_RELEASE_DECISION_MEMO.md](MOBILE_RELEASE_DECISION_MEMO.md) | Product and engineering decision support | When deciding whether to release first on the current stack or upgrade before first publication. |
| [MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md](MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md) | Real-device test harness | When running or extending Maestro + churn orchestration on a physical Android device (`TB-2026-0002`, F1/F2a). |
| [../maestro/README.md](../maestro/README.md) | Real-device Maestro operator runbook | Flows, selectors, pilot and campaign entry commands. |
| [../PRD.md](../PRD.md) | Product scope source | When checking intended scope, constraints, and release framing. |
