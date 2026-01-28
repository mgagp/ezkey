# Ezkey Core — Agent Notes

For agents working in `ezkey-core/`.

## JPA entity defaults

- **Timestamps** (`createdAt`, `updatedAt`): `@PrePersist` / `@PreUpdate` when null only.
- **Other defaults** (`active`, status): field init. No `@PrePersist` for these.
- **MapStruct** create→entity: ignore `id`, `createdAt`, `active` (etc.). Example: `IntegrationServiceMapper.toEntity`.

## Non-negotiables

- All project content **in English**.
- Constructor injection; no `@Autowired`.
