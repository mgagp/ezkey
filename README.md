# Ezkey

<img src="logo.svg" alt="Ezkey Logo" width="200">

Ezkey is an open-source cryptographic MFA platform built as a distinct alternative to browser-centric authentication models.

It does not implement FIDO2 or WebAuthn, and it should not be understood as a simplified passkey variant. Ezkey follows its own protocol and trust model, built around a direct cryptographic relationship between a trusted backend and a mobile application.

Ezkey is designed for developers, especially backend developers, who want a self-hosted MFA system with strong cryptographic guarantees, explicit trust boundaries, and APIs that stay practical to integrate.

It also includes an Admin UI for human administration, giving operators a direct surface for platform and tenant workflows without changing Ezkey's backend-first trust model.

## What Exists Today

- `Admin UI` for day-to-day human administration across Global Admin and Tenant Admin workflows.
- `Admin API` for administration, onboarding, and authentication management.
- `Auth API` for mobile enrollment and authentication flows.
- `Integration API` for machine-to-machine auth attempt lifecycle operations.
- `Mobile app` for device enrollment and user approval flows.
- `CLI and test tooling` for local stack usage, validation, and fallback workflows.
- `Self-hosted stack` for local development, demonstrations, and integration work.

The Admin UI is the primary operator surface. Global Admins manage tenants, platform operations, and sensitive platform actions such as cryptographic key operations. Tenant Admins manage integrations, enrollments, API keys, and day-to-day tenant administration.

## Security Design

Ezkey is built around cryptographic continuity across the full flow, not around a single isolated proof.

- During enrollment, `bind` and `verify` are cryptographically linked.
- During authentication, `pending` and `respond` are cryptographically linked.
- One-time proof tokens and signature validation protect the flow against replay and tampering.
- When secure private-key storage is available on the device, Ezkey uses it as an implementation building block, not as the foundation of its identity.

This is what makes Ezkey a distinct backend-to-mobile security model rather than a passkey-derived approach.

## Quick Start

The supported local quick-start path is the Bash clean start from `ezkey-tests`:

```bash
./ezkey-tests/clean-start.sh
```

On Windows, use Bash as well, for example through Git Bash.

For stack details, modes, and test-oriented workflow:

- [`ezkey-tests/README.md`](ezkey-tests/README.md)
- [`docker/README.md`](docker/README.md)
- [`docs/LOCAL_STACK_PORTS.md`](docs/LOCAL_STACK_PORTS.md)

## Documentation

- [`PRD.md`](PRD.md) - Product requirements and product framing.
- [`docs/PROJECT_POSITIONING.md`](docs/PROJECT_POSITIONING.md) - Strategic positioning and project philosophy.
- [`docs/README.md`](docs/README.md) - Documentation index.
- [`docs/ENDPOINT.md`](docs/ENDPOINT.md) - API reference.
- [`docs/RECOVERY_CODES_LIFECYCLE_ANALYSIS.md`](docs/RECOVERY_CODES_LIFECYCLE_ANALYSIS.md) - Recovery-code lifecycle analysis and recommended regeneration model.
- [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) - Development workflow.
- [`docs/OPERATIONAL.md`](docs/OPERATIONAL.md) - Operational and deployment guidance.
- [`ezkey-admin-ui/README.md`](ezkey-admin-ui/README.md) - Admin UI overview and local usage.
- [`ezkey_mobile/README.md`](ezkey_mobile/README.md) - Mobile application notes.
- [`ezkey-cli-python/README.md`](ezkey-cli-python/README.md) - CLI usage.

## Security and Compliance

Ezkey is developed with strong cryptographic validation and self-hosted operational control in mind. The project also aims to move in a direction that is favorable to SOC 2-oriented operational discipline, without claiming certification or standards equivalence.

## License

This project is licensed under the MIT License. See [`LICENSE`](LICENSE) for details.
