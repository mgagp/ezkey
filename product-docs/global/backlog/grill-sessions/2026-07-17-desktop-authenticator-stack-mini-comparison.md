# Stack mini-comparison — 2026-07-17 (Desktop authenticator)

## Purpose

Provide a pragmatic first-cut comparison of JavaFX, Electron, and Tauri for `I-2026-07-17-desktop-authenticator-reference-app`, with explicit alignment to the accepted UX quality bar (mobile/demo-device visual and workflow adequacy, operator signal-to-noise).

## Quick context: where Tauri sits

Tauri is a desktop-app framework that combines:

- a web UI (typically HTML/CSS/TypeScript),
- a lightweight Rust host shell,
- native system webviews for rendering.

Practical positioning versus Electron:

- same general "web UI in desktop shell" model,
- much smaller runtime footprint in many cases,
- stronger default permission surface through explicit command bridging,
- but a Rust layer to own and maintain.

In short: Electron is usually faster to start for web teams; Tauri is often leaner and stricter operationally, with higher backend-shell sophistication.

## Evaluation lens (first cut)

1. UX parity with mobile/demo-device (visual and workflow continuity)
2. Delivery speed for cross-platform first slice (Windows/macOS/Linux)
3. Security posture clarity (protocol parity + minimal local hardening)
4. Packaging and distribution pragmatism
5. Long-term maintainability in Ezkey stack context

## Option analysis

### JavaFX

**Pros**

- Strong fit with existing JVM/Spring ecosystem habits.
- Native desktop controls and mature packaging options.
- No JavaScript runtime bundle like Electron.

**Cons**

- Highest effort to reproduce close visual parity with mobile/demo-device web-oriented UI patterns.
- Slower UI iteration for highly customized, modern, responsive layouts.
- Cross-platform polish can require more platform-specific UI adaptation.

**Read for this idea**

- Technically credible, but weaker on the "stay visually close to mobile/demo-device" constraint unless we accept higher front-end effort.

### Electron

**Pros**

- Fastest path for teams already fluent in web UI.
- Very strong freedom to mirror mobile/demo-device information architecture and flow semantics.
- Large ecosystem and broad community knowledge.

**Cons**

- Heavier runtime and memory footprint.
- Wider default attack surface if IPC and preload boundaries are not carefully hardened.
- Packaging size and update overhead can be higher.

**Read for this idea**

- Strong UX-parity candidate with excellent speed, but operational footprint/security-hardening discipline must be watched.

### Tauri

**Pros**

- Web UI flexibility comparable to Electron for high UX parity.
- Leaner runtime posture (native webview + Rust host).
- Explicit command model encourages tighter privilege boundaries.

**Cons**

- Requires Rust ownership (tooling, CI, debugging familiarity).
- Plugin and ecosystem breadth is smaller than Electron's long tail.
- Team onboarding cost if Rust is new.

**Read for this idea**

- Best balance for this specific objective when UX parity is critical but we still want a pragmatic runtime/security posture.

## Decision table (first-cut posture)

| Criterion | JavaFX | Electron | Tauri |
|----------|--------|----------|-------|
| UX parity with mobile/demo-device | Medium | High | High |
| Cross-platform speed to first slice | Medium | High | Medium-High |
| Runtime footprint pragmatism | Medium-High | Low-Medium | High |
| Security boundary posture (default tendency) | Medium | Medium | High |
| Team familiarity (based on current signal) | Medium-High | High | Low-Medium |
| Overall first-cut fit for this idea | Medium | High | High |

## Recommendation (for TB preparation)

Primary recommendation: **Tauri** for first implementation cut, with a deliberate onboarding mini-plan.

Why:

- preserves the required UX continuity with mobile/demo-device,
- supports cross-platform target from day one,
- keeps runtime/security posture tighter than a default Electron baseline.

Fallback if delivery risk dominates onboarding: **Electron**, with explicit hardening constraints from day one.

## Decision lock (2026-07-18)

Operator decision confirmed: **Tauri is selected** for first implementation cut.

- Electron remains documented fallback only if a critical delivery blocker appears during
  implementation and is explicitly re-approved.

## Suggested risk controls if Tauri is selected

1. Keep Rust host surface minimal: only protocol-required commands.
2. Define a strict permission/command matrix before coding broad features.
3. Start with end-to-end minimal slice only (bind + verify + one approval path).
4. Add a short developer bootstrap doc for Rust/toolchain setup to reduce team friction.

## Architecture-fit addendum (2026-07-18)

An external design synthesis reinforced a contract-first model that is consistent with this
comparison:

- Define a platform-agnostic `SecureKeyProvider` as the only business-facing crypto boundary.
- Keep frontend and application services unaware of TPM/Secure Enclave/CNG internals.
- Implement platform specifics only in adapters and expose normalized capabilities for UX
  adaptation.

This favors Tauri operationally because a narrow Rust command boundary maps well to adapter
mediation. Electron remains feasible if the same boundary discipline is applied to IPC/preload
surfaces.

## Promotion impact on current Grill Me

This note resolves open question #1 (stack comparison) with a recommended direction.
Remaining blockers before TB promotion:

- hardening baseline details,
- packaging baseline,
- UX adequacy check documentation against mobile/demo-device reference.

## Links

- Backlog idea: `../ideas/I-2026-07-17-desktop-authenticator-reference-app.md`
- Grill session: `2026-07-17-desktop-authenticator-reference-app-grill-me.md`
- Architecture synthesis: `2026-07-18-desktop-authenticator-contract-first-architecture-synthesis.md`
- Signature payload canon: `../../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`
- Signature payload canon: `../../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`
