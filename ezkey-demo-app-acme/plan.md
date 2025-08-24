# Ezkey Demo App - ACME Inc

## Project Objective

To realistically test and demonstrate Ezkey's functionality, we are creating the `ezkey-demo-app-acme` sub-project whose purpose will be to pretend to be an ACME application that integrates Ezkey. We will need to generate a Spring Boot application by revising the existing pom. Initially, just create the minimal structure to launch the app on port 8082. We need to include what is necessary to build a UI with the AHA stack with Neo Brutalism UI. For the UI, start with a simple home screen that presents a sober ACME logo.

After the initial phase, we will generate API clients allowing access to Ezkey Admin APIs in order to invoke the endpoints for integration, enrollment and authattempt. We will need to add to the UI the screens allowing to enter the information to create an integration, create an enrollment, create an auth attempt in a well-organized way, as much as possible. Error handling will be minimal. The goal is to be able to chain the steps of the Ezkey APIs to demonstrate their proper functioning.

## Phase 1: Functional Minimal Draft

### 1.1 Spring Boot Configuration

- [ ] **Revise pom.xml**: Transform from `packaging=pom` to `packaging=jar`
- [ ] **Basic dependencies**:
  - Spring Boot Starter Web
  - Spring Boot Starter Thymeleaf
  - Spring Boot DevTools
  - Webjars for front-end resources
- [ ] **Port configuration**: Application on port 8082
- [ ] **Package structure**:
  - `org.ezkey.demo.acme` (main package)
  - `org.ezkey.demo.acme.controller` (web controllers)
  - `org.ezkey.demo.acme.config` (configuration)

### 1.2 Front-End Technical Stack

#### AHA Stack (Astro, htmx, Alpine.js)
- [ ] **htmx**: For dynamic interactions without complex JavaScript
- [ ] **Alpine.js**: For lightweight client-side reactivity
- [ ] **Modern CSS**: For Neo Brutalism UI

#### Neo Brutalism UI
- [ ] **Colors**: Contrasted palette (black, white, bright colors)
- [ ] **Typography**: Bold sans-serif font
- [ ] **Shapes**: Thick borders, pronounced shadows
- [ ] **Layout**: Geometric grids, controlled asymmetry

### 1.3 Application Structure

- [ ] **Main class**: `AcmeDemoApplication.java`
- [ ] **Main controller**: `HomeController.java`
- [ ] **Home page**: `index.html` (Thymeleaf)
- [ ] **Static assets**: CSS, images, JavaScript
- [ ] **ACME logo**: Sober and professional design

### 1.4 Initial Functionality

- [ ] **Home page**:
  - Centered ACME logo
  - Title "ACME Inc - Protected Application"
  - Subtitle explaining Ezkey integration
  - Sober Neo Brutalism design
- [ ] **Navigation**: Simple menu preparing for future functionalities
- [ ] **Footer**: Demonstration information

## Phase 2: Ezkey API Client Integration

### 2.1 API Client Generation

- [ ] **OpenAPI Generator**: Generate Java clients for Ezkey Admin API
- [ ] **HTTP Configuration**: RestTemplate or WebClient configured
- [ ] **Error handling**: Basic handler for API responses
- [ ] **Models**: DTOs for Ezkey requests/responses

### 2.2 Integration Services

- [ ] **IntegrationService**: Service to manage Ezkey integrations
- [ ] **EnrollmentService**: Service to manage enrollments
- [ ] **AuthAttemptService**: Service to manage authentication attempts
- [ ] **Configuration**: Ezkey API URLs (admin-api on port 9080)

## Phase 3: Complete User Interface

### 3.1 Integration Management Screens

- [ ] **Integrations page**: `/integrations`
  - List of existing integrations
  - "Create integration" button
  - Actions: View, Edit, Delete
- [ ] **Integration creation form**: `/integrations/create`
  - Fields: Name, Description
  - Client-side validation (Alpine.js)
  - Submission via htmx
- [ ] **Integration details**: `/integrations/{id}`
  - Complete information
  - QR Code for enrollment
  - List of associated enrollments

### 3.2 Enrollment Management Screens

- [ ] **Enrollments page**: `/enrollments`
  - List of enrollments by integration
  - Status: Active, Inactive, Pending
  - Filtering by integration
- [ ] **Enrollment creation form**: `/enrollments/create`
  - Integration selection
  - Device name
  - Challenge configuration
- [ ] **Enrollment details**: `/enrollments/{id}`
  - Device information
  - Authentication history
  - Management actions

### 3.3 Authentication Attempt Management Screens

- [ ] **Auth attempts page**: `/auth-attempts`
  - List of recent attempts
  - Status: Pending, Approved, Denied
  - Filtering by enrollment/integration
- [ ] **Auth attempt creation form**: `/auth-attempts/create`
  - Enrollment selection
  - Challenge configuration
- [ ] **Attempt details**: `/auth-attempts/{id}`
  - Attempt timeline
  - Result and details

## Phase 4: Complete Demonstration Flow

### 4.1 Demonstration Scenario

- [ ] **Guided workflow**: `/demo/workflow`
  - Step 1: Create an integration
  - Step 2: Create an enrollment
  - Step 3: Create an authentication attempt
  - Step 4: View the result
- [ ] **Test data**: Pre-filling with realistic data

### 4.2 Advanced Features

- [ ] **Dashboard**: Overview of statistics
- [ ] **Real-time logs**: Display of interactions with Ezkey
- [ ] **Data export**: Ability to export results
- [ ] **Demo reset**: Data cleanup to restart

## Technical Specifications

### Architecture
- **Framework**: Spring Boot 3.3.6
- **Java**: Version 21
- **Port**: 8082
- **Template Engine**: Thymeleaf
- **CSS Framework**: Custom Neo Brutalism
- **JavaScript**: htmx + Alpine.js

### Main Dependencies
- `spring-boot-starter-web`
- `spring-boot-starter-thymeleaf`
- `spring-boot-devtools`
- `webjars-locator-core`
- `htmx` (via WebJars)
- `alpinejs` (via WebJars)

### Directory Structure
```
src/main/
├── java/org/ezkey/demo/acme/
│   ├── AcmeDemoApplication.java
│   ├── controller/
│   ├── service/
│   ├── model/
│   └── config/
├── resources/
│   ├── static/
│   │   ├── css/
│   │   ├── js/
│   │   └── images/
│   ├── templates/
│   └── application.properties
```

### Configuration
- **Port**: `server.port=8082`
- **Ezkey Admin API**: `ezkey.admin.api.url=http://localhost:9080`

## Success Criteria

### Phase 1
- [ ] Spring Boot application starts on port 8082
- [ ] Home page displays ACME logo with Neo Brutalism design
- [ ] htmx and Alpine.js are correctly integrated

### Phase 2
- [ ] Ezkey API clients functional
- [ ] Successful calls to ezkey-admin-api
- [ ] Basic error handling

### Phase 3
- [ ] Complete CRUD for Integrations, Enrollments, Auth Attempts
- [ ] Intuitive and responsive user interface
- [ ] Validation and user feedback

### Phase 4
- [ ] Complete demonstration of Ezkey flow
- [ ] Integrated user documentation

## Development Notes

- **Error handling**: Minimal but informative
- **Security**: Demo application, basic security
- **Performance**: Optimized for demonstration, not production
- **Documentation**: Self-documented code with standard Ezkey javadoc
- **Tests**: Basic unit tests for critical services