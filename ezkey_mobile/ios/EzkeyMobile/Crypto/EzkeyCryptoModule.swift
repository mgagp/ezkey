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
      try deleteKeyPair(alias: alias)

      var error: Unmanaged<CFError>?
      let attributes: [String: Any] = [
        kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
        kSecAttrKeySizeInBits as String: keySize,
        kSecPrivateKeyAttrs as String: [
          kSecAttrIsPermanent as String: true,
          kSecAttrApplicationTag as String: keyTag(alias: alias, suffix: ".private")
        ],
        kSecPublicKeyAttrs as String: [
          kSecAttrIsPermanent as String: true,
          kSecAttrApplicationTag as String: keyTag(alias: alias, suffix: ".public")
        ]
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
    Data(("com.ezkeymobile.keys." + alias + suffix).utf8)
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
