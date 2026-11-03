// Semgrep fixtures for the Swift rules in mobile-security.yml.
// Run: docker run --rm -v "$PWD:/src" -w /src semgrep/semgrep semgrep --test semgrep/rules
// Not compiled: this file lives outside ios/ on purpose.

func keychainAccessibility() {
  // ruleid: ezkey-swift-keychain-accessible-not-this-device-only
  let a = kSecAttrAccessibleWhenUnlocked
  // ruleid: ezkey-swift-keychain-accessible-not-this-device-only
  let b = kSecAttrAccessibleAfterFirstUnlock
  // ok: ezkey-swift-keychain-accessible-not-this-device-only
  let c = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
  // ok: ezkey-swift-keychain-accessible-not-this-device-only
  let d = [kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
}

func keychainSync() {
  // ruleid: ezkey-swift-keychain-synchronizable
  let q = [kSecAttrSynchronizable as String: kCFBooleanTrue]
  // ok: ezkey-swift-keychain-synchronizable
  let r = [kSecAttrService as String: "org.ezkey.mobile.enrollment-key"]
}

func randomness() {
  // ruleid: ezkey-swift-libc-random
  let x = random()
  // ruleid: ezkey-swift-libc-random
  srand(42)
  // ok: ezkey-swift-libc-random
  let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
  // ok: ezkey-swift-libc-random
  let y = Int.random(in: 0..<10)
}

func logging(proofToken: String) {
  // ruleid: ezkey-swift-sensitive-log-content
  print("token: \(proofToken)")
  // ruleid: ezkey-swift-sensitive-log-content
  NSLog("signature %@", signatureBase64)
  // ok: ezkey-swift-sensitive-log-content
  print("purge done")
}

func localAuth() {
  // ruleid: ezkey-swift-biometrics-only-policy
  context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: r) { _, _ in }
  // ok: ezkey-swift-biometrics-only-policy
  context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: r) { _, _ in }
}
