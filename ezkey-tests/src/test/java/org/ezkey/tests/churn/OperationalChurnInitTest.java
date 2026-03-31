/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: OperationalChurnInitTest
 * Description: One-shot peer Global Admin provisioning for operational churn (state file).
 */

package org.ezkey.tests.churn;

import java.io.IOException;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Uses the bootstrap Global Admin to create a dedicated peer Global Admin ({@code churnglo}),
 * completes passwordless enrollment, and writes {@link
 * OperationalChurnGlobalAdminState#STATE_FILE}.
 *
 * <p>Run once per environment (or when the saved token is invalid). Idempotent: if the state file
 * exists and the bearer token still works against the Admin API, does nothing.
 *
 * <p>Invocation: {@code mvn test -pl ezkey-tests -P operational-churn-init} or {@code
 * ./ezkey-tests/scripts/run-operational-churn.sh --init}
 */
@Tag(TestTags.OPERATIONAL_CHURN_INIT)
@DisplayName("Operational churn init (peer Global Admin)")
public class OperationalChurnInitTest extends AbstractSecurityTest {

  @Test
  @DisplayName(
      "Provision peer Global Admin and save .ezkey-test/operational-churn-global-admin.json")
  void provisionPeerGlobalAdminAndSaveState() throws IOException {
    OperationalChurnGlobalAdminState existing = OperationalChurnGlobalAdminState.load();
    if (existing != null && authTokenManager.isAdminTokenValid(existing.bearerToken())) {
      log.info(
          "Operational churn peer Global Admin state already present and valid (username={});"
              + " skipping provisioning.",
          existing.username());
      return;
    }

    String bootstrapToken = authTokenManager.getAdminToken();
    PeerGlobalAdminHelper helper = new PeerGlobalAdminHelper(dockerStackConfig, cryptoApiClient);
    String token = helper.createPeerGlobalAdminAndObtainToken(bootstrapToken);

    new OperationalChurnGlobalAdminState(PeerGlobalAdminHelper.CHURN_GLOBAL_USERNAME, token).save();
    log.info(
        "Saved operational churn Global Admin state to {} (username={})",
        OperationalChurnGlobalAdminState.STATE_FILE,
        PeerGlobalAdminHelper.CHURN_GLOBAL_USERNAME);
  }
}
