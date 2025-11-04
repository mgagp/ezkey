Feature: Admin API - Enrollment Management

  Background:
    * url adminBaseUrl

  Scenario: Create an enrollment
    # First create an integration
    Given path '/integrations'
    And request
      """
      {
        "logo": "https://example.com/logo.png",
        "active": true,
        "i18n": [
          {
            "lang": "en",
            "name": "Integration for Enrollment",
            "description": "Test integration"
          }
        ]
      }
      """
    When method POST
    Then status 201
    And def integrationId = response.integrationId

    # Create an enrollment
    Given path '/enrollments'
    And request
      """
      {
        "integrationId": #(integrationId),
        "name": "Test Device",
        "challengeRequired": false
      }
      """
    When method POST
    Then status 201
    And match response contains { enrollmentId: '#number', integrationId: #(integrationId) }
    And match response.name == 'Test Device'
    And match response.status == 'CREATED'

  Scenario: List enrollments
    Given path '/enrollments'
    When method GET
    Then status 200
    And match response == '#array'

  Scenario: Delete an enrollment
    # First create integration and enrollment
    Given path '/integrations'
    And request
      """
      {
        "logo": "https://example.com/logo.png",
        "active": true,
        "i18n": [
          {
            "lang": "en",
            "name": "Integration for Enrollment Delete",
            "description": "Test"
          }
        ]
      }
      """
    When method POST
    Then status 201
    And def integrationId = response.integrationId

    Given path '/enrollments'
    And request
      """
      {
        "integrationId": #(integrationId),
        "name": "Device to Delete",
        "challengeRequired": false
      }
      """
    When method POST
    Then status 201
    And def enrollmentId = response.enrollmentId

    # Delete the enrollment
    Given path '/enrollments', enrollmentId
    When method DELETE
    Then status 204
