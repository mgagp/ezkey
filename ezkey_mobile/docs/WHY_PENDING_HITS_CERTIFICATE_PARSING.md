# Why "certificate parsing" appears after the proof-token change (V4)

## The apparent paradox

We only changed the **proof token payload** (what gets signed and verified): context + challenge in Pending, and `authAttemptAccepted` in Respond. We did **not** change any certificate format or key format in the backend. So why does the mobile fail with `OpenSSLX509CertificateFactory$ParsingException` / "Error parsing public key"?

## The real link

1. **Before V4**  
   The mobile might not have been **verifying the integration signature** on the Pending response at all (or only in a path that wasn’t exercised). So the app stored `integrationPublicKey` at bind time but **never used it for crypto** on the pending flow.

2. **With V4**  
   The spec says: clients must build the canonical payload and **verify the integration signature** over it. So we added a call to `cryptoService.verify(pendingPayload, signature, integrationPublicKey)` on the mobile. That is the **first time** we actually **parse** that key on the pending path (SubjectPublicKeyInfo or cert in the native layer).

3. **What actually fails**  
   The failure is not “we changed certificates.” The failure is: “we started **using** the integration public key for verification; when we parse it, the stored value (448 chars) is not in a format the parser accepts.” So the key may have been wrong or in an unexpected format all along; we only see it now because we started using it.

## Why 448 characters?

The backend (ezkey-core) sends the integration public key as **Base64-encoded X.509 SubjectPublicKeyInfo**, which is ~91 bytes → **~124 base64 characters**. So 448 suggests either:

- The **backend** (or some layer) is returning something longer (e.g. certificate, or different encoding), or  
- The value is correct at bind but **corrupted or altered** when stored/retrieved (e.g. storage, serialization).

## How we trace the root cause

On “Unable to load request”, the **Debug (for support)** box includes **integrationPublicKey length** (and prefix) at the time of the pending call. Compare that to the length you expect for Base64 SubjectPublicKeyInfo from the bind response (~124 characters for a typical P-256 SPKI). A much larger value (e.g. 448) suggests a certificate PEM, double-encoding, or a different field being stored—see hypothesis section above.

## Summary

We did not change certificate format. We added a **verification step** that uses the integration public key. That step is the first time we parse that key on pending, so parsing or format issues only surface once that path runs.
