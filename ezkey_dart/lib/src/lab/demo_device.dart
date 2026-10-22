import 'dart:convert';

import '../ec_p256.dart';
import '../sdk/models.dart';
import 'file_stores.dart';

/// Imports an enrollment record written by the stack's Demo Device
/// (`/app/data/enrollments/<id>.json` in container `ezkey-demo-device`).
///
/// Clean-start binds the bootstrap Global Admin (`admin.docker`, enrollment 1) with the Demo
/// Device. Importing that record lets the lab act as the admin's authenticator to obtain an Admin
/// API session, the same way the Java functional tests do. The record contains a PKCS#8 private
/// key and the enrollment proof token: treat the file as a secret and delete it after use.
Future<EzkeyEnrollment> importDemoDeviceEnrollment({
  required String demoDeviceJson,
  required String authApiBaseUrl,
  required FileDeviceKeyStore keyStore,
  DateTime? now,
}) async {
  final json = jsonDecode(demoDeviceJson) as Map<String, dynamic>;
  final enrollmentId = json['enrollmentId'] as int;
  final alias = 'demo-device-$enrollmentId';
  final privateKey = json['devicePrivateKey'] as String;
  final derivedPublicKey = await keyStore.importPkcs8(alias, privateKey);
  final recordedPublicKey = json['devicePublicKey'] as String?;
  if (recordedPublicKey != null &&
      ecPublicKeyToSpkiBase64(ecPublicKeyFromSpkiBase64(recordedPublicKey)) !=
          derivedPublicKey) {
    throw const FormatException(
      'Demo Device record: private and public key do not match',
    );
  }
  return EzkeyEnrollment(
    authApiBaseUrl: authApiBaseUrl,
    enrollmentId: enrollmentId,
    enrollmentProofToken: json['enrollmentProofToken'] as String,
    integrationPublicKey: json['integrationPublicKey'] as String,
    integrationKeyAlgorithm: 'ed25519',
    deviceKeyAlias: alias,
    devicePublicKey: derivedPublicKey,
    devicePrivateKeyStorageTier: DevicePrivateKeyStorageTier.none,
    enrolledAt: (now ?? DateTime.now()).toUtc(),
    integrationName: json['integrationName'] as String?,
    integrationDescription: json['integrationDescription'] as String?,
    enrollmentName: json['enrollmentName'] as String?,
    isSystemIntegration: true,
  );
}
