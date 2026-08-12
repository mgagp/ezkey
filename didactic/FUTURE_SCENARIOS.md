# Future didactic article scenarios (not implemented)

This file records **intentional follow-up work** that is **out of scope** for the current article template ([`templates/article-full.md`](templates/article-full.md)).

## Current template (implemented)

**“Admin-authorized integration enrollment + MFA trace”** — an operator uses **Admin API** credentials (Bearer from passwordless admin login in the lab) to create **enrollments** on an **integration**, then the transcript walks **bind → verify → auth attempt** on **Auth API** with **Crypto API** as oracle. The narrative in [`article-full.md`](templates/article-full.md) is aligned with that scenario.

## Future template (planned)

**“Integration-first + API key + end-user enrollment”**

- Create or select an **integration** with **operator-realistic** naming; optionally align **`description`** with the **astronomy-flavored** copy style in [`AGENTS.md`](AGENTS.md).
- Provision **API keys** (integration key + secret) for **machine-to-machine** calls.
- Create **enrollments** and/or **auth attempts** using **HTTP Basic / API key** posture where the product supports it (rather than leading with Global Admin Bearer for every call).
- Optional: split article instances so **credential posture** matches **integrator documentation** (tenant admin vs integration key).

When implemented, add a second Markdown template (e.g. `article-integration-api.md`) and extend [`protocol_lab`](../protocol_lab/) only if new steps are required beyond the existing Bruno parity manifest.

## Why keep this split

The two stories serve different readers: **platform operators** pushing enrollments from the admin console vs **application integrators** automating with API keys. Both use the same **cryptographic protocol** on the wire; the **authorization and setup** paths differ.
