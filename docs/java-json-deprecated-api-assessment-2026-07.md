# Java JSON / Jackson Deprecated API Assessment (2026-07)

## Mandate

- **Surface:** Java JSON processing across Ezkey modules (`ezkey-core`, `ezkey-admin-api`,
  `ezkey-auth-api`, `ezkey-integration-api`, `ezkey-crypto-api`, `ezkey-demo-device`,
  `ezkey-tests`, and related unit tests). Jackson tree model (`JsonNode` / `ObjectMapper`) is the
  primary stack; any other JSON library with deprecated call sites would also be in scope.
- **Attention axes:** methods marked `@Deprecated` on JSON APIs we actually call; prefer
  mechanical, behavior-preserving replacements (Jackson 3 JSTEP-3 text→string renames); keep
  warning volume low ahead of future Jackson minors.
- **Non-goals:** implementing fixes in the assessment session; zero-warning campaigns;
  `java-doctor-curated` / Dependabot shortlists; redesign of JSON persistence shapes; inventing
  `I-*` / `TB-*` per finding; migrating FasterXML annotations (JSTEP-1 keeps
  `com.fasterxml.jackson.annotation` by design).

## Scope and method

- Static white-box inventory of deprecated Jackson 3 databind APIs against production and test
  Java sources.
- Cross-check for non-Jackson JSON libraries (Gson, `org.json`, Jakarta JSON-P) — none found as
  call sites for deprecated APIs.
- Authority: Jackson databind **3.1.5** sources (`tools.jackson.databind.JsonNode`,
  `JsonNodeCreator`) matching parent POM `jackson-bom.version`.

## Stack versions (evidence)

| Component | Version |
| --- | --- |
| Spring Boot (parent) | 4.1.0 (context) |
| `tools.jackson` / jackson-bom | **3.1.5** |
| `com.fasterxml.jackson` bom (annotations / legacy) | 2.22.1 (annotations only; JSTEP-1) |

## Deprecation map (Jackson 3 — actionable in Ezkey)

| Deprecated API (since 3.0) | Replacement | Semantics note |
| --- | --- | --- |
| `JsonNode.asText()` | `asString()` | Coercing conversion (alias; same body) |
| `JsonNode.asText(String)` | `asString(String)` | Default when non-coercible / null / missing |
| `JsonNode.isTextual()` | `isString()` | Type predicate rename |
| `JsonNode.textValue()` | `stringValue()` | Strict string content (not used in Ezkey) |
| `JsonNodeCreator.textNode(String)` | `stringNode(String)` | Factory rename |

**Not deprecated** (explicit false-positive guard): `asInt()`, `asLong()`, `asBoolean()`,
`intValue()`, `ObjectMapper.readTree` / `writeValueAsString`, `createObjectNode`, `put(...)`.
Several helpers still use `asInt()` — that is current API, not hygiene debt.

**Already migrated (positive):** `AuditLifecycleService` reads archive payload fields with
`node.asString()` — no action.

## Findings register

| ID | Title | Severity | Confidence | Category | Deprecated API in use | Sites | Replacement direction | Quick-win potential |
| --- | --- | ---: | --- | --- | --- | ---: | --- | --- |
| JSON-DEP-001 | Auth rate-limit body ID extraction uses `isTextual` / `asText` | P2 | High | **1 — quick win** | `isTextual()`, `asText()` | 4 → 0 | `isString()` + `asString()` | **Closed (fixed 2026-07-27)** |
| JSON-DEP-002 | Test / harness `JsonNode` text accessors | P3 | High | **1 — quick win** | `asText()`, `asText(null)`, `isTextual()` | ~22 → 0 | `asString()` / `asString(null)` / `isString()` | **Closed (fixed 2026-07-27)** |
| JSON-DEP-003 | Test harness `textNode` factory | P3 | High | **1 — quick win** | `JsonNodeCreator.textNode` | 1 → 0 | `stringNode(...)` | **Closed (fixed 2026-07-27)** |

