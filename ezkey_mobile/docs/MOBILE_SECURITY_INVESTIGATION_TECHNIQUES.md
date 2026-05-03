# Mobile Security Investigation Techniques

## Purpose
This note captures the practical investigation techniques used to validate local secret handling in Ezkey Mobile.

It is intentionally pragmatic. The goal is not to simulate a nation-state lab. The goal is to build repeatable,
credible evidence for questions such as:
- Is a sensitive value still present in the wrong storage layer?
- Is a secret exposed in plaintext at rest?
- Is the app leaking sensitive material through logs or debug helpers?
- When is debug-only instrumentation justified?

## Investigation Ladder

Use the lightest credible technique first.

### 1. Static code review
Start with the code path that writes and reads the value.

For the `enrollmentProofToken` investigation, the core questions were:
- where is the token first persisted?
- where is it reloaded?
- does the storage abstraction really use secure storage, or only claim to?
- do delete and clear-all paths also clean up the secret?

Static review is the fastest way to detect a split-brain design where:
- documentation says "secure storage"
- but the actual implementation still serializes the secret into AsyncStorage or SharedPreferences

### 2. Artifact inspection on a debug build
When the app is installed as a debuggable package, `adb shell run-as <package>` can inspect the app sandbox without
rooting the phone.

This is one of the highest-value mobile investigation techniques because it answers:
- what files actually exist?
- which storage backend is used?
- is the secret visible in plaintext in the persisted artifacts?

For React Native on Android, common places to inspect are:
- `databases/RKStorage` for AsyncStorage
- `shared_prefs/` for preference-backed storage
- `files/datastore/` for Jetpack DataStore-backed storage
- `files/` or app-specific subdirectories for custom file persistence

### 3. Host-side offline inspection
Sometimes the device shell is missing tools such as `sqlite3`, `strings`, or `hexdump`.

In that case:
- stream the artifact out with `adb exec-out`
- inspect it on the host
- use Python or another known-good local toolchain

This is often the cleanest route for:
- SQLite analysis
- protobuf / DataStore binary scanning
- binary string extraction
- comparing before/after behavior after a refactor

### 4. Log inspection
Use `adb logcat` to confirm the app is not leaking secrets via:
- `console.log`
- native `Log.d` / `Log.i`
- exception messages
- debug-only dumps

Log inspection is excellent for proving the absence of accidental exposure in the runtime diagnostics path.
It is not a formal proof of encryption at rest.

### 5. Controlled behavioral verification
After changing storage behavior, run a real user path:
- fresh install
- enrollment
- pending auth
- respond flow

Then inspect storage artifacts again.

This matters because some secrets only appear after a successful flow, and some cleanup logic only runs on delete,
reset, or migration paths.

## What Counts as Good Evidence

For a local secret-handling claim, a strong practical evidence chain looks like this:

1. The write path sends the secret to a secure-storage abstraction.
2. The non-secure storage artifact no longer contains the secret in plaintext.
3. The secure-storage artifact exists and contains a logical key reference, but not the plaintext value.
4. The app still works end-to-end after reinstall and real user flows.
5. Logs do not leak the secret.

That is strong engineering evidence even if it is not a formal cryptographic proof.

## Limits of ADB, Logcat, and Artifact Inspection

These techniques can prove:
- the secret is no longer in the wrong storage layer
- the secret is not trivially visible in plaintext in common app artifacts
- the runtime flow still works after the storage split

They do **not** fully prove:
- which exact cryptographic primitive the platform wrapper used internally
- whether the Android secure-storage backend used keystore wrapping exactly as expected on every OS variant
- resistance against a rooted-device attacker with stronger extraction tooling

So these are:
- strong operational validation techniques
- not the last word in formal mobile assurance

## When Debug-Only Instrumentation Is Justified

Add disposable debug-only instrumentation when external inspection cannot answer the question cleanly.

Good reasons:
- you need to confirm which backend implementation was selected at runtime
- you need a one-time migration report
- you need a hash or length of a secret-derived value without exposing the secret itself
- you need to prove a deletion / rotation path happened

Bad reasons:
- convenience debugging that prints raw secrets
- long-lived debug utilities that normalize unsafe inspection habits
- adding UI that exposes plaintext cryptographic material to humans

## Safe Patterns for Disposable Instrumentation

If debug instrumentation becomes necessary, prefer:
- boolean flags such as `storedInSecureStorage=true`
- counts such as `secureEntries=1`
- lengths such as `tokenLength=65`
- hashes such as `sha256(token)` for correlation without disclosure
- one-shot migration counters such as `migratedLegacyProofTokenCount=1`

Avoid:
- raw token dumps
- raw signatures
- raw private-key material
- raw secure-storage payload dumps

## Recommended Practical Workflow

For Android local-secret investigations:

1. Review the write and read path in code.
2. Run the real app flow on a debug build.
3. Inspect AsyncStorage / files / prefs with `adb run-as`.
4. Pull or stream artifacts host-side if device tools are insufficient.
5. Scan logcat for accidental leaks.
6. Only if uncertainty remains, add temporary debug-only hash-based instrumentation.

## Ezkey-Specific Takeaway

For Ezkey Mobile, the strongest immediate proof after the `enrollmentProofToken` refactor is:
- `RKStorage` no longer contains `enrollmentProofToken`
- the secure-storage datastore contains the logical secure entry name
- the plaintext token is not visible in the inspected app artifacts
- enrollment plus authentication still succeed on the real device

That is the right level of evidence for release-hardening unless a stronger formal assurance requirement appears later.
