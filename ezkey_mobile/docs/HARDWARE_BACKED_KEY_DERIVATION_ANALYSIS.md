# Hardware-Backed Key Derivation Analysis

## Problem Statement

The mobile application needs to:
1. **Store a root key in hardware-backed storage** (Android Keystore StrongBox / iOS Secure Enclave) - **MANDATORY**
2. **Derive Ed25519 keys per enrollment** using HKDF-SHA-256
3. **Ensure deterministic derivation** (same enrollment ID → same Ed25519 key)

## Current Challenge

Android Keystore hardware-backed keys have strict limitations:
- ❌ **Cannot extract raw key bytes** (keys are non-extractable)
- ❌ **Cannot provide custom IV** for GCM/CBC encryption (auto-generated only)
- ❌ **Cannot use AES key directly with Mac** for HMAC (requires HMAC key type)

## Current Approach Issues

### Attempted Solution: HKDF with AES Hardware-Backed Key

**Problem**: HKDF requires:
1. Extract phase: `PRK = HMAC(salt, IKM)` where IKM is the root key
2. Expand phase: `OKM = HMAC(PRK, info | counter)`

**Blockers**:
- Cannot extract root key bytes for HKDF Extract
- Cannot use AES key directly with Mac.init() for HMAC

### Attempted Workaround: Encrypt Deterministic Seed

**Approach**: Use root key to encrypt a deterministic seed derived from enrollment ID

**Blockers**:
- GCM: Requires IV, but hardware-backed keys don't allow caller-provided IV
- CBC: Same issue - requires IV
- ECB: Attempted but still fails with "Keystore operation failed"

## Root Cause Analysis

The fundamental issue is that **Android Keystore hardware-backed keys are designed for encryption/decryption operations, not for key derivation**. They enforce security by:
1. Preventing key extraction
2. Enforcing randomized encryption (preventing deterministic operations)
3. Limiting key usage to specific algorithms

## Alternative Approaches

### Option 1: Generate Ed25519 Keys Directly in Keystore (NOT POSSIBLE)

**Why it fails**: Android Keystore does not support Ed25519 key generation. Only RSA, EC (P-256, P-384, P-521), and AES are supported.

### Option 2: Store Encrypted Seeds with Hardware-Backed Root Key

**Approach**:
1. Generate random Ed25519 seed per enrollment (32 bytes)
2. Encrypt seed with hardware-backed AES root key
3. Store encrypted seed in secure storage (Android Keystore/SharedPreferences encrypted)
4. Decrypt seed when needed for signing

**Pros**:
- ✅ Root key stays hardware-backed
- ✅ Seeds are encrypted at rest
- ✅ Deterministic per enrollment (seed stored, not derived)

**Cons**:
- ❌ Requires storage per enrollment (not purely derived)
- ❌ Still need to solve encryption with hardware-backed key

### Option 3: Use Hardware-Backed Key for Seed Encryption (Current Attempt)

**Status**: Blocked by IV requirements

**Potential Solution**: Use a different encryption mode that doesn't require IV, or find a way to make encryption deterministic.

**Investigation Needed**: Check if Android Keystore supports:
- AES in CTR mode with deterministic counter
- AES-GCM-SIV (deterministic variant)
- Other deterministic encryption modes

### Option 4: Hybrid Approach - Hardware-Backed Root + Software Derivation

**Approach**:
1. Generate root key in hardware-backed storage
2. Use root key to encrypt a "master seed" (32 bytes)
3. Store encrypted master seed in secure storage
4. Use master seed for HKDF derivation (software-based)
5. Generate Ed25519 keys from HKDF output

**Pros**:
- ✅ Root key protection via hardware-backed storage
- ✅ HKDF works in software (can extract master seed after decryption)
- ✅ Deterministic derivation

**Cons**:
- ⚠️ Master seed must be decrypted once and kept in memory
- ⚠️ Requires one-time decryption of master seed

**Security Analysis**:
- Root key: Hardware-backed (strongest protection)
- Master seed: Encrypted at rest, decrypted only when needed
- Ed25519 keys: Derived on-demand, never stored

### Option 5: Per-Enrollment Hardware-Backed Keys (NOT RECOMMENDED)

**Approach**: Generate one AES key per enrollment in hardware-backed storage

**Pros**:
- ✅ All keys hardware-backed

**Cons**:
- ❌ Multiple hardware-backed keys (resource intensive)
- ❌ Cannot derive Ed25519 from AES key directly
- ❌ Still need to solve encryption/derivation problem

## Recommended Solution: Option 4 (Hybrid Approach)

### Implementation Strategy

1. **Root Key Generation** (Hardware-Backed):
   ```kotlin
   // Generate 256-bit AES key in Android Keystore (StrongBox preferred)
   // Key alias: "ezkey_root_key"
   // Purpose: ENCRYPT | DECRYPT
   // Block mode: GCM (for encrypting master seed)
   ```

2. **Master Seed Encryption**:
   ```kotlin
   // Generate random 32-byte master seed
   val masterSeed = SecureRandom().generateSeed(32)
   
   // Encrypt master seed with root key (one-time operation)
   // Store encrypted seed in Android Keystore or encrypted SharedPreferences
   val encryptedSeed = encryptWithRootKey(masterSeed)
   ```

3. **HKDF Derivation** (Software-Based):
   ```kotlin
   // Decrypt master seed (in memory only)
   val masterSeed = decryptWithRootKey(encryptedSeed)
   
   // Derive Ed25519 seed using HKDF-SHA-256
   val enrollmentSeed = hkdfSha256(
       masterSeed, 
       "enrollment:$enrollmentId:signing"
   )
   ```

4. **Ed25519 Key Generation**:
   ```kotlin
   // Generate Ed25519 keypair from derived seed
   val keyPair = Ed25519KeyPairGenerator.fromSeed(enrollmentSeed)
   ```

### Security Properties

- ✅ **Root key**: Hardware-backed (StrongBox if available)
- ✅ **Master seed**: Encrypted at rest, decrypted only when needed
- ✅ **Ed25519 keys**: Derived on-demand, never stored
- ✅ **Deterministic**: Same enrollment ID → same Ed25519 key
- ✅ **Non-extractable**: Root key never leaves hardware-backed storage

### Trade-offs

- ⚠️ Master seed must be decrypted once per app session (acceptable)
- ⚠️ Master seed kept in memory during derivation (acceptable for short duration)
- ✅ No per-enrollment storage required (keys derived on-demand)

## Next Steps

1. **Test Option 4**: Implement hybrid approach with master seed encryption
2. **Verify**: Ensure master seed encryption works with hardware-backed root key
3. **Validate**: Test deterministic derivation across app restarts
4. **Security Review**: Confirm security properties meet requirements

## Questions to Resolve

1. Can we encrypt a 32-byte seed with hardware-backed AES key using GCM with auto-generated IV?
   - If yes: Store encrypted seed, decrypt when needed
   - If no: Need alternative encryption method

2. Is it acceptable to keep master seed in memory during derivation?
   - Security: Seed is in memory only during active use
   - Performance: One-time decryption per app session

3. Should we cache decrypted master seed in memory?
   - Pro: Faster subsequent derivations
   - Con: Seed in memory longer
   - Recommendation: Cache with app lifecycle, clear on app background

