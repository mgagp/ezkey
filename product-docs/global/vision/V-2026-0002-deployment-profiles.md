# V-2026-0002 — Deployment profiles for Ezkey installations

- **Date:** `2026-05-08`
- **Status:** `archived`
- **Superseded:** This orientation was reformulated during the 2026-05-08 grilling session. The
  single transversal "deployment profiles" platform concept was decomposed into a 3-phase plan
  that keeps the platform free of profile-aware code. See `V-2026-0010` for the new vision and
  `I-2026-0017`, `I-2026-0018` for the actionable phases.
- **Intent:** Establish deployment profiles as a first-class product concept. Each profile
  balances simplicity, security posture, and operational cost for a target audience (for example,
  a small operator running everything in a single Docker stack vs a production-grade installation
  with separated API binaries and high availability). Make the trade-offs explicit, default to a
  security-conscious posture, and document escape hatches so operators understand exactly what
  they accept when they choose a simpler profile.
- **Signals:** Two converging threads make the pattern visible. First, API-key acceptance on
  Admin API today is implicit and ambiguous (`V-2026-0003`). Second, email integration introduces
  another lever where the all-in-one mode is pragmatically defensible for SMEs but should not be
  the security-by-default position (`V-2026-0005`). The pattern repeats across features and
  deserves a canonical home rather than ad-hoc decisions per feature.
- **Potential impact:** configuration surface (Spring profiles, environment variables), Docker
  compose presets, documentation structure, security posture defaults, Admin UI copy, deployment
  guides, future SDK and CLI behavior.
- **Next step:** archived; direction superseded by `V-2026-0010`.
- **Captured by:** Marc

## Related artifacts

- `V-2026-0010` — Per-installation profile elaboration (supersedes this note)
- `I-2026-0017` — Profile elaboration template and skill
- `I-2026-0018` — Profile generator skill
