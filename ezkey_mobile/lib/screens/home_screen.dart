import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../providers/enrollment_provider.dart';
import '../providers/auth_attempt_provider.dart';
import '../widgets/status_card.dart';
import '../widgets/loading_widget.dart';
import '../widgets/error_widget.dart';

/// Home screen for the Ezkey mobile app
/// 
/// This screen displays an overview of enrollments and authentication attempts,
/// with navigation to detailed views.
/// 
/// @since 2025
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  @override
  void initState() {
    super.initState();
    // Load data when screen initializes
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _loadData();
    });
  }

  Future<void> _loadData() async {
    final enrollmentProvider = context.read<EnrollmentProvider>();
    final authAttemptProvider = context.read<AuthAttemptProvider>();
    
    await Future.wait([
      enrollmentProvider.loadEnrollments(),
      authAttemptProvider.loadAuthAttempts(),
    ]);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Ezkey'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadData,
            tooltip: 'Refresh',
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _loadData,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildWelcomeSection(),
              const SizedBox(height: 24),
              _buildStatusSection(),
              const SizedBox(height: 24),
              _buildNavigationSection(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildWelcomeSection() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  Icons.security,
                  size: 32,
                  color: Theme.of(context).primaryColor,
                ),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Welcome to Ezkey',
                        style: TextStyle(
                          fontSize: 24,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        'Manage your enrollments and authentication attempts',
                        style: TextStyle(
                          fontSize: 14,
                          color: Colors.grey,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatusSection() {
    return Consumer2<EnrollmentProvider, AuthAttemptProvider>(
      builder: (context, enrollmentProvider, authAttemptProvider, child) {
        if (enrollmentProvider.isLoading || authAttemptProvider.isLoading) {
          return const LoadingWidget();
        }

        if (enrollmentProvider.error != null || authAttemptProvider.error != null) {
          return CustomErrorWidget(
            message: enrollmentProvider.error ?? authAttemptProvider.error!,
            onRetry: _loadData,
          );
        }

        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Status Overview',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: StatusCard(
                    title: 'Enrollments',
                    count: enrollmentProvider.enrollments.length,
                    activeCount: enrollmentProvider.activeEnrollmentsCount,
                    pendingCount: enrollmentProvider.pendingEnrollmentsCount,
                    icon: Icons.person_add,
                    color: Colors.blue,
                    onTap: () => _navigateToEnrollments(),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: StatusCard(
                    title: 'Auth Attempts',
                    count: authAttemptProvider.authAttempts.length,
                    activeCount: authAttemptProvider.completedAuthAttemptsCount,
                    pendingCount: authAttemptProvider.pendingAuthAttemptsCount,
                    icon: Icons.lock,
                    color: Colors.green,
                    onTap: () => _navigateToAuthAttempts(),
                  ),
                ),
              ],
            ),
          ],
        );
      },
    );
  }

  Widget _buildNavigationSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Quick Actions',
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 12),
        Card(
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.person_add, color: Colors.blue),
                title: const Text('Enrollments'),
                subtitle: const Text('Manage your device enrollments'),
                trailing: const Icon(Icons.arrow_forward_ios),
                onTap: _navigateToEnrollments,
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(Icons.lock, color: Colors.green),
                title: const Text('Authentication Attempts'),
                subtitle: const Text('View and complete auth requests'),
                trailing: const Icon(Icons.arrow_forward_ios),
                onTap: _navigateToAuthAttempts,
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(Icons.qr_code_scanner, color: Colors.orange),
                title: const Text('Scan QR Code'),
                subtitle: const Text('Enroll via QR code'),
                trailing: const Icon(Icons.arrow_forward_ios),
                onTap: _navigateToQrScanner,
              ),
            ],
          ),
        ),
      ],
    );
  }

  void _navigateToEnrollments() {
    Navigator.pushNamed(context, '/enrollments');
  }

  void _navigateToAuthAttempts() {
    Navigator.pushNamed(context, '/auth-attempts');
  }

  void _navigateToQrScanner() {
    Navigator.pushNamed(context, '/qr-scanner');
  }
} 