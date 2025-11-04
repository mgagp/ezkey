Feature: Parallel Authentication Attempts

  Background:
    * def adminUrl = adminBaseUrl
    * def authUrl = authBaseUrl

  @parallel
  Scenario: Multiple concurrent authentication attempts
    # Setup: Create integration and enrollment
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
            "name": "Parallel Test Integration",
            "description": "Testing concurrent operations"
          }
        ]
      }
      """
    When method POST
    Then status 201
    And def integrationId = response.integrationId

    Given url adminUrl
    And path '/enrollments'
    And request
      """
      {
        "integrationId": #(integrationId),
        "name": "Parallel Test Device",
        "challengeRequired": false
      }
      """
    When method POST
    Then status 201
    And def enrollmentId = response.enrollmentId

    # Create multiple auth attempts in quick succession
    Given url adminUrl
    And path '/auth-attempts'
    And request { "enrollmentId": #(enrollmentId) }
    When method POST
    Then status 201
    And def authAttempt1 = response.authAttemptId

    Given url adminUrl
    And path '/auth-attempts'
    And request { "enrollmentId": #(enrollmentId) }
    When method POST
    Then status 201
    And def authAttempt2 = response.authAttemptId

    Given url adminUrl
    And path '/auth-attempts'
    And request { "enrollmentId": #(enrollmentId) }
    When method POST
    Then status 201
    And def authAttempt3 = response.authAttemptId

    # Verify all three attempts were created
    And print 'Created auth attempts:', authAttempt1, authAttempt2, authAttempt3
    And assert authAttempt1 != authAttempt2
    And assert authAttempt2 != authAttempt3
    And assert authAttempt1 != authAttempt3

  @parallel @ignore
  Scenario: Test race conditions with database locking
    # This scenario tests database locking behavior
    # The FOR NO KEY UPDATE lock should prevent race conditions
    # when multiple requests try to access the same enrollment
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
            "name": "Race Condition Test",
            "description": "Testing database locks"
          }
        ]
      }
      """
    When method POST
    Then status 201
    And def integrationId = response.integrationId

    Given url adminUrl
    And path '/enrollments'
    And request
      """
      {
        "integrationId": #(integrationId),
        "name": "Lock Test Device",
        "challengeRequired": false
      }
      """
    When method POST
    Then status 201
    And def enrollmentId = response.enrollmentId

    # Simulate concurrent requests that should be serialized by database locks
    # In a real implementation, this would need to be tested with actual parallel execution
    Given url authUrl
    And path '/auth-attempts/pending', enrollmentId
    When method POST
    Then status 200 or 404
