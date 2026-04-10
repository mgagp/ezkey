package org.ezkey.demo.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Ensures {@link org.ezkey.demo.device.service.EnrollmentStoreService.Record} round-trips through
 * the same ObjectMapper configuration used by the store (demo enrollment JSON files).
 */
class EnrollmentStoreRecordJsonRoundtripTest {

  @Test
  void enrollmentProofToken_roundTrips() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    EnrollmentStoreService.Record original =
        new EnrollmentStoreService.Record(
            2,
            null,
            "Name",
            null,
            "integ-pk",
            "proof-token-value",
            "dev-pub",
            "dev-priv",
            null,
            "Device",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "NONE");
    byte[] json = mapper.writeValueAsBytes(original);
    String asText = new String(json, java.nio.charset.StandardCharsets.UTF_8);
    assertThat(asText).contains("enrollmentProofToken");
    assertThat(asText).contains("proof-token-value");

    EnrollmentStoreService.Record back =
        mapper.readValue(json, EnrollmentStoreService.Record.class);
    assertThat(back.enrollmentProofToken()).isEqualTo("proof-token-value");
  }
}
