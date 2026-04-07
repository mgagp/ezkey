import 'dart:convert';

import 'package:http/http.dart' as http;

import 'enrollment_snapshot.dart';

class AdminApiClient {
  AdminApiClient({
    required this.baseUri,
    required this.bearerToken,
    http.Client? httpClient,
  }) : _httpClient = httpClient ?? http.Client();

  final Uri baseUri;
  final String bearerToken;
  final http.Client _httpClient;

  Future<List<EnrollmentSnapshot>> listEnrollments({
    String? status,
    int? integrationId,
    bool? active,
    int size = 20,
  }) async {
    final queryParameters = <String, String>{
      'page': '0',
      'size': '$size',
      'sort': 'createdAt,desc',
    };
    if (status != null && status.isNotEmpty) {
      queryParameters['status'] = status;
    }
    if (integrationId != null) {
      queryParameters['integrationId'] = '$integrationId';
    }
    if (active != null) {
      queryParameters['active'] = '$active';
    }

    final response = await _send(
      baseUri.replace(
        path: _normalizePath('/api/v1/enrollments'),
        queryParameters: queryParameters,
      ),
    );
    final decoded = jsonDecode(response.body) as Map<String, dynamic>;
    final embedded = decoded['_embedded'] as Map<String, dynamic>?;
    final items =
        (embedded?['enrollmentResponseDtoList'] as List<dynamic>? ??
                embedded?['enrollments'] as List<dynamic>? ??
                <dynamic>[])
            .cast<Map<String, dynamic>>();
    return items.map(EnrollmentSnapshot.fromJson).toList(growable: false);
  }

  Future<EnrollmentSnapshot> getEnrollment(int enrollmentId) async {
    final response = await _send(
      baseUri.replace(
        path: _normalizePath('/api/v1/enrollments/$enrollmentId'),
      ),
    );
    return EnrollmentSnapshot.fromJson(
      jsonDecode(response.body) as Map<String, dynamic>,
    );
  }

  Future<http.Response> _send(Uri uri) async {
    final response = await _httpClient.get(
      uri,
      headers: <String, String>{
        'Authorization': 'Bearer $bearerToken',
        'Accept': 'application/json',
      },
    );
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw StateError(
        'Admin API request failed (${response.statusCode}): ${response.body}',
      );
    }
    return response;
  }

  String _normalizePath(String path) {
    final basePath = baseUri.path.endsWith('/')
        ? baseUri.path.substring(0, baseUri.path.length - 1)
        : baseUri.path;
    if (basePath.isEmpty || basePath == '/') {
      return path;
    }
    return '$basePath$path';
  }
}
