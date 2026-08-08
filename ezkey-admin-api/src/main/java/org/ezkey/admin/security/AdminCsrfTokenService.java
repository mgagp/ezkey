/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Issues and validates non-secret CSRF tokens bound to the opaque Admin browser session token.
 *
 * <p>The token is a signed double-submit value. JavaScript may read and send it in {@code
 * X-CSRF-TOKEN}, but only the API can validate that it matches the HttpOnly session cookie.
 */
@Component
public class AdminCsrfTokenService {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final int NONCE_BYTES = 32;

  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Creates a signed CSRF token bound to the given session token.
   *
   * @param plainSessionToken opaque Admin session token from the HttpOnly cookie
   * @return signed CSRF token
   */
  public String createToken(String plainSessionToken) {
    byte[] nonce = new byte[NONCE_BYTES];
    secureRandom.nextBytes(nonce);
    String noncePart = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    String signaturePart = sign(plainSessionToken, noncePart);
    return noncePart + "." + signaturePart;
  }

  /**
   * Validates that the submitted CSRF token is bound to the current session token.
   *
   * @param plainSessionToken opaque Admin session token from the HttpOnly cookie
   * @param submittedToken token submitted in the CSRF header
   * @return true when the token is structurally valid and signature-bound to the session
   */
  public boolean isValid(String plainSessionToken, String submittedToken) {
    if (plainSessionToken == null
        || plainSessionToken.isBlank()
        || submittedToken == null
        || submittedToken.isBlank()) {
      return false;
    }
    String[] parts = submittedToken.split("\\.", -1);
    if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
      return false;
    }
    String expected = sign(plainSessionToken, parts[0]);
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII), parts[1].getBytes(StandardCharsets.US_ASCII));
  }

  private String sign(String plainSessionToken, String noncePart) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(sha256(plainSessionToken), HMAC_ALGORITHM));
      byte[] signature = mac.doFinal(noncePart.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Unable to sign Admin CSRF token", e);
    }
  }

  private byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to hash Admin session token", e);
    }
  }
}
