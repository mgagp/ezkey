# Java tenant SQL isolation — assessment-curated lane

Mandate-driven white-box review of Java **services and repository SQL** for tenant isolation:
Global Admin vs Tenant Admin, plus Auth API / Integration API / API-key paths, looking for
cross-tenant visibility or escalation into another tenant or Global Admin space.

This is the service/SQL companion to the 2026-08 controller-role pass. That pass closed
controller object checks (QR, `ROLE_ADMIN` convention, hide-existence target). This pass asks
whether the **queries those services run** still leak tenant space when the caller is a Tenant
Admin, an Integration API key, or an Auth API device.

- Method canon: [`../assessment-curated/README.md`](../assessment-curated/README.md)
- Assessment register: [`../../../../docs/java-tenant-sql-isolation-assessment-2026-09.md`](../../../../docs/java-tenant-sql-isolation-assessment-2026-09.md)
- Sibling: [`../java-controller-role-validation/`](../java-controller-role-validation/)
- Pass-1 campaign: [`2026-09-15-pass-1.md`](2026-09-15-pass-1.md) — HITL lot complete (five `fix`); remediations not started
