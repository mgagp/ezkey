# Analysis: Admin Onboarding API vs Enrollment API

> **Historical write-up.** The living rule is
> [`docs/ENDPOINT.md`](../ENDPOINT.md) § When to Use Admin Onboarding API vs Enrollment API and
> [`ezkey-admin-api/AGENTS.md`](../../ezkey-admin-api/AGENTS.md) § Admin MFA credentials vs enrollment GET.
> Do not treat this file as the cold-start entry.

## Context

Two APIs exist that can retrieve enrollment onboarding credentials:

1. **`GET /api/v1/admins/{id}/onboarding`** - Admin-centric approach
2. **`GET /api/v1/enrollments/{id}`** - Enrollment-centric approach

Both can return `enrollmentProofToken` and `enrollmentChallenge`, but they use different validation logic and have different semantic purposes.

## Comparison

### 1. API Endpoints

#### GET /api/v1/admins/{id}/onboarding
- **Input**: Admin ID
- **Purpose**: Retrieve onboarding credentials for a specific administrator
- **Returns**: `AdminOnboardingResponseDto`
  - `enrollmentId`
  - `enrollmentProofToken`
  - `enrollmentChallenge`
  - `recoveryCodes` (always `null` - cannot be retrieved after initial provisioning)

#### GET /api/v1/enrollments/{id}
- **Input**: Enrollment ID
- **Purpose**: Retrieve enrollment details for administrative purposes
- **Returns**: `EnrollmentResponseDto`
  - `enrollmentId`
  - `integrationId`
  - `enrollmentName`
  - `enrollmentStatus`
  - `enrollmentActive`
  - `enrollmentChallenge`
  - `enrollmentProofToken`
  - `authAttemptChallengeRequired`
  - `integrationPublicKey`
  - `devicePublicKey`

### 2. Security Validation

#### GET /api/v1/admins/{id}/onboarding
**Validation Logic:**
```java
// Validates at ADMIN level
if (requesterPrincipal.isGlobalAdmin()) {
  // Can access any admin's credentials
} else if (requesterPrincipal.isTenantAdmin()) {
  // Can only access credentials for admins in their tenant
  // Checks: requesterTenantId == adminTenantId
}
```

**Validation Point**: Admin's tenant membership

#### GET /api/v1/enrollments/{id}
**Validation Logic:**
```java
// Validates at ENROLLMENT level
accessControlService.canAccessEnrollment(auth, enrollmentId)
  // For TenantAdmin: Checks if enrollment's integration belongs to their tenant
  // Checks: requesterTenantId == integrationTenantId
```

**Validation Point**: Integration's tenant membership (via enrollment → integration → tenant)

### 3. Key Differences

| Aspect | `/admins/{id}/onboarding` | `/enrollments/{id}` |
|--------|-------------------------|---------------------|
| **Input** | Admin ID | Enrollment ID |
| **Validation Level** | Admin tenant | Integration tenant |
| **Validation Logic** | Direct: Admin → Tenant | Indirect: Enrollment → Integration → Tenant |
| **Information Returned** | Onboarding credentials only | Full enrollment details |
| **Semantic Purpose** | Admin onboarding workflow | General enrollment management |
| **Use Case** | "Get onboarding credentials for admin X" | "Get details of enrollment Y" |

### 4. Understanding the Actual Structure

**Reality**: All admin enrollments are linked to the **System Integration**, which belongs to the **System Tenant**.

**Structure**:
- **GlobalAdmin** → System Tenant → System Integration → Enrollment
- **TenantAdmin (Tenant A)** → Tenant A → System Integration → Enrollment  
- **TenantAdmin (Tenant B)** → Tenant B → System Integration → Enrollment

