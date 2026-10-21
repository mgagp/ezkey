# Configuration Reference — ezkey-crypto-api

Crypto API is a **local lab / clean-start only** utility (ADR-0016 §2.5.1). It is **not**
deployed on Lightsail / ezkey.online. It exposes cryptographic operations (key pairs, sign,
encrypt/decrypt) via REST. Flyway stays off. It uses a **read-only** DataSource (role
`ezkey_crypto`) to `SELECT` `ezkey_keyset_blob` and still needs `master.key` on the secrets volume.

> Encryption properties are defined in [ezkey-core/CONFIGURATION.md](../ezkey-core/CONFIGURATION.md).
> Role matrix: [docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md](../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md).

---

## Quick Overview

| Property | Default | Obligation | Description |
|---|---|---|---|
| `server.port` | `8081` (IDE) / `9090` (docker) | recommandé | Business HTTP port. |
| `management.server.port` | `8087` (IDE) / `9091` (docker) | recommandé | Dedicated Actuator port; host publish `127.0.0.1:9091` locally. |
| `management.server.address` | `127.0.0.1` (IDE only) | recommandé [IDE] | Loopback for IDE; Docker profile sets `0.0.0.0` (#905). |
| `server.servlet.session.cookie.name` | `EZKEY_CRYPTO_API_SESSION` | recommandé | Distinct from other localhost servlet apps (#799). |
| `spring.datasource.*` | `ezkey_crypto` @ Postgres | requis [docker] | Read-only role; SELECT on `ezkey_keyset_blob` only. |
| `spring.flyway.enabled` | `false` | requis | Never migrate from crypto-api. |
| `ezkey.encryption.enabled` | `true` | optionnel | Enable Tink encryption. |
| `ezkey.encryption.required` | `true` | requis | Never `false` (ADR-0016 Q1 / #942). |
| `ezkey.encryption.master-key-file` | `/etc/ezkey/secrets/master.key` | requis [docker] | Path to the master key file. |
| `ezkey.encryption.keyset.storage-mode` | `DATABASE` | requis | Blob-only authority (ADR-0016). |
| `ezkey.encryption.keyset.writer` | `false` | requis | Non-writer; Admin API owns CAS writes. |
| `ezkey.encryption.keyset.degraded-mode-enabled` | `false` | requis | Fail closed. |
| `ezkey.encryption.rotation.enabled` | `false` | optionnel | Always disabled (jobs run in Admin API). |
| `ezkey.encryption.reencryption.enabled` | `false` | optionnel | Always disabled. |

---

## Split health (ADR-0016 Q1)

| Endpoint | Meaning |
|---|---|
| `/actuator/health/liveness` | Process alive (`ping` + `livenessState`). Used by Docker healthchecks and bootstrap-init. Stays UP even when encryption is not ready. |
| `/actuator/health/encryption` | Named group with `tinkEncryption` — DOWN until keyset blob is loaded. |
| `/actuator/health` | Aggregate (may be DOWN when encryption is not ready). Prefer liveness for orchestration. |

```properties
management.endpoint.health.probes.enabled=true
management.endpoint.health.group.liveness.include=ping,livenessState
management.endpoint.health.group.encryption.include=tinkEncryption
management.health.db.enabled=false
```

Encrypt/decrypt stay fail-closed when the keyset is missing (`required=true`). Keypair/sign do not need the blob.

---

## Properties by Functional Group

### DataSource (read-only)

| Property | Type | Default (docker) | Obligation | Description |
|---|---|---|---|---|
| `spring.datasource.url` | `String` | `jdbc:postgresql://postgres:5432/ezkey_db` | requis [docker] | Overridable via `SPRING_DATASOURCE_URL`. |
| `spring.datasource.username` | `String` | `ezkey_crypto` | requis [docker] | SELECT-only role. |
| `spring.datasource.password` | `String` | `ezkey_crypto` | requis [docker] | From `EZKEY_DB_CRYPTO_PASSWORD` / `SPRING_DATASOURCE_PASSWORD`. |
| `spring.jpa.hibernate.ddl-auto` | `String` | `none` | requis | Schema owned by Flyway. |

### Encryption at Rest (`ezkey.encryption.*`)

**Description:** Tink AEAD for `/encrypt` and `/decrypt`. Keyset authority is Postgres
`ezkey_keyset_blob`. On AEAD failure, recovery **reloads the blob** and retries — it does **not**
consult `ezkey_encryption_key` (no GRANT). Key rotation and re-encryption jobs stay disabled here.

Full documentation: [ezkey-core/CONFIGURATION.md — Encryption at Rest](../ezkey-core/CONFIGURATION.md#encryption-at-rest-ezkeyencryption)

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.encryption.enabled` | `boolean` | `true` | optionnel | Enable/disable Tink encryption. |
| `ezkey.encryption.required` | `boolean` | `true` | requis | Never set `false`. |
| `ezkey.encryption.master-key-file` | `String` | `/etc/ezkey/secrets/master.key` | requis [docker] | Base64 256-bit master key on the secrets volume. |
| `ezkey.encryption.keyset-file` | `String` | `/etc/ezkey/keysets/keyset.json.encrypted` | optionnel | Path retained for tooling; blob is authority. |
| `ezkey.encryption.algorithm` | `String` | `AES256_GCM` | optionnel | Must match Admin API. |
| `ezkey.encryption.keyset.storage-mode` | `StorageMode` | `DATABASE` | requis | Blob-only. |
| `ezkey.encryption.keyset.writer` | `boolean` | `false` | requis | Non-writer. |
| `ezkey.encryption.keyset.degraded-mode-enabled` | `boolean` | `false` | requis | Fail closed. |
| `ezkey.encryption.keyset.max-reload-retries` | `int` | `3` | optionnel | Blob reload budget after AEAD failure. |
| `ezkey.encryption.rotation.enabled` | `boolean` | `false` | optionnel | Must remain `false`. |
| `ezkey.encryption.reencryption.enabled` | `boolean` | `false` | optionnel | Must remain `false`. |

---

## Profile Matrix

| Property | default | docker |
|---|---|---|
| `ezkey.encryption.master-key-file` | `/etc/ezkey/secrets/master.key` | `/etc/ezkey/secrets/master.key` |
| `ezkey.encryption.keyset.storage-mode` | `DATABASE` | `DATABASE` |
| `ezkey.encryption.keyset.writer` | `false` | `false` |
| `ezkey.encryption.required` | `true` | `true` |
| `spring.datasource.username` | `ezkey_crypto` | `ezkey_crypto` |
| `spring.flyway.enabled` | `false` | `false` |
| Docker healthcheck | — | `/actuator/health/liveness` |

---

## Infrastructure Notes

- **Read-only DB:** role `ezkey_crypto` — `SELECT` on `ezkey_keyset_blob` only (proven by
  `scripts/db/verify-grants.sh`).
- **No Flyway:** `spring.flyway.enabled=false`.
- **No audit:** Does not use `ezkey.audit.*` properties.
- **Same master key** as Admin / Auth / Integration — decrypt needs matching material.
- **Depends on Admin healthy** in compose so the blob exists before crypto boots.
- **Not on Lightsail** — community compose must not run crypto-api.

---

## Docker Environment Variable Reference

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/ezkey_db` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `ezkey_crypto` | DB role |
| `SPRING_DATASOURCE_PASSWORD` | from `EZKEY_DB_CRYPTO_PASSWORD` | DB password |
| `EZKEY_DB_CRYPTO_PASSWORD` | `ezkey_crypto` | Documented in `docker/.env.example` |

Master key (and optional `audit-hmac.key`) are mounted via the `encryption-secrets` volume at
`/etc/ezkey/`.
