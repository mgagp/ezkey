Feature: Auth API - Enrollment Binding

  Background:
    * url authBaseUrl
    * def adminUrl = adminBaseUrl

  Scenario: Bind to an existing enrollment
    # Setup: Create integration and enrollment via Admin API
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
            "name": "Auth API Test Integration",
            "description": "Testing auth API"
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
        "name": "Auth API Test Device",
        "challengeRequired": false
      }
      """
    When method POST
    Then status 201
    And def enrollmentId = response.enrollmentId

    # Test: Bind device to enrollment
    Given url authUrl
    And path '/enrollments/bind', enrollmentId
    When method GET
    Then status 200
    And match response contains { enrollmentId: #(enrollmentId) }
    And match response.enrollmentProofToken == '#string'
    And match response.integration contains { integrationId: #(integrationId) }

  Scenario: Attempt to bind to non-existent enrollment
    Given url authUrl
    And path '/enrollments/bind', 999999
    When method GET
    Then status 404
