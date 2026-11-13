/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminTokenPurposeMandatoryTest
 * Description: Token purpose is mandatory at mint; no silent SESSION default (#1122).
 */

package org.ezkey.integration.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.OffsetDateTime;
import org.ezkey.integration.domain.AdminTokenPurpose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link AdminToken} never falls back to {@link AdminTokenPurpose#SESSION} when a
 * purpose is omitted, so a forgotten purpose cannot mint a full Admin API session (#1122).
 *
 * @author Ezkey contributors
 * @since 2026
 */
@DisplayName("AdminToken — purpose is mandatory (#1122)")
class AdminTokenPurposeMandatoryTest {

  @Test
  @DisplayName("Constructor rejects a null purpose instead of defaulting to SESSION")
  void constructor_nullPurpose_failsFast() {
    OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(1);

    assertThatNullPointerException()
        .isThrownBy(() -> new AdminToken("hash", new EzkeyAdmin(), "TENANT", expiresAt, null))
        .withMessageContaining("tokenPurpose");
  }

  @Test
  @DisplayName("Setter rejects a null purpose")
  void setter_nullPurpose_failsFast() {
    AdminToken token = new AdminToken();

    assertThatNullPointerException()
        .isThrownBy(() -> token.setTokenPurpose(null))
        .withMessageContaining("tokenPurpose");
  }

  @Test
  @DisplayName("No-arg (JPA) constructor carries no implicit SESSION purpose")
  void noArgConstructor_hasNoDefaultPurpose() {
    assertThat(new AdminToken().getTokenPurpose()).isNull();
  }

  @Test
  @DisplayName("Constructor keeps the explicit purpose")
  void constructor_explicitPurpose_isKept() {
    AdminToken token =
        new AdminToken(
            "hash",
            new EzkeyAdmin(),
            "TENANT",
            OffsetDateTime.now().plusDays(1),
            AdminTokenPurpose.ACTIVATION);

    assertThat(token.getTokenPurpose()).isEqualTo(AdminTokenPurpose.ACTIVATION);
  }
}
