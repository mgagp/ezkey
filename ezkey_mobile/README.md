# Ezkey Mobile App

Cross-platform Flutter application for Ezkey enrollments and authentication attempts.

## Overview

Ezkey Mobile is an open-source Flutter application that provides a simple and secure way to manage device enrollments and handle authentication attempts for the Ezkey platform.

## Features (Phase 1)

### ✅ Completed
- **Project Structure**: Modular architecture with clear separation of concerns
- **Data Models**: Enrollment, AuthAttempt, and Integration models with JSON serialization
- **API Services**: Abstract API service with HTTP implementation and mock service for development
- **State Management**: Provider-based state management for enrollments and auth attempts
- **UI Components**: Reusable widgets for status cards, loading states, and error handling
- **Home Screen**: Overview dashboard with statistics and navigation
- **Deep Link Support**: Framework for handling enrollment links via SMS
- **Mock Data**: Comprehensive mock data for development and testing

### 🔄 In Progress
- QR Code scanning for enrollments
- Detailed enrollment management screens
- Authentication attempt handling screens
- Secure storage integration

### 📋 Planned (Future Phases)
- Real API integration
- Push notifications
- Biometric authentication
- Advanced security features

## Project Structure

```
lib/
├── models/           # Data models
│   ├── enrollment.dart
│   ├── auth_attempt.dart
│   └── integration.dart
├── services/         # API services
│   ├── api_service.dart
│   └── mock_api_service.dart
├── providers/        # State management
│   ├── enrollment_provider.dart
│   └── auth_attempt_provider.dart
├── screens/          # UI screens
│   └── home_screen.dart
├── widgets/          # Reusable widgets
│   ├── status_card.dart
│   ├── loading_widget.dart
│   └── error_widget.dart
├── utils/            # Utilities
│   └── deep_link_handler.dart
└── main.dart         # App entry point
```

## Getting Started

### Prerequisites
- Flutter SDK (3.8.1 or higher)
- Dart SDK
- Android Studio / VS Code
- Android SDK (for Android development)
- Xcode (for iOS development, macOS only)

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd ezkey_mobile
   ```

2. **Install dependencies**
   ```bash
   flutter pub get
   ```

3. **Run the app**
   ```bash
   flutter run
   ```

### Development Commands

```bash
# Get dependencies
flutter pub get

# Run the app
flutter run

# Run tests
flutter test

# Build for release
flutter build apk
flutter build ios

# Generate documentation
flutter pub deps
dart doc
```

## Architecture

### State Management
The app uses the Provider pattern for state management:
- `EnrollmentProvider`: Manages enrollment state and operations
- `AuthAttemptProvider`: Manages authentication attempt state and operations

### API Layer
- `ApiService`: Abstract interface for API operations
- `HttpApiService`: HTTP implementation for production
- `MockApiService`: Mock implementation for development and testing

### Data Models
All models include:
- JSON serialization/deserialization
- Copy methods for immutability
- Proper equality and hash code implementations
- Comprehensive documentation

## Deep Links

The app supports deep links for enrollment via SMS:
- **Format**: `ezkey://enroll?integrationId=<id>`
- **Handler**: `DeepLinkHandler` utility class
- **Validation**: Built-in URI validation

## Mock Data

The app includes comprehensive mock data for development:
- 3 sample integrations (Acme Corp, TechStart Inc, Global Bank)
- 2 sample enrollments (Active and Pending)
- 2 sample auth attempts (Completed and Pending)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support and questions:
- Create an issue in the repository
- Check the documentation
- Review the code examples

---

**Note**: This is Phase 1 of the Ezkey Mobile app. Future phases will include additional features and real API integration.
