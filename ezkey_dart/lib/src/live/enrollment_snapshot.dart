import 'dart:convert';

import '../payload.dart';
import '../secure_random.dart';

class EnrollmentSnapshot {
  EnrollmentSnapshot({
    required this.enrollmentId,
    this.integrationId,
    this.enrollmentName,
    this.enrollmentStatus,
    this.enrollmentActive,
    this.enrollmentProofToken,
    this.authAttemptChallengeRequired,
    this.authAttemptChallengeRequiredByPolicy,
    this.integrationPublicKey,
    this.devicePublicKey,
    this.verifiedAt,
    this.createdAt,
    this.contactEmail,
    this.contactPhoneNumber,
    this.userIdentifier,
    this.integrationName,
  });

  final int enrollmentId;
  final int? integrationId;
  final String? enrollmentName;
  final String? enrollmentStatus;
  final bool? enrollmentActive;
  final String? enrollmentProofToken;
  final bool? authAttemptChallengeRequired;
  final bool? authAttemptChallengeRequiredByPolicy;
  final String? integrationPublicKey;
  final String? devicePublicKey;
  final String? verifiedAt;
  final String? createdAt;
  final String? contactEmail;
  final String? contactPhoneNumber;
  final String? userIdentifier;
  final String? integrationName;

  factory EnrollmentSnapshot.fromJson(Map<String, dynamic> json) {
    return EnrollmentSnapshot(
      enrollmentId: (json['enrollmentId'] as num).toInt(),
      integrationId: (json['integrationId'] as num?)?.toInt(),
      enrollmentName: json['enrollmentName'] as String?,
      enrollmentStatus: json['enrollmentStatus'] as String?,
      enrollmentActive: json['enrollmentActive'] as bool?,
      enrollmentProofToken: json['enrollmentProofToken'] as String?,
      authAttemptChallengeRequired:
          json['authAttemptChallengeRequired'] as bool?,
        authAttemptChallengeRequiredByPolicy:
          json['authAttemptChallengeRequiredByPolicy'] as bool?,
      integrationPublicKey: json['integrationPublicKey'] as String?,
      devicePublicKey: json['devicePublicKey'] as String?,
      verifiedAt: json['verifiedAt'] as String?,
      createdAt: json['createdAt'] as String?,
      contactEmail: json['contactEmail'] as String?,
      contactPhoneNumber: json['contactPhoneNumber'] as String?,
      userIdentifier: json['userIdentifier'] as String?,
      integrationName: json['integrationName'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    final token = enrollmentProofToken;
    return <String, dynamic>{
      'enrollmentId': enrollmentId,
      'integrationId': integrationId,
      'enrollmentName': enrollmentName,
      'enrollmentStatus': enrollmentStatus,
      'enrollmentActive': enrollmentActive,
      'enrollmentProofToken': token,
      'authAttemptChallengeRequired': authAttemptChallengeRequired,
      'authAttemptChallengeRequiredByPolicy': authAttemptChallengeRequiredByPolicy,
      'integrationPublicKey': integrationPublicKey,
      'devicePublicKey': devicePublicKey,
      'verifiedAt': verifiedAt,
      'createdAt': createdAt,
      'contactEmail': contactEmail,
      'contactPhoneNumber': contactPhoneNumber,
      'userIdentifier': userIdentifier,
      'integrationName': integrationName,
      'sampleDeviceProofToken': generateProofToken(),
      'sampleRespondPayloadApproved': token == null
          ? null
          : buildRespondPayload(token, true),
      'sampleRespondPayloadDenied': token == null
          ? null
          : buildRespondPayload(token, false),
    };
  }

  String toPrettyJson() => const JsonEncoder.withIndent('  ').convert(toJson());
}
