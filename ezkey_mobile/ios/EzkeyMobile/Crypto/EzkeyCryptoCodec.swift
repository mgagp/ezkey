//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Module: EzkeyCryptoCodec (iOS)
// Description: Pure helpers for the iOS crypto bridge (no React, no Keychain): ECDSA low-S
//              normalization (server rejects high-S, SEC-012), flexible Base64 decoding (mirror of
//              Android IntegrationKeyVerifier), Base64URL encoding, LocalAuthentication error mapping.
//              DER encoding itself is delegated to CryptoKit (ECDSASignature.derRepresentation).
//

import CryptoKit
import Foundation
import LocalAuthentication

enum EzkeyCryptoErrorCode {
  static let keyGeneration = "EZK_KEY_GENERATION_ERROR"
  static let publicKey = "EZK_PUBLIC_KEY_ERROR"
  static let sign = "EZK_SIGN_ERROR"
  static let notFound = "EZK_KEY_NOT_FOUND"
  static let delete = "EZK_DELETE_ERROR"
  static let verify = "EZK_VERIFY_ERROR"
  static let buildTimestamp = "EZK_BUILD_TIMESTAMP_ERROR"
  static let gitSha = "EZK_GIT_SHA_ERROR"
  static let proofToken = "EZK_PROOF_TOKEN_ERROR"
  static let storageTier = "EZK_STORAGE_TIER_ERROR"
  static let authCancelled = "EZK_AUTH_CANCELLED"
  static let authUnavailable = "EZK_AUTH_UNAVAILABLE"
}

enum EzkeyCryptoCodec {

  /// secp256r1 group order n, big-endian.
  static let p256Order: [UInt8] = hexBytes(
    "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551")

  /// floor(n / 2). A signature is canonical (low-S) when s <= halfOrder (same rule as the Auth API).
  static let p256HalfOrder: [UInt8] = shiftRightOne(p256Order)

  // MARK: - Low-S

  /// Returns `r ‖ s'` where `s' = n − s` if `s > n/2`, else the input unchanged.
  /// Input must be the 64-byte CryptoKit `rawRepresentation` (32-byte r ‖ 32-byte s).
  static func normalizeLowS(_ raw: Data) -> Data {
    precondition(raw.count == 64, "ECDSA P-256 raw signature must be 64 bytes")
    let bytes = [UInt8](raw)
    let r = Array(bytes[0..<32])
    let s = Array(bytes[32..<64])
    guard compare(s, p256HalfOrder) > 0 else {
      return raw
    }
    return Data(r + subtract(p256Order, s))
  }

  static func isLowS(_ raw: Data) -> Bool {
    guard raw.count == 64 else { return false }
    return compare(Array([UInt8](raw)[32..<64]), p256HalfOrder) <= 0
  }

  /// Flips s to n − s (test helper and malleability proof; not used on the signing path).
  static func flipS(_ raw: Data) -> Data {
    let bytes = [UInt8](raw)
    return Data(Array(bytes[0..<32]) + subtract(p256Order, Array(bytes[32..<64])))
  }

  // MARK: - Fixed-width 256-bit big-endian arithmetic

  /// -1, 0, 1 like a comparator. Both operands must have the same length.
  static func compare(_ a: [UInt8], _ b: [UInt8]) -> Int {
    for i in 0..<a.count where a[i] != b[i] {
      return a[i] < b[i] ? -1 : 1
    }
    return 0
  }

  /// a − b, requires a >= b and equal lengths.
  static func subtract(_ a: [UInt8], _ b: [UInt8]) -> [UInt8] {
    var result = [UInt8](repeating: 0, count: a.count)
    var borrow = 0
    for i in stride(from: a.count - 1, through: 0, by: -1) {
      var diff = Int(a[i]) - Int(b[i]) - borrow
      borrow = diff < 0 ? 1 : 0
      if diff < 0 { diff += 256 }
      result[i] = UInt8(diff)
    }
    return result
  }

  static func shiftRightOne(_ a: [UInt8]) -> [UInt8] {
    var result = [UInt8](repeating: 0, count: a.count)
    var carry: UInt8 = 0
    for i in 0..<a.count {
      result[i] = (a[i] >> 1) | (carry << 7)
      carry = a[i] & 1
    }
    return result
  }

  // MARK: - Base64

  /// Mirror of Android `IntegrationKeyVerifier.decodeFlexibleBase64ToBytes`: accepts PEM armor,
  /// whitespace, Base64URL or standard alphabet, with or without padding. Returns nil if invalid.
  static func decodeFlexibleBase64(_ value: String) -> Data? {
    var text: String
    if value.contains("-----BEGIN") {
      text = value.split(whereSeparator: \.isNewline)
        .map { $0.trimmingCharacters(in: .whitespaces) }
        .filter { !$0.isEmpty && !$0.hasPrefix("-----") }
        .joined()
    } else {
      text = value
    }
    text = text.filter { !$0.isWhitespace }
    guard !text.isEmpty else { return nil }
    text = text.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
    let remainder = text.count % 4
    if remainder == 1 { return nil }
    if remainder > 0 { text += String(repeating: "=", count: 4 - remainder) }
    return Data(base64Encoded: text)
  }

  static func base64UrlNoPadding(_ data: Data) -> String {
    data.base64EncodedString()
      .replacingOccurrences(of: "+", with: "-")
      .replacingOccurrences(of: "/", with: "_")
      .replacingOccurrences(of: "=", with: "")
  }

  // MARK: - LocalAuthentication

  /// User/system/app cancellation → EZK_AUTH_CANCELLED; anything else → EZK_AUTH_UNAVAILABLE.
  /// The JS layer branches on these two codes (usePendingAuth.ts, SecurityScreen.tsx).
  static func authErrorCode(for error: Error?) -> String {
    if let laError = error as? LAError {
      switch laError.code {
      case .userCancel, .systemCancel, .appCancel:
        return EzkeyCryptoErrorCode.authCancelled
      default:
        return EzkeyCryptoErrorCode.authUnavailable
      }
    }
    return EzkeyCryptoErrorCode.authUnavailable
  }

  // MARK: - Hex

  static func hexBytes(_ hex: String) -> [UInt8] {
    var bytes: [UInt8] = []
    var index = hex.startIndex
    while index < hex.endIndex {
      let next = hex.index(index, offsetBy: 2)
      guard let byte = UInt8(hex[index..<next], radix: 16) else {
        preconditionFailure("Invalid hex literal: \(hex)")
      }
      bytes.append(byte)
      index = next
    }
    return bytes
  }
}
