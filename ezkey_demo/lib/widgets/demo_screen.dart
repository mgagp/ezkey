import 'package:flutter/material.dart';
import '../services/api_service.dart';
import '../services/crypto_service.dart';
import '../models/enrollment.dart';
import '../models/auth_attempt.dart';

/// Main demo screen for the Ezkey application
/// 
/// This screen provides a single interface for enrollment and authentication
/// workflows with a dark theme and cyan accent color.
/// 
/// @since 2025
class DemoScreen extends StatefulWidget {
  const DemoScreen({super.key});

  @override
  State<DemoScreen> createState() => _DemoScreenState();
}

class _DemoScreenState extends State<DemoScreen> {
  // Services
  final ApiService _apiService = ApiService();
  final CryptoService _cryptoService = CryptoService();
  
  // Controllers for text fields
  final TextEditingController _urlController = TextEditingController();
  final TextEditingController _challengeController = TextEditingController();
  
  // State variables
  String _enrollmentStatus = '';
  String _authStatus = '';
  bool _isEnrolling = false;
  bool _isCheckingAuth = false;
  bool _isResponding = false;
  bool _showAuthButtons = false;
  bool _showChallengeInput = false;
  
  // Current data
  Enrollment? _currentEnrollment;
  AuthAttempt? _currentAuthAttempt;
  Map<String, String>? _deviceKeys;

