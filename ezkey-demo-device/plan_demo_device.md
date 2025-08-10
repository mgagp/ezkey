Original prompt (verbatim)

lit plan.md et le pom.xml du ezkey-demo-acme.app préambule à notre nouvel objectif ezkey-demo-device. commence par rédiger un plan_demo_device.md dans ce projet. le stack sera comme demo-acme-app excepté que le css sera plus sobre (pas de neo-brutalism) ce sera un projet qui simule un device mobile (un telephone intelligent générique) afin de faire la demo des api de ezkey-auth-api. les api auth-app seront sur http://localhost:8080/v3/api-docs et comme pour demo-acme-app le pom.xml devra inclure la genération des DTO à partir de la spec openapi. utilise exactement les meme parametres du plugin maven de generation car je l'ai ajusté moi-meme et il est ok. le projet ezkey-demo-device va implémenter une page d'acceuil qui va demander un URL qui représente un enrollment à compléter. un bouton va alors offrir de lancer le telephone simulé. ce telephone simulé sera présenté avec un écran d'acceuil générique d'apps typique de cell. dont une app qui sera ezkey. quand on va cliquer sur cet icone ezkey on entre dans la simulation de l'app mobile. la page d'acceuil de cette app offre de prendre en cahrge un enrollment. quand on click dessus ca utilise le URL préalablement saisie. la, on suit le workflow de l'api auth-app c'est à dire, le bind. l'app telephnoe simule va alors générer une clé privée de device (regarde le projet core dans la classe SignatureService pour les détails. il faudra ajouter dans demo-device un service pour ca, copier le code est ok, il faut garder le projet completement indépendant. ensuite, le workflow est le verify API. qui envoi le enrollment id, la cle publique du device et le proof token signé. si le challenge était demandé, le telephone doit l'avoir demandé a l'usager (ce challenge est affiché dans la console demo-app-acme).  une fois le enrollment complété il faut que le backend demo device store le URL enrollment et la clé device et pour ca, utilise un fichier par enrollment en json, ce sera suffisant pour fin de demo. le telephone simule aura alors un enrollment a lister. donc, prevoir que l'app ezkey mobile simulee doit gérer une liste de enrollment et pouvoir en selectionner un. Quand un enrollment est selectionné on peut passer a la gestion d'une auth attempt. dans cet ecran de l'app mobile, on utilise le workflow API auth-api pending pour lire un auth attemp. pour cela, le device doit générer un proof token et encore ici, inspire toi du SignatureService. la derniere étape c'est l'API respond du auth attempt. si un challenge était requis, l`usager doit l'avoir saisi sur le telephone. l'usager doit aussi s'etre fait demande si il accepte ou refuse la demande d'authentification. le tout est envoyé avec le POST respond qui inclut le proof token signé par le device (check 'api pour les détails). commence par rédiger le plan a partir de toutes ces infos, inclut ce prompt tel quel au debut et ensuite une version propre du prompt tout de suite apres puis rédire le plan en donnant tous les détails du projet, les étapes d'implementation, la description du workflow dans le device simule et on revise ensemble apres

Cleaned prompt

- Read `plan.md` and `pom.xml` of `ezkey-demo-app-acme` as context.
- Create `plan_demo_device.md` in this project for the new `ezkey-demo-device` objective.
- Stack similar to demo-acme-app, but CSS is simple (no Neo Brutalism).
- Purpose: simulate a generic smartphone to demo Ezkey auth API (`ezkey-auth-api`).
- OpenAPI spec available at `http://localhost:8080/v3/api-docs`.
- `pom.xml` must generate DTOs from the OpenAPI spec using exactly the same OpenAPI Generator plugin parameters as demo-acme-app.
- UX flow:
  - Home page asks for an Enrollment URL to complete; button launches phone simulator.
  - Phone home shows generic app grid including Ezkey app.
  - Inside Ezkey app: option to handle an enrollment using the previously entered URL.
  - Follow auth-app workflow: bind, then verify.
  - Generate device private key (copy SignatureService logic into demo-device as an independent service).
  - Verify sends enrollmentId, device public key, and signed proof token. If challenge is required, prompt user for it.
  - On completion, persist per-enrollment JSON file (URL + device key). Maintain a list of enrollments for the device.
  - For a selected enrollment, handle auth attempts: poll/read pending, generate device proof token, then respond (accept/deny) with signed proof and optional challenge input.

Plan – ezkey-demo-device

1) Objectives and scope

- Build a Spring Boot web app that simulates a smartphone UI to exercise the Ezkey Auth API end-to-end.
- Keep UI minimalistic (no Neo Brutalism), but use Thymeleaf templates and WebJars if helpful.
- Implement enrollment workflow (bind + verify) and authentication workflow (pending + respond).
- Persist device enrollments locally as JSON files (one file per enrollment) for demo purposes.
- Keep the project independent: copy cryptographic helpers (SignatureService-like) into this module.

2) Tech stack and dependencies

