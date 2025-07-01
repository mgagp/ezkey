/// Model representing an Ezkey enrollment
/// 
/// This class represents the enrollment data structure used throughout the app
/// for managing user enrollments with Ezkey integrations.
/// 
/// @since 2025
class Enrollment {
  final String id;
  final String integrationId;
  final String integrationName;
  final String integrationCode;
  final String? integrationDescription;
  final String? integrationLogoUrl;
  final String status;
  final DateTime createdAt;
  final DateTime? updatedAt;

  const Enrollment({
    required this.id,
    required this.integrationId,
    required this.integrationName,
    required this.integrationCode,
    this.integrationDescription,
    this.integrationLogoUrl,
    required this.status,
    required this.createdAt,
    this.updatedAt,
  });

  /// Creates an Enrollment from JSON data
  factory Enrollment.fromJson(Map<String, dynamic> json) {
    return Enrollment(
      id: json['id'] as String,
      integrationId: json['integrationId'] as String,
      integrationName: json['integrationName'] as String,
      integrationCode: json['integrationCode'] as String,
      integrationDescription: json['integrationDescription'] as String?,
      integrationLogoUrl: json['integrationLogoUrl'] as String?,
      status: json['status'] as String,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: json['updatedAt'] != null 
          ? DateTime.parse(json['updatedAt'] as String) 
          : null,
    );
  }

  /// Converts the Enrollment to JSON data
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
      'updatedAt': updatedAt?.toIso8601String(),
    };
  }

  /// Creates a copy of this Enrollment with updated fields
  Enrollment copyWith({
    String? id,
    String? integrationId,
    String? integrationName,
    String? integrationCode,
    String? integrationDescription,
    String? integrationLogoUrl,
    String? status,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return Enrollment(
      id: id ?? this.id,
      integrationId: integrationId ?? this.integrationId,
      integrationName: integrationName ?? this.integrationName,
      integrationCode: integrationCode ?? this.integrationCode,
      integrationDescription: integrationDescription ?? this.integrationDescription,
      integrationLogoUrl: integrationLogoUrl ?? this.integrationLogoUrl,
      status: status ?? this.status,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is Enrollment &&
        other.id == id &&
        other.integrationId == integrationId &&
        other.integrationName == integrationName &&
        other.integrationCode == integrationCode &&
        other.integrationDescription == integrationDescription &&
        other.integrationLogoUrl == integrationLogoUrl &&
        other.status == status &&
        other.createdAt == createdAt &&
        other.updatedAt == updatedAt;
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
      updatedAt,
    );
  }

  @override
  String toString() {
    return 'Enrollment(id: $id, integrationId: $integrationId, integrationName: $integrationName, status: $status)';
  }
} 