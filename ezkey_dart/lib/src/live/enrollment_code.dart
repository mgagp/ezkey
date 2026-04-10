import 'dart:convert';

import 'env_config.dart';

class EnrollmentCode {
  EnrollmentCode({
    required this.enrollmentId,
    required this.enrollmentProofToken,
    this.authUrl,
  });

  final String enrollmentId;
  final String enrollmentProofToken;
  final String? authUrl;

  factory EnrollmentCode.parse(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) {
      throw const FormatException('Empty enrollment code');
    }

    try {
      final decoded = jsonDecode(trimmed);
      if (decoded is Map<String, dynamic> &&
          decoded['enrollmentId'] != null &&
          decoded['enrollmentProofToken'] != null) {
        return EnrollmentCode(
          enrollmentId: decoded['enrollmentId'].toString(),
          enrollmentProofToken: decoded['enrollmentProofToken'].toString(),
          authUrl: validateAuthUrl(decoded['authUrl']?.toString()),
        );
      }
    } on FormatException {
      // Ignore and fall back to the legacy format.
    }

    final pipeParts = trimmed.split('|');
    if (pipeParts.length >= 2) {
      return EnrollmentCode(
        enrollmentId: pipeParts.first.trim(),
        enrollmentProofToken: pipeParts.sublist(1).join('|').trim(),
      );
    }

    throw const FormatException('Unsupported enrollment code format');
  }
}