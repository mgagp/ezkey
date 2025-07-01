/// Model representing an Ezkey authentication attempt
/// 
/// This class represents the authentication attempt data structure used 
/// throughout the app for managing user authentication attempts.
/// 
/// @since 2025
class AuthAttempt {
  final String id;
  final String integrationId;
  final String integrationName;
  final String integrationCode;
  final String? integrationDescription;
  final String? integrationLogoUrl;
  final String status;
  final DateTime createdAt;
  final DateTime? completedAt;

  const AuthAttempt({
    required this.id,
    required this.integrationId,
    required this.integrationName,
    required this.integrationCode,
    this.integrationDescription,
    this.integrationLogoUrl,
    required this.status,
    required this.createdAt,
    this.completedAt,
  });

  /// Creates an AuthAttempt from JSON data
  factory AuthAttempt.fromJson(Map<String, dynamic> json) {
    return AuthAttempt(
      id: json['id'] as String,
      integrationId: json['integrationId'] as String,
      integrationName: json['integrationName'] as String,
      integrationCode: json['integrationCode'] as String,
      integrationDescription: json['integrationDescription'] as String?,
      integrationLogoUrl: json['integrationLogoUrl'] as String?,
      status: json['status'] as String,
      createdAt: DateTime.parse(json['createdAt'] as String),
      completedAt: json['completedAt'] != null 
          ? DateTime.parse(json['completedAt'] as String) 
          : null,
    );
  }

  /// Converts the AuthAttempt to JSON data
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'integrationId': integrationId,
      'integrationName': integrationName,
      'integrationCode': integrationCode,
      'integrationDescription': integrationDescription,
      'integrationLogoUrl': integrationLogoUrl,
      'status': status,
      'createdAt': createdAt.toIso8601String(),
      'completedAt': completedAt?.toIso8601String(),
    };
  }

  /// Creates a copy of this AuthAttempt with updated fields
  AuthAttempt copyWith({
    String? id,
    String? integrationId,
    String? integrationName,
    String? integrationCode,
    String? integrationDescription,
    String? integrationLogoUrl,
    String? status,
    DateTime? createdAt,
    DateTime? completedAt,
  }) {
    return AuthAttempt(
      id: id ?? this.id,
      integrationId: integrationId ?? this.integrationId,
      integrationName: integrationName ?? this.integrationName,
      integrationCode: integrationCode ?? this.integrationCode,
      integrationDescription: integrationDescription ?? this.integrationDescription,
      integrationLogoUrl: integrationLogoUrl ?? this.integrationLogoUrl,
      status: status ?? this.status,
      createdAt: createdAt ?? this.createdAt,
      completedAt: completedAt ?? this.completedAt,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is AuthAttempt &&
        other.id == id &&
        other.integrationId == integrationId &&
        other.integrationName == integrationName &&
        other.integrationCode == integrationCode &&
        other.integrationDescription == integrationDescription &&
        other.integrationLogoUrl == integrationLogoUrl &&
        other.status == status &&
        other.createdAt == createdAt &&
        other.completedAt == completedAt;
  }

  @override
  int get hashCode {
    return Object.hash(
      id,
      integrationId,
      integrationName,
      integrationCode,
      integrationDescription,
      integrationLogoUrl,
      status,
      createdAt,
      completedAt,
    );
  }

  @override
  String toString() {
    return 'AuthAttempt(id: $id, integrationId: $integrationId, integrationName: $integrationName, status: $status)';
  }
} 