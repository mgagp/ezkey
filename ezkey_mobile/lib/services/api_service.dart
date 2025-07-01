import 'dart:convert';
import 'package:http/http.dart' as http;

import '../models/enrollment.dart';
import '../models/auth_attempt.dart';
import '../models/integration.dart';

/// Abstract API service for Ezkey mobile app
/// 
/// This service provides methods to interact with the Ezkey backend APIs
/// for enrollments and authentication attempts.
/// 
/// @since 2025
abstract class ApiService {
  /// Base URL for the API
  String get baseUrl;
  
  /// API key for authentication
  String? get apiKey;

  /// Creates a new enrollment
  Future<Enrollment> createEnrollment(String integrationId);
  
  /// Gets all enrollments for the current user
  Future<List<Enrollment>> getEnrollments();
  
  /// Deletes an enrollment by ID
  Future<void> deleteEnrollment(String enrollmentId);
  
  /// Gets all authentication attempts for the current user
  Future<List<AuthAttempt>> getAuthAttempts();
  
  /// Completes an authentication attempt
  Future<AuthAttempt> completeAuthAttempt(String attemptId);
  
  /// Gets integration metadata by ID
  Future<Integration> getIntegration(String integrationId);
  
  /// Gets all available integrations
  Future<List<Integration>> getIntegrations();
}

/// Implementation of ApiService using HTTP client
/// 
/// This implementation uses the http package to make REST API calls
/// to the Ezkey backend services.
/// 
/// @since 2025
class HttpApiService implements ApiService {
  final http.Client _client;
  final String _baseUrl;
  final String? _apiKey;

  HttpApiService({
    http.Client? client,
    required String baseUrl,
    String? apiKey,
  }) : _client = client ?? http.Client(),
       _baseUrl = baseUrl,
       _apiKey = apiKey;

  @override
  String get baseUrl => _baseUrl;

  @override
  String? get apiKey => _apiKey;

  Map<String, String> get _headers {
    final headers = <String, String>{
      'Content-Type': 'application/json',
    };
    
    if (_apiKey != null) {
      headers['Authorization'] = 'Bearer $_apiKey';
    }
    
    return headers;
  }

  @override
  Future<Enrollment> createEnrollment(String integrationId) async {
    final response = await _client.post(
      Uri.parse('$_baseUrl/api/v1/enrollments'),
      headers: _headers,
      body: jsonEncode({
        'integrationId': integrationId,
      }),
    );

    if (response.statusCode == 201) {
      return Enrollment.fromJson(jsonDecode(response.body));
    } else {
      throw ApiException('Failed to create enrollment: ${response.statusCode}');
    }
  }

  @override
  Future<List<Enrollment>> getEnrollments() async {
    final response = await _client.get(
      Uri.parse('$_baseUrl/api/v1/enrollments'),
      headers: _headers,
    );

    if (response.statusCode == 200) {
      final List<dynamic> data = jsonDecode(response.body);
      return data.map((json) => Enrollment.fromJson(json)).toList();
    } else {
      throw ApiException('Failed to get enrollments: ${response.statusCode}');
    }
  }

  @override
  Future<void> deleteEnrollment(String enrollmentId) async {
    final response = await _client.delete(
      Uri.parse('$_baseUrl/api/v1/enrollments/$enrollmentId'),
      headers: _headers,
    );

    if (response.statusCode != 204) {
      throw ApiException('Failed to delete enrollment: ${response.statusCode}');
    }
  }

  @override
  Future<List<AuthAttempt>> getAuthAttempts() async {
    final response = await _client.get(
      Uri.parse('$_baseUrl/api/v1/auth-attempts'),
      headers: _headers,
    );

    if (response.statusCode == 200) {
      final List<dynamic> data = jsonDecode(response.body);
      return data.map((json) => AuthAttempt.fromJson(json)).toList();
    } else {
      throw ApiException('Failed to get auth attempts: ${response.statusCode}');
    }
  }

  @override
  Future<AuthAttempt> completeAuthAttempt(String attemptId) async {
    final response = await _client.post(
      Uri.parse('$_baseUrl/api/v1/auth-attempts/$attemptId/complete'),
      headers: _headers,
    );

    if (response.statusCode == 200) {
      return AuthAttempt.fromJson(jsonDecode(response.body));
    } else {
      throw ApiException('Failed to complete auth attempt: ${response.statusCode}');
    }
  }

  @override
  Future<Integration> getIntegration(String integrationId) async {
    final response = await _client.get(
      Uri.parse('$_baseUrl/api/v1/integrations/$integrationId'),
      headers: _headers,
    );

    if (response.statusCode == 200) {
      return Integration.fromJson(jsonDecode(response.body));
    } else {
      throw ApiException('Failed to get integration: ${response.statusCode}');
    }
  }

  @override
  Future<List<Integration>> getIntegrations() async {
    final response = await _client.get(
      Uri.parse('$_baseUrl/api/v1/integrations'),
      headers: _headers,
    );

    if (response.statusCode == 200) {
      final List<dynamic> data = jsonDecode(response.body);
      return data.map((json) => Integration.fromJson(json)).toList();
    } else {
      throw ApiException('Failed to get integrations: ${response.statusCode}');
    }
  }
}

/// Exception thrown when API calls fail
/// 
/// @since 2025
class ApiException implements Exception {
  final String message;

  ApiException(this.message);

  @override
  String toString() => 'ApiException: $message';
} 