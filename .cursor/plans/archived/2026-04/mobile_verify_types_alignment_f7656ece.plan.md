---
name: Mobile verify types alignment
overview: COMPLETED — `VerifyEnrollmentRequest` aligned with Auth API (`challengeResponse` required); types, `enrollmentsApi.verify` body, and unit tests updated; `yarn typecheck` / `yarn test` / `yarn lint` passed. Archived 2026-04.
status: completed
implementationStatus: complete
todos:
  - id: types-required
    content: Make `challengeResponse` required on `VerifyEnrollmentRequest` + JSDoc in `app/services/api/types.ts`
    status: completed
  - id: enrollments-body
    content: Simplify `enrollmentsApi.verify` body to always map `challengeResponse` to Number; update JSDoc
    status: completed
  - id: unit-tests
    content: Fix `enrollments.test.ts` authUrl verify test to include `challengeResponse` and expected JSON body
    status: completed
  - id: validate
    content: Run `yarn typecheck`, `yarn test`, `yarn lint` in `ezkey_mobile/`
    status: completed
isProject: false
---

# Align mobile enrollment verify types with Auth API

**Archived 2026-04 — Plan completed.** Implementation lives in `ezkey_mobile/app/services/api/types.ts`, `enrollments.ts`, and `__tests__/enrollments.test.ts`.

## Context

- Backend: `[ezkey-auth-api/src/main/java/org/ezkey/enrollment/dto/EnrollmentVerifyRequestDto.java](ezkey-auth-api/src/main/java/org/ezkey/enrollment/dto/EnrollmentVerifyRequestDto.java)` marks `challengeResponse` as **REQUIRED** (`Integer`).
- Wire format in `[docs/ENDPOINT.md](docs/ENDPOINT.md)` (POST `/api/v1/enrollments/verify`) includes numeric `challengeResponse`.
- Mobile reference client today declares it optional and the API layer can omit it on the wire:

```36:41:c:\github\ezkey\ezkey_mobile\app\services\api\types.ts
export type VerifyEnrollmentRequest = {
  enrollmentId: string;
  challengeResponse?: string;
  devicePublicKey: string;
  enrollmentProofTokenSigned: string;
};
```

```60:67:c:\github\ezkey\ezkey_mobile\app\services\api\enrollments.ts
  verify: async (payload: VerifyEnrollmentRequest, authUrl?: string) => {
    const config = authUrl ? {baseURL: authUrl} : undefined;
    const body = {
      enrollmentId: Number(payload.enrollmentId),
      challengeResponse: payload.challengeResponse ? Number(payload.challengeResponse) : undefined,
      devicePublicKey: payload.devicePublicKey,
      enrollmentProofTokenSigned: payload.enrollmentProofTokenSigned,
    };
```

- `[EnrollmentWizardScreen.tsx](ezkey_mobile/app/screens/EnrollmentWizard/EnrollmentWizardScreen.tsx)` already blocks submission unless the trimmed challenge is exactly 6 characters and always passes `challengeResponse` into `verify`—so tightening types is **contract alignment**, not a UX change.

## Implementation steps

1. `**[ezkey_mobile/app/services/api/types.ts](ezkey_mobile/app/services/api/types.ts)`**
  - Change `challengeResponse` from optional to **required**: `challengeResponse: string`.
  - Add a short **JSDoc** on `VerifyEnrollmentRequest` (or on `challengeResponse`) stating that this mirrors `EnrollmentVerifyRequestDto`: user-entered response (the app collects a 6-character value; the HTTP client serializes it as a **number** in JSON—see `enrollmentsApi.verify`). Keep comments in **English** per `[ezkey_mobile/AGENTS.md](ezkey_mobile/AGENTS.md)`.
2. `**[ezkey_mobile/app/services/api/enrollments.ts](ezkey_mobile/app/services/api/enrollments.ts)`**
  - Build the request body with `challengeResponse: Number(payload.challengeResponse)` (no `undefined` branch).
  - Adjust the existing file-level / `verify` JSDoc to state that `challengeResponse` is **required** and must match the server-stored challenge (pointer to `docs/ENDPOINT.md` or DTO name is enough).
3. `**[ezkey_mobile/app/services/api/__tests__/enrollments.test.ts](ezkey_mobile/app/services/api/__tests__/enrollments.test.ts)`**
  - Update the test **"verify passes authUrl as baseURL config when provided"** so the payload includes a `challengeResponse` (e.g. same pattern as the first verify test).
  - Expect `mockedPost` to receive a body with numeric `challengeResponse`, not `undefined`.
  - This replaces the old test that encoded the **permissive** contract; the new test still proves `authUrl` → `baseURL` while matching the real API.
4. **Regression checks (run locally after implementation)**
  - From `ezkey_mobile/`: `yarn typecheck` and `yarn test` (and `yarn lint` if you touch formatting).
  - No Playwright/browser scope here (mobile-only API types and unit tests).

## Risk assessment

- **Runtime**: Low—the only production call site already supplies `challengeResponse` after validation.
- **TypeScript**: Any future caller that omits the field will fail at compile time (desired).
- **Docs**: No change to `MOBILE_DEVELOPER_GUIDE.md` unless you want a cross-link later; your guide is already correct.