- Spring Boot 3.x (Java 21)
- spring-boot-starter-web (MVC)
- spring-boot-starter-thymeleaf
- spring-boot-starter-webflux (WebClient for HTTP calls)
- WebJars (optional: Bootstrap for simple styling)
- Jackson (JSON handling)
- OpenAPI Generator (models only) – exactly same plugin options as demo-acme-app, but `inputSpec` points to `http://localhost:8080/v3/api-docs`
- Validation API (jakarta.validation)

3) OpenAPI DTO generation (pom.xml)

- Replicate the OpenAPI Generator plugin from `ezkey-demo-app-acme` with identical parameters:
  - generatorName: java
  - library: resttemplate
  - serializationLibrary: jackson
  - useJakartaEe: true
  - generateApis: false
  - generateModels: true
  - generateSupportingFiles: false
  - modelPackage: `org.ezkey.demodevice.generated.dto`
  - apiPackage: `org.ezkey.demodevice.generated.api` (not used, but set)
  - invokerPackage: `org.ezkey.demodevice.generated.client`
  - date/time mapping settings identical (as in demo-acme pom)
  - output: `${project.build.directory}/generated-sources/openapi`
- Only change: `inputSpec` to `http://localhost:8080/v3/api-docs` (or copy locally and reference file path during offline dev if needed).
- Add build-helper-maven-plugin to add generated sources to compilation, identical to demo-acme-app.

4) High-level architecture

- Controllers (MVC):
  - `HomeController` – home page with Enrollment URL input and button to launch phone simulator.
  - `PhoneController` – renders generic phone home grid and routes to the Ezkey app.
  - `EzkeyAppController` – handles Ezkey app pages: enrollment flow, enrollments list/selection, auth attempt flow.

- Services:
  - `AuthApiService` – calls ezkey-auth-api endpoints (bind enrollment, verify enrollment, fetch pending auth attempt(s), respond to auth attempt).
  - `DeviceCryptoService` – internal, generates RSA key pair, signs tokens (copied/adjusted from `ezkey-core` `SignatureService`).
  - `EnrollmentStoreService` – simple filesystem persistence: one JSON file per enrollment with device keys and metadata.

- Templates (Thymeleaf): simple, sober CSS (Bootstrap-like) without Neo Brutalism.

5) UX and page flow

5.1 Home – `/`
- Minimal page with:
  - Input: Enrollment URL (text)
  - Action: “Launch Phone Simulator” → `/phone` (URL stored in session or a lightweight server-side holder until consumed)

5.2 Phone home – `/phone`
- Grid of app icons; includes an “Ezkey” app icon.
- Tapping Ezkey → `/phone/ezkey` (Ezkey app home)

5.3 Ezkey app home – `/phone/ezkey`
- Options:
  - “Handle Enrollment” – starts enrollment flow using the previously captured Enrollment URL
  - “My Enrollments” – list enrollments stored on this simulated device

5.4 Enrollment flow (Bind → Verify)
- Step A: Bind (URL parsing)
  - Parse Enrollment URL to extract necessary parameters (e.g., enrollmentId or token as defined by auth-api); show parsed preview and confirmation.
  - Generate RSA key pair via `DeviceCryptoService` (private key stored only locally for demo).
  - Prepare device proof/signature payload as required by auth-api.
  - Call auth-api bind endpoint with enrollment-identifying data.

- Step B: Verify
  - Build `verify` request with: enrollmentId, device public key, and device-signed proof token.
  - If challenge required by policy, prompt user to enter the numeric challenge provided in demo-acme console/details.
  - Send to auth-api verify endpoint and show result.
  - On success, persist JSON file for this enrollment via `EnrollmentStoreService`:
    - enrollmentId, integrationId (if available), enrollmentUrl, devicePublicKey, devicePrivateKey (DEMO ONLY), challengeRequired flag, createdAt, device label.

5.5 Enrollments list – `/phone/ezkey/enrollments`
- Read all JSON files; list enrollments (ID, label, challenge policy). Actions:
  - Select → goes to enrollment dashboard `/phone/ezkey/enrollments/{id}`
  - Delete (for cleanup)

5.6 Enrollment dashboard – `/phone/ezkey/enrollments/{id}`
- Actions:
  - “Check Pending Auth Attempt” → `/phone/ezkey/enrollments/{id}/auth`

5.7 Auth Attempt flow (Pending → Respond)
- Step A: Pending
  - Query auth-api pending endpoint for the selected enrollment.
  - Display pending attempt details.
  - Device generates a fresh device proof token (using `DeviceCryptoService`).
  - If challenge required, prompt user to enter challenge.

- Step B: Respond
  - Prompt user to Accept or Deny.
  - Build respond request with device-signed proof, accept/deny flag, and challenge value when applicable.
  - POST to auth-api respond endpoint and display the outcome.

6) Controllers and endpoints (server-side)

- `HomeController`
  - GET `/` – render home
  - POST `/capture-enrollment-url` – store URL (session or temp store) and redirect to `/phone`

