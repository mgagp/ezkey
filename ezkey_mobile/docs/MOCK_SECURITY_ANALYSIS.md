# Mock Security Analysis

> Historical note. This file remains only to explain the policy decision; it is not the source of truth for current crypto wording.

## Current State

The mobile app is intended to run with native crypto integration and without a production mock fallback.

Current messaging should reflect:
- native platform key storage is required for normal operation
- Android currently uses `Android Keystore`
- `StrongBox` is requested when available
- wording must remain conservative about hardware guarantees

## Why This Document Exists

Earlier development iterations explored mock-backed fallbacks. That direction is no longer part of the desired production posture because it weakens the security story and makes the runtime model ambiguous.

## Documentation Rule

When referring to this topic in code or docs:
- prefer `native secure key storage`
- avoid `hardware-backed keys are always used`
- avoid implying that all platforms have the same keystore guarantees

For current wording, refer to [`docs/CRYPTO.md`](../../docs/CRYPTO.md) and [`docs/MOBILE_CRYPTO_REFERENCE.md`](MOBILE_CRYPTO_REFERENCE.md).
