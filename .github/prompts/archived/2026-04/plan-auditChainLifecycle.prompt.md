# Plan : Audit Chain Lifecycle

> **Status : COMPLETED — 2026-04-19**

**TL;DR** : Le plan initial centré sur `seal-archive` et `declare-gap` a été clôturé dans une exécution plus large de refactoring vers un lifecycle d'audit unique et opinionated. Le backend, la configuration active, la documentation de propriétés, les contrats OpenAPI et les tests ciblés ont été réalignés sur ce modèle. L'export externe effectif et les ajustements UI/Postman restent hors scope de cette clôture.

## Résultat atteint

- Lifecycle checkpoints étendu à `ACTIVE -> SEALED -> EXPORTED -> PURGEABLE -> PURGED`
- Purge physique autorisée uniquement par progression du lifecycle
- Automatisation backend du lifecycle en place
- Endpoints backend et contrats API complets pour `seal-archive`, `declare-gap`, `archive-eligibility`, `confirm-archived`
- RFC 9457 `ProblemDetail` aligné sur les chemins d'erreur lifecycle
- Documentation centralisée des propriétés `ezkey.audit.archive.*`
- Compression du temps du `clean-start` Docker ajustée pour exercer le lifecycle sans exporter réellement

## Hors scope maintenu

- export réel vers stockage externe / streaming bundles
- implémentation `ezkey-common-line` Python pour l'export
- ajustements UI et ménage Postman du lifecycle

## Références

- [docs/AUDIT_LOG_LIFECYCLE_REFRAMING.md](../../../docs/AUDIT_LOG_LIFECYCLE_REFRAMING.md)
- [docs/AUDIT_LOG_LIFECYCLE_NEXT_SESSION_BRIEF.md](../../../docs/AUDIT_LOG_LIFECYCLE_NEXT_SESSION_BRIEF.md)
- [.cursor/plans/archived/2026-04/audit_chain_lifecycle_b42e184a.plan.md](../../../.cursor/plans/archived/2026-04/audit_chain_lifecycle_b42e184a.plan.md)