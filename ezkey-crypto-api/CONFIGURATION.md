# Configuration Reference — ezkey-crypto-api

Crypto API is a lightweight, **stateless** service that exposes cryptographic operations (RSA
key pair generation, signature verification) via a REST API. It has **no database**, no Flyway,
and no scheduled jobs. It reads encryption keys from files via the Docker volume.

> Encryption properties are defined in [ezkey-core/CONFIGURATION.md](../ezkey-core/CONFIGURATION.md).

---

## Quick Overview

| Property | Default | Obligation | Description |
|---|---|---|---|
| `ezkey.encryption.enabled` | `true` | optionnel | Enable Tink encryption. |
| `ezkey.encryption.master-key-file` | `/etc/ezkey/secrets/master.key` | requis [docker] | Path to the master key file. |
| `ezkey.encryption.keyset-file` | `/etc/ezkey/keysets/keyset.json.encrypted` | requis [docker] | Path to the keyset file. |
| `ezkey.encryption.algorithm` | `AES256_GCM` | optionnel | AEAD algorithm. |
| `ezkey.encryption.keyset.storage-mode` | `FILE` | optionnel | Always `FILE` in Crypto API (no DB). |
| `ezkey.encryption.rotation.enabled` | `false` | optionnel | Always disabled (jobs run in Admin API). |
| `ezkey.encryption.reencryption.enabled` | `false` | optionnel | Always disabled. |

---

## Properties by Functional Group

### Encryption at Rest (`ezkey.encryption.*`)

**Description:** Tink-based encryption used for cryptographic operations. Crypto API uses
**FILE** keyset storage mode exclusively (no database access). Key rotation and re-encryption
jobs are **always disabled** here — they run only in Admin API.

Full documentation: [ezkey-core/CONFIGURATION.md — Encryption at Rest](../ezkey-core/CONFIGURATION.md#encryption-at-rest-ezkeyencryption)

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.encryption.enabled` | `boolean` | `true` | optionnel | Enable/disable Tink encryption. |
| `ezkey.encryption.master-key-file` | `String` | `/etc/ezkey/secrets/master.key` | requis [docker] | Path to the Base64-encoded 256-bit master key file. Mounted via `encryption-secrets` Docker volume. |
| `ezkey.encryption.keyset-file` | `String` | `/etc/ezkey/keysets/keyset.json.encrypted` | requis [docker] | Path to the encrypted Tink keyset file. Mounted via `encryption-secrets` Docker volume. |
| `ezkey.encryption.algorithm` | `String` | `AES256_GCM` | optionnel | AEAD algorithm. Must match the algorithm used by Admin API. |
| `ezkey.encryption.keyset.storage-mode` | `StorageMode` | `FILE` | optionnel | Always `FILE` in Crypto API (no database). |
| `ezkey.encryption.rotation.enabled` | `boolean` | `false` | optionnel | Must remain `false`. Rotation runs only in Admin API. |
| `ezkey.encryption.reencryption.enabled` | `boolean` | `false` | optionnel | Must remain `false`. Re-encryption runs only in Admin API. |

---

## Profile Matrix

| Property | default | docker |
|---|---|---|
| `ezkey.encryption.master-key-file` | `/etc/ezkey/secrets/master.key` | `/etc/ezkey/secrets/master.key` |
| `ezkey.encryption.keyset-file` | `/etc/ezkey/keysets/keyset.json.encrypted` | `/etc/ezkey/keysets/keyset.json.encrypted` |
| `ezkey.encryption.keyset.storage-mode` | `FILE` | `FILE` |
| `ezkey.encryption.rotation.enabled` | `false` | `false` |
| `spring.autoconfigure.exclude` | `DataSourceAutoConfiguration` | `DataSourceAutoConfiguration` |
| `MANAGEMENT_SERVER_PORT` | — | *(not in docker-compose.yml)* |

> Crypto API does **not** appear independently in the main `docker-compose.yml`. It is a
> utility service available via the `crypto-api` build target.

---

## Infrastructure Notes

- **No database**: `spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration` is always set.
- **No Flyway**: `spring.flyway.enabled=false`.
- **No audit**: Does not use `ezkey.audit.*` properties.
- **No organization/QR/enrollment/demo**: Only `ezkey.encryption.*` from ezkey-core.
- **Same master key** as Admin API, Auth API, and Integration API — all four services must share
  the same key material to decrypt the same ciphertexts.

---

## Docker Environment Variable Reference

No `EZKEY_*` application-level env vars for Crypto API. Key material is mounted via the
`encryption-secrets` Docker volume at `/etc/ezkey/`.