- `PhoneController`
  - GET `/phone` – render generic phone UI (grid)

- `EzkeyAppController`
  - GET `/phone/ezkey` – app home
  - GET `/phone/ezkey/enrollment/start` – start enrollment (reads captured URL)
  - POST `/phone/ezkey/enrollment/bind` – perform bind (generates keys, prepares proof)
  - POST `/phone/ezkey/enrollment/verify` – perform verify (takes challenge if needed)
  - GET `/phone/ezkey/enrollments` – list local enrollments (from JSON store)
  - GET `/phone/ezkey/enrollments/{id}` – enrollment dashboard
  - GET `/phone/ezkey/enrollments/{id}/auth` – display pending; if present, prompt UI for respond
  - POST `/phone/ezkey/enrollments/{id}/auth/respond` – submit accept/deny + proof (+ challenge)

7) Services – responsibilities

- `AuthApiService`
  - `Mono<BindResponseDto> bind(BindRequestDto)`
  - `Mono<VerifyResponseDto> verify(VerifyRequestDto)`
  - `Mono<AuthAttemptDto> getPending(Integer enrollmentId)` (or list if API supports multiple)
  - `Mono<RespondResponseDto> respond(RespondRequestDto)`
  - Built on `WebClient`; base URL configurable (default `http://localhost:8080`).

- `DeviceCryptoService`
  - `KeyPair generateDeviceKeyPair()` (RSA-2048)
  - `String sign(String base64Payload, PrivateKey privateKey)` returning a compact/base64 signature where required
  - `String buildDeviceProof(Map<String,Object> claims, PrivateKey)` – mirrors `SignatureService` approach for JWT-like payloads if applicable

- `EnrollmentStoreService`
  - Root path: `${user.home}/.ezkey/demo-device/enrollments/` (or `data/enrollments/` under project) – simple and portable
  - `void save(EnrollmentRecord record)` – one JSON file per `enrollmentId`
  - `Optional<EnrollmentRecord> load(Integer enrollmentId)`
  - `List<EnrollmentRecord> list()`
  - `void delete(Integer enrollmentId)`

8) Data model (local store) – EnrollmentRecord (JSON)

```json
{
  "enrollmentId": 21,
  "integrationId": 1,
  "enrollmentUrl": "ezkey://enroll/21?...",
  "devicePublicKey": "...",
  "devicePrivateKey": "... (DEMO ONLY)",
  "authAttemptChallengeRequired": true,
  "deviceLabel": "John's iPhone",
  "createdAt": "2025-01-01T12:00:00Z"
}
```

9) Templates (Thymeleaf) – pages

- `templates/index.html` – home (Enrollment URL input + Launch Phone button)
- `templates/phone/home.html` – generic phone grid UI
- `templates/phone/ezkey/home.html` – Ezkey app home
- `templates/phone/ezkey/enrollment_bind.html` – bind step
- `templates/phone/ezkey/enrollment_verify.html` – verify step (with challenge input when needed)
- `templates/phone/ezkey/enrollments_list.html` – list stored enrollments
- `templates/phone/ezkey/enrollment_details.html` – per-enrollment dashboard
- `templates/phone/ezkey/auth_pending.html` – show pending attempt
- `templates/phone/ezkey/auth_respond.html` – accept/deny + challenge form

10) Configuration

- `server.port=8083` (avoid collisions with demo-acme on 8082 and auth-api on 8080)
- `ezkey.auth.api.url=http://localhost:8080`
- Profiles: `dev` default; simple logging config.

11) Security and privacy (demo notes)

- Private keys are stored in plain JSON strictly for demo purposes. Add clear disclaimers in UI and README.
- No real authentication; these pages are for demo on localhost.

12) Error handling & UX

- Basic try/catch with friendly messages.
- Show API errors and hints (e.g., “Start ezkey-auth-api on port 8080”).
- Loading/disabled states on submit buttons.

13) Milestones & tasks

M1 – Project setup
- Spring Boot skeleton, port 8083
- Basic templates and static assets (simple CSS)
- OpenAPI models generation plugin configured (same params as demo-acme, inputSpec to 8080 docs)

M2 – Device crypto + local store
- Implement `DeviceCryptoService` (copy/adapt from `SignatureService` in core)
- Implement `EnrollmentStoreService` (JSON per enrollment)

M3 – Enrollment flow
- Home capture URL → Phone → Ezkey app
- Bind + Verify steps wired to `AuthApiService`
- Persist enrollment on success; list enrollments

M4 – Auth attempt flow
- Pending retrieval for selected enrollment
- Prompt for challenge (if required) and Accept/Deny
- Respond endpoint with device-signed proof

M5 – Polish
- UX refinements, error states, basic tests for services
- README updates and screenshots

14) References

- `ezkey-demo-app-acme/plan.md` and `pom.xml` for plugin and stack patterns
- `ezkey-core` `SignatureService` for crypto logic inspiration
- PRD.txt for overall product behavior and constraints


