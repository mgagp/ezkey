//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Pure helper tests: low-S, malleability, DER via CryptoKit, SPKI, Ed25519, Base64, LAError mapping.
//

import CryptoKit
import LocalAuthentication
import XCTest

@testable import EzkeyMobile

final class EzkeyCryptoCodecTests: XCTestCase {

  private let n = EzkeyCryptoCodec.p256Order
  private let half = EzkeyCryptoCodec.p256HalfOrder

  func testHalfOrderTimesTwoPlusOneIsOrder() {
    // n is odd, so n = 2 * floor(n/2) + 1.
    var doubled = [UInt8](repeating: 0, count: 32)
    var carry = 0
    for i in stride(from: 31, through: 0, by: -1) {
      let value = Int(half[i]) * 2 + carry
      doubled[i] = UInt8(value & 0xff)
      carry = value >> 8
    }
    doubled[31] += 1
    XCTAssertEqual(doubled, n)
  }

  func testHighSIsNormalizedToNMinusS() {
    let r = [UInt8](repeating: 0x11, count: 32)
    let highS = EzkeyCryptoCodec.subtract(n, [UInt8](repeating: 0, count: 31) + [5])  // n − 5
    let out = EzkeyCryptoCodec.normalizeLowS(Data(r + highS))
    XCTAssertEqual([UInt8](out), r + [UInt8](repeating: 0, count: 31) + [5])
  }

  func testLowSAndBoundaryAreUnchanged() {
    let r = [UInt8](repeating: 0x22, count: 32)
    let low = Data(r + [UInt8](repeating: 0, count: 31) + [7])
    XCTAssertEqual(EzkeyCryptoCodec.normalizeLowS(low), low)
    let boundary = Data(r + half)  // s == n/2 stays (server rule: s <= n/2)
    XCTAssertEqual(EzkeyCryptoCodec.normalizeLowS(boundary), boundary)
    XCTAssertTrue(EzkeyCryptoCodec.isLowS(boundary))
    let justAbove = Data(r + EzkeyCryptoCodec.subtract(n, half))  // n − floor(n/2) = n/2 + 1
    XCTAssertFalse(EzkeyCryptoCodec.isLowS(justAbove))
  }

  /// Deterministic proof that normalization matters and is correct: a high-S twin of a valid
  /// signature is accepted by CryptoKit (malleable), and normalization maps it back.
  func testMalleabilityHighSTwinNormalizesToOriginal() throws {
    let key = P256.Signing.PrivateKey()
    let data = Data("malleability".utf8)
    let original = EzkeyCryptoCodec.normalizeLowS(try key.signature(for: data).rawRepresentation)
    XCTAssertTrue(EzkeyCryptoCodec.isLowS(original))

    let twin = EzkeyCryptoCodec.flipS(original)
    XCTAssertFalse(EzkeyCryptoCodec.isLowS(twin))
    XCTAssertTrue(
      key.publicKey.isValidSignature(
        try P256.Signing.ECDSASignature(rawRepresentation: twin), for: data))

    let normalized = EzkeyCryptoCodec.normalizeLowS(twin)
    XCTAssertEqual(normalized, original)
    XCTAssertTrue(
      key.publicKey.isValidSignature(
        try P256.Signing.ECDSASignature(rawRepresentation: normalized), for: data))
  }

  func testSignLowSRoundTripManyIterations() throws {
    let key = P256.Signing.PrivateKey()
    for i in 0..<200 {
      let message = "payload-\(i)"
      let b64 = try EzkeyCryptoModule.signLowS(.software(key), data: message)
      let der = try XCTUnwrap(Data(base64Encoded: b64))
      let sig = try P256.Signing.ECDSASignature(derRepresentation: der)
      XCTAssertTrue(key.publicKey.isValidSignature(sig, for: Data(message.utf8)), "iteration \(i)")
      XCTAssertTrue(EzkeyCryptoCodec.isLowS(sig.rawRepresentation), "iteration \(i)")
    }
  }

  func testDerPadsHighBitIntegersAndMinimizesShortOnes() throws {
    // r with high bit set → INTEGER 0x02 0x21 0x00 ...
    let high = Data(
      [0x80] + [UInt8](repeating: 0x01, count: 31) + [UInt8](repeating: 0, count: 31) + [9])
    let derHigh = [UInt8](
      try P256.Signing.ECDSASignature(rawRepresentation: high).derRepresentation)
    XCTAssertEqual(Array(derHigh[2...4]), [0x02, 0x21, 0x00])
    // r = 1 → INTEGER 0x02 0x01 0x01; s = 9 → 0x02 0x01 0x09.
    let short = Data(
      [UInt8](repeating: 0, count: 31) + [1] + [UInt8](repeating: 0, count: 31) + [9])
    let derShort = [UInt8](
      try P256.Signing.ECDSASignature(rawRepresentation: short).derRepresentation)
    XCTAssertEqual(derShort, [0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x09])
  }

