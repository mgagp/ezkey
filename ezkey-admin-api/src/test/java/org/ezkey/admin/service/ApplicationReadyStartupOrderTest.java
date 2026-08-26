/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ApplicationReadyStartupOrderTest
 * Description: Contract test that Admin API ApplicationReady listeners keep encryption-key sync first.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.ezkey.security.ApplicationReadyStartupOrder;
import org.ezkey.security.KeyRotationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

/**
 * Guards the Admin API cold-start sequence after TX-002 moved keyset sync onto {@link
 * ApplicationReadyEvent}.
 *
 * <p>Enrollment bootstrap writes {@code *_encryption_key_id} foreign keys. Empty-table keyset sync
 * must run first or insert fails and ShedLock can skip a later retry.
 *
 * @author Ezkey contributors
 * @since 2026
 */
class ApplicationReadyStartupOrderTest {

  @Test
  @DisplayName("Keyset sync runs before initial admin and MFA bootstrap on ApplicationReady")
  void keysetSyncRunsBeforeAdminMfaBootstrap() throws NoSuchMethodException {
    int keysetSync = orderOf(KeyRotationService.class, "initializeKeysetSync");
    int initialAdmin = orderOf(InitialGlobalAdminService.class, "initializeGlobalAdmin");
    int mfaBootstrap = orderOf(AdminBootstrapService.class, "bootstrapAdminMfa");

    assertEquals(ApplicationReadyStartupOrder.KEYSET_SYNC, keysetSync);
    assertEquals(ApplicationReadyStartupOrder.INITIAL_GLOBAL_ADMIN, initialAdmin);
    assertEquals(ApplicationReadyStartupOrder.ADMIN_MFA_BOOTSTRAP, mfaBootstrap);
    assertTrue(keysetSync < initialAdmin);
    assertTrue(initialAdmin < mfaBootstrap);
  }

  private static int orderOf(Class<?> type, String methodName) throws NoSuchMethodException {
    Method method = type.getMethod(methodName);
    assertNotNull(
        method.getAnnotation(EventListener.class),
        type.getSimpleName() + "#" + methodName + " must listen for ApplicationReadyEvent");
    Order order = method.getAnnotation(Order.class);
    assertNotNull(order, type.getSimpleName() + "#" + methodName + " must declare @Order");
    return order.value();
  }
}
