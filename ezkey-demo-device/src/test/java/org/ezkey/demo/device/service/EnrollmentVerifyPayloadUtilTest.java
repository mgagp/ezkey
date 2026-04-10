package org.ezkey.demo.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnrollmentVerifyPayloadUtilTest {

  @Test
  void buildVerifyDevicePayload_matchesCanonicalFourSegmentFormat() {
    assertThat(EnrollmentVerifyPayloadUtil.buildVerifyDevicePayload("tok", 42, 123456, "spki"))
        .isEqualTo("tok|42|123456|spki");
  }
}
