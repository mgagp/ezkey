# Demo: simulated MITM on Pending (integration signature)

This feature is intended for **presentations and security training**, not production traffic. It requires **two** gates: a global Auth API setting and a per-attempt flag.

## Behavior

1. **Global gate (Auth API):** `ezkey.demo.mitm-signature-enabled` (Spring property).  
   Environment variable: **`EZKEY_DEMO_MITM_SIGNATURE_ENABLED`** (`true` / `false`, relaxed binding).

2. **Per-attempt flag:** When creating an auth attempt via Admin API or M2M API, pass  
   `"demoMitmSignatureRequested": true`. This persists `demo_mitm_signature_enabled` on the row.  
   In Admin UI, use the demo checkbox (requires `VITE_DEMO_MODE` and session demo mode).

3. **Effect:** On `POST .../auth-attempts/pending`, after the integration signs the canonical pending payload,  
   the response may **alter** contextual approval text (e.g. a **CAD amount** in the context message) so the JSON  
   **no longer matches** what was signed. If there is no contextual title/message, the demo falls back to altering  
   another pending field so verification still fails. The mobile app then rejects the response, illustrating a MITM  
   that changed the body after signing.

4. **Logging:** When tampering applies, Auth API emits a **WARN** line containing `DEMO_MITM_TAMPER`.

5. **Audit (Admin UI):** The same `POST .../pending` request still produces a single **`AUTH_ATTEMPT_PENDING`** audit row. When tampering applies, **`eventAction`** is `auth_attempt_pending_demo_mitm` (instead of `auth_attempt_pending_found`), and **`eventDetails`** is **business-oriented** (what the integration signed vs what the device received for title/message)—no cryptographic values—so presenters can move from the mobile error to **Admin → Audit logs** and tell the story (e.g. approval amount changed), without a separate event type.

## Docker Compose and clean start

- **`docker/docker-compose.yml`** (and HA / native variants for `auth-api`) set  
  `EZKEY_DEMO_MITM_SIGNATURE_ENABLED` with default **`true`** for local stacks so a **clean start** is demo-ready.  
  Override: `EZKEY_DEMO_MITM_SIGNATURE_ENABLED=false docker compose ...` (or export before `start.sh`).

- **`ezkey-tests/clean-start.sh`** / **`clean-start.ps1`**: unless you already exported the variable, the script sets  
  - **`true`** when **not** using `--prod-safe` / `-ProdSafe` (default demo-friendly),  
  - **`false`** when using **`--prod-safe`** / **`-ProdSafe`** (closer to production).

## Related code

- `EzkeyDemoProperties` (`ezkey.demo.*`)
- `AuthAttemptPendingService` (sign-then-tamper; sets internal demo fields on the domain response)
- `AuthAttemptController` (pending audit: `eventDetails` + `auth_attempt_pending_demo_mitm` when tamper)
- Flyway `V47__add_auth_attempt_demo_mitm_signature.sql`
