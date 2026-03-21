/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.authattempt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DemoMitmSignatureTamper} (demo MITM simulation). */
@DisplayName("DemoMitmSignatureTamper")
class DemoMitmSignatureTamperTest {

  @Test
  @DisplayName("tamperContextMessageForDemoNarrative bumps first CAD amount")
  void tamperContextMessage_bumpsCadAmount() {
    String original = "Devis 2 340 $ CAD (taxes incluses).";
    String tampered = DemoMitmSignatureTamper.tamperContextMessageForDemoNarrative(original);
    assertNotNull(tampered);
    assertNotEquals(original, tampered);
    assertTrue(tampered.contains("$ CAD"), "tampered message should keep CAD");
    assertTrue(tampered.contains("102 340"), () -> "tampered=" + tampered);
  }

  @Test
  @DisplayName("tamperContextMessageForDemoNarrative appends when no CAD amount")
  void tamperContextMessage_appendsWhenNoCadPattern() {
    String original = "Approuver le contrat NDA.";
    String tampered = DemoMitmSignatureTamper.tamperContextMessageForDemoNarrative(original);
    assertNotNull(tampered);
    assertTrue(tampered.startsWith(original));
    assertTrue(tampered.length() > original.length());
  }

  @Test
  @DisplayName("tamperContextMessageForDemoNarrative returns null for blank message")
  void tamperContextMessage_blankReturnsNull() {
    assertNull(DemoMitmSignatureTamper.tamperContextMessageForDemoNarrative(null));
    assertNull(DemoMitmSignatureTamper.tamperContextMessageForDemoNarrative("   "));
  }

  @Test
  @DisplayName("tamperContextTitleForDemoNarrative appends demo suffix")
  void tamperContextTitle_appendsSuffix() {
    String t = DemoMitmSignatureTamper.tamperContextTitleForDemoNarrative("Paiement fournisseur");
    assertEquals("Paiement fournisseur — modified (demo)", t);
  }

  @Test
  @DisplayName("tamperContextTitleForDemoNarrative returns null for blank")
  void tamperContextTitle_blankReturnsNull() {
    assertNull(DemoMitmSignatureTamper.tamperContextTitleForDemoNarrative(null));
  }
}
