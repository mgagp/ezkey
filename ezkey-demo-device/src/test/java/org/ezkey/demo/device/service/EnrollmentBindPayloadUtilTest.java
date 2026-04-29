package org.ezkey.demo.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnrollmentBindPayloadUtilTest {

  @Test
  void buildBindPayload_matches_backend_canonical_form() {
    assertThat(
            EnrollmentBindPayloadUtil.buildBindPayload(
                "pt",
                7,
                "integrationPk",
                "ed25519",
                "Cafe\u0301 Demo",
                "desc",
                "Phone",
                3,
                "Tenant",
                "Workspace",
                true))
        .isEqualTo("pt|7|integrationPk|ed25519|Café Demo|desc|Phone|3|Tenant|Workspace|true");
  }
}
