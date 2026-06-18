# Ezkey Demo Device — Agent Notes

Spring Boot app simulating a mobile device for Auth API enrollment and MFA flows. Calls Auth API via **WebClient**; persists enrollments as JSON files under `data/enrollments/`.

Monorepo copy lives under `ezkey/ezkey-demo-device/`; public standalone mirror: `mgagp/ezkey-demo-device` (sync Java `src/`, `openapi-spec.json`, `pom.xml`, `AGENTS.md` after monorepo changes).

## QR `authUrl` routing (mobile parity)

When an Admin UI enrollment QR includes `authUrl`, the demo device routes **bind**, **verify**, **pending**, and **respond** for that enrollment to that base URL. The validated URL is persisted in `EnrollmentStoreService.Record.enrollmentUrl`.

| Entry path | Auth API base used | Persisted `enrollmentUrl` |
|------------|-------------------|---------------------------|
| QR with valid `authUrl` | QR URL | QR URL |
| QR without `authUrl` (pipe format) | `ezkey.auth.api.url` | null |
| Manual ID + token only | `ezkey.auth.api.url` | null |
| Later ops (verify, auth) | stored `enrollmentUrl` or config default | unchanged |

- Client: `enrollment-qr-import.js` populates hidden form field `authApiBaseUrl`.
- Server: `EnrollmentAuthApiUrlResolver` + `AuthApiUrlValidator` (mirrors mobile `urlValidation.ts`).
- Invalid non-blank QR URL → hard error (no silent fallback to config).

Standalone Docker default remains Exp1 (`https://exp1-auth-api.ezkey.org`); QR local enrollments override without `.env` change.

## Jackson 3 posture (Boot 4)

Ezkey uses the **Jackson 3 engine** with **FasterXML annotations** unchanged ([JSTEP-1](https://github.com/FasterXML/jackson-future-ideas/wiki/JSTEP-1)):

| Layer | Package | Demo-device usage |
|-------|---------|-------------------|
| Engine (`ObjectMapper`, serializers) | `tools.jackson.databind` | `EnrollmentStoreService`, tests |
| Annotations (`@JsonProperty`, `@JsonInclude`, …) | `com.fasterxml.jackson.annotation` | `EnrollmentStoreService.Record`, OpenAPI-generated DTOs |

Do **not** migrate annotations to `tools.jackson.annotation` — that namespace does not exist by design.

- **No** explicit `com.fasterxml.jackson.databind` or `jackson-datatype-jsr310` dependencies in this module.
- **OpenAPI generator** (`openapi-generator-maven-plugin`, `java` + `resttemplate`): models only (`generateApis=false`, `generateSupportingFiles=false`, `addCompileSourceRoot=false`). Compile path is scoped to `generated/dto` via build-helper. DTOs use `com.fasterxml.jackson.annotation`; HTTP JSON uses Spring Boot 4 WebClient codecs (Jackson 3).
- **`openApiNullable=false`** — no `jackson-databind-nullable` dependency.

## Regression anchor

`EnrollmentStoreRecordJsonRoundtripTest` guards enrollment file JSON field naming and round-trip. Run after mapper or `Record` changes:

```bash
mvn test -pl ezkey-demo-device -Dtest=EnrollmentStoreRecordJsonRoundtripTest
```

## OpenAPI models

- Spec: `openapi-spec.json` (generated Auth API snapshot; refresh via monorepo `scripts/update-specs.sh` when authorized).
- Generated package: `org.ezkey.demodevice.generated.dto`.
- Hand-written HTTP: `AuthApiService` (WebClient + generated DTOs).

## Running

**Clean-start stack (monorepo):** demo device on http://localhost:8083, Auth API via Docker internal URL.

**Standalone Docker (Exp1):** `./start.sh` — http://localhost:3080 (see standalone repo).

**Local JDK:** from module directory, `mvn test` then `mvn spring-boot:run` or run the repackaged JAR.