  @override
  void dispose() {
    _urlController.dispose();
    _challengeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF121212),
      appBar: AppBar(
        title: const Text(
          'EZKEY DEMO',
          style: TextStyle(
            color: Colors.white,
            fontWeight: FontWeight.bold,
            fontSize: 24,
          ),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 20),
            
            // Enrollment Section
            _buildSection(
              title: 'Enrollment',
              children: [
                TextField(
                  controller: _urlController,
                  decoration: const InputDecoration(
                    labelText: 'Enrollment URL',
                    hintText: 'https://api.ezkey.org/enrollments/bind/{id}',
                    prefixIcon: Icon(Icons.link, color: Color(0xFF00BCD4)),
                  ),
                  enabled: !_isEnrolling,
                ),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: _isEnrolling ? null : _startEnrollment,
                  child: _isEnrolling
                      ? const Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                valueColor: AlwaysStoppedAnimation<Color>(Colors.black),
                              ),
                            ),
                            SizedBox(width: 8),
                            Text('Processing...'),
                          ],
                        )
                      : const Text('Start Enrollment'),
                ),
                const SizedBox(height: 16),
                if (_enrollmentStatus.isNotEmpty)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Enrollment Status:',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              color: Color(0xFF00BCD4),
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(_enrollmentStatus),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
            
            const SizedBox(height: 32),
            
            // Authentication Section
            _buildSection(
              title: 'Authentication',
              children: [
                ElevatedButton(
                  onPressed: _currentEnrollment == null || _isCheckingAuth
                      ? null
                      : _checkPendingAuth,
                  child: _isCheckingAuth
                      ? const Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                valueColor: AlwaysStoppedAnimation<Color>(Colors.black),
                              ),
                            ),
                            SizedBox(width: 8),
                            Text('Checking...'),
                          ],
                        )
                      : const Text('Check for Pending Authentication'),
                ),
                const SizedBox(height: 16),
                if (_authStatus.isNotEmpty)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Auth Status:',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              color: Color(0xFF00BCD4),
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(_authStatus),
                        ],
                      ),
                    ),
                  ),
                
                // Auth Action Buttons (shown when auth is pending)
                if (_showAuthButtons) ...[
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton(
                          onPressed: _isResponding ? null : () => _respondToAuth(true),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.green,
                            foregroundColor: Colors.white,
                          ),
                          child: const Text('Approve'),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: ElevatedButton(
                          onPressed: _isResponding ? null : () => _respondToAuth(false),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.red,
                            foregroundColor: Colors.white,
                          ),
                          child: const Text('Deny'),
                        ),
                      ),
                    ],
                  ),
                ],
                
                // Challenge Input (shown when challenge is required)
                if (_showChallengeInput) ...[
                  const SizedBox(height: 16),
                  TextField(
                    controller: _challengeController,
                    decoration: const InputDecoration(
                      labelText: 'Challenge Response',
                      hintText: 'Enter challenge response',
                      prefixIcon: Icon(Icons.security, color: Color(0xFF00BCD4)),
                    ),
                    enabled: !_isResponding,
                  ),
                ],
              ],
            ),
            
            const SizedBox(height: 32),
            
            // Info Section
            _buildSection(
              title: 'Information',
              children: [
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Demo Instructions:',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF00BCD4),
                          ),
                        ),
                        const SizedBox(height: 8),
                        const Text(
                          '1. Enter an enrollment URL (e.g., http://localhost:8080/api/v1/enrollments/bind/3)\n'
                          '2. Click "Start Enrollment"\n'
                          '3. Check for pending authentication\n'
                          '4. Approve or deny requests\n'
                          '5. Enter challenge if required',
                          style: TextStyle(fontSize: 14),
                        ),
                        const SizedBox(height: 16),
                        const Text(
                          'API Status:',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF00BCD4),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Base URL: http://localhost:8080\n'
                          'Enrollment: ${_currentEnrollment != null ? "✅ Active" : "❌ None"}\n'
                          'Auth Attempt: ${_currentAuthAttempt != null ? "✅ Pending" : "❌ None"}',
                          style: const TextStyle(fontSize: 14),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSection({
    required String title,
    required List<Widget> children,
  }) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                color: const Color(0xFF00BCD4),
              ),
            ),
            const SizedBox(height: 16),
            ...children,
          ],
        ),
      ),
    );
  }

  // Real enrollment workflow with API calls
  void _startEnrollment() async {
    final url = _urlController.text.trim();
    
    if (url.isEmpty) {
      setState(() {
        _enrollmentStatus = 'Error: Please enter an enrollment URL';
      });
      return;
    }
    
    // Extract enrollment ID from URL
    final enrollmentId = _extractEnrollmentId(url);
    if (enrollmentId == null) {
      setState(() {
        _enrollmentStatus = 'Error: Invalid enrollment URL format. Expected: .../bind/{id}';
      });
      return;
    }
    
    setState(() {
      _isEnrolling = true;
      _enrollmentStatus = '';
    });
    
    try {
      // Step 1: Call BIND API
      setState(() {
        _enrollmentStatus = '🔄 Calling BIND API...';
      });
      
      final enrollment = await _apiService.bindEnrollment(enrollmentId);
      
      // Step 2: Generate device keys
      setState(() {
        _enrollmentStatus = '🔄 Generating device keys...';
      });
      
      final deviceKeys = await _cryptoService.generateKeyPair();
      
      // Step 3: Generate challenge response
      setState(() {
        _enrollmentStatus = '🔄 Generating challenge response...';
      });
      
      final challengeResponse = await _cryptoService.generateChallengeResponse();
      
      // Step 4: Sign enrollment code
      setState(() {
        _enrollmentStatus = '🔄 Signing enrollment data...';
      });
      
      final signedEnrollmentCode = await _cryptoService.signData(
        enrollment.enrollmentCode,
        deviceKeys['privateKey']!,
      );
      
      // Step 5: Call CONFIRM API
      setState(() {
        _enrollmentStatus = '🔄 Confirming enrollment...';
      });
      
      final confirmResult = await _apiService.confirmEnrollment(
        enrollmentId: enrollment.enrollmentId,
        challengeResponse: challengeResponse,
        devicePublicKey: deviceKeys['publicKey']!,
        enrollmentCode: enrollment.enrollmentCode,
        enrollmentCodeSigned: signedEnrollmentCode,
      );
      
      // Success
      setState(() {
        _isEnrolling = false;
        _currentEnrollment = enrollment;
        _deviceKeys = deviceKeys;
        _enrollmentStatus = '✅ Enrollment successful!\n'
            'Enrollment ID: ${enrollment.enrollmentId}\n'
            'Enrollment Code: ${enrollment.enrollmentCode}\n'
            'Status: Active\n'
            'Challenge Response: $challengeResponse';
      });
      
    } catch (e) {
      setState(() {
        _isEnrolling = false;
        _enrollmentStatus = '❌ Enrollment failed: $e';
      });
    }
  }

  // Real authentication check with API calls
  void _checkPendingAuth() async {
    if (_currentEnrollment == null) {
      setState(() {
        _authStatus = 'Error: No active enrollment found';
      });
      return;
    }
    
    setState(() {
      _isCheckingAuth = true;
      _authStatus = '';
      _showAuthButtons = false;
      _showChallengeInput = false;
    });
    
    try {
      final authAttempt = await _apiService.checkPendingAuth(_currentEnrollment!.enrollmentId);
      
      if (authAttempt == null) {
        setState(() {
          _isCheckingAuth = false;
          _currentAuthAttempt = null;
          _authStatus = 'ℹ️ No pending authentication requests found.';
        });
      } else {
        setState(() {
          _isCheckingAuth = false;
          _currentAuthAttempt = authAttempt;
          _authStatus = '🔔 Pending authentication request!\n'
              'Request ID: ${authAttempt.authAttemptId}\n'
              'Challenge Required: ${authAttempt.authAttemptChallengeRequired ? "Yes" : "No"}';
          _showAuthButtons = true;
          _showChallengeInput = authAttempt.authAttemptChallengeRequired;
        });
      }
    } catch (e) {
      setState(() {
        _isCheckingAuth = false;
        _authStatus = '❌ Failed to check auth: $e';
      });
    }
  }

  // Real authentication response with API calls
  void _respondToAuth(bool approved) async {
    if (_currentEnrollment == null || _currentAuthAttempt == null) {
      setState(() {
        _authStatus = 'Error: No active enrollment or auth attempt';
      });
      return;
    }
    
    final challenge = _challengeController.text.trim();
    
    if (_showChallengeInput && challenge.isEmpty) {
      setState(() {
        _authStatus = 'Error: Challenge response is required';
      });
      return;
    }
    
    setState(() {
      _isResponding = true;
    });
    
    try {
      // Sign the auth attempt data
      final signedEnrolleeCode = await _cryptoService.signData(
        _currentEnrollment!.enrollmentCode,
        _deviceKeys!['privateKey']!,
      );
      
      final signedAuthCode = await _cryptoService.signData(
        _currentAuthAttempt!.authAttemptCode,
        _deviceKeys!['privateKey']!,
      );
      
      // Parse challenge response if provided
      int? challengeResponse;
      if (_showChallengeInput && challenge.isNotEmpty) {
        challengeResponse = int.tryParse(challenge);
        if (challengeResponse == null) {
          throw Exception('Invalid challenge response format');
        }
      }
      
      final result = await _apiService.respondToAuth(
        authAttemptId: _currentAuthAttempt!.authAttemptId,
        enrollmentId: _currentEnrollment!.enrollmentId,
        authAttemptEnrolleeCode: _currentEnrollment!.enrollmentCode,
        authAttemptEnrolleeCodeSigned: signedEnrolleeCode,
        authAttemptCode: _currentAuthAttempt!.authAttemptCode,
        authAttemptCodeSigned: signedAuthCode,
        authAttemptChallengeResponse: challengeResponse,
        authAttemptAccepted: approved,
      );
      
      setState(() {
        _isResponding = false;
        _authStatus = approved 
            ? '✅ Authentication approved successfully!'
            : '❌ Authentication denied.';
        _showAuthButtons = false;
        _showChallengeInput = false;
        _challengeController.clear();
        _currentAuthAttempt = null;
      });
    } catch (e) {
      setState(() {
        _isResponding = false;
        _authStatus = '❌ Failed to respond to auth: $e';
      });
    }
  }

  // Extract enrollment ID from URL
  int? _extractEnrollmentId(String url) {
    try {
      final uri = Uri.parse(url);
      final pathSegments = uri.pathSegments;
      
      // Look for pattern: .../bind/{id}
      for (int i = 0; i < pathSegments.length - 1; i++) {
        if (pathSegments[i] == 'bind' && i + 1 < pathSegments.length) {
          return int.tryParse(pathSegments[i + 1]);
        }
      }
      
      return null;
    } catch (e) {
      return null;
    }
  }
}
