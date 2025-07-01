import 'dart:async';

import '../models/enrollment.dart';
import '../models/auth_attempt.dart';
import '../models/integration.dart';
import 'api_service.dart';

/// Mock implementation of ApiService for development and testing
/// 
/// This service provides mock data and simulated API responses
/// for development and testing purposes.
/// 
/// @since 2025
class MockApiService implements ApiService {
  final List<Enrollment> _enrollments = [];
  final List<AuthAttempt> _authAttempts = [];
  final List<Integration> _integrations = [];
  
  // Simulate network delay
  static const Duration _delay = Duration(milliseconds: 500);

  MockApiService() {
    _initializeMockData();
  }

  @override
  String get baseUrl => 'https://mock.ezkey.com';

  @override
  String? get apiKey => 'mock-api-key';

  void _initializeMockData() {
    // Mock integrations
    _integrations.addAll([
      Integration(
        id: 'int-001',
        name: 'Acme Corp',
        code: 'ACME',
        description: 'Enterprise security solution for Acme Corporation',
        logoUrl: 'https://via.placeholder.com/64x64/007ACC/FFFFFF?text=ACME',
        isActive: true,
        createdAt: DateTime.now().subtract(const Duration(days: 30)),
      ),
      Integration(
        id: 'int-002',
        name: 'TechStart Inc',
        code: 'TECH',
        description: 'Startup-focused authentication platform',
        logoUrl: 'https://via.placeholder.com/64x64/FF6B35/FFFFFF?text=TECH',
        isActive: true,
        createdAt: DateTime.now().subtract(const Duration(days: 15)),
      ),
      Integration(
        id: 'int-003',
        name: 'Global Bank',
        code: 'BANK',
        description: 'Financial services authentication system',
        logoUrl: 'https://via.placeholder.com/64x64/28A745/FFFFFF?text=BANK',
        isActive: true,
        createdAt: DateTime.now().subtract(const Duration(days: 7)),
      ),
    ]);

    // Mock enrollments
    _enrollments.addAll([
      Enrollment(
        id: 'enr-001',
        integrationId: 'int-001',
        integrationName: 'Acme Corp',
        integrationCode: 'ACME',
        integrationDescription: 'Enterprise security solution for Acme Corporation',
        integrationLogoUrl: 'https://via.placeholder.com/64x64/007ACC/FFFFFF?text=ACME',
        status: 'ACTIVE',
        createdAt: DateTime.now().subtract(const Duration(days: 5)),
      ),
      Enrollment(
        id: 'enr-002',
        integrationId: 'int-002',
        integrationName: 'TechStart Inc',
        integrationCode: 'TECH',
        integrationDescription: 'Startup-focused authentication platform',
        integrationLogoUrl: 'https://via.placeholder.com/64x64/FF6B35/FFFFFF?text=TECH',
        status: 'PENDING',
        createdAt: DateTime.now().subtract(const Duration(hours: 2)),
      ),
    ]);

    // Mock auth attempts
    _authAttempts.addAll([
      AuthAttempt(
        id: 'auth-001',
        integrationId: 'int-001',
        integrationName: 'Acme Corp',
        integrationCode: 'ACME',
        integrationDescription: 'Enterprise security solution for Acme Corporation',
        integrationLogoUrl: 'https://via.placeholder.com/64x64/007ACC/FFFFFF?text=ACME',
        status: 'COMPLETED',
        createdAt: DateTime.now().subtract(const Duration(hours: 1)),
        completedAt: DateTime.now().subtract(const Duration(minutes: 30)),
      ),
      AuthAttempt(
        id: 'auth-002',
        integrationId: 'int-002',
        integrationName: 'TechStart Inc',
        integrationCode: 'TECH',
        integrationDescription: 'Startup-focused authentication platform',
        integrationLogoUrl: 'https://via.placeholder.com/64x64/FF6B35/FFFFFF?text=TECH',
        status: 'PENDING',
        createdAt: DateTime.now().subtract(const Duration(minutes: 15)),
      ),
    ]);
  }

  @override
  Future<Enrollment> createEnrollment(String integrationId) async {
    await Future.delayed(_delay);
    
    final integration = _integrations.firstWhere(
      (i) => i.id == integrationId,
      orElse: () => throw ApiException('Integration not found: $integrationId'),
    );

    final enrollment = Enrollment(
      id: 'enr-${DateTime.now().millisecondsSinceEpoch}',
      integrationId: integration.id,
      integrationName: integration.name,
      integrationCode: integration.code,
      integrationDescription: integration.description,
      integrationLogoUrl: integration.logoUrl,
      status: 'PENDING',
      createdAt: DateTime.now(),
    );

    _enrollments.add(enrollment);
    return enrollment;
  }

  @override
  Future<List<Enrollment>> getEnrollments() async {
    await Future.delayed(_delay);
    return List.unmodifiable(_enrollments);
  }

  @override
  Future<void> deleteEnrollment(String enrollmentId) async {
    await Future.delayed(_delay);
    
    final index = _enrollments.indexWhere((e) => e.id == enrollmentId);
    if (index == -1) {
      throw ApiException('Enrollment not found: $enrollmentId');
    }
    
    _enrollments.removeAt(index);
  }

  @override
  Future<List<AuthAttempt>> getAuthAttempts() async {
    await Future.delayed(_delay);
    return List.unmodifiable(_authAttempts);
  }

  @override
  Future<AuthAttempt> completeAuthAttempt(String attemptId) async {
    await Future.delayed(_delay);
    
    final index = _authAttempts.indexWhere((a) => a.id == attemptId);
    if (index == -1) {
      throw ApiException('Auth attempt not found: $attemptId');
    }
    
    final attempt = _authAttempts[index];
    final completedAttempt = attempt.copyWith(
      status: 'COMPLETED',
      completedAt: DateTime.now(),
    );
    
    _authAttempts[index] = completedAttempt;
    return completedAttempt;
  }

  @override
  Future<Integration> getIntegration(String integrationId) async {
    await Future.delayed(_delay);
    
    try {
      return _integrations.firstWhere((i) => i.id == integrationId);
    } catch (e) {
      throw ApiException('Integration not found: $integrationId');
    }
  }

  @override
  Future<List<Integration>> getIntegrations() async {
    await Future.delayed(_delay);
    return List.unmodifiable(_integrations);
  }

  /// Adds a mock auth attempt for testing
  void addMockAuthAttempt(AuthAttempt attempt) {
    _authAttempts.add(attempt);
  }

  /// Clears all mock data
  void clearMockData() {
    _enrollments.clear();
    _authAttempts.clear();
    _integrations.clear();
    _initializeMockData();
  }
} 