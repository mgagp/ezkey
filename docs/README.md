# Ezkey Documentation

This directory is the repository-level documentation hub for Ezkey.

Use it for shared product, protocol, operational, security, and configuration references. For module-specific
implementation details, prefer the README or `docs/` folder inside the relevant module.

## Start Here

If you are new to the repository, read these first:

1. [../README.md](../README.md) for the top-level project overview and quick start.
2. [PROJECT_POSITIONING.md](PROJECT_POSITIONING.md) for the product thesis and backend-first framing.
3. [ARCHITECTURE.md](ARCHITECTURE.md) for the system-wide architecture view.
4. [ENDPOINT.md](ENDPOINT.md) for canonical API semantics.
5. [CRYPTO.md](CRYPTO.md) for shared cryptographic wording and guarantees.

Installing a **host JDK** to build Java modules from a fresh clone is a different path: [DEVELOPMENT.md](DEVELOPMENT.md) § *First clone on a new workstation* (`./scripts/build.sh`).

## Core References

- [PROJECT_POSITIONING.md](PROJECT_POSITIONING.md): what Ezkey is, what it is not, and why it exists.
- [ECOSYSTEM_REPOSITORIES.md](ECOSYSTEM_REPOSITORIES.md): planned integration-repository shells (`ezkey-*` satellites such as SMS and directory adapters) and naming rules.
- [ARCHITECTURE.md](ARCHITECTURE.md): system architecture, module relationships, and trust boundaries.
- [ENDPOINT.md](ENDPOINT.md): canonical Admin API and Auth API endpoint behavior.
- [CRYPTO.md](CRYPTO.md): shared cryptographic model, payload rules, and wording guardrails.
- [ENROLLMENT_SIGNATURE_PAYLOAD.md](ENROLLMENT_SIGNATURE_PAYLOAD.md): canonical enrollment bind/verify signature payload construction (what is signed, with what key, in what order).
- [AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md](AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md): canonical auth-attempt pending/respond signature payload construction.
- [LIFECYCLE_GOVERNANCE.md](LIFECYCLE_GOVERNANCE.md): entity hierarchy, eligibility chain, and reversible/irreversible action rules across all Ezkey domain entities.
- [ALERTS.md](ALERTS.md): operator-facing `ezkey_alert` subsystem (raise/touch/resolve, types, Admin API).
- [DEVELOPMENT.md](DEVELOPMENT.md): first clone on a new workstation, host JDK 25 / Maven baseline, tests, and development workflow.
- [OPERATIONAL.md](OPERATIONAL.md): deployment, operations, and production posture.
- [configuration/README.md](configuration/README.md): index for configuration properties and per-module configuration docs.
- [MAINTENANCE.md](MAINTENANCE.md): documentation maintenance expectations and standards.

## By Surface

- [ADMIN_UI.md](ADMIN_UI.md): Admin UI purpose, role split, workflow boundaries, and operator-facing scope.
- [../ezkey-admin-ui/README.md](../ezkey-admin-ui/README.md): Admin UI module-specific setup and local development.
- [MOBILE_DEVELOPER_GUIDE.md](MOBILE_DEVELOPER_GUIDE.md): shared protocol guide for third-party or alternative mobile clients.
- [../ezkey_mobile/docs/README.md](../ezkey_mobile/docs/README.md): primary mobile documentation corpus for the React Native reference app.
- [LOCAL_STACK_PORTS.md](LOCAL_STACK_PORTS.md): local stack ports, proxy mapping, and testing entry points.

## Security And Compliance

- [admin-ui-security.md](admin-ui-security.md): Admin UI token handling, headers, and deployment security notes.
- [admin-ui-security-validation.md](admin-ui-security-validation.md): practical validation checklist for Admin UI security posture.
- [AUDIT_REASON_AND_JUSTIFICATION_UI.md](AUDIT_REASON_AND_JUSTIFICATION_UI.md): audit `reason` and `justification` rules across API and UI.
- [ADMIN_PROVISIONING_DEPROVISIONING_PROCEDURE.md](ADMIN_PROVISIONING_DEPROVISIONING_PROCEDURE.md): admin lifecycle and audit-oriented procedure.
- [RECOVERY_CODES_LIFECYCLE_ANALYSIS.md](RECOVERY_CODES_LIFECYCLE_ANALYSIS.md): historical analysis (pre-regenerate). Living: [`LIFECYCLE_GOVERNANCE.md`](LIFECYCLE_GOVERNANCE.md) §3.8 and [`ENDPOINT.md`](ENDPOINT.md) `POST /api/v1/admins/{id}/recovery-codes/regenerate`.
- [security/SQL_INJECTION_POSTURE_AUDIT.md](security/SQL_INJECTION_POSTURE_AUDIT.md): SQL injection posture and repository inventory.
- [../product-docs/global/normative-posture.md](../product-docs/global/normative-posture.md): operational discipline (mapping vocabulary only on that page; not a certification claim).
- [KEY_ROTATION_PROCEDURES.md](KEY_ROTATION_PROCEDURES.md): encryption key rotation operational procedures.
- [dependency-posture-admin-and-ui.md](dependency-posture-admin-and-ui.md): Admin API / Admin UI dependency anchors, audits, and backlog.
- [../SECURITY.md](../SECURITY.md): security policy and disclosure process.

## Operations And Tooling

- [monitoring/](monitoring/): dashboards, monitoring setup, and supporting notes.
- [cloudflare/](cloudflare/): site and edge workflow notes.
- [lightsail/community-host.md](lightsail/community-host.md): create/bootstrap a NEW community Lightsail host for ezkey.online (parallel to EXP1; AWS CLI scripts under `scripts/lightsail/`).
- [dev-tools/](dev-tools/): internal development tools and helper assets.
- [plan/operational-churn-ezkey.plan.md](plan/operational-churn-ezkey.plan.md): operational churn testing plan.

## Notes

- Keep shared truth at the repository level when it applies across modules.
- Keep product-, UI-, or implementation-specific interpretation close to the owning module.
- Do not use this index as a substitute for module-level READMEs; use it as the routing layer into the right document set.