### Category 2 — complex / redesign

No deprecated JSON call sites require concurrency, storage, or contract redesign. Category 2 is
**empty** for this pass.

## Evidence

### JSON-DEP-001 — production Auth API

`RateLimitFilter` extracts numeric IDs from cached request bodies; string-typed JSON numbers go
through the textual branch:

Former usage (both `enrollmentId` and `authAttemptId` extractors):

```java
if (idNode.isTextual()) {
  return Integer.parseInt(idNode.asText());
}
```

Replacement applied (2026-07-27):

```java
if (idNode.isString()) {
  return Integer.parseInt(idNode.asString());
}
```

Risk notes:

- Replacement is a rename alias in Jackson 3.1.5 (`asText()` delegates to `asString()`).
- Fail-open on parse errors is unchanged (method returns `null` in `catch`).
- Blast radius: Auth API rate limiting key extraction only.

Disposition: **closed** — HITL **fix**; both call sites migrated.

### JSON-DEP-002 — tests and functional-test helpers

Call sites (all `tools.jackson.databind`):

| File | Calls |
| --- | ---: |
| `ezkey-tests/.../AdminBootstrapService.java` | 5× `asText()` |
| `ezkey-tests/.../TenantAdminTestHelper.java` | 5× `asText()` |
| `ezkey-tests/.../BootstrapCredentialsExtractor.java` | 2× `asText()` |
| `ezkey-tests/.../AuthTokenManager.java` | 1× `asText()` |
| `ezkey-tests/.../OperationalChurnGlobalAdminState.java` | 2× `asText(null)` |
| `ezkey-admin-api/.../QrCodePayloadServiceTest.java` | 7× `asText()` + 1× `isTextual()` |

Operator seed example: functional-test helper
`org.ezkey.tests.util.AdminBootstrapService` (not the production
`org.ezkey.admin.service.AdminBootstrapService`, which does not call these APIs).

Disposition: **closed** — HITL **fix**; bulk rename applied 2026-07-27.

```691:691:ezkey-tests/src/test/java/org/ezkey/tests/util/BootstrapCredentialsExtractor.java
                .map(code -> mapper.getNodeFactory().stringNode(code))
```

Disposition: **closed** — HITL **fix**; migrated 2026-07-27.

## Checked clean (not deprecated or not used)

| API / pattern | Status in Ezkey sources |
| --- | --- |
| `JsonNode.asInt` / `asLong` / `asBoolean` | Used; **not** `@Deprecated` in 3.1.5 |
| `JsonNode.textValue()` | **Not used** |
| `ObjectMapper.treeToValue(TreeNode, …)` (deprecated since 3.1) | **Not used** — prefer `JsonNode` overloads if added later |
| `ObjectReader` / `ObjectWriter.getTypeFactory()` | **Not used** |
| `SerializationFeature.WRITE_EMPTY_JSON_ARRAYS` | **Not used** |
| Gson / `org.json` / Jakarta JSON-P tree APIs | **Not used** for JSON tree access |
| `com.fasterxml.jackson.annotation.*` on DTOs | Intentional JSTEP-1 posture — **out of scope** |
| Demo Device `ObjectMapper` round-trip | No deprecated text accessors |
| Production `BootstrapCredentialsFileExporter` | Uses `asInt()` only (current) |

## Recommended HITL lot

1. **JSON-DEP-001** — migrate `RateLimitFilter` textual branches to `isString` / `asString` —
   **closed (fixed 2026-07-27)**.
2. **JSON-DEP-002** — bulk rename in test helpers + `QrCodePayloadServiceTest` —
   **closed (fixed 2026-07-27)**.
3. **JSON-DEP-003** — `textNode` → `stringNode` in `BootstrapCredentialsExtractor` —
   **closed (fixed 2026-07-27)**.

All three are category-1 mechanical renames with documented Jackson 3 replacements.
