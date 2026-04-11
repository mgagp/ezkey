/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/** Ensures every {@link EventType} is classified in exactly one {@link EventTypeFamily}. */
class EventTypeFamilyTest {

  @Test
  void everyEventTypeBelongsToExactlyOneFamily() {
    EnumSet<EventType> seen = EnumSet.noneOf(EventType.class);
    for (EventTypeFamily family : EventTypeFamily.values()) {
      for (EventType type : family.getMemberEventTypes()) {
        assertTrue(seen.add(type), "Event type appears in more than one family: " + type);
      }
    }
    assertEquals(
        EnumSet.allOf(EventType.class), seen, "Some EventType values are missing a family");
  }
}
