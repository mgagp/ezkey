import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'services/mock_api_service.dart';
import 'providers/enrollment_provider.dart';
import 'providers/auth_attempt_provider.dart';
import 'screens/home_screen.dart';

/// Main entry point for the Ezkey mobile application
/// 
/// This application provides enrollment and authentication management
/// for the Ezkey platform.
/// 
/// @since 2025
void main() {
  runApp(const EzkeyApp());
}

/// Main application widget
/// 
/// Configures the app theme, providers, and navigation.
/// 
/// @since 2025
class EzkeyApp extends StatelessWidget {
  const EzkeyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        // Services
        Provider<MockApiService>(
          create: (_) => MockApiService(),
        ),
        
        // Providers
        ChangeNotifierProvider<EnrollmentProvider>(
          create: (context) => EnrollmentProvider(
            context.read<MockApiService>(),
          ),
        ),
        ChangeNotifierProvider<AuthAttemptProvider>(
          create: (context) => AuthAttemptProvider(
            context.read<MockApiService>(),
          ),
        ),
      ],
      child: MaterialApp(
        title: 'Ezkey',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(
            seedColor: const Color(0xFF1976D2),
            brightness: Brightness.light,
          ),
          useMaterial3: true,
          appBarTheme: const AppBarTheme(
            centerTitle: true,
            elevation: 0,
          ),
          cardTheme: CardThemeData(
            elevation: 2,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
          elevatedButtonTheme: ElevatedButtonThemeData(
            style: ElevatedButton.styleFrom(
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
          ),
        ),
        home: const HomeScreen(),
        routes: {
          '/': (context) => const HomeScreen(),
          // Additional routes will be added in future phases
        },
      ),
    );
  }
}
