import 'dart:convert';

/// Authentication attempt data model for Ezkey API responses
/// 
/// Represents authentication attempt information returned by the PENDING API
/// 
/// @since 2025
class AuthAttempt {
  final int authAttemptId;
  final int enrollmentId;
  final String authAttemptCode;
  final String authAttemptCodeSigned;
  final bool authAttemptChallengeRequired;
  final String? challenge;

  const AuthAttempt({
    required this.authAttemptId,
    required this.enrollmentId,
    required this.authAttemptCode,
    required this.authAttemptCodeSigned,
    required this.authAttemptChallengeRequired,
    this.challenge,
  });

  /// Create AuthAttempt from JSON
  factory AuthAttempt.fromJson(Map<String, dynamic> json) {
    return AuthAttempt(
      authAttemptId: json['authAttemptId'] as int,
      enrollmentId: json['enrollmentId'] as int,
      authAttemptCode: json['authAttemptCode'] as String,
      authAttemptCodeSigned: json['authAttemptCodeSigned'] as String,
      authAttemptChallengeRequired: json['authAttemptChallengeRequired'] as bool,
      challenge: json['challenge'] as String?,
    );
  }

  /// Convert AuthAttempt to JSON
  Map<String, dynamic> toJson() {
    return {
      'authAttemptId': authAttemptId,
      'enrollmentId': enrollmentId,
      'authAttemptCode': authAttemptCode,
      'authAttemptCodeSigned': authAttemptCodeSigned,
      'authAttemptChallengeRequired': authAttemptChallengeRequired,
      if (challenge != null) 'challenge': challenge,
    };
  }

  /// Create AuthAttempt from JSON string
  factory AuthAttempt.fromJsonString(String jsonString) {
    final json = jsonDecode(jsonString) as Map<String, dynamic>;
    return AuthAttempt.fromJson(json);
  }

  /// Convert AuthAttempt to JSON string
  String toJsonString() {
    return jsonEncode(toJson());
  }

  /// Create a copy of this AuthAttempt with updated fields
  AuthAttempt copyWith({
    int? authAttemptId,
    int? enrollmentId,
    String? authAttemptCode,
    String? authAttemptCodeSigned,
    bool? authAttemptChallengeRequired,
    String? challenge,
  }) {
    return AuthAttempt(
      authAttemptId: authAttemptId ?? this.authAttemptId,
      enrollmentId: enrollmentId ?? this.enrollmentId,
      authAttemptCode: authAttemptCode ?? this.authAttemptCode,
      authAttemptCodeSigned: authAttemptCodeSigned ?? this.authAttemptCodeSigned,
      authAttemptChallengeRequired: authAttemptChallengeRequired ?? this.authAttemptChallengeRequired,
      challenge: challenge ?? this.challenge,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is AuthAttempt &&
        other.authAttemptId == authAttemptId &&
        other.enrollmentId == enrollmentId &&
        other.authAttemptCode == authAttemptCode &&
        other.authAttemptCodeSigned == authAttemptCodeSigned &&
        other.authAttemptChallengeRequired == authAttemptChallengeRequired &&
        other.challenge == challenge;
  }

  @override
  int get hashCode {
    return authAttemptId.hashCode ^
        enrollmentId.hashCode ^
        authAttemptCode.hashCode ^
        authAttemptCodeSigned.hashCode ^
        authAttemptChallengeRequired.hashCode ^
        challenge.hashCode;
  }

  @override
  String toString() {
    return 'AuthAttempt(authAttemptId: $authAttemptId, enrollmentId: $enrollmentId, challengeRequired: $authAttemptChallengeRequired)';
  }
}
