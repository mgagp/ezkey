import 'package:flutter/material.dart';
import 'widgets/demo_screen.dart';

/// Main entry point for the Ezkey Demo application
/// 
/// This application provides a single-screen demo of Ezkey enrollment
/// and authentication workflows with a dark theme.
/// 
/// @since 2025
void main() {
  runApp(const EzkeyDemoApp());
}

/// Main application widget
/// 
/// Configures the app theme and navigation.
/// 
/// @since 2025
class EzkeyDemoApp extends StatelessWidget {
  const EzkeyDemoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Ezkey Demo',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        // Dark theme with cyan accent color
        brightness: Brightness.dark,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF00BCD4), // Cyan accent
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
        
        // App bar theme
        appBarTheme: const AppBarTheme(
          centerTitle: true,
          elevation: 0,
          backgroundColor: Colors.transparent,
          foregroundColor: Colors.white,
        ),
        
        // Card theme
        cardTheme: CardThemeData(
          elevation: 4,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          color: const Color(0xFF1E1E1E),
        ),
        
        // Elevated button theme
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFF00BCD4),
            foregroundColor: Colors.black,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(8),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
          ),
        ),
        
        // Text field theme
        inputDecorationTheme: InputDecorationTheme(
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: Color(0xFF00BCD4)),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8),
            borderSide: const BorderSide(color: Color(0xFF00BCD4), width: 2),
          ),
          filled: true,
          fillColor: const Color(0xFF2A2A2A),
        ),
        
        // Text theme
        textTheme: const TextTheme(
          headlineLarge: TextStyle(
            color: Colors.white,
            fontSize: 28,
            fontWeight: FontWeight.bold,
          ),
          headlineMedium: TextStyle(
            color: Colors.white,
            fontSize: 24,
            fontWeight: FontWeight.w600,
          ),
          bodyLarge: TextStyle(
            color: Colors.white,
            fontSize: 16,
          ),
          bodyMedium: TextStyle(
            color: Colors.white70,
            fontSize: 14,
          ),
        ),
      ),
      home: const DemoScreen(),
    );
  }
}
