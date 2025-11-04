Feature: Admin API - Integration Management

  Background:
    * url adminBaseUrl

  Scenario: Create a new integration
    Given path '/integrations'
    And request
      """
      {
        "logo": "https://example.com/logo.png",
        "active": true,
        "i18n": [
          {
            "lang": "en",
            "name": "Test Integration",
            "description": "A test integration for functional testing"
          }
        ]
      }
      """
    When method POST
    Then status 201
    And match response contains { integrationId: '#number', active: true }
    And match response.i18n[0] contains { lang: 'en', name: 'Test Integration' }

  Scenario: List all integrations
    Given path '/integrations'
    When method GET
    Then status 200
    And match response == '#array'

  Scenario: Get integration by ID
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
            "name": "Test Integration for Get",
            "description": "A test integration"
          }
        ]
      }
      """
    When method POST
    Then status 201
    And def integrationId = response.integrationId

    # Now get the integration
    Given path '/integrations', integrationId
    When method GET
    Then status 200
    And match response.integrationId == integrationId
    And match response.i18n[0].name == 'Test Integration for Get'

  Scenario: Delete an integration
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
            "name": "Test Integration to Delete",
            "description": "Will be deleted"
          }
        ]
      }
      """
    When method POST
    Then status 201
    And def integrationId = response.integrationId

    # Now delete it
    Given path '/integrations', integrationId
    When method DELETE
    Then status 204

    # Verify it's deleted
    Given path '/integrations', integrationId
    When method GET
    Then status 404
