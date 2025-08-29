import 'dart:convert';

/// Enrollment data model for Ezkey API responses
/// 
/// Represents the enrollment information returned by the BIND API
/// 
/// @since 2025
class Enrollment {
  final int enrollmentId;
  final String integrationPublicKey;
  final String enrollmentCode;
  final String enrollmentCodeSigned;
  final String simulationDevicePublicKey;
  final String simulationDevicePrivateKey;

  const Enrollment({
    required this.enrollmentId,
    required this.integrationPublicKey,
    required this.enrollmentCode,
    required this.enrollmentCodeSigned,
    required this.simulationDevicePublicKey,
    required this.simulationDevicePrivateKey,
  });

  /// Create Enrollment from JSON
  factory Enrollment.fromJson(Map<String, dynamic> json) {
    return Enrollment(
      enrollmentId: json['enrollmentId'] as int,
      integrationPublicKey: json['integrationPublicKey'] as String,
      enrollmentCode: json['enrollmentCode'] as String,
      enrollmentCodeSigned: json['enrollmentCodeSigned'] as String,
      simulationDevicePublicKey: json['simulationDevicePublicKey'] as String,
      simulationDevicePrivateKey: json['simulationDevicePrivateKey'] as String,
    );
  }

  /// Convert Enrollment to JSON
  Map<String, dynamic> toJson() {
    return {
      'enrollmentId': enrollmentId,
      'integrationPublicKey': integrationPublicKey,
      'enrollmentCode': enrollmentCode,
      'enrollmentCodeSigned': enrollmentCodeSigned,
      'simulationDevicePublicKey': simulationDevicePublicKey,
      'simulationDevicePrivateKey': simulationDevicePrivateKey,
    };
  }

  /// Create Enrollment from JSON string
  factory Enrollment.fromJsonString(String jsonString) {
    final json = jsonDecode(jsonString) as Map<String, dynamic>;
    return Enrollment.fromJson(json);
  }

  /// Convert Enrollment to JSON string
  String toJsonString() {
    return jsonEncode(toJson());
  }

  /// Create a copy of this Enrollment with updated fields
  Enrollment copyWith({
    int? enrollmentId,
    String? integrationPublicKey,
    String? enrollmentCode,
    String? enrollmentCodeSigned,
    String? simulationDevicePublicKey,
    String? simulationDevicePrivateKey,
  }) {
    return Enrollment(
      enrollmentId: enrollmentId ?? this.enrollmentId,
      integrationPublicKey: integrationPublicKey ?? this.integrationPublicKey,
      enrollmentCode: enrollmentCode ?? this.enrollmentCode,
      enrollmentCodeSigned: enrollmentCodeSigned ?? this.enrollmentCodeSigned,
      simulationDevicePublicKey: simulationDevicePublicKey ?? this.simulationDevicePublicKey,
      simulationDevicePrivateKey: simulationDevicePrivateKey ?? this.simulationDevicePrivateKey,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is Enrollment &&
        other.enrollmentId == enrollmentId &&
        other.integrationPublicKey == integrationPublicKey &&
        other.enrollmentCode == enrollmentCode &&
        other.enrollmentCodeSigned == enrollmentCodeSigned &&
        other.simulationDevicePublicKey == simulationDevicePublicKey &&
        other.simulationDevicePrivateKey == simulationDevicePrivateKey;
  }

  @override
  int get hashCode {
    return enrollmentId.hashCode ^
        integrationPublicKey.hashCode ^
        enrollmentCode.hashCode ^
        enrollmentCodeSigned.hashCode ^
        simulationDevicePublicKey.hashCode ^
        simulationDevicePrivateKey.hashCode;
  }

  @override
  String toString() {
    return 'Enrollment(enrollmentId: $enrollmentId, enrollmentCode: $enrollmentCode)';
  }
}
