Original prompt (verbatim)

avant de procéder a l'implémentation tu vas préparer un fichier tasks.md en reprendant les milestone M1 M2 etc et rédigeant des listes de taches todo. le but est qu'on va procéder par étapes et il est possible que j'en fasse des bouts et qu'on alterne le codage entre toi et moi alors je veux cette liste pour maintenir l'état d'avancement et pouvoir cocher et reviser pour se retrouver facilement et ajuster le tir au besoin. au tout debut du fichier, inclut ce prompt verbatim et sa version propre et enchaine avec la liste des milesstone, todo, task détaillée !

Cleaned prompt

- Prepare a `tasks.md` file before implementation.
- Include this prompt verbatim at the top, then a cleaned version.
- Reuse milestones (M1, M2, …) and write detailed TODO task lists.
- We will alternate coding; use this checklist to track progress, check off items, and adjust as needed.

Milestones and detailed TODOs

M1 — Project setup and OpenAPI models

- [x] Create Spring Boot 3.x project skeleton (module: `ezkey-demo-device`, Java 21)
- [x] Configure port `server.port=8083`
- [x] Add dependencies in `pom.xml`:
  - [x] `spring-boot-starter-web`
  - [x] `spring-boot-starter-thymeleaf`
  - [x] `spring-boot-starter-webflux` (WebClient)
  - [x] WebJars (Bootstrap)
  - [x] Jackson (annotations)
  - [x] `jakarta.validation:jakarta.validation-api`
- [x] Configure OpenAPI Generator plugin (copy exact settings from demo-acme-app):
  - [x] `generatorName=java`, `library=resttemplate`, `serializationLibrary=jackson`, `useJakartaEe=true`
  - [x] generateModels=true, generateApis=false, generateSupportingFiles=false
  - [x] identical date/time/type mappings as demo-acme
  - [x] `modelPackage=org.ezkey.demodevice.generated.dto`
  - [x] `output=${project.build.directory}/generated-sources/openapi`
  - [x] `inputSpec=http://localhost:8080/v3/api-docs` (auth-api)
  - [x] Add build-helper-maven-plugin to add generated sources
- [x] Basic folders: `controller`, `service`, `templates`
- [x] Minimal base pages and CSS (simple Bootstrap-based, no Neo Brutalism)
- [~] Verify `mvn package` generates DTOs successfully (depends on auth-api running at 8080)

M2 — Device crypto + local store

- [x] Implement `DeviceCryptoService`
  - [x] Generate RSA-2048 key pair
  - [x] Sign payloads (SHA256withRSA)
  - [x] Base64/encoding helpers for keys and signatures
  - [ ] Unit test basic sign/verify round-trip
- [x] Implement `EnrollmentStoreService` (filesystem JSON)
  - [x] Storage root `data/enrollments/`
  - [x] Save one JSON per enrollmentId containing: enrollmentUrl, device keys, metadata
  - [x] Load/list/delete operations
  - [ ] Add simple tests for save/load/list

M3 — Enrollment flow (Bind → Verify) — revised to use real auth-api endpoints

- [ ] `HomeController` and home page `/`
  - [x] Input for Enrollment URL
  - [x] Store URL (sessionStorage on client) and navigate to `/phone`
  - [ ] Optionally mirror server-side capture if needed later
- [ ] `PhoneController` `/phone`
  - [x] Render generic phone grid UI; include featured “Ezkey” app icon
- [ ] `EzkeyAppController` — Ezkey mobile app simulation
  - [ ] App home `/phone/ezkey` with actions:
    - [ ] “My Enrollments” (list stored from JSON files)
  - [ ] Enrollment begin (Bind) — when user taps Ezkey from phone home, immediately process the captured URL:
    - [ ] Read Enrollment URL from sessionStorage (client) or pass via query param to server controller
    - [ ] Parse to extract the bind endpoint: `GET /api/v1/enrollment/bind/{id}` (real endpoint; do not invent)
    - [ ] Call bind endpoint via `AuthApiService`
    - [ ] Persist bind response fields in `EnrollmentStoreService` record:
      - [ ] `integrationPublicKey`
      - [ ] `enrollmentProofToken`
      - [ ] Also persist `enrollmentUrl`
      - [ ] Generate and persist device key pair (public/private Base64) if not already
  - [ ] Prompt user for challenge code (displayed in demo-acme) after bind
  - [ ] Verify step `POST /api/v1/enrollments/verify`
    - [ ] Build request with: `enrollmentId`, `challengeResponse`, `devicePublicKey`, `enrollmentProofTokenSigned`
    - [ ] Compute `enrollmentProofTokenSigned` by signing the proof token using `DeviceCryptoService`
    - [ ] Submit verify; on success, update stored record (e.g., mark verified, keep keys and tokens)
- [ ] Templates (Thymeleaf) for Ezkey app pages:
  - [ ] `/phone/ezkey` app home
  - [ ] `/phone/ezkey/enrollment/bind` result + challenge prompt
  - [ ] `/phone/ezkey/enrollment/verify` success/failure feedback
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


