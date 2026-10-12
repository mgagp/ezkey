/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * DEBUG log-capture around the entryHmac projection path (Integrity cut 3 F10).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class EntriesDigestProjectionLoggingTest {

  private static final Pattern HEX_DIGEST = Pattern.compile("(?i)\\b[0-9a-f]{32,}\\b");

  private static final OffsetDateTime WIN_START =
      OffsetDateTime.of(2026, 2, 19, 10, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime WIN_END =
      OffsetDateTime.of(2026, 2, 19, 10, 5, 0, 0, ZoneOffset.UTC);

  @Mock private AuditChainCheckpointRepository checkpointRepository;
  @Mock private AuditLogRepository auditLogRepository;

  private AuditHmacService hmacService;
  private AuditChainVerificationService verificationService;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger serviceLogger;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    hmacService = TestHmacServiceFactory.create();
    verificationService =
        new AuditChainVerificationService(checkpointRepository, auditLogRepository, hmacService);
    serviceLogger = (Logger) LoggerFactory.getLogger(AuditChainVerificationService.class);
    previousLevel = serviceLogger.getLevel();
    serviceLogger.setLevel(Level.DEBUG);
    logAppender = new ListAppender<>();
    logAppender.start();
    serviceLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    serviceLogger.detachAppender(logAppender);
    serviceLogger.setLevel(previousLevel);
  }

  @Test
  void computeEntriesDigest_debugLog_containsCountButNoHexDigest() {
    String secretLookingHmac = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    String entriesDigest = hmacService.computeHmac(secretLookingHmac);
    String chainHmac = hmacService.computeHmac(entriesDigest + "|GENESIS");
    AuditChainCheckpoint cp = new AuditChainCheckpoint();
    cp.setWindowStart(WIN_START);
    cp.setWindowEnd(WIN_END);
    cp.setEntryCount(1);
    cp.setEntriesDigest(entriesDigest);
    cp.setPrevChainHmac(null);
    cp.setChainHmac(chainHmac);
    cp.setCheckpointType("REGULAR");

    when(checkpointRepository.findByWindowRange(any(), any())).thenReturn(List.of(cp));
    when(checkpointRepository.findEarliest()).thenReturn(Optional.of(cp));
    when(checkpointRepository.findLatest()).thenReturn(Optional.of(cp));
    when(auditLogRepository.findEntryHmacsInWindow(any(), any()))
        .thenReturn(List.of(new TestEntryHmacView(1L, secretLookingHmac)));

    verificationService.verifyChain(WIN_START, WIN_END);

    List<String> debugMessages =
        logAppender.list.stream()
            .filter(e -> e.getLevel() == Level.DEBUG)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    assertFalse(debugMessages.isEmpty(), "expected at least one DEBUG line from projection");
    assertTrue(
        debugMessages.stream().anyMatch(m -> m.contains("hmac projection")),
        "expected projection DEBUG marker");
    for (String msg : debugMessages) {
      assertFalse(
          HEX_DIGEST.matcher(msg).find(),
          "DEBUG must not contain hex digests/HMAC material: " + msg);
      assertFalse(msg.contains(secretLookingHmac), "DEBUG must not echo entry_hmac value");
      assertFalse(msg.contains(entriesDigest), "DEBUG must not echo entries_digest");
    }
  }
}
