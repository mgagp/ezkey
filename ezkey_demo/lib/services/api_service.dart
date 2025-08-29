import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/enrollment.dart';
import '../models/auth_attempt.dart';

/// API Service for communicating with Ezkey local API
/// 
/// Handles all HTTP requests to the local Ezkey API endpoints
/// 
/// @since 2025
class ApiService {
  static const String _baseUrl = 'http://localhost:8080';
  static const Duration _timeout = Duration(seconds: 30);

  /// Get enrollment information from BIND endpoint
  /// 
  /// Calls GET /api/v1/enrollments/bind/{id}
  /// 
  /// [enrollmentId] - The enrollment ID to bind
  /// Returns [Enrollment] object with enrollment details
  Future<Enrollment> bindEnrollment(int enrollmentId) async {
    try {
      final url = Uri.parse('$_baseUrl/api/v1/enrollments/bind/$enrollmentId');
      
      final response = await http.get(url).timeout(_timeout);
      
      if (response.statusCode == 200) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        return Enrollment.fromJson(json);
      } else {
        throw ApiException(
          'Failed to bind enrollment: ${response.statusCode}',
          response.statusCode,
          response.body,
        );
      }
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException('Network error: $e', 0, e.toString());
    }
  }

  /// Confirm enrollment with device keys
  /// 
  /// Calls POST /api/v1/enrollments/confirm
  /// 
  /// [enrollmentId] - The enrollment ID
  /// [challengeResponse] - The challenge response
  /// [devicePublicKey] - The device public key
  /// [enrollmentCode] - The enrollment code
  /// [enrollmentCodeSigned] - The signed enrollment code
  /// Returns confirmation status
  Future<Map<String, dynamic>> confirmEnrollment({
    required int enrollmentId,
    required int challengeResponse,
    required String devicePublicKey,
    required String enrollmentCode,
    required String enrollmentCodeSigned,
  }) async {
    try {
      final url = Uri.parse('$_baseUrl/api/v1/enrollments/confirm');
      
      final body = {
        'enrollmentId': enrollmentId,
        'challengeResponse': challengeResponse,
        'devicePublicKey': devicePublicKey,
        'enrollmentCode': enrollmentCode,
        'enrollmentCodeSigned': enrollmentCodeSigned,
      };
      
      final response = await http.post(
        url,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(body),
      ).timeout(_timeout);
      
      if (response.statusCode == 200) {
        return jsonDecode(response.body) as Map<String, dynamic>;
      } else {
        throw ApiException(
          'Failed to confirm enrollment: ${response.statusCode}',
          response.statusCode,
          response.body,
        );
      }
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException('Network error: $e', 0, e.toString());
    }
  }

  /// Check for pending authentication attempts
  /// 
  /// Calls GET /api/v1/authattempts/pending/{enrollmentId}
  /// 
  /// [enrollmentId] - The enrollment ID to check
  /// Returns [AuthAttempt] if pending, null if none
  Future<AuthAttempt?> checkPendingAuth(int enrollmentId) async {
    try {
      final url = Uri.parse('$_baseUrl/api/v1/authattempts/pending/$enrollmentId');
      
      final response = await http.get(url).timeout(_timeout);
      
      if (response.statusCode == 200) {
        final body = response.body.trim();
        if (body.isEmpty) {
          return null; // No pending auth attempts
        }
        
        final json = jsonDecode(body) as Map<String, dynamic>;
        return AuthAttempt.fromJson(json);
      } else if (response.statusCode == 404) {
        return null; // No pending auth attempts
      } else {
        throw ApiException(
          'Failed to check pending auth: ${response.statusCode}',
          response.statusCode,
          response.body,
        );
      }
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException('Network error: $e', 0, e.toString());
    }
  }

  /// Respond to authentication attempt
  /// 
  /// Calls POST /api/v1/authattempts/respond/{id}
  /// 
  /// [authAttemptId] - The auth attempt ID
  /// [enrollmentId] - The enrollment ID
  /// [authAttemptEnrolleeCode] - The enrollee code
  /// [authAttemptEnrolleeCodeSigned] - The signed enrollee code
  /// [authAttemptCode] - The auth attempt code
  /// [authAttemptCodeSigned] - The signed auth attempt code
  /// [authAttemptChallengeResponse] - The challenge response (if required)
  /// [authAttemptAccepted] - Whether the auth is accepted
  /// Returns response status
  Future<Map<String, dynamic>> respondToAuth({
    required int authAttemptId,
    required int enrollmentId,
    required String authAttemptEnrolleeCode,
    required String authAttemptEnrolleeCodeSigned,
    required String authAttemptCode,
    required String authAttemptCodeSigned,
    int? authAttemptChallengeResponse,
    required bool authAttemptAccepted,
  }) async {
    try {
      final url = Uri.parse('$_baseUrl/api/v1/authattempts/respond/$authAttemptId');
      
      final body = {
        'enrollmentId': enrollmentId,
        'authAttemptEnrolleeCode': authAttemptEnrolleeCode,
        'authAttemptEnrolleeCodeSigned': authAttemptEnrolleeCodeSigned,
        'authAttemptCode': authAttemptCode,
        'authAttemptCodeSigned': authAttemptCodeSigned,
        if (authAttemptChallengeResponse != null)
          'authAttemptChallengeResponse': authAttemptChallengeResponse,
        'authAttemptAccepted': authAttemptAccepted,
      };
      
      final response = await http.post(
        url,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(body),
      ).timeout(_timeout);
      
      if (response.statusCode == 200) {
        return jsonDecode(response.body) as Map<String, dynamic>;
      } else {
        throw ApiException(
          'Failed to respond to auth: ${response.statusCode}',
          response.statusCode,
          response.body,
        );
      }
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException('Network error: $e', 0, e.toString());
    }
  }
}

/// Custom exception for API errors
/// 
/// Provides detailed error information for debugging
/// 
/// @since 2025
class ApiException implements Exception {
  final String message;
  final int statusCode;
  final String responseBody;

  const ApiException(this.message, this.statusCode, this.responseBody);

  @override
  String toString() {
    return 'ApiException: $message (Status: $statusCode, Response: $responseBody)';
  }
}
