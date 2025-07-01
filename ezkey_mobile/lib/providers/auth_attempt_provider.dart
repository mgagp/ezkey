import 'package:flutter/foundation.dart';

import '../models/auth_attempt.dart';
import '../services/api_service.dart';

/// Provider for managing authentication attempt state
/// 
/// This provider handles the state management for authentication attempts,
/// including loading and completing auth attempts.
/// 
/// @since 2025
class AuthAttemptProvider with ChangeNotifier {
  final ApiService _apiService;
  
  List<AuthAttempt> _authAttempts = [];
  bool _isLoading = false;
  String? _error;

  AuthAttemptProvider(this._apiService);

  /// List of all authentication attempts
  List<AuthAttempt> get authAttempts => List.unmodifiable(_authAttempts);
  
  /// Loading state
  bool get isLoading => _isLoading;
  
  /// Error message if any
  String? get error => _error;
  
  /// Whether there are any auth attempts
  bool get hasAuthAttempts => _authAttempts.isNotEmpty;
  
  /// Number of pending auth attempts
  int get pendingAuthAttemptsCount => 
      _authAttempts.where((a) => a.status == 'PENDING').length;
  
  /// Number of completed auth attempts
  int get completedAuthAttemptsCount => 
      _authAttempts.where((a) => a.status == 'COMPLETED').length;

  /// Loads all authentication attempts from the API
  Future<void> loadAuthAttempts() async {
    _setLoading(true);
    _clearError();
    
    try {
      final authAttempts = await _apiService.getAuthAttempts();
      _authAttempts = authAttempts;
      notifyListeners();
    } catch (e) {
      _setError(e.toString());
    } finally {
      _setLoading(false);
    }
  }

  /// Completes an authentication attempt
  Future<AuthAttempt?> completeAuthAttempt(String attemptId) async {
    _setLoading(true);
    _clearError();
    
    try {
      final completedAttempt = await _apiService.completeAuthAttempt(attemptId);
      
      // Update the attempt in the list
      final index = _authAttempts.indexWhere((a) => a.id == attemptId);
      if (index != -1) {
        _authAttempts[index] = completedAttempt;
      }
      
      notifyListeners();
      return completedAttempt;
    } catch (e) {
      _setError(e.toString());
      return null;
    } finally {
      _setLoading(false);
    }
  }

  /// Gets an auth attempt by ID
  AuthAttempt? getAuthAttemptById(String attemptId) {
    try {
      return _authAttempts.firstWhere((a) => a.id == attemptId);
    } catch (e) {
      return null;
    }
  }

  /// Gets auth attempts by status
  List<AuthAttempt> getAuthAttemptsByStatus(String status) {
    return _authAttempts.where((a) => a.status == status).toList();
  }

  /// Gets auth attempts by integration ID
  List<AuthAttempt> getAuthAttemptsByIntegration(String integrationId) {
    return _authAttempts.where((a) => a.integrationId == integrationId).toList();
  }

  /// Gets pending auth attempts
  List<AuthAttempt> get pendingAuthAttempts {
    return getAuthAttemptsByStatus('PENDING');
  }

  /// Gets completed auth attempts
  List<AuthAttempt> get completedAuthAttempts {
    return getAuthAttemptsByStatus('COMPLETED');
  }

  /// Refreshes the auth attempts list
  Future<void> refresh() async {
    await loadAuthAttempts();
  }

  /// Clears all auth attempts (for testing)
  void clearAuthAttempts() {
    _authAttempts.clear();
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