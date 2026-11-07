/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.tests.connector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.ezkey.tests.tags.TestTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Searches the real output of every container for what must never be logged on the send path. Run
 * it after the scenarios, so it covers their logs too.
 *
 * <p>This shows known shapes are absent in the scenarios played. It does not prove that no rare
 * error path leaks.
 */
@Tag(TestTags.CONNECTOR_MOCK)
@DisplayName("QR delivery log canary")
class QrDeliveryLogCanaryFunctionalTest extends AbstractQrDeliveryFunctionalTest {

  private static final String CANARY_RECIPIENT = "canary.recipient@example.com";
  private static final String SCENARIO_RECIPIENT = "mock-provider-rejected@example.com";
  private static final Pattern LONG_BASE64 = Pattern.compile("[A-Za-z0-9+/]{400,}={0,2}");

  @Test
  @DisplayName("no recipient, token, QR or request body in any container log")
  void logsHoldNothingSensitive() throws Exception {
    Integer enrollmentId = newEnrollment(newIntegration(), CANARY_RECIPIENT);
    String proofToken = get("/enrollments/" + enrollmentId).jsonPath().get("enrollmentProofToken");
    assertThat(proofToken).isNotBlank();
    send(enrollmentId, "fr");

    List<String> containers = containers();
    assertThat(containers).contains("ezkey-admin-api", "ezkey-connector-mock");

    for (String container : containers) {
      String logs = logs(container);
      assertThat(logs).as(container).doesNotContain(CANARY_RECIPIENT, SCENARIO_RECIPIENT);
      assertThat(logs).as(container).doesNotContain(proofToken);
      assertThat(logs).as(container).doesNotContain("iVBORw0KGgo", "qrPngBase64");
      assertThat(LONG_BASE64.matcher(logs).find()).as(container + " long base64").isFalse();
    }
  }

  private static List<String> containers() throws IOException, InterruptedException {
    List<String> names = new ArrayList<>();
    for (String name : docker("ps", "-a", "--format", "{{.Names}}").split("\\R")) {
      if (name.startsWith("ezkey-")) {
        names.add(name);
      }
    }
    return names;
  }

  private static String logs(String container) throws IOException, InterruptedException {
    return docker("logs", container);
  }
}
