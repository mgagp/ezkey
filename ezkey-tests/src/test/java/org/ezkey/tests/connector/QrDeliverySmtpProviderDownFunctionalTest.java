/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Description: On-screen fallback when the mail provider is down (#1206 step 5).
 */
package org.ezkey.tests.connector;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.response.Response;
import java.util.UUID;
import org.ezkey.tests.tags.TestTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Runs with Mailpit stopped (the script does it): the connector still answers, the send is a
 * rejection that says the provider is unavailable, and the QR stays available on screen.
 */
@Tag(TestTags.CONNECTOR_SMTP)
@DisplayName("QR delivery with the mail provider down")
class QrDeliverySmtpProviderDownFunctionalTest extends AbstractQrDeliveryFunctionalTest {

  @Test
  @DisplayName("the send is rejected as provider-unavailable and the QR stays on screen")
  void providerDown() {
    assertThat(System.getProperty("ezkey.tests.mailpit.down"))
        .as("run through scripts/ci-connector-scenarios.sh, which stops Mailpit first")
        .isEqualTo("true");
    String recipient = "down-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    Integer enrollmentId = newEnrollment(newIntegration(), recipient);

    Response sent = send(enrollmentId, "en");
    assertThat(sent.statusCode()).isEqualTo(200);
    assertThat(sent.jsonPath().getString("outcome")).isEqualTo("rejected");
    assertThat(sent.jsonPath().getString("code")).isEqualTo("provider-unavailable");

    configureForAdminApi(dockerStackConfig);
    given()
        .header("Authorization", "Bearer " + adminToken())
        .accept("image/png")
        .when()
        .get("/enrollments/" + enrollmentId + "/qrcode")
        .then()
        .statusCode(200);
  }
}
