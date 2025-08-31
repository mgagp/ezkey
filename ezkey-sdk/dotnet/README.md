# Ezkey .NET SDK

The Ezkey .NET SDK provides easy integration with Ezkey Admin and Auth APIs for .NET applications.

## Features

- **.NET 6+ Support**: Modern .NET with nullable reference types
- **Minimal Dependencies**: Uses only System.Text.Json and HttpClient
- **Configurable Endpoints**: Support for custom Admin and Auth API URLs
- **Complete API Coverage**: All Admin and Auth API operations
- **Async/Await**: Modern asynchronous programming model
- **Strong Typing**: Full type safety with C# generics

## Quick Start

### 1. Build the SDK

```bash
# Linux/Mac
./build.sh

# Windows
build.cmd
```

### 2. Install the SDK

```bash
dotnet add package Ezkey.Sdk
```

### 3. Basic Usage

```csharp
using Ezkey.Sdk;

// Initialize client with default localhost URLs
var client = new EzkeyClient();

// Or with custom URLs
var client = new EzkeyClient(
    "https://admin.yourcompany.com",
    "https://auth.yourcompany.com"
);

try
{
    // Use Admin API
    var integration = await client.Admin.CreateIntegrationAsync(
        "https://company.com/logo.png",
        "My App",
        "My application description"
    );
    
    // Use Auth API
    var binding = await client.Auth.BindEnrollmentAsync(enrollmentId);
}
catch (EzkeyException ex)
{
    Console.WriteLine($"API Error: {ex.Message}");
    Console.WriteLine($"Status: {ex.StatusCode}");
}
```

## Requirements

- .NET 6+
- System.Text.Json
- System.Net.Http

## License

MIT License - see LICENSE file in the project root.