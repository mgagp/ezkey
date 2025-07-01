import 'package:flutter/foundation.dart';

import '../models/enrollment.dart';
import '../services/api_service.dart';

/// Provider for managing enrollment state
/// 
/// This provider handles the state management for enrollments,
/// including loading, creating, and deleting enrollments.
/// 
/// @since 2025
class EnrollmentProvider with ChangeNotifier {
  final ApiService _apiService;
  
  List<Enrollment> _enrollments = [];
  bool _isLoading = false;
  String? _error;

  EnrollmentProvider(this._apiService);

  /// List of all enrollments
  List<Enrollment> get enrollments => List.unmodifiable(_enrollments);
  
  /// Loading state
  bool get isLoading => _isLoading;
  
  /// Error message if any
  String? get error => _error;
  
  /// Whether there are any enrollments
  bool get hasEnrollments => _enrollments.isNotEmpty;
  
  /// Number of active enrollments
  int get activeEnrollmentsCount => 
      _enrollments.where((e) => e.status == 'ACTIVE').length;
  
  /// Number of pending enrollments
  int get pendingEnrollmentsCount => 
      _enrollments.where((e) => e.status == 'PENDING').length;

  /// Loads all enrollments from the API
  Future<void> loadEnrollments() async {
    _setLoading(true);
    _clearError();
    
    try {
      final enrollments = await _apiService.getEnrollments();
      _enrollments = enrollments;
      notifyListeners();
    } catch (e) {
      _setError(e.toString());
    } finally {
      _setLoading(false);
    }
  }

  /// Creates a new enrollment
  Future<Enrollment?> createEnrollment(String integrationId) async {
    _setLoading(true);
    _clearError();
    
    try {
      final enrollment = await _apiService.createEnrollment(integrationId);
      _enrollments.add(enrollment);
      notifyListeners();
      return enrollment;
    } catch (e) {
      _setError(e.toString());
      return null;
    } finally {
      _setLoading(false);
    }
  }

  /// Deletes an enrollment
  Future<bool> deleteEnrollment(String enrollmentId) async {
    _setLoading(true);
    _clearError();
    
    try {
      await _apiService.deleteEnrollment(enrollmentId);
      _enrollments.removeWhere((e) => e.id == enrollmentId);
      notifyListeners();
      return true;
    } catch (e) {
      _setError(e.toString());
      return false;
    } finally {
      _setLoading(false);
    }
  }

  /// Gets an enrollment by ID
  Enrollment? getEnrollmentById(String enrollmentId) {
    try {
      return _enrollments.firstWhere((e) => e.id == enrollmentId);
    } catch (e) {
      return null;
    }
  }

  /// Gets enrollments by status
  List<Enrollment> getEnrollmentsByStatus(String status) {
    return _enrollments.where((e) => e.status == status).toList();
  }

  /// Gets enrollments by integration ID
  List<Enrollment> getEnrollmentsByIntegration(String integrationId) {
    return _enrollments.where((e) => e.integrationId == integrationId).toList();
  }

  /// Refreshes the enrollments list
  Future<void> refresh() async {
    await loadEnrollments();
  }

  /// Clears all enrollments (for testing)
  void clearEnrollments() {
    _enrollments.clear();
    notifyListeners();
  }

  void _setLoading(bool loading) {
    _isLoading = loading;
    notifyListeners();
  }

  void _setError(String error) {
    _error = error;
    notifyListeners();
  }

  void _clearError() {
    _error = null;
  }
} 