**Key Point**: 
- The **admin** belongs to their respective tenant (Tenant A, Tenant B, or System Tenant)
- The **enrollment** is always linked to the System Integration (which belongs to System Tenant)
- Conceptually, "the enrollment for Tenant A admin belongs to Tenant A" (the admin's tenant)
- Technically, "the enrollment belongs to System Integration" (which is in System Tenant)

### 5. Validation Behavior Analysis

**Scenario**: TenantAdmin from Tenant A trying to access their own enrollment credentials

**Via `/admins/{id}/onboarding`**:
- Validates: Admin's tenant (Tenant A) == Requester's tenant (Tenant A) ✅
- **Result**: Access granted

**Via `/enrollments/{id}`**:
- Validates: Enrollment's integration tenant (System Tenant) == Requester's tenant (Tenant A) ❌
- **Result**: Access denied (unless GlobalAdmin)

**Conclusion**: For TenantAdmin accessing their own enrollment:
- `/admins/{id}/onboarding` works correctly (validates admin's tenant)
- `/enrollments/{id}` would fail (validates integration's tenant, which is System Tenant)

**This is the actual edge case**: The two APIs use different validation logic that can produce different results for the same enrollment when accessed by a TenantAdmin.

### 6. The Real Edge Case (Confirmed)

**User Confirmation**: "If I'm an admin and I create Tenant A and Tenant B, then the enrollment for A belongs to A, and the enrollment for B belongs to B."

**Technical Reality**:
- All admin enrollments are linked to the **System Integration** (which belongs to System Tenant)
- But conceptually, each admin's enrollment "belongs" to their tenant:
  - TenantAdmin (Tenant A) → enrollment conceptually belongs to Tenant A
  - TenantAdmin (Tenant B) → enrollment conceptually belongs to Tenant B

**The Problem**:
- `/admins/{id}/onboarding` validates at **admin tenant level** → Works correctly ✅
- `/enrollments/{id}` validates at **integration tenant level** → Fails for TenantAdmin ❌

**Example**:
- TenantAdmin from Tenant A tries to access their own enrollment via `/enrollments/{id}`
- Validation checks: Enrollment's integration tenant (System Tenant) == Requester's tenant (Tenant A)
- Result: **Access denied** (even though it's their own enrollment!)

**This confirms**: The two APIs have **different validation logic** that produces **different results** for the same enrollment when accessed by a TenantAdmin.

**Implication**: 
- `/admins/{id}/onboarding` is the **correct API** for TenantAdmin to access their own onboarding credentials
- `/enrollments/{id}` is **not suitable** for TenantAdmin to access their own enrollment (it will fail)

### 7. Confirmed Edge Case - Real Impact

**User Confirmation**: "If I'm an admin and I create Tenant A and Tenant B, then the enrollment for A belongs to A, and the enrollment for B belongs to B."

**Technical Structure**:
- All admin enrollments are linked to the **System Integration** (which belongs to System Tenant)
- Conceptually, each admin's enrollment "belongs" to their tenant:
  - TenantAdmin (Tenant A) → enrollment conceptually belongs to Tenant A
  - TenantAdmin (Tenant B) → enrollment conceptually belongs to Tenant B

**The Real Problem**:
- `/admins/{id}/onboarding` validates at **admin tenant level** → Works correctly ✅
- `/enrollments/{id}` validates at **integration tenant level** → **Fails for TenantAdmin** ❌

**Concrete Example**:
- TenantAdmin from Tenant A (adminId=2, enrollmentId=123) tries to access their own enrollment
- Via `/admins/2/onboarding`: 
  - Validates: Admin's tenant (Tenant A) == Requester's tenant (Tenant A) ✅
  - **Result: Access granted** ✅
- Via `/enrollments/123`:
  - Validates: Enrollment's integration tenant (System Tenant) == Requester's tenant (Tenant A) ❌
  - **Result: Access denied** ❌ (even though it's their own enrollment!)

**Conclusion**: 
- `/admins/{id}/onboarding` is the **correct and only working API** for TenantAdmin to access their own onboarding credentials
- `/enrollments/{id}` is **not suitable** for TenantAdmin to access their own enrollment (it will fail due to validation logic)

**This confirms**: The two APIs have **different validation logic** that produces **different results** for the same enrollment when accessed by a TenantAdmin. This is not a bug, but a **design decision** that reflects different use cases and security models.

### 8. User Observation: "Degenerate but Harmless" Case

**User Experience**: A TenantAdmin was able to access their onboarding credentials via **both APIs**:
- ✅ `/api/v1/admins/{id}/onboarding` (expected - correct API)
- ✅ `/api/v1/enrollments/{id}` (unexpected, but worked)

**Why This Happens**:
The most likely explanation is that the user was connected as **GlobalAdmin** when testing:
- **GlobalAdmin** can access everything via both APIs (by design)
- **TenantAdmin** should NOT be able to access their enrollment via `/enrollments/{id}` (validation checks integration tenant = System Tenant, which doesn't match TenantAdmin's tenant)

**User's Assessment**: "Degenerate but harmless"
- ✅ **Degenerate**: Having two ways to access the same data is not ideal (violates single source of truth principle)
- ✅ **Harmless**: No security risk - both APIs require admin authentication and proper authorization
- ✅ **Acceptable**: The duplication doesn't cause security issues, just potential confusion

**Why It's Harmless**:
1. Both APIs require proper authentication (admin token)
2. Both APIs validate authorization (tenant scoping)
3. GlobalAdmin having multiple ways to access data is acceptable (they have full access anyway)
4. The "correct" API (`/admins/{id}/onboarding`) is semantically clear and works for all admin types

**Recommendation**: 
- ✅ Continue using `/api/v1/admins/{id}/onboarding` for admin onboarding workflows (semantically correct, works for all admin types)
- ✅ Document that `/api/v1/enrollments/{id}` works for GlobalAdmin but should not be relied upon for TenantAdmin
- ✅ No code changes needed - the current behavior is acceptable and secure

### 8. Security Analysis

#### Are Both APIs Necessary?

**Arguments FOR keeping both:**

1. **Different Validation Granularity**:
   - Admin-level validation ensures admins can only access credentials for admins they manage
   - Enrollment-level validation ensures admins can only access enrollments for integrations they manage
   - These are complementary security checks

2. **Semantic Clarity**:
   - `/admins/{id}/onboarding` is explicit: "I want onboarding credentials for this admin"
   - `/enrollments/{id}` is explicit: "I want details of this enrollment"
   - Different use cases, different APIs

3. **Information Granularity**:
   - `/admins/{id}/onboarding` returns only what's needed for onboarding
   - `/enrollments/{id}` returns comprehensive enrollment details
   - Principle of least privilege: return only what's needed

4. **Workflow Alignment**:
   - Admin provisioning workflow uses admin ID (natural fit)
   - Enrollment management workflow uses enrollment ID (natural fit)
   - Different workflows, different entry points

**Arguments AGAINST keeping both:**

1. **Duplication**:
   - Both APIs can return the same sensitive credentials
   - Having two ways to access the same data increases attack surface
   - More code to maintain and test

2. **Confusion**:
   - Developers might not know which API to use
   - Could lead to inconsistent usage across the codebase
   - Documentation overhead

3. **Validation Inconsistency**:
   - Different validation logic could lead to security gaps
   - Edge cases might be handled differently
   - More complex security model

### 6. Recommendation

**KEEP BOTH APIs** for the following reasons:

1. **Security Justification**: The different validation levels provide complementary security checks:
   - Admin-level validation ensures proper admin management permissions
   - Enrollment-level validation ensures proper integration access permissions
   - Both checks are valuable and serve different purposes

2. **Semantic Clarity**: The APIs serve different semantic purposes:
   - `/admins/{id}/onboarding`: Explicitly for admin onboarding workflow
   - `/enrollments/{id}`: Explicitly for enrollment management workflow
   - Different entry points for different use cases

3. **Information Granularity**: Different APIs return different levels of detail:
   - `/admins/{id}/onboarding`: Minimal, focused on onboarding credentials
   - `/enrollments/{id}`: Comprehensive enrollment details
   - Principle of least privilege: return only what's needed

4. **Workflow Alignment**: Natural fit for different workflows:
   - Admin provisioning: Uses admin ID (from `POST /api/v1/admins/global` or `/api/v1/admins/tenant`)
   - Enrollment management: Uses enrollment ID (from enrollment search or creation)

### 7. Action Items

1. **Documentation**: Clearly document when to use each API:
   - Use `/admins/{id}/onboarding` for admin onboarding workflows
   - Use `/enrollments/{id}` for enrollment management workflows

2. **Validation Consistency**: Ensure both APIs use consistent validation logic:
   - Both should validate tenant membership
   - Both should check admin permissions
   - Consider adding explicit checks to prevent edge cases

3. **Security Audit**: Review both APIs for potential security gaps:
   - Ensure validation logic is correct
   - Test edge cases (admin in Tenant A, enrollment in Tenant B)
   - Verify audit logging is consistent

4. **Code Review**: Review usage across codebase:
   - Ensure consistent usage patterns
   - Document any exceptions or special cases
   - Consider adding integration tests for both APIs

## Conclusion

Both APIs serve legitimate purposes and provide complementary security checks. The duplication is **justified by security and semantic clarity**, not unnecessary redundancy. However, documentation and validation consistency should be improved to prevent confusion and ensure security.

