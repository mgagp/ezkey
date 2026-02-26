/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Test utility that creates ready-to-use {@link AuditHmacService} instances without a Spring
 * context, by writing a temporary key file to the JVM temp directory.
 *
 * @since 2026
 */
final class TestHmacServiceFactory {

  private TestHmacServiceFactory() {}

  /**
   * Creates an active {@link AuditHmacService} backed by a freshly generated 256-bit key written to
   * a temp file. {@code init()} is called automatically.
   */
  static AuditHmacService create() {
    try {
      byte[] keyBytes = new byte[32];
      new SecureRandom().nextBytes(keyBytes);
      Path keyFile = Files.createTempFile("ezkey-test-hmac-", ".key");
      keyFile.toFile().deleteOnExit();
      Files.writeString(
          keyFile, Base64.getEncoder().encodeToString(keyBytes), StandardCharsets.UTF_8);

      AuditHmacProperties props = new AuditHmacProperties();
      props.setEnabled(true);
      props.setHmacKeyFile(keyFile.toString());
      props.setInstanceId("test-instance");

      AuditHmacService service = new AuditHmacService(props);
      service.init();
      return service;
    } catch (IOException e) {
      throw new UncheckedIOException("Could not create test HMAC service", e);
    }
  }

  /**
   * Creates a disabled (inactive) {@link AuditHmacService}. {@code isActive()} returns {@code
   * false}.
   */
  static AuditHmacService createInactive() {
    AuditHmacProperties props = new AuditHmacProperties();
    props.setEnabled(false);
    AuditHmacService service = new AuditHmacService(props);
    service.init();
    return service;
  }
}
