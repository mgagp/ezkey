Feature: End-to-End Passwordless Login Flow

  Background:
    * def adminUrl = adminBaseUrl
    * def authUrl = authBaseUrl

  @e2e
  Scenario: Complete passwordless authentication flow
    # Step 1: Create Integration (Admin API)
    Given url adminUrl
    And path '/integrations'
    And request
      """
      {
        "logo": "https://example.com/logo.png",
        "active": true,
        "i18n": [
          {
            "lang": "en",
            "name": "E2E Test Integration",
            "description": "End-to-end testing"
          }
        ]
      }
      """
    When method POST
    Then status 201
    And def integrationId = response.integrationId
    And print 'Created integration:', integrationId

    # Step 2: Create Enrollment (Admin API)
    Given url adminUrl
    And path '/enrollments'
    And request
      """
      {
        "integrationId": #(integrationId),
        "name": "E2E Test Device",
        "challengeRequired": false
      }
      """
    When method POST
    Then status 201
    And def enrollmentId = response.enrollmentId
    And print 'Created enrollment:', enrollmentId

    # Step 3: Bind Device (Auth API)
    Given url authUrl
    And path '/enrollments/bind', enrollmentId
    When method GET
    Then status 200
    And match response contains { enrollmentId: #(enrollmentId) }
    And def enrollmentProofToken = response.enrollmentProofToken
    And print 'Device bound, proof token received'

    # Step 4: Verify Enrollment (Auth API)
    # Note: In real scenario, device would generate keys and sign proof token
    # For testing, we'll simulate this with mock data
    Given url authUrl
    And path '/enrollments/verify'
    And request
      """
      {
        "enrollmentId": #(enrollmentId),
        "enrollmentProofToken": #(enrollmentProofToken),
        "devicePublicKey": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0Z6Qzp3X9r8h5K9YnW1x\nTest Mock Key Data For Testing Purposes Only\n-----END PUBLIC KEY-----"
      }
      """
    When method POST
    Then status 200
    And print 'Enrollment verified'

    # Step 5: Create Auth Attempt (Admin API)
    Given url adminUrl
    And path '/auth-attempts'
    And request
      """
      {
        "enrollmentId": #(enrollmentId)
      }
      """
    When method POST
    Then status 201
    And def authAttemptId = response.authAttemptId
    And print 'Created auth attempt:', authAttemptId

    # Step 6: Check Pending Auth (Auth API - simulating mobile device polling)
    Given url authUrl
    And path '/auth-attempts/pending', enrollmentId
    When method POST
    Then status 200
    And match response contains { authAttemptId: #(authAttemptId) }
    And def authAttemptProofToken = response.authAttemptProofToken
    And print 'Auth attempt found on device'

    # Step 7: Respond to Auth Attempt (Auth API - simulating user approval)
    Given url authUrl
    And path '/auth-attempts/respond'
    And request
      """
      {
        "authAttemptId": #(authAttemptId),
        "authAttemptProofToken": #(authAttemptProofToken),
        "accepted": true
      }
      """
    When method POST
    Then status 200
    And print 'User approved authentication'

    # Step 8: Check Auth Attempt Status (Admin API)
    Given url adminUrl
    And path '/auth-attempts', authAttemptId
    When method GET
    Then status 200
    And match response.status == 'ACCEPTED'
    And print 'Authentication completed successfully'
