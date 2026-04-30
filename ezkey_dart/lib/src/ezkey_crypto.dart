import 'package:cryptography/cryptography.dart';
import 'package:pointycastle/export.dart' as pc;

import 'ec_p256.dart';
import 'ed25519.dart';
import 'encoding.dart';
import 'payload.dart' as payloads;
import 'secure_random.dart';

class EzKeyCrypto {
  static EcP256KeyPair generateDeviceKeyPair() => generateEcP256KeyPair();

  static String exportDevicePublicKey(EcP256KeyPair keyPair) {
    return ecPublicKeyToSpkiBase64(keyPair.publicKey);
  }

  static String signWithDeviceKey(EcP256KeyPair keyPair, String data) {
    return signEcdsaSha256(keyPair.privateKey, data);
  }

  static bool verifyDeviceSignature(
    pc.ECPublicKey publicKey,
    String data,
    String signatureBase64Std,
  ) {
    return verifyEcdsaSha256(publicKey, data, signatureBase64Std);
  }

  static Future<bool> verifyIntegrationSignature(
    String payload,
    String signatureBase64Url,
    String publicKeyBase64Url,
  ) {
    return verifyEd25519(payload, signatureBase64Url, publicKeyBase64Url);
  }

  static Future<String> signIntegrationPayloadForTesting(
    String payload,
    String pkcs8Base64Std,
  ) {
    return signEd25519WithPkcs8(payload, pkcs8Base64Std);
  }

  static String buildPendingPayload(
    String proofToken,
    bool challengeRequired,
    String? contextTitle,
    String? contextMessage,
  ) {
    return payloads.buildPendingPayload(
      proofToken,
      challengeRequired,
      contextTitle,
      contextMessage,
    );
  }

  static String buildRespondPayload(String proofToken, bool accepted) {
    return payloads.buildRespondPayload(proofToken, accepted);
  }

  static String buildRespondResultPayload(
    String proofToken,
    String authAttemptId,
    String result,
    String? message,
  ) {
    return payloads.buildRespondResultPayload(
      proofToken,
      authAttemptId,
      result,
      message,
    );
  }

  /// Canonical bind payload string (integration-signed). Delegates to `payload.dart`.
  static String buildEnrollmentBindPayload({
    required String enrollmentProofToken,
    required int enrollmentId,
    required String integrationPublicKey,
    required String integrationKeyAlgorithm,
    String? integrationName,
    String? integrationDescription,
    String? enrollmentName,
    int? tenantId,
    String? tenantName,
    String? tenantDescription,
  }) {
    return payloads.buildEnrollmentBindPayload(
      enrollmentProofToken: enrollmentProofToken,
      enrollmentId: enrollmentId,
      integrationPublicKey: integrationPublicKey,
      integrationKeyAlgorithm: integrationKeyAlgorithm,
      integrationName: integrationName,
      integrationDescription: integrationDescription,
      enrollmentName: enrollmentName,
      tenantId: tenantId,
      tenantName: tenantName,
      tenantDescription: tenantDescription,
    );
  }

  /// Canonical verify-request payload (device-signed). Delegates to `payload.dart`.
  static String buildEnrollmentVerifyDevicePayload(
    String enrollmentProofToken,
    int enrollmentId,
    int challengeResponse,
    String devicePublicKey,
  ) {
    return payloads.buildEnrollmentVerifyDevicePayload(
      enrollmentProofToken,
      enrollmentId,
      challengeResponse,
      devicePublicKey,
    );
  }

  /// Canonical verify-response payload (integration-signed). Delegates to `payload.dart`.
  static String buildEnrollmentVerifyResultPayload(
    String enrollmentProofToken,
    int enrollmentId,
    String outcome,
    String? message,
  ) {
    return payloads.buildEnrollmentVerifyResultPayload(
      enrollmentProofToken,
      enrollmentId,
      outcome,
      message,
    );
  }

  static String generateDeviceProofToken() => generateProofToken();

  static SimplePublicKey parseIntegrationPublicKey(String base64Value) {
    return ed25519PublicKeyFromBase64Url(base64Value);
  }

  static List<int> wrapIntegrationPublicKeyToSpki(List<int> raw32) {
    return wrapEd25519Raw32ToSpki(raw32);
  }
}
