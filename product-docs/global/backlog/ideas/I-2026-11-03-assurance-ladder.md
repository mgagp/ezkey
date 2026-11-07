# Backlog Idea — `I-2026-11-03-assurance-ladder` Assurance ladder: proportionate standards posture (exploration)

## Metadata

- **ID:** `I-2026-11-03-assurance-ladder`
- **Status:** `captured`
- **Priority:** `P3`
- **Created at:** `2026-11-03`
- **Updated at:** `2026-11-07`
- **Last reviewed at:** `2026-11-03`
- **Component tags:** `docs`, `security`, `mobile`, `lab:experimental`
- **Captured by:** Marc
- **GitHub issue:** [#1260](https://github.com/mgagp/ezkey/issues/1260) (mobile encryption export and import position, one slice of this idea)

## Intent

Explore, as a laboratory track, how far Ezkey should go toward recognized security standards
(SOC 2, ISO/IEC 27001, OpenSSF, OWASP MASVS) in a way that is **proportionate, gradual and close
to zero cost**. The outcome is a clear answer to *what is necessary, what makes adoption easier,
and what waits for a sponsor*, plus a repeatable signal source for continuous improvement. It is
not a certification program and it adds no claim.

## Problem and value

- **Problem:** The 2025 SOC 2 preparation cluster was retired in 2026-08 (PR #471, #482) because
  it implied a certification path the project cannot walk. The retirement left a gap: there is no
  stated answer to "what would an adopter's auditor need from Ezkey?", and no regular pass that
  surfaces standards-related blind spots.
- **Expected value:**
  - Adopters running their own SOC 2 or ISO 27001 program get Ezkey as a **well-behaved component**
    and a **good evidence source**, with less effort on their side.
  - The maintainer gets a cheap, dated **signal generator** that feeds the existing hygiene lanes.
  - The mobile apps, which Ezkey publishes itself, get a profile that matches store distribution.

## Key finding: the adopter is the audited party

SOC 2 is an attestation of a **service organization** on **its own system**. An open-source
project that does not operate a production service is never the audited entity. When an adopter
runs Ezkey, its auditor sees Ezkey in two ways:

1. **Third-party software component** — vulnerability and change management (TSC CC7.1, CC8.1)
   and supplier / supply-chain risk (CC9.2). Typical questions: how the adopter learns about
   vulnerabilities (security advisories), how fast it patches, the component inventory (SBOM),
   binary provenance, and the support / end-of-life policy.
2. **A tool that produces controls and evidence** — MFA and access (CC6.1–CC6.3), audit trail and
   monitoring (CC7.2), justification on sensitive change (CC8.1). The auditor samples evidence such
   as audit-log exports, admin access reviews, and proof of revocation.

Ezkey's job is therefore to be a **good component** (verifiable project practices) and a **good
evidence source** (features plus a mapping guide). Neither requires certifying the project.

## Three surfaces, three profiles

| Surface | Accountable party | Proportionate reference |
| --- | --- | --- |
| **Ezkey project** (code, CI, releases) | Maintainer | OpenSSF OSPS Baseline (Level 1, then 2), OpenSSF Best Practices badge, OpenSSF Scorecard; NIST SSDF as vocabulary |
| **Mobile apps** (Google Play, Apple App Store) | **Ezkey as publisher** — the only surface where the project is the actual supplier | Store policies (Play Data safety, Apple privacy manifest, encryption export declaration, see [#1260](https://github.com/mgagp/ezkey/issues/1260)) and OWASP MASVS; App Defense Alliance MASA Level 1 as a later, paid option |
| **Backend platform** (not operated in production by the project) | **Adopter** | Adopter-facing kit mapped to SOC 2 TSC and ISO/IEC 27001:2022 Annex A, reference only |
| *Community instance* (`ezkey.online`) | Maintainer, evaluation only | **Explicitly out of scope** of any standard: public alpha, all-in-one, no SLA (see `V-2026-09-26-public-alpha-posture-closeout`) |

## Comparable projects (same pattern)

- **Teleport** publishes a SOC 2 page that maps each relevant criterion (CC6, CC7, CC8) to product
  features and tells readers to confirm with their auditor. This is the model for the adopter guide.
- **Zitadel, Bitwarden, Authgear** hold SOC 2 / ISO 27001 for **their hosted cloud** only. In
  self-hosted deployments, controls such as audit-log retention move to the operator. Ezkey has no
  hosted offering, so only the "operator carries it" half applies.
- **Mullvad, Proton, Bitwarden** have their mobile apps assessed separately (independent audits,
  MASA) and publish the reports. This matches the profile of store-published apps.
- **Keycloak** has no SOC 2 as a community project; certification-grade assurance comes through a
  commercial distribution. This illustrates the "sponsor as trigger" step.

## Proportionality ladder

| Rung | Content (indicative) | Cost | Trigger |
| --- | --- | --- | --- |
| **Necessary** | OSPS Baseline Level 1; security advisory channel; written support / end-of-life policy; store compliance items; explicit "community instance out of scope" line | ~0 | none |
| **Facilitator** | Adopter compliance guide (TSC + ISO Annex A mapping, Teleport-style); shared-responsibility matrix; consolidated hardening guide; audit-log evidence export recipe; SBOM and signed releases; OSPS Level 2; Best Practices badge; incident response note | time only | Marc's decision, lot by lot |
| **Sponsor-triggered** | MASA Level 1 through an accredited lab; published external penetration test; SOC 2 / ISO 27001 for a **legal entity** that would operate an Ezkey service; a self-hosted GRC tool (for example CISO Assistant Community or Probo) | thousands to tens of thousands | sponsor or legal entity |

Each signal pass (Scorecard, OSPS Baseline scanner, store checklist, mapping-guide review) yields
P1/P2/P3 findings routed into the **existing** hygiene lanes. No parallel compliance program.

## Scope

- **In scope:**
  - This framing (adopter = audited party; three surfaces; ladder).
  - Periodic, read-only signal passes using free tools.
  - Lot-by-lot facilitator deliverables, each through a normal docs or hygiene PR.
  - A **private** working repository (`mgagp/ezkey-assurance`, created on Marc's go) for sensitive
    working material only: self-assessments, raw gap analysis, untriaged signals. Gap details stay
    out of the public corpus until fixed or accepted.
- **Out of scope:**
  - Any SOC 2 Type I/II, ISO 27001 certification, or attestation-equivalence claim for the project.
  - "Working towards SOC 2" or "compliant" language in public or operator-facing material.
  - Making a GRC tool, Comply, or Probo a project goal (see
    [`normative-posture.md`](../../normative-posture.md)).
  - Bringing the community instance up to a production standard.

## Key assumptions

- No sponsor and no legal entity in the coming months; the ladder stops at the facilitator rung
  until that changes.
- Store compliance for the mobile apps is already partly tracked under
  [`I-2026-08-02-mobile-play-official-compliance-gate`](I-2026-08-02-mobile-play-official-compliance-gate.md);
  this idea does not duplicate it.
- Free tooling is enough for the necessary and facilitator rungs.

## Risks and exceptions

- **Claim drift:** a mapping guide can read like a compliance claim. Mitigation: product terms
  first, "reference only, confirm with your auditor" wording, review against
  [`docs/SECURITY_POSTURE.md`](../../../../docs/SECURITY_POSTURE.md) honest-claims table.
- **Ceremony creep:** the ladder must not become the retired 12–18 month roadmap under a new name.
  Each lot needs a concrete adopter or maintainer benefit.
- **Single maintainer:** governance criteria that assume segregation of duties cannot be met by the
  project itself; name this honestly instead of simulating it.
- **Tool longevity:** strongdm/comply, the 2025 primary tool choice, has seen no activity since
  2022-07; any tool choice is re-evaluated at the sponsor rung.

## Promotion notes

Stay `captured` until the first signal pass is triaged with Marc. Promote to `incubating` when the
first facilitator lot (likely the adopter compliance guide skeleton) is chosen.

## Links

- Canon: [`normative-posture.md`](../../normative-posture.md),
  [`docs/SECURITY_POSTURE.md`](../../../../docs/SECURITY_POSTURE.md), [`SECURITY.md`](../../../../SECURITY.md)
- Live posture: [`V-2026-09-26-public-alpha-posture-closeout`](../../vision/V-2026-09-26-public-alpha-posture-closeout.md)
- CI / supply-chain inventory: [`docs/engineering/ci-cd-best-practices-plan.md`](../../../../docs/engineering/ci-cd-best-practices-plan.md)
- Mobile store gate: [`I-2026-08-02-mobile-play-official-compliance-gate`](I-2026-08-02-mobile-play-official-compliance-gate.md)
- Retired SOC 2 cluster: git history, commit `db66d5e3c` (PR #471)
- External references:
  [Teleport SOC 2 mapping](https://goteleport.com/docs/zero-trust-access/compliance-frameworks/soc2/),
  [OpenSSF OSPS Baseline](https://baseline.openssf.org/),
  [OSPS Baseline scanner (Privateer)](https://github.com/ossf/pvtr-github-repo-scanner),
  [OWASP MASVS](https://mas.owasp.org/MASVS/),
  [App Defense Alliance MASA](https://appdefensealliance.dev/masa)
