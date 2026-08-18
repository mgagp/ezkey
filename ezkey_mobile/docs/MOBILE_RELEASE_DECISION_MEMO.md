# Ezkey Mobile — Release Decision Memo

Decision memo for Android Play Store release timing versus React Native stack upgrade timing.

## Purpose

This note does not replace the technical audit. It frames the decision that remains once the current workspace state is understood.

## Decision (2026-08-14)

**Release the first official Play listing on the current stack** (React Native `0.86.2`, React `19.2.7`). Treat a later **0.87** program (Node 22, AGP 9, Strict TypeScript API, coupled Vision Camera / worklets slice) as **post-listing debt**, not a pre-launch blocker.

Coming Soon stays in the app as a sober roadmap (local-auth / auth policy + certificate pinning).

## Current Workspace Facts

- React Native `0.86.2` and React `19.2.7` in `ezkey_mobile/package.json` (Active 0.86 line).
- `compileSdkVersion` / `targetSdkVersion` **36**; `minSdkVersion` **31**.
- Release signing via external `EZKEY_UPLOAD_*` Gradle properties.
- `package.json` `"version": "1.0.0"` is Android `versionName`; `versionCode` is **2** (confirm against Play Console before upload).
- Permission surface: `INTERNET`, `CAMERA`, `USE_BIOMETRIC`.
- Official in-app copy (no invited-audience framing) and a fail-open Play flexible-update check ship in this line.

## Version-Convergence Constraint To Preserve

Future modernization should treat React, React Native, camera, and camera-adjacent runtime packages as a coupled upgrade slice. Do not bump the core React stack and assume Vision Camera will follow.

## Why release now (Option A)

- Immediate goal is a credible official listing, not a toolchain program.
- Android API posture already meets Play shape.
- 0.86 remains in Active support; 0.87 is too young to treat as a hygiene bump.
- The mobile product scope is narrow (enroll, list, approve/deny).

Costs: ecosystem drift continues after listing; the later 0.87 program still needs explicit camera-stack validation time.

## Option B — Upgrade before first public release (rejected for this listing)

Would delay the official listing for a coupled RN/camera program with little product gain for v1.0.0 users.

## Practical Closure

Use [`MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`](MOBILE_PLAY_RELEASE_READINESS_AUDIT.md) as the live gap list. Do not revive deleted historical migration notes.
