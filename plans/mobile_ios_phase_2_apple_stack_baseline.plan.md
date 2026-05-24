---
name: mobile-ios-phase-2-apple-stack-baseline
overview: Lock the Apple-side toolchain and project baseline for fast, low-friction Ezkey iPhone development without chasing unnecessary bleeding-edge choices.
todos:
  - id: choose-toolchain-baseline
    content: Confirm the target Xcode, CocoaPods, and simulator baseline compatible with React Native 0.85.2.
    status: pending
  - id: confirm-min-ios-target
    content: Validate whether iOS 15.1 remains the right minimum deployment target or whether a modest raise is justified.
    status: pending
  - id: verify-clean-ios-build
    content: Ensure the iOS project can build and launch a minimal shell on simulator with the chosen baseline.
    status: pending
  - id: document-baseline
    content: Record the baseline decisions and rationale for future phases.
    status: pending
isProject: false
retrofitted_by: R-2026-0003
retrofitted_at: 2026-05-24
---

# Mobile iOS Phase 2 - Apple Stack Baseline

## Goal
Choose a practical Apple baseline that stays aligned with the current React Native stack while avoiding both unnecessary lag and unnecessary churn.

## Current default recommendation
- React Native: `0.85.2`
- Xcode: latest stable version supported by the React Native baseline
- Minimum deployment target: start with `iOS 15.1`
- Development posture: modern simulator image for day-to-day work, conservative minimum OS for distribution

## In scope
- Toolchain compatibility for React Native and iOS native builds.
- iPhone simulator setup suitable for fast iteration.
- Project-level baseline settings that future phases can assume.
- A simple rationale for the chosen minimum iOS target.

## Out of scope
- Native crypto implementation details.
- Camera validation strategy beyond what affects basic tooling decisions.
- Long-term App Store release optimization.

## Deliverables
- A documented Apple baseline for iPhone work.
- A successful clean simulator launch of the iOS shell.
- A short note explaining why the project uses a modern Xcode baseline with a conservative minimum iOS target.

## Exit gate
Phase 2 is complete when the project has a stable, documented iOS build baseline and the simulator can run the app shell without stack-level uncertainty.

## Main risks
- Picking a minimum iOS version that creates unnecessary future maintenance.
- Picking a minimum iOS version that is too recent and reduces adoption without practical benefit.
- Letting Apple toolchain drift become a hidden blocker in later phases.

## Replan trigger
Replan if React Native 0.85.2 or a required camera/native dependency forces a materially different minimum iOS target than expected.
