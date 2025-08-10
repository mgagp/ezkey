Original prompt (verbatim)

avant de procéder a l'implémentation tu vas préparer un fichier tasks.md en reprendant les milestone M1 M2 etc et rédigeant des listes de taches todo. le but est qu'on va procéder par étapes et il est possible que j'en fasse des bouts et qu'on alterne le codage entre toi et moi alors je veux cette liste pour maintenir l'état d'avancement et pouvoir cocher et reviser pour se retrouver facilement et ajuster le tir au besoin. au tout debut du fichier, inclut ce prompt verbatim et sa version propre et enchaine avec la liste des milesstone, todo, task détaillée !

Cleaned prompt

- Prepare a `tasks.md` file before implementation.
- Include this prompt verbatim at the top, then a cleaned version.
- Reuse milestones (M1, M2, …) and write detailed TODO task lists.
- We will alternate coding; use this checklist to track progress, check off items, and adjust as needed.

Milestones and detailed TODOs

M1 — Project setup and OpenAPI models

- [ ] Create Spring Boot 3.x project skeleton (module: `ezkey-demo-device`, Java 21)
- [ ] Configure port `server.port=8083`
- [ ] Add dependencies in `pom.xml`:
  - [ ] `spring-boot-starter-web`
  - [ ] `spring-boot-starter-thymeleaf`
  - [ ] `spring-boot-starter-webflux` (WebClient)
  - [ ] WebJars (Bootstrap or minimal CSS if desired)
  - [ ] Jackson (annotations)
  - [ ] `jakarta.validation:jakarta.validation-api`
- [ ] Configure OpenAPI Generator plugin (copy exact settings from demo-acme-app):
  - [ ] `generatorName=java`, `library=resttemplate`, `serializationLibrary=jackson`, `useJakartaEe=true`
  - [ ] generateModels=true, generateApis=false, generateSupportingFiles=false
  - [ ] identical date/time/type mappings as demo-acme
  - [ ] `modelPackage=org.ezkey.demodevice.generated.dto`
  - [ ] `output=${project.build.directory}/generated-sources/openapi`
  - [ ] `inputSpec=http://localhost:8080/v3/api-docs` (auth-api)
  - [ ] Add build-helper-maven-plugin to add generated sources
- [ ] Basic folders: `controller`, `service`, `templates`, `static/css`
- [ ] Minimal base layout and CSS (no Neo Brutalism)
- [ ] Verify `mvn package` generates DTOs successfully

M2 — Device crypto + local store

- [ ] Implement `DeviceCryptoService`
  - [ ] Generate RSA-2048 key pair
  - [ ] Sign payloads (compatible with auth-api proof expectations)
  - [ ] Base64/encoding helpers as needed
  - [ ] Unit test basic sign/verify round-trip
- [ ] Implement `EnrollmentStoreService` (filesystem JSON)
  - [ ] Decide storage root (e.g., `data/enrollments/` or `${user.home}/.ezkey/demo-device/enrollments/`)
  - [ ] Save one JSON per enrollmentId containing: enrollmentUrl, device keys, metadata
  - [ ] Load/list/delete operations
  - [ ] Add simple tests for save/load/list

M3 — Enrollment flow (Bind → Verify)

- [ ] `HomeController` and home page `/`
  - [ ] Input for Enrollment URL
  - [ ] POST to capture/store URL (session or temp store)
  - [ ] Button: “Launch Phone Simulator” → `/phone`
- [ ] `PhoneController` `/phone`
  - [ ] Render generic phone grid UI; include “Ezkey” app icon
- [ ] `EzkeyAppController` — Ezkey app pages
  - [ ] App home `/phone/ezkey` with actions:
    - [ ] “Handle Enrollment” (uses captured URL)
    - [ ] “My Enrollments” (list stored)
  - [ ] Start enrollment `/phone/ezkey/enrollment/start`
    - [ ] Parse Enrollment URL (extract needed identifiers/tokens)
    - [ ] Generate device key pair via `DeviceCryptoService`
    - [ ] Build device proof/payload
    - [ ] Call auth-api bind endpoint
  - [ ] Verify step `/phone/ezkey/enrollment/verify`
    - [ ] Prompt user for challenge if policy requires
    - [ ] POST verify with enrollmentId, device public key, signed proof token
    - [ ] On success, persist JSON via `EnrollmentStoreService`
- [ ] Templates (Thymeleaf) for the above pages with simple, clean styling
- [ ] Error/empty states and basic messaging

M4 — Auth attempt flow (Pending → Respond)

- [ ] Enrollments list `/phone/ezkey/enrollments`
  - [ ] Read JSON files, display list with select/delete actions
- [ ] Enrollment dashboard `/phone/ezkey/enrollments/{id}`
  - [ ] Action: “Check Pending Auth Attempt” → `/phone/ezkey/enrollments/{id}/auth`
- [ ] Pending `/phone/ezkey/enrollments/{id}/auth`
  - [ ] Fetch pending auth attempt using auth-api
  - [ ] Show attempt info; prompt for challenge if required
  - [ ] Prompt Accept or Deny
- [ ] Respond `/phone/ezkey/enrollments/{id}/auth/respond`
  - [ ] Build respond request (include device-signed proof token, accept/deny, challenge if required)
  - [ ] Call auth-api respond; display outcome
- [ ] UX: loading states, error messages

M5 — Polish, docs, and tests

- [ ] Service tests (crypto, store, minimal integration for service calls guarded by profile)
- [ ] Improve parsing/validation of Enrollment URL with helpful errors
- [ ] Finalize CSS and templates (consistent simple theme)
- [ ] README for demo usage and disclaimers (private key in JSON is DEMO ONLY)
- [ ] Screenshots/gifs of flows (optional)
- [ ] Review against PRD and adjust flows/labels

Backlog / nice-to-have

- [ ] Optional: In-memory store alternative for quick reset
- [ ] Optional: Import/export enrollments as a zip
- [ ] Optional: Toggle polling for pending attempts

Notes / decisions

- Use the exact OpenAPI Generator plugin parameters from demo-acme-app; only `inputSpec` changes to auth-api docs.
- Keep this module independent; copy and adapt crypto logic instead of referencing `ezkey-core` directly.
- Store private keys only for demo; mark clearly in UI and README.


