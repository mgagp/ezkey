package org.ezkey.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class providing cryptographic hashing for sensitive data lookups.
 *
 * <p>Generates deterministic SHA-256 hashes that can be safely persisted alongside encrypted
 * values. Hashes allow secure equality comparisons without exposing original secrets in the
 * database. Intended for proof tokens and other high-entropy secrets that must remain searchable
 * after Tink encryption is applied.
 *
 * @since 2025
 */
public final class SensitiveDataHasher {

  private SensitiveDataHasher() {
    // Utility class
  }

  /**
   * Computes a SHA-256 hash for the provided value and returns the lowercase hexadecimal string.
   *
   * <p>Returned hashes are always 64 hexadecimal characters. If the input is {@code null} or blank,
   * this method returns {@code null}. Blank inputs are not hashed to avoid leaking the encoding of
   * empty secrets.
   *
   * @param value the sensitive value to hash
   * @return hexadecimal SHA-256 hash or {@code null} when value is null/blank
   */
  public static String sha256Hex(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder(hashBytes.length * 2);
      for (byte hashByte : hashBytes) {
        String hex = Integer.toHexString(0xff & hashByte);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm not available", exception);
    }
  }
}
