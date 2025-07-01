import 'dart:async';
import 'package:flutter/material.dart';

/// Deep link handler for Ezkey mobile app
/// 
/// This utility handles deep links for enrollment via SMS links
/// and other navigation scenarios.
/// 
/// @since 2025
class DeepLinkHandler {
  static const String _scheme = 'ezkey';
  static const String _host = 'enroll';
  
  /// Handles incoming deep links
  /// 
  /// Parses the URI and navigates to appropriate screen
  /// based on the link structure.
  static Future<void> handleDeepLink(
    Uri uri,
    BuildContext context,
  ) async {
    try {
      if (uri.scheme == _scheme && uri.host == _host) {
        await _handleEnrollmentLink(uri, context);
      } else {
        // Handle other types of links if needed
        debugPrint('Unhandled deep link: $uri');
      }
    } catch (e) {
      debugPrint('Error handling deep link: $e');
      _showErrorSnackBar(context, 'Invalid link format');
    }
  }

  /// Handles enrollment deep links
  /// 
  /// Expected format: ezkey://enroll?integrationId=xxx
  static Future<void> _handleEnrollmentLink(
    Uri uri,
    BuildContext context,
  ) async {
    final integrationId = uri.queryParameters['integrationId'];
    
    if (integrationId == null || integrationId.isEmpty) {
      _showErrorSnackBar(context, 'Missing integration ID');
      return;
    }

    // Navigate to enrollment screen with integration ID
    // This will be implemented in future phases
    debugPrint('Navigating to enrollment with integration ID: $integrationId');
    
    // For now, show a success message
    _showSuccessSnackBar(context, 'Enrollment link received');
  }

  /// Generates a deep link for enrollment
  /// 
  /// Creates a properly formatted deep link for sharing
  /// enrollment invitations.
  static String generateEnrollmentLink(String integrationId) {
    return '$_scheme://$_host?integrationId=$integrationId';
  }

  /// Validates if a URI is a valid Ezkey deep link
  static bool isValidDeepLink(Uri uri) {
    return uri.scheme == _scheme && uri.host == _host;
  }

  /// Extracts integration ID from deep link
  static String? extractIntegrationId(Uri uri) {
    if (!isValidDeepLink(uri)) {
      return null;
    }
    
    return uri.queryParameters['integrationId'];
  }

  static void _showErrorSnackBar(BuildContext context, String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.red,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  static void _showSuccessSnackBar(BuildContext context, String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.green,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }
} 