  func testSpkiPrefixAndLength() {
    let spki = [UInt8](P256.Signing.PrivateKey().publicKey.derRepresentation)
    XCTAssertEqual(spki.count, 91)
    XCTAssertEqual(
      Array(spki[0..<26]),
      EzkeyCryptoCodec.hexBytes("3059301306072a8648ce3d020106082a8648ce3d030107034200"))
  }

  // MARK: Ed25519 (RFC 8032 §7.1 test 1: empty message)

  private let rfcPublicHex = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
  private let rfcSignatureHex =
    "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
    + "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"

  private func b64(_ hex: String) -> String {
    Data(EzkeyCryptoCodec.hexBytes(hex)).base64EncodedString()
  }
  private func b64url(_ hex: String) -> String {
    EzkeyCryptoCodec.base64UrlNoPadding(Data(EzkeyCryptoCodec.hexBytes(hex)))
  }

  func testEd25519Rfc8032VectorAcceptedInEveryEncoding() {
    XCTAssertTrue(
      EzkeyCryptoModule.verifyEd25519(
        data: "", signatureBase64: b64(rfcSignatureHex), publicKeyBase64: b64(rfcPublicHex)))
    XCTAssertTrue(
      EzkeyCryptoModule.verifyEd25519(
        data: "", signatureBase64: b64url(rfcSignatureHex), publicKeyBase64: b64url(rfcPublicHex)))
    let pem = "-----BEGIN PUBLIC KEY-----\n\(b64(rfcPublicHex))\n-----END PUBLIC KEY-----\n"
    XCTAssertTrue(
      EzkeyCryptoModule.verifyEd25519(
        data: "", signatureBase64: b64(rfcSignatureHex), publicKeyBase64: pem))
  }

  func testEd25519RejectsTamperingAndMalformedInputWithoutThrowing() {
    var sig = EzkeyCryptoCodec.hexBytes(rfcSignatureHex)
    sig[10] ^= 0x01
    XCTAssertFalse(
      EzkeyCryptoModule.verifyEd25519(
        data: "", signatureBase64: Data(sig).base64EncodedString(),
        publicKeyBase64: b64(rfcPublicHex)))
    XCTAssertFalse(
      EzkeyCryptoModule.verifyEd25519(
        data: "x", signatureBase64: b64(rfcSignatureHex), publicKeyBase64: b64(rfcPublicHex)))
    let otherKey = Curve25519.Signing.PrivateKey().publicKey.rawRepresentation.base64EncodedString()
    XCTAssertFalse(
      EzkeyCryptoModule.verifyEd25519(
        data: "", signatureBase64: b64(rfcSignatureHex), publicKeyBase64: otherKey))
    XCTAssertFalse(
      EzkeyCryptoModule.verifyEd25519(
        data: "", signatureBase64: "%%%not-base64%%%", publicKeyBase64: b64(rfcPublicHex)))
    XCTAssertFalse(
      EzkeyCryptoModule.verifyEd25519(
        data: "", signatureBase64: b64(rfcSignatureHex),
        publicKeyBase64: b64(String(rfcPublicHex.dropLast(2)))))
    XCTAssertFalse(
      EzkeyCryptoModule.verifyEd25519(
        data: "", signatureBase64: b64(String(rfcSignatureHex.dropLast(2))),
        publicKeyBase64: b64(rfcPublicHex)))
    XCTAssertFalse(
      EzkeyCryptoModule.verifyEd25519(data: "", signatureBase64: "", publicKeyBase64: ""))
  }

  func testFlexibleBase64HandlesPaddingAndAlphabets() {
    XCTAssertEqual(EzkeyCryptoCodec.decodeFlexibleBase64("-_8"), Data([0xfb, 0xff]))
    XCTAssertEqual(EzkeyCryptoCodec.decodeFlexibleBase64("+/8="), Data([0xfb, 0xff]))
    XCTAssertEqual(EzkeyCryptoCodec.decodeFlexibleBase64(" +/ 8\n"), Data([0xfb, 0xff]))
    XCTAssertNil(EzkeyCryptoCodec.decodeFlexibleBase64("A"))
    XCTAssertNil(EzkeyCryptoCodec.decodeFlexibleBase64("   "))
  }

  // MARK: LocalAuthentication mapping

  func testAuthErrorMapping() {
    for code in [LAError.Code.userCancel, .systemCancel, .appCancel] {
      XCTAssertEqual(
        EzkeyCryptoCodec.authErrorCode(for: LAError(code)), "EZK_AUTH_CANCELLED", "\(code)")
    }
    for code in [
      LAError.Code.passcodeNotSet, .biometryNotAvailable, .biometryNotEnrolled,
      .biometryLockout, .notInteractive, .authenticationFailed, .userFallback,
    ] {
      XCTAssertEqual(
        EzkeyCryptoCodec.authErrorCode(for: LAError(code)), "EZK_AUTH_UNAVAILABLE", "\(code)")
    }
    XCTAssertEqual(EzkeyCryptoCodec.authErrorCode(for: nil), "EZK_AUTH_UNAVAILABLE")
  }
}
