import 'package:flutter/material.dart';

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
  
  // Mock data for demo
  String? _currentEnrollmentId;
  String? _currentAuthAttemptId;

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
                  onPressed: _currentEnrollmentId == null || _isCheckingAuth
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
                          '1. Enter an enrollment URL\n'
                          '2. Click "Start Enrollment"\n'
                          '3. Check for pending authentication\n'
                          '4. Approve or deny requests\n'
                          '5. Enter challenge if required',
                          style: TextStyle(fontSize: 14),
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

  // Mock enrollment workflow
  void _startEnrollment() async {
    final url = _urlController.text.trim();
    
    if (url.isEmpty) {
      setState(() {
        _enrollmentStatus = 'Error: Please enter an enrollment URL';
      });
      return;
    }
    
    setState(() {
      _isEnrolling = true;
      _enrollmentStatus = '';
    });
    
    // Simulate API call delay
    await Future.delayed(const Duration(seconds: 2));
    
    // Mock successful enrollment
    setState(() {
      _isEnrolling = false;
      _currentEnrollmentId = '12345';
      _enrollmentStatus = '✅ Enrollment successful!\n'
          'Integration: Acme Corp Admin Portal\n'
          'Enrollment ID: $_currentEnrollmentId\n'
          'Status: Active';
    });
  }

  // Mock authentication check
  void _checkPendingAuth() async {
    setState(() {
      _isCheckingAuth = true;
      _authStatus = '';
      _showAuthButtons = false;
      _showChallengeInput = false;
    });
    
    // Simulate API call delay
    await Future.delayed(const Duration(seconds: 1));
    
    // Mock pending authentication
    setState(() {
      _isCheckingAuth = false;
      _currentAuthAttemptId = '67890';
      _authStatus = '🔔 Pending authentication request!\n'
          'Request ID: $_currentAuthAttemptId\n'
          'Challenge Required: Yes';
      _showAuthButtons = true;
      _showChallengeInput = true;
    });
  }

  // Mock authentication response
  void _respondToAuth(bool approved) async {
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
    
    // Simulate API call delay
    await Future.delayed(const Duration(seconds: 1));
    
    setState(() {
      _isResponding = false;
      _authStatus = approved 
          ? '✅ Authentication approved successfully!'
          : '❌ Authentication denied.';
      _showAuthButtons = false;
      _showChallengeInput = false;
      _challengeController.clear();
    });
  }
}
