/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */
package org.ezkey.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Test helper for creating master key files with the secure, owner-only permissions that {@link
 * TinkKeyManager} requires on POSIX systems.
 *
 * <p>Production ({@code TinkKeyManager.verifyFilePermissions}) rejects a master key file that is
 * readable or writable by group/others on non-Windows platforms. Tests must therefore create the
 * file with {@code rw-------} (0600); otherwise the default umask (typically 0644 on Linux/macOS)
 * makes initialization fail. That gap was previously masked on Windows, where the POSIX permission
 * check is skipped, so tests only exercised the happy path there.
 *
 * @since 2026
 */
final class TestMasterKeys {

  private TestMasterKeys() {}

  /**
   * Generate a random 32-byte master key, write it Base64-encoded to {@code path}, and restrict the
   * file to owner-only permissions on POSIX file systems.
   *
   * @param path destination master key file
   * @return the raw 32-byte master key that was written (decoded form)
   * @throws IOException if the file cannot be written or its permissions cannot be applied
   */
  static byte[] write(Path path) throws IOException {
    byte[] masterKey = new byte[32];
    new SecureRandom().nextBytes(masterKey);
    Files.writeString(path, Base64.getEncoder().encodeToString(masterKey), StandardCharsets.UTF_8);
    restrictToOwner(path);
    return masterKey;
  }

  /**
   * Restrict {@code path} to {@code rw-------} on POSIX file systems; a no-op on file systems
   * without POSIX permission support (e.g. Windows), mirroring the production permission check.
   *
   * @param path file whose permissions should be restricted to the owner
   * @throws IOException if the permissions cannot be applied
   */
  static void restrictToOwner(Path path) throws IOException {
    PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
    if (view != null) {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }
  }
}
