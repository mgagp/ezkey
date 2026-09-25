import Foundation
import React
import Security

@objc(EzkeyCryptoModule)
class EzkeyCryptoModule: NSObject, RCTBridgeModule {
  static func moduleName() -> String! {
    return "EzkeyCryptoModule"
  }

  static func requiresMainQueueSetup() -> Bool {
    false
  }

  @objc
  func generateRsaKeyPair(_ alias: String,
                          resolver resolve: @escaping RCTPromiseResolveBlock,
                          rejecter reject: @escaping RCTPromiseRejectBlock) {
    do {
      if try fetchKey(alias: alias, keyClass: kSecAttrKeyClassPrivate) != nil {
        resolve(true)
        return
      }

      var error: Unmanaged<CFError>?
      var privateAttributes: [String: Any] = [
        kSecAttrIsPermanent as String: true,
        kSecAttrApplicationTag as String: keyTag(alias: alias, suffix: ".private"),
        kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
      ]

      #if targetEnvironment(simulator)
      privateAttributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
      #else
      if #available(iOS 11.3, *) {
        if let access =
          SecAccessControlCreateWithFlags(nil,
                                          kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                                          [.privateKeyUsage],
                                          nil) {
          privateAttributes[kSecAttrAccessControl as String] = access
        }
        privateAttributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        privateAttributes[kSecAttrTokenID as String] = kSecAttrTokenIDSecureEnclave
      } else {
        privateAttributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
      }
      #endif

      var publicAttributes: [String: Any] = [
        kSecAttrIsPermanent as String: true,
        kSecAttrApplicationTag as String: keyTag(alias: alias, suffix: ".public"),
        kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
      ]

      let attributes: [String: Any] = [
        kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
        kSecAttrKeySizeInBits as String: keySize,
        kSecPrivateKeyAttrs as String: privateAttributes,
        kSecPublicKeyAttrs as String: publicAttributes
      ]

      guard SecKeyCreateRandomKey(attributes as CFDictionary, &error) != nil else {
        throw EzkeyCryptoError.operationFailure(.keypair, error?.takeRetainedValue())
      }

      resolve(true)
    } catch {
      reject(EzkeyCryptoError.operationFailure(.keypair, error as? CFError).code,
             error.localizedDescription,
             error)
    }
  }

  @objc
  func getPublicKey(_ alias: String,
                    resolver resolve: @escaping RCTPromiseResolveBlock,
                    rejecter reject: @escaping RCTPromiseRejectBlock) {
    do {
      guard let publicKey = try fetchKey(alias: alias, keyClass: kSecAttrKeyClassPublic) else {
        reject(EzkeyCryptoError.notFound.code,
               "RSA public key for alias \(alias) not found",
               nil)
        return
      }

      var error: Unmanaged<CFError>?
      guard let keyData = SecKeyCopyExternalRepresentation(publicKey, &error) as Data? else {
        throw EzkeyCryptoError.operationFailure(.publicKey, error?.takeRetainedValue())
      }

      resolve(keyData.base64EncodedString())
    } catch {
      reject(EzkeyCryptoError.operationFailure(.publicKey, error as? CFError).code,
             error.localizedDescription,
             error)
    }
  }

  @objc
  func sign(_ alias: String,
            dataBase64: String,
            resolver resolve: @escaping RCTPromiseResolveBlock,
            rejecter reject: @escaping RCTPromiseRejectBlock) {
    do {
      guard let payload = Data(base64Encoded: dataBase64) else {
        reject(EzkeyCryptoError.invalidPayload.code,
               "Unable to decode payload for signing",
               nil)
        return
      }

      guard let privateKey = try fetchKey(alias: alias, keyClass: kSecAttrKeyClassPrivate) else {
        reject(EzkeyCryptoError.notFound.code,
               "RSA private key for alias \(alias) not found",
               nil)
        return
      }

      var error: Unmanaged<CFError>?
      guard let signature =
        SecKeyCreateSignature(privateKey,
                              .rsaSignatureMessagePKCS1v15SHA256,
                              payload as CFData,
                              &error) as Data? else {
        throw EzkeyCryptoError.operationFailure(.sign, error?.takeRetainedValue())
      }

      resolve(signature.base64EncodedString())
    } catch {
      reject(EzkeyCryptoError.operationFailure(.sign, error as? CFError).code,
             error.localizedDescription,
             error)
    }
  }

  /**
   * Returns an ISO-8601 UTC string for diagnostics. On iOS this uses the main bundle directory
   * modification date as a proxy for the install/build (no Gradle `BuildConfig` equivalent here).
   */
  @objc
  func getBuildTimestamp(_ resolve: @escaping RCTPromiseResolveBlock,
                         rejecter reject: @escaping RCTPromiseRejectBlock) {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime]
    guard let path = Bundle.main.bundlePath,
          let attrs = try? FileManager.default.attributesOfItem(atPath: path),
          let date = attrs[.modificationDate] as? Date else {
      resolve("")
      return
    }
    resolve(formatter.string(from: date))
  }

  /// Android stamps a git short SHA at compile time. iOS has no equivalent yet.
  @objc
  func getGitShortSha(_ resolve: @escaping RCTPromiseResolveBlock,
                      rejecter reject: @escaping RCTPromiseRejectBlock) {
    resolve("")
  }

  /// Device proof token: same format as Java `SignatureService.generateProofToken()` (SecRandomCopyBytes + Base64URL, no padding).
  @objc
  func generateProofToken(_ resolve: @escaping RCTPromiseResolveBlock,
                            rejecter reject: @escaping RCTPromiseRejectBlock) {
    func base64UrlNoPadding(_ data: Data) -> String {
      var s = data.base64EncodedString()
        .replacingOccurrences(of: "+", with: "-")
        .replacingOccurrences(of: "/", with: "_")
      while s.hasSuffix("=") {
        s.removeLast()
      }
      return s
    }
    var randomBytes = [UInt8](repeating: 0, count: 32)
    var saltBytes = [UInt8](repeating: 0, count: 16)
    let r1 = SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes)
    let r2 = SecRandomCopyBytes(kSecRandomDefault, saltBytes.count, &saltBytes)
    guard r1 == errSecSuccess && r2 == errSecSuccess else {
      reject("EZK_PROOF_TOKEN_ERROR", "SecRandomCopyBytes failed", nil)
      return
    }
    let token =
      base64UrlNoPadding(Data(randomBytes)) + "." + base64UrlNoPadding(Data(saltBytes))
    resolve(token)
  }

  /**
   * Auth API enrollment verify: client-reported tier (NONE / STANDARD / STRONG).
   * Android EC P-256 path reports StrongBox / Keystore; iOS RSA enrollment parity is pending — return NONE until
   * native introspection matches the Android mapping.
   */
  @objc
  func getEnrollmentPrivateKeyStorageTier(_ enrollmentId: String,
                                            resolver resolve: @escaping RCTPromiseResolveBlock,
                                            rejecter reject: @escaping RCTPromiseRejectBlock) {
    resolve("NONE")
  }

  @objc
  func deleteKey(_ alias: String,
                 resolver resolve: @escaping RCTPromiseResolveBlock,
                 rejecter reject: @escaping RCTPromiseRejectBlock) {
    do {
      try deleteKeyPair(alias: alias)
      resolve(true)
    } catch {
      reject(EzkeyCryptoError.operationFailure(.delete, error as? CFError).code,
             error.localizedDescription,
             error)
    }
  }

  private let keySize = 2048

  private func keyTag(alias: String, suffix: String) -> Data {
    Data(("org.ezkey.mobile.keys." + alias + suffix).utf8)
  }

  private func fetchKey(alias: String, keyClass: CFString) throws -> SecKey? {
    let query: [String: Any] = [
      kSecClass as String: kSecClassKey,
      kSecAttrApplicationTag as String: keyTag(alias: alias, suffix: keyClassSuffix(for: keyClass)),
      kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
      kSecAttrKeyClass as String: keyClass,
      kSecReturnRef as String: true
    ]

    var item: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &item)

    if status == errSecItemNotFound {
      return nil
    }

    guard status == errSecSuccess, let key = item as? SecKey else {
      throw EzkeyCryptoError.operationFailure(.query, nil)
    }

    return key
  }

  private func deleteKeyPair(alias: String) throws {
    let privateQuery: [String: Any] = [
      kSecClass as String: kSecClassKey,
      kSecAttrApplicationTag as String: keyTag(alias: alias, suffix: ".private"),
      kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
      kSecAttrKeyClass as String: kSecAttrKeyClassPrivate
    ]

    let publicQuery: [String: Any] = [
      kSecClass as String: kSecClassKey,
      kSecAttrApplicationTag as String: keyTag(alias: alias, suffix: ".public"),
      kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
      kSecAttrKeyClass as String: kSecAttrKeyClassPublic
    ]

    SecItemDelete(privateQuery as CFDictionary)
    SecItemDelete(publicQuery as CFDictionary)
  }

  private func keyClassSuffix(for keyClass: CFString) -> String {
    keyClass == kSecAttrKeyClassPrivate ? ".private" : ".public"
  }
}

private enum EzkeyCryptoError: Error {
  case operationFailure(EzkeyCryptoDomain, CFError?)
  case invalidPayload
  case notFound

  enum EzkeyCryptoDomain {
    case keypair
    case publicKey
    case sign
    case delete
    case query
  }

  var code: String {
    switch self {
    case .operationFailure(let domain, _):
      switch domain {
      case .keypair: return "EZK_KEYPAIR_ERROR"
      case .publicKey: return "EZK_PUBLIC_KEY_ERROR"
      case .sign: return "EZK_SIGN_ERROR"
      case .delete: return "EZK_DELETE_ERROR"
      case .query: return "EZK_QUERY_ERROR"
      }
    case .invalidPayload:
      return "EZK_INVALID_PAYLOAD"
    case .notFound:
      return "EZK_KEY_NOT_FOUND"
    }
  }
}
