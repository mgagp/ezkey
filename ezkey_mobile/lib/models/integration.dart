/// Model representing an Ezkey integration metadata
/// 
/// This class represents the integration metadata structure used throughout 
/// the app for displaying integration information.
/// 
/// @since 2025
class Integration {
  final String id;
  final String name;
  final String code;
  final String? description;
  final String? logoUrl;
  final bool isActive;
  final DateTime createdAt;
  final DateTime? updatedAt;

  const Integration({
    required this.id,
    required this.name,
    required this.code,
    this.description,
    this.logoUrl,
    required this.isActive,
    required this.createdAt,
    this.updatedAt,
  });

  /// Creates an Integration from JSON data
  factory Integration.fromJson(Map<String, dynamic> json) {
    return Integration(
      id: json['id'] as String,
      name: json['name'] as String,
      code: json['code'] as String,
      description: json['description'] as String?,
      logoUrl: json['logoUrl'] as String?,
      isActive: json['isActive'] as bool,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: json['updatedAt'] != null 
          ? DateTime.parse(json['updatedAt'] as String) 
          : null,
    );
  }

  /// Converts the Integration to JSON data
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'code': code,
      'description': description,
      'logoUrl': logoUrl,
      'isActive': isActive,
      'createdAt': createdAt.toIso8601String(),
      'updatedAt': updatedAt?.toIso8601String(),
    };
  }

  /// Creates a copy of this Integration with updated fields
  Integration copyWith({
    String? id,
    String? name,
    String? code,
    String? description,
    String? logoUrl,
    bool? isActive,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return Integration(
      id: id ?? this.id,
      name: name ?? this.name,
      code: code ?? this.code,
      description: description ?? this.description,
      logoUrl: logoUrl ?? this.logoUrl,
      isActive: isActive ?? this.isActive,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is Integration &&
        other.id == id &&
        other.name == name &&
        other.code == code &&
        other.description == description &&
        other.logoUrl == logoUrl &&
        other.isActive == isActive &&
        other.createdAt == createdAt &&
        other.updatedAt == updatedAt;
  }

  @override
  int get hashCode {
    return Object.hash(
      id,
      name,
      code,
      description,
      logoUrl,
      isActive,
      createdAt,
      updatedAt,
    );
  }

  @override
  String toString() {
    return 'Integration(id: $id, name: $name, code: $code, isActive: $isActive)';
  }
} 