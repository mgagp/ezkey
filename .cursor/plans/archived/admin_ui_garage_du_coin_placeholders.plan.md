---
status: archived
archived_date: 2025-03-08
completion_status: fully_implemented
---

# ARCHIVED PLAN

**This plan has been fully implemented and archived for historical reference.**

**Completion Date:** 2025-03-08
**Status:** Fully Implemented and Validated

---

# Admin UI — Placeholder theme: "Garage du coin"

## Objective

Unify form placeholder examples under a single, light thematic: **Garage du coin** (French, kept as-is). The humour is **subtle**: do not explicitly describe the garage (e.g. no "garage d'auto luxueuse"). Simply naming the brands **Porsche, Mercedes, Audi** where a bit of detail is needed is enough to suggest the idea — a "corner garage" that's clearly a bit more than ordinary. That discrete touch is the intended effect.

## Scope note

AGENTS.md states "All content in English." Here, **only placeholder example values** use the French theme; labels, buttons, and messages stay in English.

---

## 1. Inventory

### 1.1 Explicit ACME / company-style (to change)


| File            | Field / context       | Current placeholder           | Replacement                                                                                              |
| --------------- | --------------------- | ----------------------------- | -------------------------------------------------------------------------------------------------------- |
| tenants.tsx     | Tenant Name           | `e.g. Acme Corp`              | `e.g. Garage du coin`                                                                                    |
| tenants.tsx     | Description           | `Optional description`        | Optional: e.g. `Porsche, Mercedes, Audi` (brands only = subtle luxury wink)                              |
| tenants.tsx     | Organization Name     | `Acme Corporation`            | `Garage du coin` or `Garage du coin (Porsche, Mercedes, Audi)` — brands only, no "auto luxueuse" wording |
| tenants.tsx     | Organization Domain   | `acme.com`                    | `garageducoin.com`                                                                                       |
| tenants.tsx     | Primary Contact Name  | `Jane Doe`                    | `Oscar Dupont` (garage contact only)                                                                     |
| tenants.tsx     | Primary Contact Email | `jane@acme.com`               | `oscar@garageducoin.com`                                                                                 |
| admins.tsx      | Username              | `jsmith`                      | `marie.dupont` (generic persona)                                                                        |
| admins.tsx      | First / Last Name     | `John` / `Smith`              | `Marie` / `Dupont`                                                                                       |
| admins.tsx      | Email                 | `jsmith@example.com`          | `marie@garageducoin.com`                                                                                 |
| enrollments.tsx | Enrollment Name       | `e.g. John Smith — iPhone 15` | `e.g. Marie Dupont — iPhone 15` (generic)                                                                |
| enrollments.tsx | Contact Email         | `user@example.com`            | `user@garageducoin.com` (generic)                                                                        |


### 1.2 No change (technical or generic)

- API Keys / API Key detail, Login, Justifications, Search, Auth attempts, Encryption keys — unchanged (see original inventory).

---

## 2. Proposed replacements (conventions) — two personae

**Garage du coin (tenants only)** — contact = **Oscar Dupont** (subtle Sesame Street wink; "garage" contact):
- **Company / tenant name**: `e.g. Garage du coin`
- **Organization name**: `Garage du coin` or `Garage du coin (Porsche, Mercedes, Audi)` — brands only, no "auto luxueuse" wording.
- **Domain**: `garageducoin.com`
- **Primary contact name**: `Oscar Dupont`
- **Primary contact email**: `oscar@garageducoin.com`
- **Optional description** (tenant): e.g. `Porsche, Mercedes, Audi` or keep generic.

**Generic persona (admins, enrollments)** — **Marie Dupont** everywhere else:
- **Admin**: `marie.dupont`, `Marie` / `Dupont`, `marie@garageducoin.com`
- **Enrollment**: `e.g. Marie Dupont — iPhone 15`, `user@garageducoin.com`

---

## 3. Implementation summary

- **tenants.tsx**: 6 placeholders (tenant name, org name, org domain, contact name, contact email; optional description).
- **admins.tsx**: 4 placeholders (username, first name, last name, email).
- **enrollments.tsx**: 2 placeholders (enrollment name, contact email).

Total: 12–13 string edits in 3 files. Subtlety rule: **names and brands only; no descriptive phrase like "garage d'auto luxueuse".**
